package com.anitail.desktop.utils

import java.io.File

object CacheUtils {
    fun clearCache() {
        val cacheDir = File(System.getProperty("user.home"), ".anitail/cache")
        if (cacheDir.exists()) {
            cacheDir.deleteRecursively()
        }
    }
}
