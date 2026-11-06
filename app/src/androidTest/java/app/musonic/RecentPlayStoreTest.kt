package app.musonic

import androidx.test.platform.app.InstrumentationRegistry
import app.musonic.data.RecentPlayStore
import app.musonic.data.Song
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class RecentPlayStoreTest {
    @Test fun historyIsPersistedDeduplicatedAndAccountScoped() {
        val store = RecentPlayStore.get(InstrumentationRegistry.getInstrumentation().targetContext)
        val a = UUID.randomUUID().toString()
        val b = UUID.randomUUID().toString()
        val first = Song("1", "First", "Artist", "Album", "cover", 123, true)
        val second = first.copy(id = "2", title = "Second")
        try {
            store.record(a, first); store.record(a, second); store.record(a, first)
            store.record(b, second)
            assertEquals(listOf(first, second), store.read(a))
            assertEquals(listOf(second), store.read(b))
            store.close()
            assertEquals("History must survive reopening SQLite", listOf(first, second), store.read(a))
            store.remove(a)
            assertTrue(store.read(a).isEmpty())
            assertEquals(listOf(second), store.read(b))
        } finally { store.remove(a); store.remove(b) }
    }
}
