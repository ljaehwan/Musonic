package app.musonic

import app.musonic.data.SubsonicClient
import org.junit.Assert.*
import org.junit.Test

class PlayTransportTest {
    @Test fun cleartextIsRejectedEvenForPreviouslySavedOptIn() {
        assertFalse(BuildConfig.ALLOW_HTTP)
        assertThrows(IllegalArgumentException::class.java) {
            SubsonicClient.normalizeServer("http://192.0.2.1:4533", true)
        }
        assertEquals("https", SubsonicClient.normalizeServer("https://music.example.com").scheme)
    }
}
