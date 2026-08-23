package com.fyiplayer.app.player

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

// 64 MB LRU disk cache for played media bytes -- a re-watch or a shorts-pager swipe-back skips
// the network entirely once a clip is in cache.
private const val CACHE_BYTES = 64L * 1024 * 1024

/** Lazy process-wide singleton: [SimpleCache] holds an exclusive lock on its directory, so there
 *  must be exactly one live instance for `cacheDir/media`. */
internal object MediaCache {
    @Volatile private var cache: Cache? = null

    fun get(context: Context): Cache = cache ?: synchronized(this) {
        cache ?: SimpleCache(
            File(context.applicationContext.cacheDir, "media"),
            LeastRecentlyUsedCacheEvictor(CACHE_BYTES),
            // Required by SimpleCache since media3 2.12+ for its on-disk index.
            StandaloneDatabaseProvider(context.applicationContext),
        ).also { cache = it }
    }
}
