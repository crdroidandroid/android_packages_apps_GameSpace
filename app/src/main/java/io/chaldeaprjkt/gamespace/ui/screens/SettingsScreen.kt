@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package io.chaldeaprjkt.gamespace.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.ui.components.SettingsDropdown
import io.chaldeaprjkt.gamespace.ui.components.SettingsSection
import io.chaldeaprjkt.gamespace.ui.components.SettingsSlider
import io.chaldeaprjkt.gamespace.ui.components.SettingsSwitch
import io.chaldeaprjkt.gamespace.ui.viewmodel.SettingsViewModel

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?
)

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val listState = rememberLazyListState()

    var showQuickStartDialog by remember { mutableStateOf(false) }

    var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    LaunchedEffect(Unit) {
        installedApps = loadInstalledApps(context)
    }

    val callsModeOptions = listOf(
        "0" to stringResource(R.string.in_game_calls_no_action),
        "1" to stringResource(R.string.in_game_calls_auto_answer),
        "2" to stringResource(R.string.in_game_calls_auto_reject)
    )

    val ringerModeOptions = listOf(
        "0" to stringResource(R.string.ringer_mode_silent),
        "1" to stringResource(R.string.ringer_mode_vibrate),
        "2" to stringResource(R.string.ringer_mode_normal),
        "3" to stringResource(R.string.ringer_mode_no_change)
    )

    if (showQuickStartDialog) {
        QuickStartAppsDialog(
            apps = installedApps,
            selectedApps = viewModel.quickStartApps.split(",").filter { it.isNotBlank() }.toSet(),
            onDismiss = { showQuickStartDialog = false },
            onConfirm = { selected ->
                viewModel.updateQuickStartApps(selected.joinToString(","))
                showQuickStartDialog = false
            }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                SettingsSection(title = stringResource(R.string.notifications)) {
                    SettingsSwitch(
                        title = stringResource(R.string.auto_dnd_title),
                        summary = stringResource(R.string.auto_dnd_summary),
                        checked = viewModel.autoDnd,
                        onCheckedChange = { viewModel.updateAutoDnd(it) },
                        icon = painterResource(R.drawable.materialsymbols_ic_do_not_disturb_on_rounded_filled)
                    )

                    SettingsSwitch(
                        title = stringResource(R.string.call_overlay_enabled_title),
                        summary = stringResource(R.string.call_overlay_enabled_summary),
                        checked = viewModel.callOverlayEnabled,
                        onCheckedChange = { viewModel.updateCallOverlay(it) },
                        icon = painterResource(R.drawable.materialsymbols_ic_call_rounded_filled)
                    )

                    SettingsSwitch(
                        title = stringResource(R.string.danmaku_notification_mode_title),
                        summary = stringResource(R.string.danmaku_notification_mode_summary),
                        checked = viewModel.danmakuNotification,
                        onCheckedChange = { viewModel.updateDanmakuNotification(it) },
                        icon = Icons.Filled.ChatBubble
                    )

                    SettingsDropdown(
                        title = stringResource(R.string.in_game_calls_title),
                        selectedValue = viewModel.callsMode.toString(),
                        options = callsModeOptions,
                        onValueChange = { viewModel.updateCallsMode(it.toIntOrNull() ?: 0) },
                        icon = painterResource(R.drawable.materialsymbols_ic_phone_in_talk_rounded_filled)
                    )

                    SettingsDropdown(
                        title = stringResource(R.string.ringer_mode_title),
                        selectedValue = viewModel.ringerMode.toString(),
                        options = ringerModeOptions,
                        onValueChange = { viewModel.updateRingerMode(it.toIntOrNull() ?: 3) },
                        icon = painterResource(R.drawable.materialsymbols_ic_volume_up_rounded_filled)
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsSection(title = stringResource(R.string.display_gestures)) {
                    SettingsSwitch(
                        title = stringResource(R.string.auto_brightness_disabled_title),
                        summary = stringResource(R.string.auto_brightness_disabled_summary),
                        checked = viewModel.noAutoBrightness,
                        onCheckedChange = { viewModel.updateNoAutoBrightness(it) },
                        icon = painterResource(R.drawable.materialsymbols_ic_brightness_6_rounded_filled)
                    )

                    SettingsSwitch(
                        title = stringResource(R.string.three_screenshot_disabled_title),
                        summary = stringResource(R.string.three_screenshot_disabled_summary),
                        checked = viewModel.noThreeScreenshot,
                        onCheckedChange = { viewModel.updateNoThreeScreenshot(it) },
                        icon = painterResource(R.drawable.materialsymbols_ic_gesture_rounded_filled)
                    )

                    SettingsSwitch(
                        title = stringResource(R.string.stay_awake_title),
                        summary = stringResource(R.string.stay_awake_summary),
                        checked = viewModel.stayAwake,
                        onCheckedChange = { viewModel.updateStayAwake(it) },
                        icon = painterResource(R.drawable.materialsymbols_ic_visibility_rounded_filled)
                    )

                    SettingsSlider(
                        title = stringResource(R.string.icon_idle_alpha_title),
                        value = viewModel.iconIdleAlpha,
                        onValueChange = { viewModel.updateIconIdleAlpha(it) },
                        valueRange = 5f..100f,
                        valueLabel = "${viewModel.iconIdleAlpha.toInt()}%",
                        icon = painterResource(R.drawable.materialsymbols_ic_opacity_rounded_filled)
                    )

                    SettingsSlider(
                        title = stringResource(R.string.gamespace_menu_opacity_title),
                        value = viewModel.menuOpacity,
                        onValueChange = { viewModel.updateMenuOpacity(it) },
                        valueRange = 0f..100f,
                        valueLabel = "${viewModel.menuOpacity.toInt()}%",
                        icon = painterResource(R.drawable.materialsymbols_ic_opacity_rounded_filled)
                    )
                }
            }

            item {
                if (viewModel.isBypassSupported) {
                    Spacer(modifier = Modifier.height(16.dp))
                    SettingsSection(title = "Power") {
                        SettingsSwitch(
                            title = stringResource(R.string.bypass_charge_enabled_title),
                            summary = stringResource(R.string.bypass_charge_enabled_summary),
                            checked = viewModel.bypassChargeEnabled,
                            onCheckedChange = { viewModel.updateBypassChargeEnabled(it) },
                            icon = Icons.Rounded.BatteryChargingFull
                        )
                    }
                }
            }

        }
    }
}

@Composable
private fun QuickStartAppsDialog(
    apps: List<AppInfo>,
    selectedApps: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    val toggleButtonShapes = ToggleButtonShapes(
        shape = ToggleButtonDefaults.squareShape,
        pressedShape = ToggleButtonDefaults.pressedShape,
        checkedShape = ToggleButtonDefaults.roundShape,
    )

    val selected = remember { mutableStateListOf<String>().apply { addAll(selectedApps) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.quick_start_apps_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.height(400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(apps) { app ->
                    val isSelected = selected.contains(app.packageName)
                    ToggleButton(
                        checked = isSelected,
                        onCheckedChange = { checked ->
                            if (checked) {
                                selected.add(app.packageName)
                            } else {
                                selected.remove(app.packageName)
                            }
                        },
                        shapes = toggleButtonShapes,
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (app.icon != null) {
                                Icon(
                                    painter = BitmapPainter(
                                        app.icon.toBitmap(48, 48).asImageBitmap()
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = Color.Unspecified
                                )
                            }
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected.toSet()) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun loadInstalledApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN, null).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    
    return pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        .mapNotNull { resolveInfo ->
            val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
            val packageName = activityInfo.packageName
            try {
                val appInfo = pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
                AppInfo(
                    packageName = packageName,
                    label = pm.getApplicationLabel(appInfo).toString(),
                    icon = pm.getApplicationIcon(appInfo)
                )
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
        .sortedBy { it.label.lowercase() }
}
