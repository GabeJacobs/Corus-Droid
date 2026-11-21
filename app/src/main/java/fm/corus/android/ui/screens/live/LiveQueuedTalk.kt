package fm.corus.android.ui.screens.live

// Joining Talk and subsequent snapshots must not override a deliberate mute.
internal fun LiveRoom.startsQueuedTalkAfter(previous: LiveRoom?, userId: String?): Boolean =
    previous != null && previous.id == id && previous.host.uid == host.uid && host.uid == userId &&
        previous.revision == revision && previous.talkAfterSongArmed && previous.voiceMode != "talk" &&
        voiceMode == "talk" && status != "ended" && voiceSession != null && voiceSession != previous.voiceSession
