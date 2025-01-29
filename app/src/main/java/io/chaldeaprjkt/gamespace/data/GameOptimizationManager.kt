/*
 * SPDX-FileCopyrightText: 2025 DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package io.chaldeaprjkt.gamespace.data

import android.app.ActivityManager
import android.content.Context
import android.content.ComponentCallbacks2
import android.content.SharedPreferences
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
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

    private val activityManager: ActivityManager = context.getSystemService(ActivityManager::class.java)

    var isLaunchBoostEnabled: Boolean
        get() = prefs.getBoolean(KEY_LAUNCH_BOOST, true)
        set(value) = prefs.edit().putBoolean(KEY_LAUNCH_BOOST, value).apply()

    var isMemoryManagementEnabled: Boolean
        get() = prefs.getBoolean(KEY_MEMORY_MANAGEMENT, false)
        set(value) = prefs.edit().putBoolean(KEY_MEMORY_MANAGEMENT, value).apply()

    var loadPriority: String
        get() = prefs.getString(KEY_LOAD_PRIORITY, "balanced") ?: "balanced"
        set(value) = prefs.edit().putString(KEY_LOAD_PRIORITY, value).apply()

    var isCacheManagementEnabled: Boolean
        get() = prefs.getBoolean(KEY_CACHE_MANAGEMENT, true)
        set(value) = prefs.edit().putBoolean(KEY_CACHE_MANAGEMENT, value).apply()

    fun optimizeGameLaunch(packageName: String) {
        if (isLaunchBoostEnabled) {
            // Set process priority to foreground
            Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND)
        }

        if (isMemoryManagementEnabled) {
            clearBackgroundProcesses()
        }

        when (loadPriority) {
            "performance" -> {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)
                trimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
            }
            "powersave" -> {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                trimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
            }
            else -> {
                Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT)
                trimMemory(ComponentCallbacks2.TRIM_MEMORY_MODERATE)
            }
        }

        if (isCacheManagementEnabled) {
            optimizeGameCache(packageName)
        }
    }

    private fun trimMemory(level: Int) {
        try {
            val runtimeTrimMemory = ActivityManager::class.java.getMethod("trimMemory", Int::class.java)
            runtimeTrimMemory.invoke(activityManager, level)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun clearBackgroundProcesses() {
        val runningApps = activityManager.runningAppProcesses ?: return
        runningApps.forEach { processInfo ->
            if (processInfo.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                activityManager.killBackgroundProcesses(processInfo.processName)
            }
        }
    }

    private fun optimizeGameCache(packageName: String) {
        try {
            // Clear package cache if it's too large
            val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
            packageInfo.applicationInfo?.dataDir?.let { dataDir ->
                val dir = java.io.File(dataDir)
                if (dir.exists()) {
                    val cacheSize = dir.walkTopDown()
                        .filter { it.isFile }
                        .map { it.length() }
                        .sum()

                    if (cacheSize > CACHE_THRESHOLD) {
                        context.packageManager.clearPackagePreferredActivities(packageName)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val KEY_LAUNCH_BOOST = "launch_boost"
        private const val KEY_MEMORY_MANAGEMENT = "memory_management"
        private const val KEY_LOAD_PRIORITY = "load_priority"
        private const val KEY_CACHE_MANAGEMENT = "cache_management"
        private const val CACHE_THRESHOLD = 100 * 1024 * 1024L // 100MB
    }
} 
