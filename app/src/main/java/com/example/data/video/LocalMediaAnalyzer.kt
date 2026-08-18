package com.example.data.video

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.example.domain.analysis.AudioSignal
import com.example.domain.analysis.AudioSignalType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

class LocalMediaAnalyzer(private val context: Context) {
    data class Result(
        val metadata: SourceVideoMetadata,
        val hasAudioTrack: Boolean,
        val audioSignals: List<AudioSignal>
    )

    suspend fun analyze(uri: Uri): kotlin.Result<Result> = withContext(Dispatchers.IO) {
        runCatching {
            val metadata = Media3VideoProcessor(context).inspectSource(uri)
                ?: error("تعذر قراءة metadata للفيديو.")
            val extractor = MediaExtractor()
            val audioTrack = try {
                if (uri.scheme == "content") extractor.setDataSource(context, uri, null)
                else extractor.setDataSource(uri.toString())
                (0 until extractor.trackCount).firstOrNull { index ->
                    extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                }
            } finally {
                // The extractor is reused only if an audio track was found; otherwise release now.
            }
            if (audioTrack == null) {
                extractor.release()
                return@runCatching Result(metadata, false, emptyList())
            }
            extractor.selectTrack(audioTrack)
            val format = extractor.getTrackFormat(audioTrack)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("صيغة الصوت غير معروفة.")
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            val signals = decodeEnergy(extractor, codec, format)
            codec.stop()
            codec.release()
            extractor.release()
            Result(metadata, true, signals)
        }
    }

    private fun decodeEnergy(
        extractor: MediaExtractor,
        codec: MediaCodec,
        format: MediaFormat
    ): List<AudioSignal> {
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        val signals = mutableListOf<AudioSignal>()
        var idleLoops = 0
        while (idleLoops < 200) {
            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val input = codec.getInputBuffer(inputIndex) ?: continue
                    val sampleSize = extractor.readSampleData(input, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    idleLoops++
                    if (inputDone) continue
                }
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                else -> if (outputIndex >= 0) {
                    idleLoops = 0
                    val output = codec.getOutputBuffer(outputIndex)
                    if (output != null && bufferInfo.size > 0) {
                        output.position(bufferInfo.offset)
                        output.limit(bufferInfo.offset + bufferInfo.size)
                        val energy = pcmEnergy(output)
                        val start = bufferInfo.presentationTimeUs / 1_000_000f
                        val duration = estimateBufferDuration(format, bufferInfo.size)
                        val end = (start + duration).coerceAtLeast(start + 0.01f)
                        val type = when {
                            energy < 0.015f -> AudioSignalType.SILENCE
                            energy > 0.65f -> AudioSignalType.PEAK
                            else -> AudioSignalType.SPEECH_ENERGY
                        }
                        signals += AudioSignal(start, end, type, energy, 0.75f, "MediaCodec PCM RMS")
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                }
            }
        }
        return signals
    }

    private fun pcmEnergy(buffer: java.nio.ByteBuffer): Float {
        val pcm = buffer.order(ByteOrder.LITTLE_ENDIAN)
        var sum = 0.0
        var count = 0
        var peak = 0
        while (pcm.remaining() >= 2) {
            val sample = pcm.short.toInt()
            val magnitude = abs(sample)
            peak = maxOf(peak, magnitude)
            sum += sample.toDouble() * sample.toDouble()
            count++
        }
        if (count == 0) return 0f
        val rms = sqrt(sum / count) / Short.MAX_VALUE
        return maxOf(rms.toFloat(), peak.toFloat() / Short.MAX_VALUE).coerceIn(0f, 1f)
    }

    private fun estimateBufferDuration(format: MediaFormat, byteCount: Int): Float {
        val sampleRate = formatIntOrDefault(format, MediaFormat.KEY_SAMPLE_RATE, 16_000)
        val channels = formatIntOrDefault(format, MediaFormat.KEY_CHANNEL_COUNT, 1)
        return (byteCount / 2f / channels / sampleRate).coerceAtLeast(0.01f)
    }

    private fun formatIntOrDefault(format: MediaFormat, key: String, default: Int): Int =
        if (format.containsKey(key)) runCatching { format.getInteger(key) }.getOrDefault(default) else default
}
