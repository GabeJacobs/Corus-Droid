package fm.corus.android.data.repository

import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.GiftAnalytics
import kotlinx.coroutines.CancellationException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class GiftInventory(
    val capacity: Int,
    val available: Int,
    val used: Int,
    val nextRefillAtMs: Long?,
) {
    val isEmpty: Boolean get() = available <= 0
}

data class GiftReceiptPreview(
    val senderId: String,
    val senderUsername: String,
    val senderDisplayName: String,
    val senderAvatarUrl: String?,
    val giftType: String,
    val note: String?,
    val sentAtMs: Long,
)

data class GiftStatus(
    val inventory: GiftInventory,
    val catalogIds: List<String>,
)

data class GiftSendResult(
    val giftType: String,
    val alreadySent: Boolean,
    val inventory: GiftInventory,
    val recentGifts: List<GiftReceiptPreview>,
)

sealed class GiftRepositoryException(message: String) : Exception(message) {
    class Unavailable : GiftRepositoryException("Gifts aren't available right now.")
    class NoSlots : GiftRepositoryException("Your next Gift is still refilling.")
    class InvalidResponse : GiftRepositoryException("Corus couldn't load your Gifts. Please try again.")
}

internal fun parseGiftInventory(raw: Map<*, *>?): GiftInventory? {
    val capacity = (raw?.get("capacity") as? Number)?.toInt() ?: return null
    val available = (raw["available"] as? Number)?.toInt() ?: return null
    return GiftInventory(
        capacity = capacity.coerceAtLeast(0),
        available = available.coerceAtLeast(0),
        used = ((raw["used"] as? Number)?.toInt() ?: (capacity - available)).coerceAtLeast(0),
        nextRefillAtMs = (raw["nextRefillAtMs"] as? Number)?.toLong()?.takeIf { it > 0 },
    )
}

private fun parseGiftPreview(raw: Map<*, *>): GiftReceiptPreview? {
    val senderId = (raw["senderId"] as? String)?.takeIf { it.isNotBlank() } ?: return null
    val giftType = (raw["giftType"] as? String)?.takeIf { it.isNotBlank() } ?: return null
    return GiftReceiptPreview(
        senderId = senderId,
        senderUsername = raw["senderUsername"] as? String ?: "",
        senderDisplayName = raw["senderDisplayName"] as? String ?: "",
        senderAvatarUrl = (raw["senderAvatarURL"] as? String)?.takeIf { it.isNotBlank() },
        giftType = giftType,
        note = (raw["note"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
        sentAtMs = (raw["sentAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
    )
}

internal fun parseGiftStatus(raw: Map<*, *>?): GiftStatus? {
    if (raw?.get("enabled") != true) return null
    val inventory = parseGiftInventory(raw["inventory"] as? Map<*, *>) ?: return null
    val catalog = (raw["catalog"] as? List<*>)?.mapNotNull { item ->
        ((item as? Map<*, *>)?.get("id") as? String)?.takeIf { it.isNotBlank() }
    }.orEmpty()
    return GiftStatus(inventory, catalog)
}

internal fun parseGiftSendResult(raw: Map<*, *>?): GiftSendResult? {
    val giftType = (raw?.get("giftType") as? String)?.takeIf { it.isNotBlank() } ?: return null
    val inventory = parseGiftInventory(raw["inventory"] as? Map<*, *>) ?: return null
    val recent = (raw["recentGifts"] as? List<*>)
        ?.mapNotNull { (it as? Map<*, *>)?.let(::parseGiftPreview) }
        .orEmpty()
    return GiftSendResult(
        giftType = giftType,
        alreadySent = raw["alreadySent"] as? Boolean ?: false,
        inventory = inventory,
        recentGifts = recent,
    )
}

@Singleton
class GiftRepository @Inject constructor(
    private val functions: FirebaseFunctions,
    private val auth: FirebaseAuth,
    private val analytics: AnalyticsService,
) {
    suspend fun thankGift(postId: String, giftId: String, source: String = "post_receipt"): java.util.Date {
        analytics.logGiftEvent("thanks_started", source)
        try {
            val raw = functions.getHttpsCallable("thankGift")
                .call(mapOf("postId" to postId, "giftId" to giftId)).await().getData() as? Map<*, *>
            val thankedAt = (raw?.get("thankedAt") as? Number)?.toLong()
                ?: throw GiftRepositoryException.InvalidResponse()
            analytics.logGiftEvent("thanks_completed", source, result = if (raw["alreadyThanked"] == true) "already_thanked" else "thanked")
            return java.util.Date(thankedAt)
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            analytics.logGiftEvent("thanks_failed", source, errorCode = GiftAnalytics.errorCode(error))
            throw error
        }
    }

    private val statusScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val statusMutex = Mutex()
    private var statusRequest: Deferred<GiftStatus>? = null
    private var statusRequestUid: String? = null
    private var cacheGeneration = 0L
    private data class CachedStatus(val status: GiftStatus, val fetchedAt: Long, val uid: String?)

    @Volatile private var cached: CachedStatus? = null

    /** Last status fetched within [maxAgeMs], so the picker can open fully
     *  laid out instead of showing a loading state. Callers still refresh. */
    fun cachedStatus(maxAgeMs: Long = CACHE_MAX_AGE_MS): GiftStatus? =
        cached?.takeIf {
            it.uid == auth.currentUser?.uid && System.currentTimeMillis() - it.fetchedAt <= maxAgeMs
        }?.status

    /** Warm the cache ahead of the picker (post menu open). Never throws. */
    suspend fun prefetchStatus() {
        // Already warm: skip the call entirely so repeated menu opens cost nothing.
        if (cachedStatus(PREFETCH_FRESH_MS) != null) return
        try { getStatus() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) {}
    }

    suspend fun getStatus(): GiftStatus {
        val uid = auth.currentUser?.uid
        val request = statusMutex.withLock {
            val generation = cacheGeneration
            statusRequest?.takeIf { it.isActive && statusRequestUid == uid } ?: statusScope.async {
                val raw = functions.getHttpsCallable("getGiftStatus").call().await().getData() as? Map<*, *>
                val status = parseGiftStatus(raw) ?: throw GiftRepositoryException.InvalidResponse()
                statusMutex.withLock {
                    if (generation == cacheGeneration && auth.currentUser?.uid == uid) {
                        cached = CachedStatus(status, System.currentTimeMillis(), uid)
                    }
                }
                status
            }.also { statusRequest = it; statusRequestUid = uid }
        }
        return request.await()
    }

    private companion object {
        const val CACHE_MAX_AGE_MS = 5 * 60_000L
        const val PREFETCH_FRESH_MS = 60_000L
    }

    suspend fun sendGift(
        postId: String,
        giftType: String,
        note: String?,
        requestId: String = UUID.randomUUID().toString(),
    ): GiftSendResult {
        val payload = mutableMapOf<String, Any>(
            "postId" to postId,
            "giftType" to giftType,
            "requestId" to requestId,
        )
        note?.trim()?.takeIf { it.isNotEmpty() }?.let { payload["note"] = it }
        val raw = try {
            functions.getHttpsCallable("sendGift").call(payload).await().getData() as? Map<*, *>
        } catch (error: FirebaseFunctionsException) {
            when (error.code) {
                FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> throw GiftRepositoryException.NoSlots()
                FirebaseFunctionsException.Code.FAILED_PRECONDITION -> throw GiftRepositoryException.Unavailable()
                else -> throw error
            }
        }
        statusMutex.withLock {
            cacheGeneration++
            cached = null
            statusRequest = null
        }
        return parseGiftSendResult(raw) ?: throw GiftRepositoryException.InvalidResponse()
    }
}
