/*
 * Copyright (C) 2025 AxionOS
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
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.SystemProperties
import android.provider.Settings
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.*
import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.data.AppSettings
import io.chaldeaprjkt.gamespace.data.SystemSettings
import io.chaldeaprjkt.gamespace.utils.ScreenUtils
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

interface TileAction {
    val id: String
    val label: String
    val icon: Int
    val isEnabled: Boolean

    @Composable
    fun observeEnabled(): State<Boolean>

    fun toggle()
}

abstract class BaseTile(
    override val id: String,
    override val label: String,
    override val icon: Int
) : TileAction

class ToggleableTile(
    id: String,
    label: String,
    icon: Int,
    private val state: MutableState<Boolean>,
    private val setter: (Boolean) -> Unit
) : BaseTile(id, label, icon) {

    override val isEnabled: Boolean get() = state.value

    @Composable
    override fun observeEnabled(): State<Boolean> = state

    override fun toggle() {
        val newValue = !state.value
        setter(newValue)
        state.value = newValue
    }
}

class FixedActionTile(
    id: String,
    label: String,
    icon: Int,
    private val action: () -> Unit
) : BaseTile(id, label, icon) {

    override val isEnabled: Boolean = false

    @Composable
    override fun observeEnabled(): State<Boolean> = rememberUpdatedState(false)

    override fun toggle() = action()
}

@Singleton
class TileRepository @Inject constructor(
    private val context: Context,
    private val appSettings: AppSettings,
    private val systemSettings: SystemSettings,
    private val screenUtils: ScreenUtils
) {
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    val wifiState = mutableStateOf(wifiManager.isWifiEnabled)
    val btState = mutableStateOf(bluetoothManager?.adapter?.isEnabled == true)
    val dndState = mutableStateOf(
        notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    )
    val autoRotateState = mutableStateOf(
        Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 1
    )
    val airplaneModeState = mutableStateOf(
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
    )
    val mobileDataState = mutableStateOf(telephonyManager?.isDataEnabled ?: false)
    val touchBoostState = mutableStateOf(
        SystemProperties.getInt("persist.sys.touchboost_enable", 0) == 1
    )
    
    private var lastRefreshTime = 0L
    private val refreshDebounceMs = 1000L

    private val defaultTiles: List<TileAction> = buildList {
        add(
            ToggleableTile(
                id = "notification",
                label = "Danmaku",
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
                label = "Stay Awake",
                icon = R.drawable.materialsymbols_ic_bedtime_rounded_filled,
                state = mutableStateOf(systemSettings.stayAwake),
                setter = { systemSettings.stayAwake = it }
            )
        )

        add(
            ToggleableTile(
                id = "wifi",
                label = "Wi-Fi",
                icon = R.drawable.materialsymbols_ic_wifi_rounded_filled,
                state = wifiState,
                setter = { wifiManager.isWifiEnabled = it }
            )
        )

        add(
            ToggleableTile(
                id = "dnd",
                label = "DND",
                icon = R.drawable.materialsymbols_ic_do_not_disturb_on_rounded_filled,
                state = dndState,
                setter = { enabled ->
                    notificationManager.setInterruptionFilter(
                        if (enabled) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                        else NotificationManager.INTERRUPTION_FILTER_ALL
                    )
                }
            )
        )

        add(
            ToggleableTile(
                id = "fps_info",
                label = "FPS Info",
                icon = R.drawable.materialsymbols_ic_bar_chart_rounded_filled,
                state = mutableStateOf(appSettings.showFps),
                setter = { appSettings.showFps = it }
            )
        )

        add(
            ToggleableTile(
                id = "auto_rotate",
                label = "Auto Rotate",
                icon = R.drawable.materialsymbols_ic_screen_rotation_up_rounded_filled,
                state = autoRotateState,
                setter = {
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.ACCELEROMETER_ROTATION,
                        if (it) 1 else 0
                    )
                }
            )
        )

        add(
            FixedActionTile(
                id = "boost_memory",
                label = "Boost Memory",
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
                label = "Settings",
                icon = R.drawable.materialsymbols_ic_settings_rounded_filled,
                action = {
                    val intent = Intent(Settings.ACTION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
            )
        )

        add(
            ToggleableTile(
                id = "airplane_mode",
                label = "Airplane Mode",
                icon = R.drawable.materialsymbols_ic_flight_rounded_filled,
                state = airplaneModeState,
                setter = {
                    Settings.Global.putInt(
                        context.contentResolver,
                        Settings.Global.AIRPLANE_MODE_ON,
                        if (it) 1 else 0
                    )
                    context.sendBroadcast(Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED).apply {
                        putExtra("state", it)
                    })
                }
            )
        )

        add(
            ToggleableTile(
                id = "bluetooth",
                label = "Bluetooth",
                icon = R.drawable.materialsymbols_ic_bluetooth_rounded_filled,
                state = btState,
                setter = {
                    val adapter = bluetoothManager?.adapter
                    if (adapter != null) {
                        if (it) adapter.enable() else adapter.disable()
                    }
                }
            )
        )

        if (telephonyManager != null && telephonyManager.phoneType != TelephonyManager.PHONE_TYPE_NONE) {
            add(
                ToggleableTile(
                    id = "mobile_data",
                    label = "Mobile Data",
                    icon = R.drawable.materialsymbols_ic_android_cell_4_bar_rounded_filled,
                    state = mobileDataState,
                    setter = {
                        try {
                            telephonyManager.setDataEnabledForReason(
                                TelephonyManager.DATA_ENABLED_REASON_USER,
                                it
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                )
            )
        }
        
        if (SystemProperties.getBoolean("persist.sys.target_supports_touch_boost", false)) {
            add(
                ToggleableTile(
                    id = "touch_boost",
                    label = "Touch Boost",
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

    private val tileOrderKey = "tile_order"

    private val _tileOrder = mutableStateListOf<String>().apply {
        addAll(loadTileOrder())
    }

    val allAvailableTiles: List<TileAction>
        get() = defaultTiles

    private val _tiles = mutableStateListOf<TileAction>()
    val tiles: SnapshotStateList<TileAction> get() = _tiles

    val isBrightnessVisible: MutableState<Boolean> = mutableStateOf(appSettings.brightnessEnabled)
    
    val isFpsGraphVisible: MutableState<Boolean> = mutableStateOf(appSettings.fpsGraphEnabled)

    init {
        _tiles.addAll(
            _tileOrder.mapNotNull { id ->
                defaultTiles.find { it.id == id }
            }
        )
    }

    fun refreshStates() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastRefreshTime < refreshDebounceMs) return
        lastRefreshTime = currentTime
        
        val newWifiState = wifiManager.isWifiEnabled
        if (newWifiState != wifiState.value) {
            wifiState.value = newWifiState
        }
        
        val newBtState = bluetoothManager?.adapter?.isEnabled == true
        if (newBtState != btState.value) {
            btState.value = newBtState
        }
        
        val newDndState = notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        if (newDndState != dndState.value) {
            dndState.value = newDndState
        }
        
        val newAutoRotateState = Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 1
        if (newAutoRotateState != autoRotateState.value) {
            autoRotateState.value = newAutoRotateState
        }
        
        val newAirplaneModeState = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        if (newAirplaneModeState != airplaneModeState.value) {
            airplaneModeState.value = newAirplaneModeState
        }
        
        val newMobileDataState = telephonyManager?.isDataEnabled ?: false
        if (newMobileDataState != mobileDataState.value) {
            mobileDataState.value = newMobileDataState
        }
        
        if (SystemProperties.getBoolean("persist.sys.target_supports_touch_boost", false)) {
            val newTouchBoostState = SystemProperties.getInt("persist.sys.touchboost_enable", 0) == 1
            if (newTouchBoostState != touchBoostState.value) {
                touchBoostState.value = newTouchBoostState
            }
        }
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
}
