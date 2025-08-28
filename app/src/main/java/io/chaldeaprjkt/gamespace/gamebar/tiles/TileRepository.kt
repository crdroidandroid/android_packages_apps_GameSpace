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
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.provider.Settings
import android.telephony.TelephonyManager
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.*
import androidx.compose.ui.graphics.vector.ImageVector
import io.chaldeaprjkt.gamespace.data.AppSettings
import io.chaldeaprjkt.gamespace.data.SystemSettings
import io.chaldeaprjkt.gamespace.utils.ScreenUtils
import io.chaldeaprjkt.gamespace.R
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

interface TileAction {
    val id: String
    val label: String
    val icon: ImageVector
    val isEnabled: Boolean

    @Composable
    fun observeEnabled(): State<Boolean>

    fun toggle()
}

abstract class BaseTile(
    override val id: String,
    override val label: String,
    override val icon: ImageVector
) : TileAction

class ToggleableTile(
    id: String,
    label: String,
    icon: ImageVector,
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
    icon: ImageVector,
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

    val wifiState = mutableStateOf(wifiManager.isWifiEnabled)
    val btState = mutableStateOf(BluetoothAdapter.getDefaultAdapter()?.isEnabled == true)
    val dndState = mutableStateOf(
        notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE
    )
    val autoRotateState = mutableStateOf(
        Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 1
    )
    val airplaneModeState = mutableStateOf(
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
    )
    val mobileDataState = mutableStateOf(telephonyManager?.isDataEnabled ?: false)

    private val defaultTiles: List<TileAction> = buildList {
        add(
            ToggleableTile(
                id = "notification",
                label = "Danmaku",
                icon = Icons.Default.Notifications,
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
                icon = Icons.Default.Bedtime,
                state = mutableStateOf(appSettings.stayAwake),
                setter = {
                    appSettings.stayAwake = it
                    screenUtils.stayAwake = it
                }
            )
        )

        add(
            ToggleableTile(
                id = "wifi",
                label = "Wi-Fi",
                icon = Icons.Default.Wifi,
                state = wifiState,
                setter = { wifiManager.isWifiEnabled = it }
            )
        )

        add(
            ToggleableTile(
                id = "dnd",
                label = "DND",
                icon = Icons.Default.DoNotDisturb,
                state = dndState,
                setter = { enabled ->
                    notificationManager.setInterruptionFilter(
                        if (enabled) NotificationManager.INTERRUPTION_FILTER_NONE
                        else NotificationManager.INTERRUPTION_FILTER_ALL
                    )
                }
            )
        )

        add(
            ToggleableTile(
                id = "fps_info",
                label = "FPS Info",
                icon = Icons.Default.BarChart,
                state = mutableStateOf(appSettings.showFps),
                setter = { appSettings.showFps = it }
            )
        )

        add(
            ToggleableTile(
                id = "auto_rotate",
                label = "Auto Rotate",
                icon = Icons.Default.ScreenRotation,
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
                icon = Icons.Default.Speed,
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
                icon = Icons.Default.Settings,
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
                icon = Icons.Default.AirplanemodeActive,
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
                icon = Icons.Default.Bluetooth,
                state = btState,
                setter = {
                    val adapter = BluetoothAdapter.getDefaultAdapter()
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
                    icon = Icons.Default.DataUsage,
                    state = mobileDataState,
                    setter = {
                        try {
                            telephonyManager.setDataEnabled(it)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
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
        wifiState.value = wifiManager.isWifiEnabled.takeIf { it != wifiState.value } ?: wifiState.value
        btState.value = (BluetoothAdapter.getDefaultAdapter()?.isEnabled == true).takeIf { it != btState.value } ?: btState.value
        dndState.value = (notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE)
            .takeIf { it != dndState.value } ?: dndState.value
        autoRotateState.value = (Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 1)
            .takeIf { it != autoRotateState.value } ?: autoRotateState.value
        airplaneModeState.value = (Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1)
            .takeIf { it != airplaneModeState.value } ?: airplaneModeState.value
        mobileDataState.value = (telephonyManager?.isDataEnabled ?: false)
            .takeIf { it != mobileDataState.value } ?: mobileDataState.value
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
