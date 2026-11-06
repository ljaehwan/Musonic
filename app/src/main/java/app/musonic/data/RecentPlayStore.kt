package app.musonic.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Local listening history, isolated by saved server account. Never stores authentication URLs. */
class RecentPlayStore private constructor(context: Context) : SQLiteOpenHelper(context.applicationContext, "recent-plays.db", null, 1) {
    private val revision = MutableStateFlow(0L)
    val changes = revision.asStateFlow()
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE recent (entry_id INTEGER PRIMARY KEY AUTOINCREMENT, profile TEXT NOT NULL, song_id TEXT NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, album TEXT NOT NULL, cover TEXT, duration INTEGER NOT NULL, starred INTEGER NOT NULL, UNIQUE(profile, song_id))")
        db.execSQL("CREATE INDEX recent_account ON recent(profile, entry_id DESC)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    @Synchronized fun record(profile: String, song: Song) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.insertWithOnConflict("recent", null, ContentValues().apply {
                put("profile", profile); put("song_id", song.id); put("title", song.title); put("artist", song.artist)
                put("album", song.album); put("cover", song.coverArt); put("duration", song.duration); put("starred", if (song.starred) 1 else 0)
            }, SQLiteDatabase.CONFLICT_REPLACE)
            db.execSQL("DELETE FROM recent WHERE profile = ? AND entry_id NOT IN (SELECT entry_id FROM recent WHERE profile = ? ORDER BY entry_id DESC LIMIT 5000)", arrayOf(profile, profile))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        revision.value++
    }
    @Synchronized fun read(profile: String): List<Song> = readableDatabase.rawQuery(
        "SELECT song_id, title, artist, album, cover, duration, starred FROM recent WHERE profile = ? ORDER BY entry_id DESC", arrayOf(profile)
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(Song(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4), cursor.getInt(5), cursor.getInt(6) == 1)) } }
    @Synchronized fun remove(profile: String) { writableDatabase.delete("recent", "profile = ?", arrayOf(profile)); revision.value++ }
    companion object {
        @Volatile private var instance: RecentPlayStore? = null
        fun get(context: Context): RecentPlayStore = instance ?: synchronized(this) { instance ?: RecentPlayStore(context).also { instance = it } }
    }
}
