package fm.corus.android.ui.screens.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import dagger.hilt.android.qualifiers.ApplicationContext
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Foreground-only: manual cities and approximate permission are never automatically republished. */
@Singleton
class MapDeviceCityRefresh @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth, private val db: FirebaseFirestore,
    private val repository: MapRepository, private val remote: RemoteConfigService,
) {
    private val checked = mutableMapOf<String,Long>()
    private val mutex = Mutex()
    private fun precise() = ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    suspend fun refresh() {
        val uid=auth.currentUser?.uid ?: return
        if(!remote.mapEnabled || !precise() || System.currentTimeMillis()-(checked[uid] ?: 0L)<15*60_000L || !mutex.tryLock()) return
        try {
            val ref=db.collection("map_presence").document(uid)
            val existing=ref.get(Source.SERVER).await()
            if(existing.getString("source")!="device" || existing.getString("audience") !in listOf("everyone","following")) return
            val manager=context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val location=withTimeout(20_000L) { suspendCancellableCoroutine<Location> { continuation ->
                val listener=object:LocationListener { override fun onLocationChanged(location:Location){manager.removeUpdates(this);if(continuation.isActive)continuation.resume(location)} }
                continuation.invokeOnCancellation { manager.removeUpdates(listener) }
                val provider=if(manager.isProviderEnabled(LocationManager.GPS_PROVIDER))LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
                manager.requestSingleUpdate(provider,listener,Looper.getMainLooper())
            } }
            checked[uid]=System.currentTimeMillis()
            val city=repository.resolve(location)
            if(auth.currentUser?.uid!=uid || !remote.mapEnabled || !precise() || city.cityId==existing.getString("cityId"))return
            db.runTransaction { tx ->
                val current=tx.get(ref)
                if(auth.currentUser?.uid==uid && current.getString("source")=="device" && current.get("updatedAt")==existing.get("updatedAt")) {
                    tx.update(ref,city.payload()+mapOf("source" to "device","updatedAt" to FieldValue.serverTimestamp()))
                }
            }.await()
        } catch(e:Exception) { if(e is kotlinx.coroutines.CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException)throw e }
        finally { mutex.unlock() }
    }
}
