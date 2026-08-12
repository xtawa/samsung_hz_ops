@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.xtawa.samsunghzops.ui

import android.content.Context
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xtawa.samsunghzops.core.model.AppProfile
import com.xtawa.samsunghzops.core.model.CapabilityState
import com.xtawa.samsunghzops.core.model.DisplayModeInfo
import com.xtawa.samsunghzops.core.model.RefreshMode
import com.xtawa.samsunghzops.core.model.RefreshRange
import com.xtawa.samsunghzops.ui.theme.SamsungHzOpsTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HzOpsApp(viewModel: MainViewModel) {
    SamsungHzOpsTheme {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val snackbarHostState = remember { SnackbarHostState() }
        val context = LocalContext.current
        LaunchedEffect(state.snackbar) {
            state.snackbar?.let {
                snackbarHostState.showSnackbar(it)
                viewModel.clearSnackbar()
            }
        }
        if (state.detailTitle != null && state.detailBody != null) {
            AlertDialog(
                onDismissRequest = viewModel::closeDetails,
                confirmButton = { TextButton(onClick = viewModel::closeDetails) { Text("知道了") } },
                title = { Text(state.detailTitle!!) },
                text = { Text(state.detailBody!!) },
            )
        }
        if (state.showEmergencyResetConfirm) {
            EmergencyResetDialog(
                snapshotCount = state.managedSnapshotCount,
                busy = state.busy,
                onDismiss = viewModel::dismissEmergencyReset,
                onConfirm = viewModel::confirmEmergencyReset,
            )
        }
        Scaffold(
            topBar = {
                var expanded by remember { mutableStateOf(false) }
                TopAppBar(
                    title = { Text("Samsung Hz Ops", fontWeight = FontWeight.SemiBold) },
                    actions = {
                        IconButton(onClick = { expanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多选项")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(
                                text = { Text("重新读取设备状态") },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                onClick = {
                                    expanded = false
                                    viewModel.refreshCapabilities()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("紧急一键还原") },
                                leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null) },
                                enabled = !state.busy,
                                onClick = {
                                    expanded = false
                                    viewModel.requestEmergencyReset()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("查看诊断") },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                onClick = {
                                    expanded = false
                                    viewModel.showDetails(
                                        "诊断日志",
                                        state.journal.take(10).joinToString("\n") { record ->
                                            "${if (record.committed) "✓" else "!"} ${record.operation}${record.error?.let { ": $it" } ?: ""}"
                                        }.ifBlank { "还没有事务记录" },
                                    )
                                },
                            )
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    AppDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = state.destination == destination,
                            onClick = { viewModel.selectDestination(destination) },
                            icon = { DestinationIcon(destination) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            when (state.destination) {
                AppDestination.CONTROL -> ControlPage(state, viewModel, padding)
                AppDestination.RULES -> RulesPage(state, viewModel, padding)
                AppDestination.TOOLS -> ToolsPage(state, viewModel, padding)
                AppDestination.MORE -> MorePage(state, viewModel, padding, context)
            }
        }
    }
}

@Composable
private fun DestinationIcon(destination: AppDestination) {
    val icon = when (destination) {
        AppDestination.CONTROL -> Icons.Default.Tune
        AppDestination.RULES -> Icons.Default.Security
        AppDestination.TOOLS -> Icons.Default.Build
        AppDestination.MORE -> Icons.Default.MoreHoriz
    }
    Icon(icon, contentDescription = destination.label)
}

@Composable
private fun ControlPage(
    state: MainUiState,
    viewModel: MainViewModel,
    padding: PaddingValues,
) {
    val modes = state.snapshot.supportedModes
    val hzValues = modes.map { it.refreshRateHz }.distinct().sorted()
    var minSlider by remember(hzValues, state.snapshot.range) {
        mutableFloatStateOf(state.snapshot.range?.minHz ?: hzValues.firstOrNull() ?: 60f)
    }
    var maxSlider by remember(hzValues, state.snapshot.range) {
        mutableFloatStateOf(state.snapshot.range?.maxHz ?: hzValues.lastOrNull() ?: 120f)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            CurrentRateCard(state)
        }
        item {
            SectionTitle("核心刷新率控制", "模式变更会同时写入系统最低/最高刷新率")
            Spacer(Modifier.height(8.dp))
            ModeSelector(state.snapshot.activeMode, viewModel::applyMode)
        }
        item {
            ElevatedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("自适应范围", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "仅使用设备真实报告的 Display.Mode；不会猜测不存在的档位。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    RangeSliderRow(
                        label = "最低",
                        value = minSlider,
                        values = hzValues,
                        enabled = hzValues.isNotEmpty(),
                        onValueChange = { minSlider = it.coerceAtMost(maxSlider) },
                        onValueChangeFinished = {
                            viewModel.applyAdaptiveRange(minSlider, maxSlider)
                        },
                    )
                    RangeSliderRow(
                        label = "最高",
                        value = maxSlider,
                        values = hzValues,
                        enabled = hzValues.isNotEmpty(),
                        onValueChange = { maxSlider = it.coerceAtLeast(minSlider) },
                        onValueChangeFinished = {
                            viewModel.applyAdaptiveRange(minSlider, maxSlider)
                        },
                    )
                }
            }
        }
        item {
            FeatureSwitchRow(
                icon = Icons.Default.BatteryStd,
                title = "省电模式保持高刷",
                summary = "PSM 开启时重应用高刷新率；三星 OEM 键值需在目标机 read-back 校准",
                checked = state.psm.keepHighRefresh,
                onCheckedChange = viewModel::setKeepHighRefresh,
                onInfo = { viewModel.showDetails("省电模式保持高刷", "监听 POWER_SAVE_MODE_CHANGED，并通过三星 PSM Global 键在权限可用时重应用策略。映射为反向工程推断，目标固件需验证。") },
            )
        }
        item {
            PermissionSummary(state, onRefresh = viewModel::refreshCapabilities)
        }
        item {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
                onClick = viewModel::requestEmergencyReset,
            ) {
                Icon(Icons.Default.Restore, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("紧急一键还原所有更改")
            }
        }
    }
}

@Composable
private fun EmergencyResetDialog(
    snapshotCount: Int,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Restore, contentDescription = null) },
        title = { Text("紧急一键还原所有更改") },
        text = {
            Text(
                "将停止本 App 的自动化和监视器，并把已记录的 $snapshotCount 项系统设置恢复到接管前状态。不会关闭系统省电模式，也不会删除你的应用规则。",
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !busy) { Text("立即还原") }
        },
    )
}

@Composable
private fun CurrentRateCard(state: MainUiState) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("当前刷新率", style = MaterialTheme.typography.labelLarge)
                    Text(
                        state.snapshot.activeRefreshRateHz?.let { "%.0f Hz".format(it) } ?: "读取中…",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text(state.snapshot.activeMode.label) },
                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                )
            }
            Text(
                state.decision.reason,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            state.snapshot.lastError?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ModeSelector(selected: RefreshMode, onSelected: (RefreshMode) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RefreshMode.entries.forEach { mode ->
            FilterChip(
                modifier = Modifier.weight(1f),
                selected = selected == mode,
                onClick = { onSelected(mode) },
                label = {
                    Text(
                        when (mode) {
                            RefreshMode.STANDARD -> "标准"
                            RefreshMode.ADAPTIVE -> "自适应"
                            RefreshMode.MAXIMUM -> "最高"
                        },
                    )
                },
                leadingIcon = if (selected == mode) {
                    { Icon(Icons.Default.CheckCircle, contentDescription = null) }
                } else null,
            )
        }
    }
}

@Composable
private fun RangeSliderRow(
    label: String,
    value: Float,
    values: List<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    val min = values.firstOrNull() ?: 60f
    val max = values.lastOrNull() ?: 120f
    val steps = (values.size - 2).coerceAtLeast(0)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(36.dp), style = MaterialTheme.typography.labelMedium)
        Slider(
            modifier = Modifier.weight(1f),
            value = value.coerceIn(min, max),
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = min..max,
            steps = steps,
            enabled = enabled && min < max,
        )
        Text("%.0f".format(value), modifier = Modifier.width(52.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PermissionSummary(state: MainUiState, onRefresh: () -> Unit) {
    val granted = state.capabilities.count { it.state == CapabilityState.GRANTED }
    ElevatedCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("权限与能力", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("$granted/${state.capabilities.size}", style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "刷新") }
            }
            state.capabilities.filter { it.state != CapabilityState.GRANTED }.take(2).forEach {
                Text("• ${it.explanation}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RulesPage(state: MainUiState, viewModel: MainViewModel, padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionTitle("规则", "所有触发器最终汇入同一个刷新策略引擎") }
        item {
            FeatureSwitchRow(
                icon = Icons.Default.Security,
                title = "自动化策略",
                summary = "前台应用、锁屏、相机、投屏、折叠状态统一驱动刷新率",
                checked = state.automationEnabled,
                onCheckedChange = viewModel::setAutomationEnabled,
                onInfo = { viewModel.showDetails("自动化策略", "启动前台服务监听策略流；服务只调用 RefreshRateRepository，不直接写 Settings。") },
            )
        }
        item {
            ElevatedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Apps, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Per-App 刷新率", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = viewModel::addForegroundProfile) { Icon(Icons.Default.Add, contentDescription = "添加当前应用") }
                    }
                    Text("进入目标应用后点 +，保存该应用的 Adaptive/Standard/Maximum 档位与范围。", style = MaterialTheme.typography.bodySmall)
                    if (state.profiles.isEmpty()) {
                        Text("还没有应用规则", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        state.profiles.forEach { profile -> ProfileRow(profile, viewModel::deleteProfile) }
                    }
                }
            }
        }
        item {
            RuleInfoCard("息屏与锁屏", "屏幕熄灭或 Keyguard 时回落到标准档，解锁后恢复策略", Icons.Default.Lock) {
                viewModel.showDetails("息屏与锁屏", "默认开启 pauseWhenScreenOff/pauseOnKeyguard，避免息屏维持高刷新率。")
            }
        }
        item {
            RuleInfoCard("相机与投屏", "相机、Cast 活跃时可强制最高刷新率", Icons.Default.PlayArrow) {
                viewModel.showDetails("相机与投屏", "相机与投屏信号会进入同一策略决策，不在事件监听器内直接改系统设置。")
            }
        }
        item {
            RuleInfoCard("折叠屏配置", "主屏与外屏配置隔离，设备未暴露外屏时显示不可用", Icons.Default.MoreHoriz) {
                viewModel.showDetails("折叠屏配置", "外屏键值与折叠状态需要目标机校准；当前 UI 保留入口并显示能力状态。")
            }
        }
    }
}

@Composable
private fun ProfileRow(profile: AppProfile, onDelete: (AppProfile) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(profile.appLabel, style = MaterialTheme.typography.bodyLarge)
            Text("${profile.packageName} · ${profile.preferredMode.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { onDelete(profile) }) { Icon(Icons.Default.Delete, contentDescription = "删除") }
    }
}

@Composable
private fun ToolsPage(state: MainUiState, viewModel: MainViewModel, padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionTitle("工具", "可选工具均显示所需权限与设备支持状态") }
        item {
            FeatureSwitchRow(Icons.Default.PlayArrow, "刷新率监视器", "常驻通知显示实时 Display.Mode 刷新率", false, { _ -> }, onInfo = viewModel::startMonitor)
            Row(Modifier.fillMaxWidth().padding(start = 56.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = viewModel::startMonitor) { Icon(Icons.Default.PlayArrow, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("启动") }
                TextButton(onClick = viewModel::stopMonitor) { Icon(Icons.Default.Stop, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("停止") }
            }
        }
        item { FeatureSwitchRow(Icons.Default.Speed, "状态栏网络速度", "通过 OEM network_speed 设置显示实时网速", state.networkSpeedEnabled, viewModel::setNetworkSpeed, onInfo = { viewModel.showDetails("网络速度", "需要 Settings.System 写入权限；部分三星版本还需要系统 UI 重新加载。") }) }
        item { FeatureSwitchRow(Icons.Default.BatteryStd, "快速 Doze", "将 device_idle_constants 调整为更快进入空闲", state.quickDozeEnabled, viewModel::setQuickDoze, onInfo = { viewModel.showDetails("快速 Doze", "会写入 Global device_idle_constants；关闭时删除自定义值，系统可能采用默认参数。") }) }
        item { FeatureSwitchRow(Icons.Default.DarkMode, "系统动画", "关闭三组动画倍率以减少视觉延迟", state.animationsEnabled, viewModel::setAnimations, onInfo = { viewModel.showDetails("系统动画", "修改 window/transition/animator_duration_scale 三个 Global 键。") }) }
        item { FeatureSwitchRow(Icons.Default.Settings, "强制应用可调整大小", "允许未声明 resizeable 的应用进入多窗口", state.forceResizableEnabled, viewModel::setForceResizable, onInfo = { viewModel.showDetails("强制可调整大小", "使用 Global force_resizable_activities；需 Secure/Global 特权写入。") }) }
        item { FeatureSwitchRow(Icons.Default.Sensors, "传感器隐私开关", "通过 sensors_off 全局开关快速禁用传感器", state.sensorsOffEnabled, viewModel::setSensorsOff, onInfo = { viewModel.showDetails("传感器隐私", "需要特权写入；不同 One UI 版本可能由系统 UI 覆盖状态。") }) }
        item { FeatureSwitchRow(Icons.Default.Sync, "自动同步", "切换 ContentResolver 主同步开关", state.syncEnabled, viewModel::setSync, onInfo = { viewModel.showDetails("自动同步", "使用系统 ContentResolver.setMasterSyncAutomatically。") }) }
        item { ToolInfoRow("AOD / 息屏显示", "按 aod_mode 进入息屏显示设置；当前版本提供开关写入", Icons.Default.DarkMode) { viewModel.setAod(!state.aodEnabled) } }
        item { ToolInfoRow("电池保护", "三星充电上限属于 OEM 专有能力，未探测到可写后端时保持只读", Icons.Default.BatteryStd) { viewModel.showDetails("电池保护", "当前工程不伪造 BatteryManager 写入；后续可接 Samsung 系统服务或特权伴侣。") } }
        item { ToolInfoRow("分辨率", "DisplayManager 不允许普通应用直接改变物理分辨率", Icons.Default.Settings) { viewModel.showDetails("分辨率", "需要 OEM/Root 后端；当前入口仅展示能力，不会写入未知键。") } }
    }
}

@Composable
private fun MorePage(state: MainUiState, viewModel: MainViewModel, padding: PaddingValues, context: Context) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionTitle("更多", "权限、集成、诊断与恢复") }
        item {
            ElevatedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("权限清单", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::refreshCapabilities) { Text("刷新") }
                    }
                    state.capabilities.forEach { capability ->
                        CapabilityRow(capability) {
                            if (capability.capability.name == "WRITE_SYSTEM_SETTINGS") {
                                context.startActivity(viewModel.openWriteSettingsIntent())
                            } else {
                                viewModel.showDetails(capability.capability.name, capability.explanation)
                            }
                        }
                    }
                }
            }
        }
        item { ToolInfoRow("Tasker / Locale", "提供动作与条件的集成占位；建议使用 app shortcuts 或广播契约", Icons.Default.ArrowForward) { viewModel.showDetails("Tasker / Locale", "后续将加入显式广播：SET_MODE、SET_RANGE、GET_STATUS，并以签名权限保护写入动作。") } }
        item { ToolInfoRow("快捷方式与 QS Tile", "刷新率模式磁贴已注册；可从 More 中将其加入系统面板", Icons.Default.Tune) { viewModel.showDetails("QS Tile", "RefreshModeTileService 会读取当前策略并循环切换 Standard/Adaptive/Maximum。") } }
        item { ToolInfoRow("主题与语言", "Material 3 动态取色；跟随系统深色模式与语言", Icons.Default.Language) { viewModel.showDetails("主题与语言", "当前使用系统动态颜色与系统 locale；自定义偏好存储接口已预留。") } }
        item { ToolInfoRow("诊断日志", "最近 ${state.journal.size} 次事务；包含失败原因与回滚记录", Icons.Default.Info) { viewModel.showDetails("诊断日志", state.journal.take(10).joinToString("\n") { record -> "${if (record.committed) "✓" else "!"} ${record.operation}${record.error?.let { ": $it" } ?: ""}" }.ifBlank { "还没有事务记录" }) } }
        item {
            ToolInfoRow(
                "紧急一键还原所有更改",
                "停止自动化/监视器，并恢复 ${state.managedSnapshotCount} 项已记录系统设置",
                Icons.Default.Restore,
            ) { viewModel.requestEmergencyReset() }
        }
        item {
            Text("Samsung Hz Ops · 原生实现 0.1.0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionTitle(title: String, summary: String) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FeatureSwitchRow(
    icon: ImageVector,
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onInfo: () -> Unit,
) {
    ElevatedCard {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onInfo) { Icon(Icons.Default.Info, contentDescription = "详情") }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun RuleInfoCard(title: String, summary: String, icon: ImageVector, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
private fun ToolInfoRow(title: String, summary: String, icon: ImageVector, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
private fun CapabilityRow(capability: com.xtawa.samsunghzops.core.model.CapabilityStatus, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (capability.state == CapabilityState.GRANTED) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = if (capability.state == CapabilityState.GRANTED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(capability.capability.name, style = MaterialTheme.typography.bodyMedium)
            Text(capability.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (capability.actionLabel != null) TextButton(onClick = onAction) { Text(capability.actionLabel) }
    }
}
