package fm.corus.android.ui.screens.live

import io.livekit.android.audio.AudioProcessorInterface
import java.nio.ByteBuffer

// Gate after microphone DSP, so noise suppression is already warm when speech is released.
internal class LiveVoiceSpeechGate(private val allowed: () -> Boolean) : AudioProcessorInterface {
    override fun isEnabled() = true
    override fun getName() = "corus_voice_readiness"
    override fun initializeAudioProcessing(sampleRateHz: Int, numChannels: Int) = Unit
    override fun resetAudioProcessing(newRate: Int) = Unit
    override fun processAudio(numBands: Int, numFrames: Int, buffer: ByteBuffer) {
        if (!allowed()) for (index in 0 until buffer.capacity()) buffer.put(index, 0)
    }
}
