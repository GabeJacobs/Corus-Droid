package fm.corus.android.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import fm.corus.android.data.model.CymbalMessage
import java.io.File
import java.util.Base64

/** Deterministic, app-owned recent history for instant/offline thread opening. */
class MessageLocalStore(context: Context) {
    private val gson = Gson()
    // A mocked Context used by local JVM tests has no filesDir; treat storage as
    // unavailable instead of accidentally writing relative to the repository.
    private val directory = context.filesDir?.let { File(it, "message-history-v1").apply { mkdirs() } }

    @Synchronized
    fun load(userId: String, threadId: String): List<CymbalMessage> = runCatching {
        val file = file(userId, threadId) ?: return emptyList()
        if (!file.exists()) return emptyList()
        val type = object : TypeToken<List<CymbalMessage>>() {}.type
        gson.fromJson<List<CymbalMessage>>(file.readText(), type)
            .sortedByDescending { it.createdAt }
    }.getOrDefault(emptyList())

    @Synchronized
    fun save(userId: String, threadId: String, messages: List<CymbalMessage>) {
        runCatching {
            val retained = messages.sortedByDescending { it.createdAt }.take(RETAINED_MESSAGES)
            val destination = file(userId, threadId) ?: return
            val temporary = File(destination.parentFile, "${destination.name}.tmp")
            temporary.writeText(gson.toJson(retained))
            if (!temporary.renameTo(destination)) {
                destination.writeText(temporary.readText())
                temporary.delete()
            }
        }
    }

    private fun file(userId: String, threadId: String): File? {
        val key = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("$userId:$threadId".toByteArray())
        return directory?.let { File(it, "$key.json") }
    }

    companion object { const val RETAINED_MESSAGES = 200 }
}
