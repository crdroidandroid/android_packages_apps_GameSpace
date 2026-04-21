/*
 * Copyright (C) 2020 The exTHmUI Open Source Project
 * Copyright (C) 2021 AOSP-Krypton Project
 * Copyright (C) 2022 Nameless-AOSP Project
 * Copyright (C) 2023 the risingOS android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.chaldeaprjkt.gamespace.gamebar

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class DanmakuServiceListener : NotificationListenerService() {

    private val postedNotifications = mutableMapOf<String, Long>()

    private val appLabelsCache = mutableMapOf<String, String?>()

    var danmakuServiceInterface: DanmakuServiceInterface? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        getActiveNotifications()?.forEach { sbn ->
            if (sbn.isClearable && !sbn.isOngoing) {
                val danmakuText = extractDanmakuText(sbn)
                if (danmakuText.isNotBlank()) {
                    postedNotifications[danmakuText] = System.currentTimeMillis()
                }
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notificationMode = danmakuServiceInterface?.danmakuNotificationMode ?: return
        if (!notificationMode || !sbn.isClearable || sbn.isOngoing) return

        val danmakuText = extractDanmakuText(sbn)

        if (danmakuText.isNotBlank()) {
            if (!postedNotifications.containsKey(danmakuText)) {
                postedNotifications[danmakuText] = System.currentTimeMillis()
                danmakuServiceInterface?.showNotificationAsOverlay(danmakuText)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val danmakuText = extractDanmakuText(sbn)
        if (danmakuText.isNotBlank()) {
            postedNotifications.remove(danmakuText)
        }
    }

    private fun extractDanmakuText(sbn: StatusBarNotification): String {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        val appLabel = appLabelsCache.getOrPut(sbn.packageName) {
            danmakuServiceInterface?.getApplabel(sbn.packageName)
        } ?: ""

        // Skip annoying notification headers
        if (!title.isNullOrBlank() && !appLabel.isNullOrBlank()) {
            if (sbn.isGroup && title.contains(appLabel, ignoreCase = true)) return ""
        }

        return buildDanmakuText(title, text)
    }

    private fun buildDanmakuText(title: String?, text: String?): String {
        val sb = StringBuilder()
        if (!title.isNullOrBlank()) {
            sb.append("[").append(title).append("]")
        }
        if (!text.isNullOrBlank()) {
            if (sb.isNotEmpty()) sb.append(" ")
            sb.append(text)
        }
        return sb.toString().trim()
    }

    companion object {
        private const val NOTIFICATIONS_MAX_CACHED = 99
    }
}
