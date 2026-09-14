package fm.corus.android.ui.screens.map

import android.annotation.SuppressLint
import android.graphics.Color
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import fm.corus.android.ui.theme.LocalCorusDarkTheme
import fm.corus.android.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

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
    focusRevision: Int = 0,
    focusInVisibleMap: Boolean = false,
    citySheetOpen: Boolean = false,
    mapTopInsetFraction: Float = 0f,
    mapBottomOcclusionFraction: Float = .52f,
    showArtwork: Boolean = false,
    playbackMode: String? = null,
    playingUserId: String? = null,
    loadLatest: (suspend (List<String>) -> Map<String, fm.corus.android.data.model.CymbalPost?>)? = null,
) {
    val context = LocalContext.current
    val fontData = remember(context) {
        context.resources.openRawResource(R.font.nunito).use {
            Base64.encodeToString(it.readBytes(), Base64.NO_WRAP)
        }
    }
    val dark = LocalCorusDarkTheme.current
    val currentCities by rememberUpdatedState(cities)
    val currentOnCity by rememberUpdatedState(onCity)
    val currentLoadLatest by rememberUpdatedState(loadLatest)
    var artwork by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
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
    val payload = JSONObject(appleMapPayload(cities, filter, dark, focus, artwork, playbackMode, playingUserId))
        .put("focusRevision", focusRevision)
        .put("focusInVisibleMap", focusInVisibleMap)
        .put("citySheetOpen", citySheetOpen)
        .put("mapTopInsetFraction", mapTopInsetFraction)
        .put("mapBottomOcclusionFraction", mapBottomOcclusionFraction)
        .toString()

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
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
                            "window.CorusAppleMap&&window.CorusAppleMap.update($payload)",
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
                }, "CorusAndroidMap")
                tag = token
                loadDataWithBaseURL(
                    "https://app.corus.fm",
                    appleMapHtml(token, compact, fontData),
                    "text/html",
                    "utf-8",
                    null,
                )
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
            webView.evaluateJavascript("window.CorusAppleMap&&window.CorusAppleMap.update($payload)", null)
        },
        onRelease = { webView ->
            webView.removeJavascriptInterface("CorusAndroidMap")
            webView.loadUrl("about:blank")
            webView.destroy()
        },
    )
}

internal fun mapClusterArtwork(faceIds: List<String>, artworkByUser: Map<String, String?>): Map<String, String> =
    faceIds.asSequence().distinct().mapNotNull { id ->
        artworkByUser[id]?.takeIf(String::isNotBlank)?.let { id to it }
    }.take(3).toMap()

internal fun mapFocusedFaces(previews: List<MapPerson>, selectedPeople: List<MapPerson>, cityId: String?, playingUserId: String?): List<MapPerson> =
    stableMapFaces(emptyList(), previews + selectedPeople.filter { it.city.cityId == cityId })
        .sortedByDescending { it.user.id == playingUserId }.take(3)

internal const val MAP_CLUSTER_AVATAR_SIZE_PX = 38f
internal const val MAP_CLUSTER_ARTWORK_AVATAR_RATIO = 1.25f
internal const val MAP_CLUSTER_ART_FAN_WIDTH_PX = 136f
internal val MAP_CLUSTER_ARTWORK_SIZE_PX = MAP_CLUSTER_AVATAR_SIZE_PX * MAP_CLUSTER_ARTWORK_AVATAR_RATIO
internal val MAP_CLUSTER_ARTWORK_LEFT_PX = (MAP_CLUSTER_ART_FAN_WIDTH_PX - MAP_CLUSTER_ARTWORK_SIZE_PX) / 2f

private fun appleMapPayload(
    cities: List<MapCitySummary>, filter: String, dark: Boolean, focus: MapCity?, artwork: Map<String, String>, playbackMode: String?, playingUserId: String?,
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
                        stableMapFaces(emptyList(), facet.previews).forEach { person ->
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
          .city{position:relative;border:0;background:transparent;padding:0;display:flex;flex-direction:column;align-items:center;cursor:pointer;font-family:CorusNunito,Nunito,sans-serif;-webkit-font-smoothing:antialiased}
          .faces{position:relative;height:${MAP_CLUSTER_AVATAR_SIZE_PX}px;min-width:${MAP_CLUSTER_AVATAR_SIZE_PX}px;display:flex;align-items:center;justify-content:center}
          .face{position:relative;flex-shrink:0;width:${MAP_CLUSTER_AVATAR_SIZE_PX}px;height:${MAP_CLUSTER_AVATAR_SIZE_PX}px;border:0;border-radius:50%;overflow:hidden;background:#d7e4f6;color:#17202b;display:flex;align-items:center;justify-content:center;font-size:12px;font-weight:800;box-shadow:0 1px 3px #0003}
          .face + .face{margin-left:-12px}.face img{position:absolute;inset:0;display:block;width:100%;height:100%;object-fit:contain;object-position:center;background:#d7e4f6}
          .art-fan{position:absolute;bottom:80px;left:50%;width:${MAP_CLUSTER_ART_FAN_WIDTH_PX}px;height:66px;pointer-events:none;transform:translateX(-50%)}
          .art{position:absolute;left:${MAP_CLUSTER_ARTWORK_LEFT_PX}px;bottom:0;width:${MAP_CLUSTER_ARTWORK_SIZE_PX}px;height:${MAP_CLUSTER_ARTWORK_SIZE_PX}px;border-radius:11px;object-fit:cover;box-shadow:0 6px 10px rgba(0,0,0,.35);opacity:0;transform:translate(0,38px) scale(.45) rotate(0deg);transition:opacity .55s cubic-bezier(.2,.8,.25,1),transform .55s cubic-bezier(.2,.8,.25,1)}
          /* The active Listen Mode pin is the iOS treatment: one larger
             currently-playing cover over the avatar cluster and a blue pill. */
          .art-fan.listening{bottom:91px}.art-fan.listening .art{left:${MAP_CLUSTER_ARTWORK_LEFT_PX}px;width:${MAP_CLUSTER_ARTWORK_SIZE_PX}px;height:${MAP_CLUSTER_ARTWORK_SIZE_PX}px;border-radius:12px}
          .art-fan.raised .art{opacity:1}
          .art-fan.raised .art{transform:translate(var(--fan-x),var(--fan-y)) scale(1) rotate(var(--fan-r))}
          .art-1{transition-delay:.055s}.art-2{transition-delay:.11s}
          .count{position:absolute;right:-15px;top:-9px;width:23px;height:23px;padding:0;border-radius:50%;display:flex;align-items:center;justify-content:center;background:#6595ef;color:white;font-size:13px;line-height:23px;font-weight:800;font-variation-settings:"wght" 800;box-shadow:0 1px 3px #0002}
          .label{margin-top:6px;padding:5px 14px;border-radius:18px;background:#fff;color:#17202b;font-size:15px;line-height:18px;font-weight:600;font-variation-settings:"wght" 600;white-space:nowrap;box-shadow:0 2px 8px #0002}
          .label.listening{display:flex;align-items:center;gap:7px;background:#6595ef;color:#fff}.label.listening svg{width:18px;height:18px;fill:none;stroke:currentColor;stroke-width:2.4;stroke-linecap:round;stroke-linejoin:round}
          .dark .label{background:#22252b;color:#fff}.dark .face{border-color:#22252b}
          /* iOS uses smaller, tighter markers in the Search map preview
             (`MapCityClusterPin(faceSize: 28)`). The full map keeps the
             larger treatment above. */
          .compact .faces{height:28px;min-width:28px}.compact .face{width:28px;height:28px;font-size:10px}.compact .face + .face{margin-left:-10px}.compact .count{right:-12px;top:-8px;min-width:20px;height:20px;font-size:11px;line-height:20px}.compact .label{margin-top:5px;padding:4px 10px;border-radius:16px;font-size:14px;line-height:17px}
        </style>
        <script async crossorigin src="https://cdn.apple-mapkit.com/mk/6.x.x/mapkit.core.js" data-callback="initMapKitLoaderV2" data-token="$escapedToken" data-libraries="map,annotations"></script>
        </head><body class="${if (compact) "compact" else ""}"><div id="map"></div><script>
        let map, kit, annotations=[];
        const interactive=$gestures;
        const compact=${if (compact) "true" else "false"};
        function sizeMap(){const h=window.innerHeight||1;for(const el of [document.documentElement,document.body,document.getElementById('map')])el.style.height=h+'px';if(map&&map.region)map.region=map.region}window.addEventListener('resize',sizeMap);
        function initMapKitLoaderV2(){ sizeMap(); mapkit.load(['map','annotations']).then(function(k){ kit=k; map=new kit.Map('map',{mapType:kit.MapType.Standard,colorScheme:kit.ColorScheme.Light,showsPointsOfInterest:false,showsUserLocation:false,isScrollEnabled:interactive,isZoomEnabled:interactive,isRotationEnabled:false,showsZoomControl:false,showsMapTypeControl:false,region:{center:{latitude:38,longitude:-84},span:{latitudeDelta:45,longitudeDelta:70}}}); sizeMap(); window.CorusAppleMap.ready=true; window.CorusAppleMap.apply(); }).catch(function(){ document.body.dataset.error='true'; }); }
        // MapKit anchors custom annotations at their bottom center. The compact
        // marker is 68px tall plus its 9px badge overhang: a negative anchor
        // offset moves it down half
        // that visible height so the whole pin, not its bottom, is centered.
        function marker(city){const b=document.createElement('button');b.className='city';b.type='button';b.onclick=function(){window.CorusAndroidMap.selectCity(city.id)};const faces=document.createElement('span');faces.className='faces';city.faces.forEach(function(person){const face=document.createElement('span');face.className='face';face.textContent=(person.name||'?').slice(0,1);if(person.avatar){const img=document.createElement('img');img.alt='';img.src=person.avatar;img.onerror=function(){img.remove()};face.appendChild(img)}faces.appendChild(face)});const count=document.createElement('span');count.className='count';count.textContent=city.count.toLocaleString();const badgeSize=Math.max(compact?20:23,count.textContent.length*8+(compact?6:7));count.style.width=badgeSize+"px";count.style.height=badgeSize+"px";faces.appendChild(count);const focused=interactive&&city.id===window.CorusAppleMap.data.focus?.id;const listening=focused&&window.CorusAppleMap.data.playbackMode==='listen';const sheetFocused=focused&&window.CorusAppleMap.data.citySheetOpen;const label=document.createElement('span');label.className='label'+(listening?' listening':'');if(listening){label.innerHTML='<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 14v-3a8 8 0 0 1 16 0v3M4 14h3v5H4zM20 14h-3v5h3z"/></svg><span>'+city.name+'</span>'}else{label.textContent=city.name}const arts=city.faces.filter(function(p){return p.artwork&&(!listening||p.id===window.CorusAppleMap.data.playingUserId)});if(focused&&arts.length){const fan=document.createElement('span');fan.className='art-fan'+(listening?' listening':'');arts.slice(0,listening?1:3).forEach(function(person,index){const spread=index-(arts.length-1)/2;const art=document.createElement('img');art.className='art art-'+index;art.alt='';art.src=person.artwork;art.style.setProperty('--fan-x',(spread*36)+'px');art.style.setProperty('--fan-y',(Math.abs(spread)*10-4)+'px');art.style.setProperty('--fan-r',(spread*19)+'deg');fan.appendChild(art)});b.appendChild(fan);requestAnimationFrame(function(){void fan.offsetWidth;requestAnimationFrame(function(){setTimeout(function(){fan.classList.add('raised')},35)})})}b.append(faces,label);/* MapKit anchors annotations at their bottom edge. Shift the focused marker down by its full visual stack so the visible cluster — not its label baseline — lands at map center. */const markerOffset=sheetFocused?-62:(focused?-88:(interactive?0:-38.5));return new kit.Annotation({latitude:city.latitude,longitude:city.longitude},function(){return b},{calloutEnabled:false,animates:false,anchorOffset:new DOMPoint(0,markerOffset),displayPriority:focused?1000:750});}
        window.CorusAppleMap={ready:false,data:{cities:[],dark:false,focus:null},lastFocus:null,lastMarkerKey:null,update:function(data){this.data=data;this.apply()},apply:function(){if(!this.ready||!map)return;sizeMap();document.body.classList.toggle('dark',this.data.dark);/* The Android surface must follow Corus' resolved theme just as SwiftUI's Map does. The old fixed Light scheme made a dark Android screen contain a bright map. */map.colorScheme=this.data.dark?kit.ColorScheme.Dark:kit.ColorScheme.Light;/* Opening a city sheet changes camera framing, not the marker. Recreating its DOM would discard decoded artwork and replay the fan animation. */const markerKey=JSON.stringify({cities:this.data.cities,dark:this.data.dark,focus:this.data.focus?.id||'',playbackMode:this.data.playbackMode||'',playingUserId:this.data.playingUserId||''});if(this.lastMarkerKey!==markerKey){this.lastMarkerKey=markerKey;annotations.forEach(function(a){map.removeAnnotation(a)});annotations=this.data.cities.map(marker);annotations.forEach(function(a){map.addAnnotation(a)})}const focus=this.data.focus;const top=Math.max(0,Math.min(.8,this.data.mapTopInsetFraction||0));const bottom=Math.max(0,Math.min(.95,this.data.mapBottomOcclusionFraction||0));const focusKey=focus&&focus.id+":"+this.data.focusRevision+":"+top.toFixed(4)+":"+bottom.toFixed(4);if(focus&&this.lastFocus!==focusKey){const animate=this.lastFocus!==null;this.lastFocus=focusKey;const visibleFocus=this.data.focusInVisibleMap&&interactive;const latitudeDelta=visibleFocus?.6:(interactive?7:22);const longitudeDelta=visibleFocus?latitudeDelta*window.innerWidth/window.innerHeight/Math.max(.15,Math.cos(focus.latitude*Math.PI/180)):(interactive?10:34);/* Center the cluster in the map band that remains visible between the Compose header and either the city sheet or listening card. Geometry is part of the camera key because the listening card is measured after its first frame. */const offset=(bottom-top)/2;map.setRegionAnimated({center:{latitude:focus.latitude-(visibleFocus?latitudeDelta*offset:0),longitude:focus.longitude},span:{latitudeDelta:latitudeDelta,longitudeDelta:longitudeDelta}},animate)}}};
        </script></body></html>
    """.trimIndent()
}
