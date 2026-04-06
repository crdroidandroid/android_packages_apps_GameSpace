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
package io.chaldeaprjkt.gamespace.gamebar.tiles

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemProperties
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.android.axion.platform.AxPlatformClient
import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.data.AppSettings
import io.chaldeaprjkt.gamespace.data.SystemSettings
import javax.inject.Inject
import javax.inject.Singleton

interface TileAction {
    val id: String
    val label: String
    val icon: Int
    val isEnabled: Boolean
    @Composable fun observeEnabled(): State<Boolean>
    fun toggle()
}

class ToggleableTile(
    override val id: String,
    override var label: String,
    override val icon: Int,
    private val state: MutableState<Boolean>,
    private val setter: (Boolean) -> Unit,
) : TileAction {
    override val isEnabled: Boolean get() = state.value
    @Composable override fun observeEnabled(): State<Boolean> = rememberUpdatedState(state.value)
    override fun toggle() {
        state.value = !state.value
        setter(state.value)
    }
}

class FixedActionTile(
    override val id: String,
    override val label: String,
    override val icon: Int,
    private val action: () -> Unit,
) : TileAction {
    override val isEnabled: Boolean = false
    @Composable override fun observeEnabled(): State<Boolean> = rememberUpdatedState(false)
    override fun toggle() = action()
}

class PlatformTile(
    override val id: String,
    override val icon: Int,
    private val feature: String,
    private val platform: AxPlatformClient,
) : TileAction {
    val activeState = mutableStateOf(false)
    val labelState = mutableStateOf("")

    override val label: String get() = labelState.value
    override val isEnabled: Boolean get() = activeState.value
    @Composable override fun observeEnabled(): State<Boolean> = rememberUpdatedState(activeState.value)

    override fun toggle() {
        platform.toggle(feature)
    }

    fun updateFromState(state: Bundle) {
        activeState.value = state.getBoolean("active", false)
        val newLabel = AxPlatformClient.getLabel(state)
        if (!newLabel.isNullOrBlank()) labelState.value = newLabel
    }
}

@Singleton
class TileRepository @Inject constructor(
    private val context: Context,
    private val appSettings: AppSettings,
    private val systemSettings: SystemSettings,
) {
    private lateinit var platform: AxPlatformClient

    private lateinit var defaultTiles: List<TileAction>
    private val platformTiles = mutableListOf<PlatformTile>()

    private val platformListener = object : AxPlatformClient.Listener() {
        override fun onFeatureChanged(feature: String, active: Boolean) {
            platformTiles.find { it.id == feature }?.activeState?.value = active
        }

        override fun onStateChanged(key: String, state: Bundle) {
            platformTiles.find { it.id == key }?.updateFromState(state)
        }
    }

    private val _tileOrder = mutableStateListOf<String>()

    val allAvailableTiles: List<TileAction>
        get() = defaultTiles

    private val _tiles = mutableStateListOf<TileAction>()
    val tiles: SnapshotStateList<TileAction> get() = _tiles

    val isBrightnessVisible: MutableState<Boolean> = mutableStateOf(appSettings.brightnessEnabled)
    val isFpsGraphVisible: MutableState<Boolean> = mutableStateOf(appSettings.fpsGraphEnabled)
    fun init(platform: AxPlatformClient) {
        this.platform = platform

        defaultTiles = buildDefaultTiles()

        platform.addListener(platformListener)

        refreshPlatformStates()

        _tileOrder.clear()
        _tileOrder.addAll(loadTileOrder())
        _tiles.clear()
        _tiles.addAll(
            _tileOrder.mapNotNull { id ->
                defaultTiles.find { it.id == id }
            }
        )
    }

    fun refreshPlatformStates() {
        if (!::platform.isInitialized) return
        platformTiles.forEach { tile ->
            val state = platform.getState(tile.id)
            if (!state.isEmpty) tile.updateFromState(state)
        }
    }

    fun dispose() {
        platform.removeListener(platformListener)
    }

    fun setBrightnessEnabled(enabled: Boolean) {
        isBrightnessVisible.value = enabled
        appSettings.brightnessEnabled = enabled
    }

    fun setFpsGraphEnabled(enabled: Boolean) {
        isFpsGraphVisible.value = enabled
        appSettings.fpsGraphEnabled = enabled
    }

    private fun saveTileOrder() {
        appSettings.tileOrder = _tileOrder
    }

    private fun loadTileOrder(): List<String> {
        val savedOrder = appSettings.tileOrder
        return if (savedOrder.isNotEmpty()) {
            savedOrder.filter { id -> defaultTiles.any { it.id == id } }
        } else {
            defaultTiles.map { it.id }
        }
    }

    fun updateTileSelection(selectedIds: List<String>) {
        _tiles.clear()
        _tiles.addAll(selectedIds.mapNotNull { id ->
            defaultTiles.find { it.id == id }
        })
        _tileOrder.clear()
        _tileOrder.addAll(selectedIds)
        saveTileOrder()
    }

    private fun platformTile(feature: String, iconRes: Int, fallbackLabel: String): PlatformTile {
        val tile = PlatformTile(
            id = feature,
            icon = iconRes,
            feature = feature,
            platform = platform,
        )
        tile.labelState.value = fallbackLabel
        platformTiles.add(tile)
        return tile
    }

    private fun buildDefaultTiles(): List<TileAction> = buildList {
        platformTiles.clear()

        add(platformTile(
            AxPlatformClient.FEATURE_WIFI,
            R.drawable.materialsymbols_ic_wifi_rounded_filled,
            context.getString(R.string.tile_wifi),
        ))
        add(platformTile(
            AxPlatformClient.FEATURE_BLUETOOTH,
            R.drawable.materialsymbols_ic_bluetooth_rounded_filled,
            context.getString(R.string.tile_bluetooth),
        ))
        add(platformTile(
            AxPlatformClient.FEATURE_ZEN,
            R.drawable.materialsymbols_ic_do_not_disturb_on_rounded_filled,
            context.getString(R.string.tile_dnd),
        ))
        add(platformTile(
            AxPlatformClient.FEATURE_ROTATION,
            R.drawable.materialsymbols_ic_screen_rotation_up_rounded_filled,
            context.getString(R.string.tile_auto_rotate),
        ))
        add(platformTile(
            AxPlatformClient.FEATURE_MOBILE_DATA,
            R.drawable.materialsymbols_ic_android_cell_4_bar_rounded_filled,
            context.getString(R.string.tile_mobile_data),
        ))
        add(platformTile(
            AxPlatformClient.FEATURE_AIRPLANE_MODE,
            R.drawable.materialsymbols_ic_flight_rounded_filled,
            context.getString(R.string.tile_airplane_mode),
        ))
        add(platformTile(
            AxPlatformClient.FEATURE_FLASHLIGHT,
            R.drawable.materialsymbols_ic_flashlight_on_rounded_filled,
            context.getString(R.string.tile_flashlight),
        ))
        add(platformTile(
            AxPlatformClient.FEATURE_DARK_MODE,
            R.drawable.materialsymbols_ic_dark_mode_rounded_filled,
            context.getString(R.string.tile_dark_mode),
        ))
        add(platformTile(
            AxPlatformClient.FEATURE_LOCATION,
            R.drawable.materialsymbols_ic_location_on_rounded_filled,
            context.getString(R.string.tile_location),
        ))
        add(platformTile(
            AxPlatformClient.FEATURE_BATTERY_SAVER,
            R.drawable.materialsymbols_ic_battery_saver_rounded_filled,
            context.getString(R.string.tile_battery_saver),
        ))
        add(platformTile(
            AxPlatformClient.FEATURE_HOTSPOT,
            R.drawable.materialsymbols_ic_wifi_tethering_rounded_filled,
            context.getString(R.string.tile_hotspot),
        ))
        add(platformTile(
            AxPlatformClient.FEATURE_NFC,
            R.drawable.materialsymbols_ic_nfc_rounded_filled,
            context.getString(R.string.tile_nfc),
        ))
        add(platformTile(
            AxPlatformClient.FEATURE_NIGHT_LIGHT,
            R.drawable.materialsymbols_ic_nights_stay_rounded_filled,
            context.getString(R.string.tile_night_light),
        ))
        add(platformTile(
            AxPlatformClient.FEATURE_AOD,
            R.drawable.materialsymbols_ic_aod_rounded_filled,
            context.getString(R.string.tile_aod),
        ))
        add(platformTile(
            AxPlatformClient.FEATURE_DATA_SAVER,
            R.drawable.materialsymbols_ic_data_saver_on_rounded_filled,
            context.getString(R.string.tile_data_saver),
        ))
        add(platformTile(
            AxPlatformClient.FEATURE_COLOR_INVERSION,
            R.drawable.materialsymbols_ic_invert_colors_rounded_filled,
            context.getString(R.string.tile_color_inversion),
        ))
        add(platformTile(
            AxPlatformClient.FEATURE_COLOR_CORRECTION,
            R.drawable.materialsymbols_ic_palette_rounded_filled,
            context.getString(R.string.tile_color_correction),
        ))
        add(platformTile(
            AxPlatformClient.FEATURE_REDUCE_BRIGHTNESS,
            R.drawable.materialsymbols_ic_brightness_low_rounded_filled,
            context.getString(R.string.tile_reduce_brightness),
        ))
        add(platformTile(
            AxPlatformClient.FEATURE_ONE_HANDED_MODE,
            R.drawable.materialsymbols_ic_phone_android_rounded_filled,
            context.getString(R.string.tile_one_handed),
        ))
        add(platformTile(
            AxPlatformClient.FEATURE_HEADS_UP,
            R.drawable.materialsymbols_ic_notifications_active_rounded_filled,
            context.getString(R.string.tile_heads_up),
        ))
        add(platformTile(
            AxPlatformClient.FEATURE_AUTO_SYNC,
            R.drawable.materialsymbols_ic_sync_rounded_filled,
            context.getString(R.string.tile_auto_sync),
        ))
        add(platformTile(
            AxPlatformClient.FEATURE_CAMERA_PRIVACY,
            R.drawable.materialsymbols_ic_photo_camera_rounded_filled,
            context.getString(R.string.tile_camera_privacy),
        ))
        add(platformTile(
            AxPlatformClient.FEATURE_MIC_PRIVACY,
            R.drawable.materialsymbols_ic_mic_rounded_filled,
            context.getString(R.string.tile_mic_privacy),
        ))
        add(platformTile(
            AxPlatformClient.FEATURE_WORK_PROFILE,
            R.drawable.materialsymbols_ic_work_rounded_filled,
            context.getString(R.string.tile_work_profile),
        ))
        add(platformTile(
            AxPlatformClient.FEATURE_USB_TETHER,
            R.drawable.materialsymbols_ic_usb_rounded_filled,
            context.getString(R.string.tile_usb_tether),
        ))
        add(platformTile(
            AxPlatformClient.FEATURE_DREAM,
            R.drawable.materialsymbols_ic_bedtime_rounded_filled,
            context.getString(R.string.tile_dream),
        ))
        add(platformTile(
            AxPlatformClient.FEATURE_READING_MODE,
            R.drawable.materialsymbols_ic_menu_book_rounded_filled,
            context.getString(R.string.tile_reading_mode),
        ))
        add(platformTile(
            AxPlatformClient.FEATURE_POWER_SHARE,
            R.drawable.materialsymbols_ic_battery_charging_full_rounded_filled,
            context.getString(R.string.tile_power_share),
        ))
        add(platformTile(
            AxPlatformClient.FEATURE_CAFFEINE,
            R.drawable.materialsymbols_ic_local_cafe_rounded_filled,
            context.getString(R.string.tile_caffeine),
        ))
        add(platformTile(
            AxPlatformClient.FEATURE_VPN,
            R.drawable.materialsymbols_ic_vpn_key_rounded_filled,
            context.getString(R.string.tile_vpn),
        ))
        add(platformTile(
            AxPlatformClient.FEATURE_CAST,
            R.drawable.materialsymbols_ic_cast_rounded_filled,
            context.getString(R.string.tile_cast),
        ))
        add(platformTile(
            AxPlatformClient.FEATURE_PROFILES,
            R.drawable.materialsymbols_ic_manage_accounts_rounded_filled,
            context.getString(R.string.tile_profiles),
        ))
        add(platformTile(
            AxPlatformClient.FEATURE_SMART_PIXELS,
            R.drawable.materialsymbols_ic_grid_on_rounded_filled,
            context.getString(R.string.tile_smart_pixels),
        ))
        add(platformTile(
            AxPlatformClient.FEATURE_SCREEN_RECORD,
            R.drawable.materialsymbols_ic_videocam_rounded_filled,
            context.getString(R.string.tile_screen_record),
        ))
        add(platformTile(
            AxPlatformClient.FEATURE_SCREENSHOT,
            R.drawable.materialsymbols_ic_screenshot_rounded_filled,
            context.getString(R.string.tile_screenshot),
        ))

        add(
            ToggleableTile(
                id = "notification",
                label = context.getString(R.string.tile_danmaku),
                icon = R.drawable.materialsymbols_ic_notifications_rounded_filled,
                state = mutableStateOf(appSettings.danmakuNotification),
                setter = {
                    appSettings.danmakuNotification = it
                    systemSettings.headsup = !it
                }
            )
        )

        add(
            ToggleableTile(
                id = "stay_awake",
                label = context.getString(R.string.tile_stay_awake),
                icon = R.drawable.materialsymbols_ic_bedtime_rounded_filled,
                state = mutableStateOf(systemSettings.stayAwake),
                setter = { systemSettings.stayAwake = it }
            )
        )

        add(
            ToggleableTile(
                id = "fps_info",
                label = context.getString(R.string.tile_fps_info),
                icon = R.drawable.materialsymbols_ic_bar_chart_rounded_filled,
                state = mutableStateOf(appSettings.showFps),
                setter = { appSettings.showFps = it }
            )
        )

        add(
            FixedActionTile(
                id = "boost_memory",
                label = context.getString(R.string.tile_boost_memory),
                icon = R.drawable.materialsymbols_ic_speed_rounded_filled,
                action = {
                    try {
                        ActivityManager.getService().releaseMemory(606, 60, false, false)
                    } catch (_: Exception) {}
                    Toast.makeText(context, context.getString(R.string.boost_memory), Toast.LENGTH_SHORT).show()
                }
            )
        )

        add(
            FixedActionTile(
                id = "settings",
                label = context.getString(R.string.tile_settings),
                icon = R.drawable.materialsymbols_ic_settings_rounded_filled,
                action = {
                    val intent = Intent(Settings.ACTION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
            )
        )

        if (SystemProperties.getBoolean("persist.sys.target_supports_touch_boost", false)) {
            val touchBoostState = mutableStateOf(
                SystemProperties.getInt("persist.sys.touchboost_enable", 0) == 1
            )
            add(
                ToggleableTile(
                    id = "touch_boost",
                    label = context.getString(R.string.tile_touch_boost),
                    icon = R.drawable.materialsymbols_ic_touch_app_rounded_filled,
                    state = touchBoostState,
                    setter = {
                        val newVal = if (it) 1 else 0
                        SystemProperties.set("persist.sys.touchboost_enable", "$newVal")
                        touchBoostState.value = it
                    }
                )
            )
        }
    }
}
