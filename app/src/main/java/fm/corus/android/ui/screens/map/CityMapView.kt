package fm.corus.android.ui.screens.map

import android.graphics.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.res.ResourcesCompat
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import fm.corus.android.ui.theme.LocalCorusDarkTheme
import fm.corus.android.R
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.*

internal fun mapSpanZoom(latitude: Double, width: Double, kilometers: Double): Double = log2(40075016.686 * cos(latitude * PI / 180) / 512 * width.coerceAtLeast(1.0) / (kilometers * 1000)).coerceIn(1.0, 16.0)
private fun centerBelow(latitude: Double, zoom: Double, offset: Double): Double {
    val phi = latitude.coerceIn(-85.0, 85.0) * PI / 180
    val worldY = (1 - ln(tan(phi) + 1 / cos(phi)) / PI) / 2
    val y = worldY + offset / (512 * 2.0.pow(zoom))
    return atan(sinh(PI * (1 - 2 * y))) * 180 / PI
}

data class MapCameraPosition(val latitude: Double, val longitude: Double, val zoom: Double)

@Composable
fun CityMapView(cities: List<MapCitySummary>, filter: String, selected: MapCity?, playing: MapCity?, modifier: Modifier = Modifier, compact: Boolean = false, sessionKey: Int = 0, browsing: MapCity? = null, anchor: MapCity? = null, initialCamera: MapCameraPosition? = null, onCameraChanged: (MapCameraPosition) -> Unit = {}, showArtwork: Boolean = true, playbackMode: String? = null, loadLatest: (suspend (List<String>) -> Map<String, fm.corus.android.data.model.CymbalPost?>)? = null, onCity: (MapCity) -> Unit) {
    val context = LocalContext.current; val dark = LocalCorusDarkTheme.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestCameraChanged by rememberUpdatedState(onCameraChanged); val restoredCamera = remember { initialCamera }; var restoredFocus by remember { mutableStateOf(false) };
    val latestSelect by rememberUpdatedState(onCity); val latestCities by rememberUpdatedState(cities)
    val view = remember { MapLibre.getInstance(context); MapView(context).also { it.onCreate(null) } }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var focused by remember(sessionKey) { mutableStateOf<String?>(null) }
    val markers = remember { mutableMapOf<String, Marker>() }; val signatures = remember { mutableMapOf<String, String>() }
    val photos = remember { mutableMapOf<String, Bitmap>() }
    val retainedFaces = remember { mutableMapOf<String, List<MapPerson>>() }
    val activeCity = playing ?: selected ?: browsing ?: anchor
    val latestActiveCity by rememberUpdatedState(activeCity)
    var fanPoint by remember { mutableStateOf<PointF?>(null) }
    var fanVisible by remember { mutableStateOf(false) }
    fun updateFanPosition(m: MapLibreMap) {
        val city = latestActiveCity
        val coordinate = city?.let { LatLng(it.latitude,it.longitude) }
        fanPoint = coordinate?.let { m.projection.toScreenLocation(it) }
        fanVisible = coordinate != null && m.projection.visibleRegion.latLngBounds.contains(coordinate)
    }
    var styleReady by remember { mutableIntStateOf(0) }
    var viewportRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(view, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> when (event) {
            Lifecycle.Event.ON_START -> view.onStart(); Lifecycle.Event.ON_RESUME -> view.onResume()
            Lifecycle.Event.ON_PAUSE -> view.onPause(); Lifecycle.Event.ON_STOP -> view.onStop(); else -> Unit
        } }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) view.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.onResume()
        onDispose { lifecycle.removeObserver(observer); view.onPause(); view.onStop(); view.onDestroy() }
    }
    Box(modifier) {
    AndroidView(factory = { view.also { v -> v.getMapAsync { m ->
        m.setMinZoomPreference(1.0); m.setMaxZoomPreference(16.0)
        m.uiSettings.setAllGesturesEnabled(!compact)
        m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(restoredCamera?.latitude ?: 20.0, restoredCamera?.longitude ?: -30.0), restoredCamera?.zoom ?: 1.5))
        m.setOnMarkerClickListener { marker -> if (!compact) latestCities.firstOrNull { it.city.cityId == marker.snippet }?.let { latestSelect(it.city) }; true }
        m.addOnCameraMoveListener { updateFanPosition(m) }
        m.addOnCameraIdleListener {
            updateFanPosition(m)
            val density = context.resources.displayMetrics.density
            val zoom = mapSpanZoom(m.cameraPosition.target?.latitude ?: 20.0, (v.width / density).toDouble(), 35.0)
            if (kotlin.math.abs(m.maxZoomLevel - zoom) > 0.01) m.setMaxZoomPreference(zoom)
            m.cameraPosition.target?.let { latestCameraChanged(MapCameraPosition(it.latitude, it.longitude, m.cameraPosition.zoom)) }
            viewportRevision++
        }
        map = m
    } } }, modifier = Modifier.fillMaxSize())
    val fanPeople = cities.firstOrNull { it.city.cityId == activeCity?.cityId }?.facets?.get(filter)?.previews.orEmpty()
    MapArtworkFan(activeCity?.cityId, stableMapFaces(retainedFaces[activeCity?.cityId].orEmpty(), fanPeople), !compact && showArtwork && fanVisible, fanPoint, loadLatest)
    }
    LaunchedEffect(activeCity?.cityId, viewportRevision) { map?.let { updateFanPosition(it) } }
    LaunchedEffect(map, dark) {
        // `positron` and `dark` are intentionally muted cartographic styles.
        // iOS uses Apple Maps' standard, colored map, so use OpenFreeMap's
        // Liberty standard style in both themes rather than a grayscale base.
        map?.setStyle("https://tiles.openfreemap.org/styles/liberty") { styleReady++ }
    }
    LaunchedEffect(map, cities, filter, playing?.cityId, activeCity?.cityId, playbackMode, styleReady, viewportRevision) {
        val m = map ?: return@LaunchedEffect
        if (styleReady == 0) return@LaunchedEffect
        val visible = cities.filter { (it.facets[filter]?.count ?: 0) > 0 }
        visible.forEach { retainedFaces[it.city.cityId] = stableMapFaces(retainedFaces[it.city.cityId].orEmpty(), it.facets[filter]?.previews.orEmpty()) }
        val ids = visible.map { it.city.cityId }.toSet()
        markers.keys.toList().filterNot { it in ids }.forEach { id -> markers.remove(id)?.let(m::removeMarker); signatures.remove(id) }
        val density = context.resources.displayMetrics.density
        fun icon(summary: MapCitySummary): org.maplibre.android.annotations.Icon {
            val active = summary.city.cityId == playing?.cityId
            val label = "${if (active) if (playbackMode == "watch") "▣ " else "♫ " else ""}${summary.city.cityName}"
            val faces = retainedFaces[summary.city.cityId].orEmpty()
            // Markers are rendered into a bitmap, so Compose typography cannot
            // reach them automatically. Load Corus's Nunito face explicitly to
            // keep city labels aligned with the rest of the app and iOS parity.
            val clusterTypeface = ResourcesCompat.getFont(context, R.font.nunito)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 13 * density
                typeface = Typeface.create(clusterTypeface ?: Typeface.DEFAULT, Typeface.BOLD)
            }
            // Match iOS's content-sized city capsule. The old 100dp minimum
            // made short names such as Brooklyn look detached from their faces.
            val width = max(paint.measureText(label) + 20 * density, 76 * density).toInt()
            val faceSize = 34 * density; val faceTop = 8*density; val labelTop = 48*density
            val height = (labelTop + 30*density).toInt()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888); val canvas = Canvas(bitmap)
            paint.color = if (active) 0xff6495ed.toInt() else if (dark) 0xff22252b.toInt() else Color.WHITE
            canvas.drawRoundRect(0f,labelTop,width.toFloat(),height.toFloat(),15*density,15*density,paint)
            paint.color = if (active || dark) Color.WHITE else 0xff17202b.toInt()
            canvas.drawText(label,12*density,labelTop+15*density-(paint.ascent()+paint.descent())/2,paint)
            val rowWidth = faceSize + (faces.size - 1).coerceAtLeast(0)*22*density
            faces.indices.reversed().forEach { i ->
                val person = faces[i]
                val x = (width-rowWidth)/2+i*22*density; val url = person.user.avatarThumbURL ?: person.user.avatarURL
                paint.color = if(dark) 0xff33363b.toInt() else 0xffdadde2.toInt()
                canvas.drawCircle(x+faceSize/2,faceTop+faceSize/2,faceSize/2,paint)
                val photo = photos[url]
                if(photo != null) {
                    val save=canvas.save(); canvas.clipPath(Path().apply { addCircle(x+faceSize/2,faceTop+faceSize/2,faceSize/2,Path.Direction.CW) })
                    canvas.drawBitmap(photo,null,RectF(x,faceTop,x+faceSize,faceTop+faceSize),null);canvas.restoreToCount(save)
                } else {
                    paint.color=0xff80848a.toInt();paint.textSize=13*density
                    val initial=(person.user.displayName.ifBlank { person.user.username }).take(1).uppercase()
                    canvas.drawText(initial,x+(faceSize-paint.measureText(initial))/2,faceTop+faceSize/2-(paint.ascent()+paint.descent())/2,paint)
                }
            }
            val count = java.text.NumberFormat.getIntegerInstance().format(summary.facets[filter]?.count ?: 0)
            paint.textSize=11*density;val badgeWidth=paint.measureText(count)+12*density
            val badgeX=((width+rowWidth)/2+10*density-badgeWidth).coerceAtMost(width-badgeWidth)
            paint.color=0xff6495ed.toInt();canvas.drawRoundRect(badgeX,0f,badgeX+badgeWidth,20*density,10*density,10*density,paint)
            paint.color=Color.WHITE;canvas.drawText(count,badgeX+6*density,10*density-(paint.ascent()+paint.descent())/2,paint)
            return IconFactory.getInstance(context).fromBitmap(bitmap)
        }
        visible.forEach { summary ->
            val city = summary.city; val signature = "${city}:${summary.facets[filter]}:${retainedFaces[city.cityId]}:${playing?.cityId == city.cityId}:$playbackMode:$dark:$styleReady"
            val marker = markers[city.cityId]
            if (marker == null) markers[city.cityId] = m.addMarker(MarkerOptions().position(LatLng(city.latitude,city.longitude)).snippet(city.cityId).icon(icon(summary)))
            else if (signatures[city.cityId] != signature) { marker.position=LatLng(city.latitude,city.longitude);marker.icon=icon(summary) }
            signatures[city.cityId]=signature
            if (m.projection.visibleRegion.latLngBounds.contains(LatLng(city.latitude,city.longitude))) launch {
                var changed = false
                retainedFaces[city.cityId].orEmpty().forEach { person ->
                    val url = person.user.avatarThumbURL ?: person.user.avatarURL ?: return@forEach
                    if (url !in photos) {
                        val result = runCatching { SingletonImageLoader.get(context).execute(ImageRequest.Builder(context).data(url).size(96).allowHardware(false).build()) as? SuccessResult }.getOrNull()
                        result?.image?.let { photos[url] = it.toBitmap(); changed = true }
                    }
                }
                if (changed) markers[city.cityId]?.icon=icon(summary)
            }
        }
    }
    LaunchedEffect(map, selected?.cityId, playing?.cityId, browsing?.cityId, compact, anchor?.cityId, cities.firstOrNull()?.city?.cityId, sessionKey, styleReady) {
        val target = playing ?: selected ?: browsing ?: anchor ?: (if (compact) cities.firstOrNull()?.city else null) ?: return@LaunchedEffect
        val m = map ?: return@LaunchedEffect
        if (focused == target.cityId || styleReady == 0) return@LaunchedEffect
        val first = focused == null; focused = target.cityId
        if (first && restoredCamera != null && !restoredFocus) { restoredFocus = true; return@LaunchedEffect }
        val density = context.resources.displayMetrics.density
        val width = (view.width/density).toDouble().coerceAtLeast(300.0)
        val spanZoom = mapSpanZoom(target.latitude,width,if (playing != null) 50.0 else 35.0)
        val zoom = if (first && playing == null && selected == null) mapSpanZoom(target.latitude,width,14.0*111.32) else if (browsing != null && playing == null && selected == null) m.cameraPosition.zoom else if (!first && playing == null && !compact) min(m.cameraPosition.zoom,spanZoom) else spanZoom
        val offset = if(compact)0.0 else min(130.0,view.height/density*.22)
        val center=LatLng(centerBelow(target.latitude,zoom,offset),target.longitude)
        val update=CameraUpdateFactory.newLatLngZoom(center,zoom)
        if (compact || !android.animation.ValueAnimator.areAnimatorsEnabled()) m.moveCamera(update) else m.easeCamera(update,450)
    }
}
