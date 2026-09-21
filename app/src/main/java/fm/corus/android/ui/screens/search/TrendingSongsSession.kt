package fm.corus.android.ui.screens.search

import fm.corus.android.data.model.TrendingSong
import fm.corus.android.data.model.TrendingMovie
import fm.corus.android.data.model.TrendingHashtag
import fm.corus.android.data.model.TrendingArtist
import fm.corus.android.data.model.TrendingAlbum
import fm.corus.android.data.model.TrendingDirector
import fm.corus.android.data.model.TrendingWindow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One process-lifetime chart for the compact preview and its See All page. */
internal object TrendingSongsSession {
    private val _window = MutableStateFlow(TrendingWindow.DEFAULT)
    val window: StateFlow<TrendingWindow> = _window
    private val _songs = MutableStateFlow<List<TrendingSong>>(emptyList())
    val songs: StateFlow<List<TrendingSong>> = _songs
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    private var generation = 0
    private var fetching = false

    fun choose(window: TrendingWindow) {
        if (_window.value == window) return
        generation++
        _window.value = window
        _songs.value = emptyList()
        _loading.value = true
        fetching = false
    }

    fun seedWeek(songs: List<TrendingSong>) {
        if (_window.value == TrendingWindow.WEEK && !fetching && _songs.value.isEmpty()) {
            _songs.value = songs
            _loading.value = false
        }
    }

    suspend fun load(minDisplayMs: Long = 0, fetch: suspend (TrendingWindow) -> List<TrendingSong>) {
        if (fetching || (!_loading.value && _songs.value.isNotEmpty())) return
        val window = _window.value
        val request = generation
        fetching = true
        _loading.value = true
        val started = System.currentTimeMillis()
        val result = try { fetch(window) } catch (_: Exception) { emptyList() }
        val remaining = minDisplayMs - (System.currentTimeMillis() - started)
        if (remaining > 0) delay(remaining)
        if (request == generation) {
            _songs.value = result
            _loading.value = false
            fetching = false
        }
    }
}

/** Shared preview/full-list state for the other windowed charts. */
internal class TrendingChartSession<T>(defaultWindow: TrendingWindow) {
    val window = MutableStateFlow(defaultWindow)
    val rows = MutableStateFlow<List<T>>(emptyList())
    val loading = MutableStateFlow(true)
    private var generation = 0
    private var fetching = false

    fun choose(selected: TrendingWindow) {
        if (window.value == selected) return
        generation++
        window.value = selected
        rows.value = emptyList()
        loading.value = true
        fetching = false
    }

    suspend fun load(minDisplayMs: Long = 0, fetch: suspend (TrendingWindow) -> List<T>) {
        if (fetching || (!loading.value && rows.value.isNotEmpty())) return
        val selected = window.value
        val request = generation
        fetching = true
        loading.value = true
        val started = System.currentTimeMillis()
        val result = try { fetch(selected) } catch (_: Exception) { emptyList() }
        val remaining = minDisplayMs - (System.currentTimeMillis() - started)
        if (remaining > 0) delay(remaining)
        if (request == generation) {
            rows.value = result
            loading.value = false
            fetching = false
        }
    }
}

internal object TrendingChartSessions {
    val films = TrendingChartSession<TrendingMovie>(TrendingWindow.FILMS_DEFAULT)
    val hashtags = TrendingChartSession<TrendingHashtag>(TrendingWindow.DEFAULT)
    val artists = TrendingChartSession<TrendingArtist>(TrendingWindow.DEFAULT)
    val albums = TrendingChartSession<TrendingAlbum>(TrendingWindow.DEFAULT)
    val directors = TrendingChartSession<TrendingDirector>(TrendingWindow.DIRECTORS_DEFAULT)
}
