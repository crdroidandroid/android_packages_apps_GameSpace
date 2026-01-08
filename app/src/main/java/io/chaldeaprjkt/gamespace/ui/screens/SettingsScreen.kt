@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package io.chaldeaprjkt.gamespace.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AppRegistration
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.ui.components.GameCard
import io.chaldeaprjkt.gamespace.ui.components.SettingsDropdown
import io.chaldeaprjkt.gamespace.ui.components.SettingsSection
import io.chaldeaprjkt.gamespace.ui.components.SettingsSlider
import io.chaldeaprjkt.gamespace.ui.components.SettingsSwitch
import io.chaldeaprjkt.gamespace.ui.viewmodel.RegisteredGame
import io.chaldeaprjkt.gamespace.ui.viewmodel.SettingsViewModel

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?
)

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onAddGameClick: () -> Unit,
    onGameClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val listState = rememberLazyListState()
    val fabVisible by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 || !listState.canScrollForward
        }
    }
    val focusRequester = remember { FocusRequester() }

    var showQuickStartDialog by remember { mutableStateOf(false) }
    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }

    BackHandler(fabMenuExpanded) { fabMenuExpanded = false }

    val fabItems = listOf(
        Triple(
            Icons.Filled.AppRegistration,
            "Select apps to launch quickly"
        ) {
            showQuickStartDialog = true
            fabMenuExpanded = false
        },
        Triple(
            Icons.Filled.SportsEsports,
            "Add game"
        ) {
            onAddGameClick()
            fabMenuExpanded = false
        }
    )

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
        "0" to "Silent",
        "1" to "Vibrate",
        "2" to "Normal",
        "3" to "Do not change"
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        },
        floatingActionButton = {
            FloatingActionButtonMenu(
                expanded = fabMenuExpanded,
                button = {
                    ToggleFloatingActionButton(
                        modifier =
                            Modifier.semantics {
                                    traversalIndex = -1f
                                    stateDescription =
                                        if (fabMenuExpanded) "Expanded" else "Collapsed"
                                    contentDescription = "Toggle menu"
                                }
                                .animateFloatingActionButton(
                                    visible = fabVisible || fabMenuExpanded,
                                    alignment = Alignment.BottomEnd,
                                )
                                .focusRequester(focusRequester),
                        checked = fabMenuExpanded,
                        onCheckedChange = { fabMenuExpanded = !fabMenuExpanded },
                    ) {
                        val imageVector by remember {
                            derivedStateOf {
                                if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.Filled.Add
                            }
                        }
                        Icon(
                            painter = rememberVectorPainter(imageVector),
                            contentDescription = null,
                            modifier = Modifier.animateIcon({ checkedProgress }),
                        )
                    }
                },
            ) {
                fabItems.forEachIndexed { i, item ->
                    FloatingActionButtonMenuItem(
                        modifier =
                            Modifier.semantics {
                                    isTraversalGroup = true
                                    if (i == fabItems.size - 1) {
                                        customActions =
                                            listOf(
                                                CustomAccessibilityAction(
                                                    label = "Close menu",
                                                    action = {
                                                        fabMenuExpanded = false
                                                        true
                                                    },
                                                )
                                            )
                                    }
                                }
                                .then(
                                    if (i == 0) {
                                        Modifier.onKeyEvent {
                                            if (
                                                it.type == KeyEventType.KeyDown &&
                                                    (it.key == Key.DirectionUp ||
                                                        (it.isShiftPressed && it.key == Key.Tab))
                                            ) {
                                                focusRequester.requestFocus()
                                                return@onKeyEvent true
                                            }
                                            return@onKeyEvent false
                                        }
                                    } else {
                                        Modifier
                                    }
                                ),
                        onClick = item.third,
                        icon = { Icon(item.first, contentDescription = null) },
                        text = { Text(text = item.second) },
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item {
                GameIllustration()
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsSection(title = "Notifications") {
                    SettingsSwitch(
                        title = stringResource(R.string.call_overlay_enabled_title),
                        summary = stringResource(R.string.call_overlay_enabled_summary),
                        checked = viewModel.callOverlayEnabled,
                        onCheckedChange = { viewModel.updateCallOverlay(it) },
                        icon = Icons.Rounded.Call
                    )

                    SettingsSwitch(
                        title = stringResource(R.string.danmaku_notification_mode_title),
                        summary = stringResource(R.string.danmaku_notification_mode_summary),
                        checked = viewModel.danmakuNotification,
                        onCheckedChange = { viewModel.updateDanmakuNotification(it) },
                        icon = Icons.Rounded.ChatBubble
                    )

                    SettingsDropdown(
                        title = stringResource(R.string.in_game_calls_title),
                        selectedValue = viewModel.callsMode.toString(),
                        options = callsModeOptions,
                        onValueChange = { viewModel.updateCallsMode(it.toIntOrNull() ?: 0) },
                        icon = Icons.Rounded.PhoneInTalk
                    )

                    SettingsDropdown(
                        title = stringResource(R.string.ringer_mode_title),
                        selectedValue = viewModel.ringerMode.toString(),
                        options = ringerModeOptions,
                        onValueChange = { viewModel.updateRingerMode(it.toIntOrNull() ?: 3) },
                        icon = Icons.AutoMirrored.Rounded.VolumeUp
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsSection(title = "Display & Gestures") {
                    SettingsSwitch(
                        title = stringResource(R.string.auto_brightness_disabled_title),
                        summary = stringResource(R.string.auto_brightness_disabled_summary),
                        checked = viewModel.noAutoBrightness,
                        onCheckedChange = { viewModel.updateNoAutoBrightness(it) },
                        icon = Icons.Rounded.Brightness6
                    )

                    SettingsSwitch(
                        title = stringResource(R.string.three_screenshot_disabled_title),
                        summary = stringResource(R.string.three_screenshot_disabled_summary),
                        checked = viewModel.noThreeScreenshot,
                        onCheckedChange = { viewModel.updateNoThreeScreenshot(it) },
                        icon = Icons.Rounded.Gesture
                    )

                    SettingsSwitch(
                        title = stringResource(R.string.stay_awake_title),
                        summary = stringResource(R.string.stay_awake_summary),
                        checked = viewModel.stayAwake,
                        onCheckedChange = { viewModel.updateStayAwake(it) },
                        icon = Icons.Rounded.Visibility
                    )

                    SettingsSlider(
                        title = stringResource(R.string.gamespace_menu_opacity_title),
                        value = viewModel.menuOpacity,
                        onValueChange = { viewModel.updateMenuOpacity(it) },
                        valueRange = 0f..100f,
                        valueLabel = "${viewModel.menuOpacity.toInt()}%",
                        icon = Icons.Rounded.Opacity
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                GameLibrarySection(
                    registeredGames = viewModel.registeredGames,
                    onGameClick = onGameClick
                )
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
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
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

@Composable
private fun GameIllustration(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "illustration")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(horizontal = 16.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val controllerWidth = size.width * 0.5f
            val controllerHeight = size.height * 0.4f

            rotate(rotation * 0.02f, pivot = Offset(centerX, centerY)) {
                drawCircle(
                    color = primaryColor.copy(alpha = 0.15f),
                    radius = size.minDimension * 0.4f * pulse,
                    center = Offset(centerX, centerY)
                )
            }

            for (i in 0..5) {
                val angle = rotation + i * 60
                val orbitRadius = size.minDimension * 0.35f
                val particleX = centerX + kotlin.math.cos(Math.toRadians(angle.toDouble())).toFloat() * orbitRadius
                val particleY = centerY + kotlin.math.sin(Math.toRadians(angle.toDouble())).toFloat() * orbitRadius
                drawCircle(
                    color = tertiaryColor.copy(alpha = 0.4f),
                    radius = 4.dp.toPx(),
                    center = Offset(particleX, particleY)
                )
            }

            val controllerLeft = centerX - controllerWidth / 2
            val controllerTop = centerY - controllerHeight / 2

            val controllerPath = Path().apply {
                val cornerRadius = controllerHeight * 0.4f
                moveTo(controllerLeft + cornerRadius, controllerTop)
                lineTo(controllerLeft + controllerWidth - cornerRadius, controllerTop)
                quadraticTo(
                    controllerLeft + controllerWidth, controllerTop,
                    controllerLeft + controllerWidth, controllerTop + controllerHeight * 0.3f
                )
                lineTo(controllerLeft + controllerWidth, controllerTop + controllerHeight * 0.7f)
                quadraticTo(
                    controllerLeft + controllerWidth, controllerTop + controllerHeight,
                    controllerLeft + controllerWidth - cornerRadius, controllerTop + controllerHeight
                )
                lineTo(controllerLeft + cornerRadius, controllerTop + controllerHeight)
                quadraticTo(
                    controllerLeft, controllerTop + controllerHeight,
                    controllerLeft, controllerTop + controllerHeight * 0.7f
                )
                lineTo(controllerLeft, controllerTop + controllerHeight * 0.3f)
                quadraticTo(
                    controllerLeft, controllerTop,
                    controllerLeft + cornerRadius, controllerTop
                )
                close()
            }

            drawPath(
                path = controllerPath,
                color = primaryColor.copy(alpha = 0.3f)
            )
            drawPath(
                path = controllerPath,
                color = primaryColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            val dpadCenterX = controllerLeft + controllerWidth * 0.25f
            val dpadCenterY = centerY
            val dpadSize = controllerHeight * 0.25f

            drawRect(
                color = secondaryColor,
                topLeft = Offset(dpadCenterX - dpadSize * 0.3f, dpadCenterY - dpadSize),
                size = Size(dpadSize * 0.6f, dpadSize * 2)
            )
            drawRect(
                color = secondaryColor,
                topLeft = Offset(dpadCenterX - dpadSize, dpadCenterY - dpadSize * 0.3f),
                size = Size(dpadSize * 2, dpadSize * 0.6f)
            )

            val buttonCenterX = controllerLeft + controllerWidth * 0.75f
            val buttonCenterY = centerY
            val buttonRadius = controllerHeight * 0.12f
            val buttonSpacing = buttonRadius * 2.2f

            drawCircle(
                color = tertiaryColor,
                radius = buttonRadius,
                center = Offset(buttonCenterX, buttonCenterY - buttonSpacing * 0.5f)
            )
            drawCircle(
                color = primaryColor,
                radius = buttonRadius,
                center = Offset(buttonCenterX + buttonSpacing * 0.5f, buttonCenterY)
            )
            drawCircle(
                color = secondaryColor,
                radius = buttonRadius,
                center = Offset(buttonCenterX, buttonCenterY + buttonSpacing * 0.5f)
            )
            drawCircle(
                color = tertiaryColor.copy(alpha = 0.7f),
                radius = buttonRadius,
                center = Offset(buttonCenterX - buttonSpacing * 0.5f, buttonCenterY)
            )

            drawCircle(
                color = onPrimaryContainer.copy(alpha = 0.3f),
                radius = controllerHeight * 0.08f,
                center = Offset(centerX - controllerWidth * 0.1f, centerY)
            )
            drawCircle(
                color = onPrimaryContainer.copy(alpha = 0.3f),
                radius = controllerHeight * 0.08f,
                center = Offset(centerX + controllerWidth * 0.1f, centerY)
            )
        }

        Text(
            text = "Game Space",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        )
    }
}

@Composable
private fun GameLibrarySection(
    registeredGames: List<RegisteredGame>,
    onGameClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.game_list_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (registeredGames.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No games added yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                registeredGames.forEach { game ->
                    GameCard(
                        title = game.label,
                        subtitle = getGameModeLabel(game.mode),
                        icon = rememberDrawablePainter(game.icon),
                        onClick = { onGameClick(game.packageName) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberDrawablePainter(drawable: Drawable?): BitmapPainter {
    return if (drawable != null) {
        BitmapPainter(drawable.toBitmap(96, 96).asImageBitmap())
    } else {
        BitmapPainter(createBitmap(1, 1).asImageBitmap())
    }
}

private fun getGameModeLabel(mode: Int): String {
    return when (mode) {
        1 -> "Standard"
        2 -> "Performance"
        3 -> "Battery"
        else -> "Standard"
    }
}
