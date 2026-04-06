/*
 * Copyright (C) 2025-2026 AxionOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.chaldeaprjkt.gamespace.gamebar.mapper

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class TapMappingStore(context: Context, private val gson: Gson) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var nextId = 0

    fun load(packageName: String): List<TapMapping> {
        val json = prefs.getString(key(packageName), null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<TapMapping>>() {}.type
            val list: List<TapMapping> = gson.fromJson(json, type)
            nextId = (list.maxOfOrNull { it.id } ?: -1) + 1
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(packageName: String, mappings: List<TapMapping>) {
        val json = gson.toJson(mappings)
        prefs.edit().putString(key(packageName), json).apply()
    }

    fun clear(packageName: String) {
        prefs.edit().remove(key(packageName)).apply()
    }

    fun generateId(): Int = nextId++

    private fun key(packageName: String) = "${KEY_PREFIX}$packageName"

    companion object {
        private const val PREFS_NAME = "tap_mappings"
        private const val KEY_PREFIX = "mappings_"
    }
}
