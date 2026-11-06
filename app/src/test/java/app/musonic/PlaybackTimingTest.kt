package app.musonic

import app.musonic.playback.resolveDurationMs
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTimingTest {
    @Test fun unknownEngineDurationUsesServerSeconds() {
        assertEquals(214_000L, resolveDurationMs(-9223372036854775807L, 214))
    }
    @Test fun knownEngineDurationReplacesRoundedServerDuration() {
        assertEquals(214_680L, resolveDurationMs(214_680L, 214))
    }
    @Test fun mediaSessionMetadataRestoresMissingQueueDuration() {
        assertEquals(214_000L, resolveDurationMs(0L, 0, 214_000L))
    }
    @Test fun noValidSourceRemainsUnknownInsteadOfInventingLength() {
        assertEquals(0L, resolveDurationMs(-1L, 0, null))
        assertEquals(0L, resolveDurationMs(0L, -1, -1L))
    }
    @Test fun secondsAreConvertedWithoutIntegerOverflow() {
        assertEquals(2_147_483_647_000L, resolveDurationMs(0L, Int.MAX_VALUE))
    }
}
