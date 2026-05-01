/*
 * SPDX-FileCopyrightText: 2025 DerpFest AOSP
 * SPDX-FileCopyrightText: 2026 crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package io.chaldeaprjkt.gamespace.data

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import io.chaldeaprjkt.gamespace.R
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameOptimizationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "game_optimization_settings",
        Context.MODE_PRIVATE
    )

    var isMemoryManagementEnabled: Boolean
        get() = prefs.getBoolean(KEY_MEMORY_MANAGEMENT, false)
        set(value) = prefs.edit().putBoolean(KEY_MEMORY_MANAGEMENT, value).apply()

    var isCacheManagementEnabled: Boolean
        get() = prefs.getBoolean(KEY_CACHE_MANAGEMENT, true)
        set(value) = prefs.edit().putBoolean(KEY_CACHE_MANAGEMENT, value).apply()

    fun optimizeGameLaunch(packageName: String) {
        if (isMemoryManagementEnabled) {
            clearBackgroundProcesses()
        }
        if (isCacheManagementEnabled) {
            optimizeGameCache(packageName)
        }
    }

    private fun trimMemory(level: Int) {
        val activityManager: ActivityManager = context.getSystemService(ActivityManager::class.java)
        try {
            val runtimeTrimMemory = ActivityManager::class.java.getMethod("trimMemory", Int::class.java)
            runtimeTrimMemory.invoke(activityManager, level)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun clearBackgroundProcesses() {
        val activityManager: ActivityManager = context.getSystemService(ActivityManager::class.java)
        val runningApps = activityManager.runningAppProcesses ?: return
        runningApps.forEach { processInfo ->
            if (processInfo.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                activityManager.killBackgroundProcesses(processInfo.processName)
            }
        }
    }

    private fun optimizeGameCache(packageName: String) {
        try {
            val pm = context.packageManager
            val packageInfo = pm.getPackageInfo(packageName, 0)
            val appInfo = packageInfo.applicationInfo ?: return

            val cacheSize = listOfNotNull(
                appInfo.dataDir?.let { File(it, "cache") },
                appInfo.dataDir?.let { File(it, "code_cache") },
                appInfo.deviceProtectedDataDir?.let { File(it, "cache") },
                appInfo.deviceProtectedDataDir?.let { File(it, "code_cache") }
            )
                .filter { it.exists() && it.isDirectory }
                .sumOf { dir -> dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }

            if (cacheSize <= CACHE_THRESHOLD) return

            val observerClass = Class.forName("android.content.pm.IPackageDataObserver")
            val deleteCache = PackageManager::class.java.getMethod(
                "deleteApplicationCacheFiles",
                String::class.java,
                observerClass
            )
            deleteCache.invoke(pm, packageName, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val KEY_MEMORY_MANAGEMENT = "memory_management"
        private const val KEY_CACHE_MANAGEMENT = "cache_management"
        private const val CACHE_THRESHOLD = 100 * 1024 * 1024L // 100MB
    }
} 
