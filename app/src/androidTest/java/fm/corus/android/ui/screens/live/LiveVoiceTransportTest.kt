package fm.corus.android.ui.screens.live

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fm.corus.android.MainActivity
import io.livekit.android.LiveKit
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in cloud transport check. Access comes from a short-lived, app-scoped
 * local test file, never from checked-in keys or production Firebase users. */
@RunWith(AndroidJUnit4::class)
class LiveVoiceTransportTest {
    @Test fun nativeMicrophoneCanPublishToLiveKit() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("corusLiveTransport") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.getExternalFilesDir(null), "live-voice-test.json")
        val config = JSONObject(file.readText())
        file.delete()
        ActivityScenario.launch(MainActivity::class.java).use {
            runBlocking {
                val room = LiveKit.create(context)
                try {
                    withTimeout(25000) {
                        room.connect(config.getString("url"), config.getString("token"))
                        val enabled = room.localParticipant.setMicrophoneEnabled(true)
                        assertTrue("Microphone publish returned false; permissions=${room.localParticipant.permissions}", enabled)
                        while (room.localParticipant.audioTrackPublications.isEmpty()) delay(100)
                        delay(10000)
                    }
                } finally { room.disconnect(); room.release() }
            }
        }
    }
}
