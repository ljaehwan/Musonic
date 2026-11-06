package app.musonic.playback

/** Engine timestamps win once available; Subsonic duration is in seconds. */
internal fun resolveDurationMs(engineDurationMs: Long, serverDurationSeconds: Int, metadataDurationMs: Long? = null): Long =
    engineDurationMs.takeIf { it > 0 }
        ?: serverDurationSeconds.toLong().takeIf { it > 0 }?.times(1000L)
        ?: metadataDurationMs?.takeIf { it > 0 }
        ?: 0L
