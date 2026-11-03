package fm.corus.android.ui.screens.map

/** Lives with the retained WebView. A parsed/reloaded page must replay its
 * latest payload even if Compose supplied the same data before it was ready. */
internal class MapPayloadGate {
    private var lastDocument: String? = null
    private var lastPayload: String? = null

    fun shouldSend(document: String, payload: String, force: Boolean = false): Boolean {
        if (!force && document == lastDocument && payload == lastPayload) return false
        lastDocument = document
        lastPayload = payload
        return true
    }
}
