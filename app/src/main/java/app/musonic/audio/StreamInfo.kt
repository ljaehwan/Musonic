package app.musonic.audio

import app.musonic.R
import app.musonic.settings.*

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks
import androidx.media3.common.Player
import java.util.Locale
import kotlin.math.roundToInt

/** Information from the selected incoming track, never from a requested transcode setting. */
data class StreamInfo(val codec: String, val averageBitrate: Int?, val sampleRate: Int?) {
    fun label(context: android.content.Context): String = listOfNotNull(
        if (codec == "Audio") context.getString(R.string.audio_generic) else codec,
        averageBitrate?.let { "${(it / 1000.0).roundToInt()} kbps" } ?: context.getString(R.string.bitrate_unknown),
        sampleRate?.let { String.format(Locale.US, "%s kHz", (it / 1000.0).toString().removeSuffix(".0")) },
    ).joinToString("   ")

    companion object {
        fun current(player: Player): StreamInfo? = if (player.currentMediaItem != null &&
            (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_ENDED)) selected(player.currentTracks) else null

        fun selected(tracks: Tracks): StreamInfo? {
            for (group in tracks.groups) {
                if (group.type != C.TRACK_TYPE_AUDIO) continue
                for (index in 0 until group.length) if (group.isTrackSelected(index)) return from(group.getTrackFormat(index))
            }
            return null
        }

        @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
        fun from(format: Format): StreamInfo {
            val codec = when (format.sampleMimeType) {
                "audio/mpeg" -> "MP3"
                "audio/mpeg-L1" -> "MP1"
                "audio/mpeg-L2" -> "MP2"
                "audio/mp4a-latm", "audio/aac" -> "AAC"
                "audio/flac" -> "FLAC"
                "audio/alac" -> "ALAC"
                "audio/opus" -> "Opus"
                "audio/vorbis" -> "Vorbis"
                "audio/raw" -> "PCM"
                "audio/ac3" -> "AC-3"
                "audio/eac3", "audio/eac3-joc" -> "E-AC-3"
                else -> "Audio"
            }
            // Peak rate, source-file metadata and requested rate are not the stream's average rate.
            return StreamInfo(codec, format.averageBitrate.takeIf { it > 0 }, format.sampleRate.takeIf { it > 0 })
        }
    }
}
