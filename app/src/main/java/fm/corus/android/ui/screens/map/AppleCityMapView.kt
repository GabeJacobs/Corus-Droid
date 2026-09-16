package fm.corus.android.ui.screens.map

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.util.Log
import android.util.Base64
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import fm.corus.android.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.LocalCorusDarkTheme
import fm.corus.android.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONObject

/** Keeps Search's already-rendered MapKit surface alive across full-Map navigation. */
private object MapPreviewWebViewCache {
    private var key: String? = null
    private var webView: WebView? = null

    fun take(cacheKey: String): WebView? {
        if (key != cacheKey) {
            webView?.destroy()
            webView = null
            key = null
            return null
        }
        return webView.also { webView = null }
    }

    fun put(cacheKey: String, replacement: WebView) {
        if (webView !== replacement) webView?.destroy()
        key = cacheKey
        webView = replacement
    }
}

/**
 * Apple has no native Android MapKit SDK. MapKit JS is Apple's supported
 * cross-platform map surface, including Android browsers, so the map lives in
 * one retained WebView. We load it once per surface and send small JSON marker
 * updates instead of reloading on Compose recompositions or camera movement.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun AppleCityMapView(
    cities: List<MapCitySummary>,
    filter: String,
    compact: Boolean,
    token: String,
    focus: MapCity?,
    selectedPeople: List<MapPerson> = emptyList(),
    modifier: Modifier = Modifier,
    onCity: (MapCity) -> Unit,
    onCameraSettled: (MapCameraPosition) -> Unit = {},
    focusRevision: Int = 0,
    focusInVisibleMap: Boolean = false,
    citySheetOpen: Boolean = false,
    mapTopInsetFraction: Float = 0f,
    mapBottomOcclusionFraction: Float = .52f,
    showArtwork: Boolean = false,
    playbackMode: String? = null,
    playingUserId: String? = null,
    loadLatest: (suspend (List<String>) -> Map<String, fm.corus.android.data.model.CymbalPost?>)? = null,
    onVisualReady: () -> Unit = {},
) {
    val context = LocalContext.current
    val fontData = remember(context) {
        context.resources.openRawResource(R.font.nunito).use {
            Base64.encodeToString(it.readBytes(), Base64.NO_WRAP)
        }
    }
    val dark = LocalCorusDarkTheme.current
    val previewCacheKey = "$token:$dark"
    val retainedPreviewWebView = remember(previewCacheKey, compact) {
        if (compact) MapPreviewWebViewCache.take(previewCacheKey) else null
    }
    val currentCities by rememberUpdatedState(cities)
    val currentOnCity by rememberUpdatedState(onCity)
    val currentOnCameraSettled by rememberUpdatedState(onCameraSettled)
    val currentLoadLatest by rememberUpdatedState(loadLatest)
    val currentOnVisualReady by rememberUpdatedState(onVisualReady)
    val currentFocusID by rememberUpdatedState(focus?.cityId.orEmpty())
    var artwork by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var hasRenderedMarkers by remember(token) { mutableStateOf(retainedPreviewWebView != null) }
    var transitionSnapshot by remember(previewCacheKey) { mutableStateOf<Bitmap?>(null) }
    var snapshotVisible by remember(previewCacheKey) { mutableStateOf(false) }
    var lastAppliedFocusID by remember(token) { mutableStateOf<String?>(null) }
    val snapshotAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (snapshotVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(
            // Match iOS MapPreviewSection.presentSnapshot: keep the old image
            // above a fully rendered replacement, then blend it away.
            durationMillis = if (android.animation.ValueAnimator.areAnimatorsEnabled()) 320 else 0,
        ),
        finishedListener = { if (it == 0f) transitionSnapshot = null },
        label = "mapPreviewSnapshot",
    )
    val expectsMarkers = cities.any { (it.facets[filter]?.count ?: 0) > 0 }
    val previewFaces = cities.firstOrNull { it.city.cityId == focus?.cityId }?.facets?.get(filter)?.previews.orEmpty()
    val focusedFaces = mapFocusedFaces(previewFaces, selectedPeople, focus?.cityId, playingUserId)
    val artworkKey = "${focus?.cityId}:${focusedFaces.joinToString { it.user.id }}"
    LaunchedEffect(artworkKey, showArtwork) {
        artwork = emptyMap()
        if (!showArtwork || focus == null || focusedFaces.isEmpty() || currentLoadLatest == null) return@LaunchedEffect
        // iOS waits briefly before raising a focused cluster, preventing a fan
        // from flashing while someone is panning between cities.
        kotlinx.coroutines.delay(250)
        val posts = runCatching { currentLoadLatest?.invoke(focusedFaces.map { it.user.id }).orEmpty() }.getOrDefault(emptyMap())
        artwork = mapClusterArtwork(focusedFaces.map { it.user.id }, posts.mapValues { it.value?.displayImageURL })
    }
    val viewerId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val payload = JSONObject(appleMapPayload(cities, filter, dark, focus, artwork, playbackMode, playingUserId, viewerId))
        .put("focusRevision", focusRevision)
        .put("focusInVisibleMap", focusInVisibleMap)
        .put("citySheetOpen", citySheetOpen)
        .put("mapTopInsetFraction", mapTopInsetFraction)
        .put("mapBottomOcclusionFraction", mapBottomOcclusionFraction)
        .toString()
    val currentPayload by rememberUpdatedState(payload)

    Box(modifier) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            (retainedPreviewWebView ?: WebView(if (compact) context.applicationContext else context)).apply {
                if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
                setBackgroundColor(Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.mediaPlaybackRequiresUserGesture = true
                settings.setSupportZoom(false)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_NEVER
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                        Log.w("AppleCityMap", "${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                        return true
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        // AndroidView can receive a Compose update before the
                        // inline bridge exists. Replay the first payload after
                        // parsing so the first painted frame has its markers.
                        view.evaluateJavascript(
                            "window.CorusAppleMap&&window.CorusAppleMap.update($currentPayload)",
                            null,
                        )
                    }
                }
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun selectCity(cityId: String) {
                        post {
                            currentCities.firstOrNull { it.city.cityId == cityId }
                                ?.let { currentOnCity(it.city) }
                        }
                    }

                    @JavascriptInterface
                    fun cameraSettled(latitude: Double, longitude: Double, latitudeDelta: Double) {
                        post {
                            currentOnCameraSettled(
                                MapCameraPosition(latitude, longitude, latitudeDelta)
                            )
                        }
                    }

                    @JavascriptInterface
                    fun markersRendered(focusID: String) {
                        post {
                            hasRenderedMarkers = true
                            if (focusID == currentFocusID) snapshotVisible = false
                            currentOnVisualReady()
                        }
                    }
                }, "CorusAndroidMap")
                tag = token
                if (retainedPreviewWebView == null) {
                    loadDataWithBaseURL(
                        "https://app.corus.fm",
                        appleMapHtml(token, compact, fontData),
                        "text/html",
                        "utf-8",
                        null,
                    )
                } else {
                    post { evaluateJavascript("window.CorusAppleMap&&window.CorusAppleMap.update($currentPayload)", null) }
                }
            }
        },
        update = { webView ->
            // A Remote Config token rotation is the only reason to reload the
            // document. Normal filter, avatar, count, and theme updates stay
            // within the already-warm MapKit surface.
            if (webView.tag != token) {
                webView.tag = token
                webView.loadDataWithBaseURL(
                    "https://app.corus.fm",
                    appleMapHtml(token, compact, fontData),
                    "text/html",
                    "utf-8",
                    null,
                )
            }
            val nextFocusID = focus?.cityId
            if (compact && lastAppliedFocusID == null && nextFocusID != null &&
                hasRenderedMarkers && webView.width > 0 && webView.height > 0) {
                transitionSnapshot = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888).also {
                    webView.draw(Canvas(it))
                }
                snapshotVisible = true
            }
            lastAppliedFocusID = nextFocusID
            webView.evaluateJavascript("window.CorusAppleMap&&window.CorusAppleMap.update($payload)", null)
        },
        onRelease = { webView ->
            webView.removeJavascriptInterface("CorusAndroidMap")
            if (compact) {
                MapPreviewWebViewCache.put(previewCacheKey, webView)
            } else {
                webView.loadUrl("about:blank")
                webView.destroy()
            }
        },
    )
    transitionSnapshot?.let { snapshot ->
        Image(
            bitmap = snapshot.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = snapshotAlpha },
        )
    }
    if (expectsMarkers && !hasRenderedMarkers) {
        CircularProgressIndicator(
            modifier = Modifier.align(Alignment.Center).size(22.dp),
            color = CorusColors.Accent,
            strokeWidth = 2.dp,
        )
    }
    }
}

internal fun mapClusterArtwork(faceIds: List<String>, artworkByUser: Map<String, String?>): Map<String, String> =
    faceIds.asSequence().distinct().mapNotNull { id ->
        artworkByUser[id]?.takeIf(String::isNotBlank)?.let { id to it }
    }.take(3).toMap()

internal fun mapFocusedFaces(previews: List<MapPerson>, selectedPeople: List<MapPerson>, cityId: String?, playingUserId: String?): List<MapPerson> =
    stableMapFaces(emptyList(), previews + selectedPeople.filter { it.city.cityId == cityId })
        .sortedByDescending { it.user.id == playingUserId }.take(3)

internal fun mapClusterFaces(candidates: List<MapPerson>, viewerId: String): List<MapPerson> =
    stableMapFaces(emptyList(), candidates.sortedByDescending { it.user.id == viewerId })

internal const val MAP_CLUSTER_AVATAR_SIZE_PX = 38f
internal const val MAP_CLUSTER_ARTWORK_AVATAR_RATIO = 1.25f
internal const val MAP_CLUSTER_ART_FAN_WIDTH_PX = 136f
internal val MAP_CLUSTER_ARTWORK_SIZE_PX = MAP_CLUSTER_AVATAR_SIZE_PX * MAP_CLUSTER_ARTWORK_AVATAR_RATIO
internal val MAP_CLUSTER_ARTWORK_LEFT_PX = (MAP_CLUSTER_ART_FAN_WIDTH_PX - MAP_CLUSTER_ARTWORK_SIZE_PX) / 2f

private fun appleMapPayload(
    cities: List<MapCitySummary>, filter: String, dark: Boolean, focus: MapCity?, artwork: Map<String, String>, playbackMode: String?, playingUserId: String?, viewerId: String,
): String {
    val visible = cities.filter { (it.facets[filter]?.count ?: 0) > 0 }
    return JSONObject().apply {
        put("dark", dark)
        put("playbackMode", playbackMode ?: "")
        put("playingUserId", playingUserId ?: "")
        put("focus", focus?.let { city -> JSONObject().apply {
            put("id", city.cityId)
            put("latitude", city.latitude)
            put("longitude", city.longitude)
        } })
        put("cities", JSONArray().apply {
            visible.forEach { summary ->
                val facet = summary.facets.getValue(filter)
                put(JSONObject().apply {
                    put("id", summary.city.cityId)
                    put("name", summary.city.cityName)
                    put("latitude", summary.city.latitude)
                    put("longitude", summary.city.longitude)
                    put("count", facet.count)
                    put("faces", JSONArray().apply {
                        mapClusterFaces(facet.previews, viewerId).forEach { person ->
                            put(JSONObject().apply {
                                put("id", person.user.id)
                                put("name", person.user.displayName.ifBlank { person.user.username })
                                put("avatar", person.user.avatarThumbURL ?: person.user.avatarURL ?: "")
                                put("artwork", artwork[person.user.id] ?: "")
                            })
                        }
                    })
                })
            }
        })
    }.toString()
}

internal fun appleMapHtml(token: String, compact: Boolean, fontData: String): String {
    val escapedToken = token.replace("&", "&amp;").replace("\"", "&quot;")
    val gestures = if (compact) "false" else "true"
    return """
        <!doctype html><html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
        <style>
          html,body,#map{margin:0;width:100%;height:100vh;overflow:hidden;background:#d7e7ff}
          /* WebView does not inherit Compose's font resolver. Load the exact
             bundled Nunito face so MapKit annotations keep the app's type
             language instead of silently falling back to Arial. */
          @font-face{font-family:CorusNunito;src:url('data:font/ttf;base64,$fontData') format('truetype');font-style:normal;font-weight:200 1000;font-display:block}
          @keyframes cluster-in{from{opacity:0;transform:scale(.84)}to{opacity:var(--cluster-opacity);transform:scale(1)}}
          .city{--cluster-opacity:1;position:relative;border:0;background:transparent;padding:0;display:block;cursor:pointer;font-family:CorusNunito,Nunito,sans-serif;-webkit-font-smoothing:antialiased;opacity:var(--cluster-opacity);transition:opacity .22s ease-in-out;animation:cluster-in .22s ease-out}
          /* Keep the annotation's measured width stable while focus changes.
             The label is overlaid below the faces, and this inner layer gives
             the whole pin the same gentle upward motion as iOS. */
          .pin-content{position:relative;display:flex;align-items:center;justify-content:center;transition:transform .22s ease-in-out;transform:translateY(0)}
          .city.active .pin-content{transform:translateY(-10px)}
          .faces{position:relative;height:${MAP_CLUSTER_AVATAR_SIZE_PX}px;min-width:${MAP_CLUSTER_AVATAR_SIZE_PX}px;display:flex;align-items:center;justify-content:center;transition:transform .22s ease-in-out}
          .face{position:relative;flex-shrink:0;width:${MAP_CLUSTER_AVATAR_SIZE_PX}px;height:${MAP_CLUSTER_AVATAR_SIZE_PX}px;border:0;border-radius:50%;overflow:hidden;background:#d7e4f6;color:#17202b;display:flex;align-items:center;justify-content:center;font-size:12px;font-weight:800;box-shadow:0 1px 3px #0003}
          .city.density-1 .faces{height:41px;min-width:41px}.city.density-1 .face{width:41px;height:41px;font-size:12.5px}.city.density-2 .faces{height:44px;min-width:44px}.city.density-2 .face{width:44px;height:44px;font-size:13px}
          .face:first-child{z-index:3}.face:nth-child(2){z-index:2}.face:nth-child(3){z-index:1}.face + .face{margin-left:-12px}.face img{position:absolute;inset:0;display:block;width:100%;height:100%;object-fit:contain;object-position:center;background:#d7e4f6}
          /* Pin the fan's bottom to the avatar's top, matching iOS's 66-point
             top overlay. This is independent of whether the label is mounted
             or animating, so focus updates cannot strand covers too high. */
          .art-fan{position:absolute;bottom:${MAP_CLUSTER_AVATAR_SIZE_PX}px;left:50%;width:${MAP_CLUSTER_ART_FAN_WIDTH_PX}px;height:66px;pointer-events:none;transform:translateX(-50%)}.city.density-1 .art-fan{bottom:41px}.city.density-2 .art-fan{bottom:44px}
          .art{position:absolute;left:${MAP_CLUSTER_ARTWORK_LEFT_PX}px;bottom:0;width:${MAP_CLUSTER_ARTWORK_SIZE_PX}px;height:${MAP_CLUSTER_ARTWORK_SIZE_PX}px;border-radius:11px;object-fit:cover;box-shadow:0 6px 10px rgba(0,0,0,.35);opacity:0;transform:translate(0,38px) scale(.45) rotate(0deg);transition:opacity .55s cubic-bezier(.2,.8,.25,1),transform .55s cubic-bezier(.2,.8,.25,1)}
          /* The active Listen Mode pin is the iOS treatment: one larger
             currently-playing cover over the avatar cluster and a blue pill. */
          .art-fan.listening{bottom:calc(${MAP_CLUSTER_AVATAR_SIZE_PX}px + 11px)}.city.density-1 .art-fan.listening{bottom:52px}.city.density-2 .art-fan.listening{bottom:55px}.art-fan.listening .art{left:${MAP_CLUSTER_ARTWORK_LEFT_PX}px;width:${MAP_CLUSTER_ARTWORK_SIZE_PX}px;height:${MAP_CLUSTER_ARTWORK_SIZE_PX}px;border-radius:12px}
          .art-fan.raised .art{opacity:1}
          .art-fan.raised .art{transform:translate(var(--fan-x),var(--fan-y)) scale(1) rotate(var(--fan-r))}
          .art-1{transition-delay:.055s}.art-2{transition-delay:.11s}
          .count{position:absolute;right:-15px;top:-9px;z-index:4;width:23px;height:23px;padding:0;border-radius:50%;display:flex;align-items:center;justify-content:center;background:#6595ef;color:white;font-size:13px;line-height:23px;font-weight:800;font-variation-settings:"wght" 800;box-shadow:0 1px 3px #0002}.city.density-1 .count{font-size:13.5px}.city.density-2 .count{font-size:14px}
          .label{position:absolute;top:100%;left:50%;margin-top:6px;padding:5px 14px;border-radius:18px;background:#fff;color:#17202b;font-size:15px;line-height:18px;font-weight:600;font-variation-settings:"wght" 600;white-space:nowrap;box-shadow:0 2px 8px #0002;opacity:1;transform:translateX(-50%) scale(1);transform-origin:top center;transition:opacity .22s ease-in-out,transform .22s ease-in-out}
          .label:not(.visible){opacity:0;transform:translateX(-50%) scale(.92);pointer-events:none}
          .label.listening{display:flex;align-items:center;gap:7px;background:#6595ef;color:#fff}.label.listening svg{width:18px;height:18px;fill:none;stroke:currentColor;stroke-width:2.4;stroke-linecap:round;stroke-linejoin:round}
          .dark .label{background:#22252b;color:#fff}.dark .face{border-color:#22252b}
          /* On the interactive Map, only markers whose rendered footprint
             competes with the focused city are secondary. Distant cities stay
             fully visible so the map continues to feel populated. */
          .city.unselected{--cluster-opacity:.65}
          .city.overlap-dim{--cluster-opacity:.45}
          /* iOS uses smaller, tighter markers in the Search map preview
             (`MapCityClusterPin(faceSize: 28)`). The full map keeps the
             larger treatment above. */
          .compact .city{animation:none}.compact .faces{height:28px;min-width:28px}.compact .face{width:28px;height:28px;font-size:10px}.compact .city.density-1 .faces{height:30px;min-width:30px}.compact .city.density-1 .face{width:30px;height:30px;font-size:10.5px}.compact .city.density-2 .faces{height:32px;min-width:32px}.compact .city.density-2 .face{width:32px;height:32px;font-size:11px}.compact .city.density-0 .face + .face{margin-left:-12px}.compact .city.density-1 .face + .face{margin-left:-10px}.compact .city.density-2 .face + .face{margin-left:-7px}.compact .count{right:-12px;top:-8px;min-width:20px;height:20px;font-size:11px;line-height:20px}.compact .city.density-1 .count{font-size:11.5px}.compact .city.density-2 .count{font-size:12px}.compact .label{margin-top:5px;padding:4px 10px;border-radius:16px;font-size:14px;line-height:17px}
          .compact .city:not(.active){--cluster-opacity:.65}.compact .city.overlap-dim{--cluster-opacity:.45}.compact .city:not(.active) .faces{transform:scale(.84);transform-origin:center}
          @media (prefers-reduced-motion:reduce){.city,.pin-content,.faces,.label{transition:none;animation:none}}
        </style>
        <script async crossorigin src="https://cdn.apple-mapkit.com/mk/6.x.x/mapkit.core.js" data-callback="initMapKitLoaderV2" data-token="$escapedToken" data-libraries="map,annotations"></script>
        </head><body class="${if (compact) "compact" else ""}"><div id="map"></div><script>
        let map, kit, annotations=[];
        const interactive=$gestures;
        const compact=${if (compact) "true" else "false"};
        function sizeMap(){const h=window.innerHeight||1;for(const el of [document.documentElement,document.body,document.getElementById('map')])el.style.height=h+'px';if(map&&map.region)map.region=map.region}window.addEventListener('resize',sizeMap);
        function initMapKitLoaderV2(){ sizeMap(); mapkit.load(['map','annotations']).then(function(k){ kit=k; map=new kit.Map('map',{mapType:kit.MapType.Standard,colorScheme:kit.ColorScheme.Light,showsPointsOfInterest:false,showsUserLocation:false,isScrollEnabled:interactive,isZoomEnabled:interactive,isRotationEnabled:false,showsZoomControl:false,showsMapTypeControl:false,region:{center:{latitude:38,longitude:-84},span:{latitudeDelta:45,longitudeDelta:70}}}); sizeMap(); map.addEventListener('region-change-end',function(){refreshOverlapDimming();if(compact){window.CorusAppleMap.apply();return}if(!interactive||!map.region)return;const region=map.region;window.CorusAndroidMap.cameraSettled(region.center.latitude,region.center.longitude,region.span.latitudeDelta)}); window.CorusAppleMap.ready=true; window.CorusAppleMap.apply(); }).catch(function(){ document.body.dataset.error='true'; }); }
        // Keep the marker's geographic anchor fixed. Artwork rises in an
        // absolute overlay just like iOS and must never move the city itself.
        function markerView(city){
          const densityTier=city.count>=15?2:(city.count>=5?1:0);const b=document.createElement('button');b.className='city density-'+densityTier;b.type='button';b.onclick=function(){window.CorusAndroidMap.selectCity(city.id)};
          const faces=document.createElement('span');faces.className='faces';
          city.faces.forEach(function(person){const face=document.createElement('span');face.className='face';face.textContent=(person.name||'?').slice(0,1);if(person.avatar){const img=document.createElement('img');img.alt='';img.src=person.avatar;img.onerror=function(){img.remove()};face.appendChild(img)}faces.appendChild(face)});
          const count=document.createElement('span');count.className='count';count.textContent=city.count.toLocaleString();const compactBadgeSize=20+densityTier;const badgeSize=Math.max(compact?compactBadgeSize:23+densityTier,count.textContent.length*8+(compact?6+densityTier:7+densityTier));count.style.width=badgeSize+'px';count.style.height=(compact?compactBadgeSize:badgeSize)+'px';count.style.lineHeight=(compact?compactBadgeSize:badgeSize)+'px';faces.appendChild(count);
          const active=city.id===window.CorusAppleMap.data.focus?.id;b.classList.toggle('active',active);
          const focused=interactive&&active;const listening=focused&&window.CorusAppleMap.data.playbackMode==='listen';
          const label=document.createElement('span');label.className='label'+(listening?' listening':'')+(active?' visible':'');if(listening){label.innerHTML='<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 14v-3a8 8 0 0 1 16 0v3M4 14h3v5H4zM20 14h-3v5h3z"/></svg><span>'+city.name+'</span>'}else{label.textContent=city.name}
          const arts=city.faces.filter(function(p){return p.artwork&&(!listening||p.id===window.CorusAppleMap.data.playingUserId)});
          const content=document.createElement('span');content.className='pin-content';
          if(focused&&arts.length){const fan=document.createElement('span');fan.className='art-fan'+(listening?' listening':'');arts.slice(0,listening?1:3).forEach(function(person,index){const spread=index-(arts.length-1)/2;const art=document.createElement('img');art.className='art art-'+index;art.alt='';art.src=person.artwork;art.style.setProperty('--fan-x',(spread*36)+'px');art.style.setProperty('--fan-y',(Math.abs(spread)*10-4)+'px');art.style.setProperty('--fan-r',(spread*19)+'deg');fan.appendChild(art)});content.appendChild(fan);requestAnimationFrame(function(){void fan.offsetWidth;requestAnimationFrame(function(){setTimeout(function(){if(fan.isConnected)fan.classList.add('raised')},35)})})}
          content.append(faces,label);b.appendChild(content);
          /* Compact annotations are bottom-anchored by MapKit. Unfocused pins
             use half the face height; the focused pin uses a smaller offset
             because its absolute label extends below the face and the inner
             content already animates upward by 10px. The old -38.5 value was
             for an in-flow label and now placed the focused cluster too low. */
          b.dataset.anchorOffset=String(interactive?0:(active?-10:-18));
          return b;
        }
        function markerSignature(city){return JSON.stringify([city,city.id===window.CorusAppleMap.data.focus?.id,window.CorusAppleMap.data.playbackMode||'',window.CorusAppleMap.data.playingUserId||''])}
        function updateMarker(annotation,city){
          const signature=markerSignature(city);const active=city.id===window.CorusAppleMap.data.focus?.id;
          if(annotation.corusSignature!==signature){
            const fresh=markerView(city);const wasActive=annotation.corusButton.classList.contains('active');const currentLabel=annotation.corusButton.querySelector('.label');const freshLabel=fresh.querySelector('.label');
            if(freshLabel)freshLabel.remove();
            annotation.corusButton.className=fresh.className;annotation.corusButton.classList.toggle('active',wasActive);annotation.corusButton.replaceChildren(...fresh.childNodes);
            const labelHost=annotation.corusButton.querySelector('.pin-content')||annotation.corusButton;
            if(currentLabel&&freshLabel){const wasVisible=currentLabel.classList.contains('visible');currentLabel.innerHTML=freshLabel.innerHTML;currentLabel.className=freshLabel.className;currentLabel.classList.toggle('visible',wasVisible);labelHost.appendChild(currentLabel);requestAnimationFrame(function(){currentLabel.className=freshLabel.className})}
            else if(freshLabel)labelHost.appendChild(freshLabel);
            requestAnimationFrame(function(){annotation.corusButton.classList.toggle('active',active)});
            annotation.anchorOffset=new DOMPoint(0,Number(fresh.dataset.anchorOffset));annotation.corusSignature=signature
          }
          annotation.selected=active;
        }
        function marker(city){
          const b=markerView(city);const annotation=new kit.Annotation({latitude:city.latitude,longitude:city.longitude},function(){return b},{calloutEnabled:false,animates:false,anchorOffset:new DOMPoint(0,Number(b.dataset.anchorOffset)),collisionMode:'none',selected:city.id===window.CorusAppleMap.data.focus?.id,displayPriority:1000});
          annotation.corusId=city.id;annotation.corusButton=b;annotation.corusSignature=markerSignature(city);return annotation;
        }
        function refreshOverlapDimming(){
          annotations.forEach(function(annotation){annotation.corusButton?.classList.remove('unselected','overlap-dim')});
          const focusId=window.CorusAppleMap?.data?.focus?.id;
          const focused=annotations.find(function(annotation){return annotation.corusId===focusId});
          if(!focused?.corusButton||!map?.region)return;
          const region=map.region;const width=window.innerWidth||1;const height=window.innerHeight||1;
          function point(annotation){let longitudeDelta=annotation.coordinate.longitude-region.center.longitude;if(longitudeDelta>180)longitudeDelta-=360;if(longitudeDelta< -180)longitudeDelta+=360;return{x:width*(.5+longitudeDelta/Math.max(region.span.longitudeDelta,.0001)),y:height*(.5-(annotation.coordinate.latitude-region.center.latitude)/Math.max(region.span.latitudeDelta,.0001))}}
          const focusedPoint=point(focused);const focusHalfWidth=compact?62:80;const focusHalfHeight=compact?42:80;
          const focusRect={left:focusedPoint.x-focusHalfWidth,right:focusedPoint.x+focusHalfWidth,top:focusedPoint.y-focusHalfHeight,bottom:focusedPoint.y+focusHalfHeight};
          annotations.forEach(function(annotation){
            if(annotation===focused||!annotation.corusButton)return;
            annotation.corusButton.classList.add('unselected');
            const markerPoint=point(annotation);const halfWidth=compact?39:55;const halfHeight=compact?34:65;
            const overlaps=markerPoint.x-halfWidth<focusRect.right&&markerPoint.x+halfWidth>focusRect.left&&markerPoint.y-halfHeight<focusRect.bottom&&markerPoint.y+halfHeight>focusRect.top;
            annotation.corusButton.classList.toggle('overlap-dim',overlaps);
          });
        }
        function compactPreviewCities(cities){
          if(!compact||!map?.convertCoordinateToPointOnPage)return cities;
          const width=window.innerWidth||1,height=window.innerHeight||1,insetX=24,insetY=16;
          const ranked=cities.filter(function(city){return city.count>0}).slice().sort(function(a,b){return b.count-a.count||a.id.localeCompare(b.id)});
          const allProjected=ranked.map(function(city){const point=map.convertCoordinateToPointOnPage({latitude:city.latitude,longitude:city.longitude});return{city:city,x:point.x,y:point.y}});const projected=allProjected.filter(function(row){return row.x>=insetX&&row.x<=width-insetX&&row.y>=insetY&&row.y<=height-insetY});
          const focused=projected.find(function(row){return row.city.id===window.CorusAppleMap.data.focus?.id});const selected=focused?[focused]:[];
          function overlaps(row){return selected.some(function(chosen){return Math.abs(row.x-chosen.x)<38&&Math.abs(row.y-chosen.y)<28})}
          const horizontal=projected.slice().sort(function(a,b){return a.x-b.x});[horizontal,horizontal.slice().reverse()].forEach(function(entries){const edge=entries.find(function(row){return !selected.some(function(chosen){return chosen.city.id===row.city.id})&&!overlaps(row)});if(edge)selected.push(edge)});
          const cells=new Map();projected.forEach(function(row){const column=Math.max(0,Math.min(7,Math.floor(row.x/width*8)));const line=Math.max(0,Math.min(1,Math.floor(row.y/height*2)));const key=line*8+column;const current=cells.get(key);if(!current||current.city.count<row.city.count)cells.set(key,row)});Array.from(cells.keys()).sort(function(a,b){return a-b}).forEach(function(key){const row=cells.get(key);if(selected.length<16&&!overlaps(row))selected.push(row)});
          projected.forEach(function(row){if(selected.length<16&&!selected.some(function(chosen){return chosen.city.id===row.city.id})&&!overlaps(row))selected.push(row)});
          return selected.map(function(row){return row.city});
        }
        // Retain annotation objects across focus and artwork updates. Removing
        // and recreating them while MapKit is settling can detach pins from
        // the moving basemap until the next gesture.
        window.CorusAppleMap={ready:false,data:{cities:[],dark:false,focus:null},lastFocus:null,update:function(data){this.data=data;this.apply()},apply:function(){
          if(!this.ready||!map)return;sizeMap();document.body.classList.toggle('dark',this.data.dark);map.colorScheme=this.data.dark?kit.ColorScheme.Dark:kit.ColorScheme.Light;
          const visibleCities=compactPreviewCities(this.data.cities);const cityIds=new Set(visibleCities.map(function(city){return city.id}));
          annotations.filter(function(annotation){return !cityIds.has(annotation.corusId)}).forEach(function(annotation){map.removeAnnotation(annotation)});
          annotations=annotations.filter(function(annotation){return cityIds.has(annotation.corusId)});
          const byId=new Map(annotations.map(function(annotation){return [annotation.corusId,annotation]}));
          visibleCities.forEach(function(city){let annotation=byId.get(city.id);if(!annotation){annotation=marker(city);annotations.push(annotation);byId.set(city.id,annotation);map.addAnnotation(annotation)}else{if(annotation.coordinate.latitude!==city.latitude||annotation.coordinate.longitude!==city.longitude)annotation.coordinate={latitude:city.latitude,longitude:city.longitude};updateMarker(annotation,city)}});
          refreshOverlapDimming();
          // MapKit may paint a private copy of a custom annotation view, so the
          // source button's `isConnected` is not a valid render signal. Once a
          // non-empty annotation set has been accepted, wait through two paint
          // frames and dismiss the native loading indicator.
          if(annotations.length){const renderedFocus=this.data.focus?.id||'';requestAnimationFrame(function(){requestAnimationFrame(function(){window.CorusAndroidMap.markersRendered(renderedFocus)})})}
          const focus=this.data.focus;const top=Math.max(0,Math.min(.8,this.data.mapTopInsetFraction||0));const bottom=Math.max(0,Math.min(.95,this.data.mapBottomOcclusionFraction||0));const focusKey=focus&&this.data.focusRevision+':'+top.toFixed(4)+':'+bottom.toFixed(4);
          if(focus&&this.lastFocus!==focusKey){const animate=interactive&&this.lastFocus!==null;this.lastFocus=focusKey;const visibleFocus=this.data.focusInVisibleMap&&interactive;const latitudeDelta=visibleFocus?.6:(interactive?7:125);const longitudeDelta=visibleFocus?latitudeDelta*window.innerWidth/window.innerHeight/Math.max(.15,Math.cos(focus.latitude*Math.PI/180)):(interactive?10:300);const offset=(bottom-top)/2;map.setRegionAnimated({center:{latitude:focus.latitude-(visibleFocus?latitudeDelta*offset:0),longitude:focus.longitude},span:{latitudeDelta:latitudeDelta,longitudeDelta:longitudeDelta}},animate)}
        }};
        </script></body></html>
    """.trimIndent()
}
