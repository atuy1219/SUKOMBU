package com.atuy.scomb.ui.features

import android.content.Intent
import android.net.Uri
import android.widget.ImageView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.atuy.scomb.BuildConfig
import com.atuy.scomb.R
import com.atuy.scomb.data.manager.SettingsManager
import com.atuy.scomb.ui.Screen
import com.atuy.scomb.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        LogoutDialog(
            onConfirm = {
                showLogoutDialog = false
                viewModel.logout()
                navController.navigate(Screen.Login.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        inclusive = true
                    }
                    launchSingleTop = true
                }
            },
            onDismiss = { showLogoutDialog = false }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingsGroupCard {
                ThemeSettingsSection(
                    currentMode = uiState.themeMode,
                    onModeChange = { viewModel.updateThemeMode(it) }
                )
            }
        }

        item {
            SettingsGroupCard {
                HomeSettingsSection(
                    showHomeNews = uiState.showHomeNews,
                    onShowHomeNewsChange = { viewModel.updateShowHomeNews(it) }
                )
            }
        }

        item {
            SettingsGroupCard {
                TimetableSettingsSection(
                    displayWeekDays = uiState.displayWeekDays,
                    onDisplayWeekDaysChange = { viewModel.updateDisplayWeekDays(it) },
                    periodCount = uiState.timetablePeriodCount,
                    onPeriodCountChange = { viewModel.updateTimetablePeriodCount(it) }
                )
            }
        }

        item {
            SettingsGroupCard {
                NotificationSettingsSection(
                    selectedTimings = uiState.notificationTimings,
                    onTimingsChange = { viewModel.updateNotificationTimings(it) }
                )
            }
        }

        item {
            SettingsGroupCard {
                AppInfoSection(
                    onVersionClick = { viewModel.onVersionClick() }
                )
            }
        }

        if (uiState.isDebugMode) {
            item {
                SettingsGroupCard {
                    DebugSection(
                        onTestNotificationClick = { viewModel.scheduleTestNotification() },
                        onDisableDebugModeClick = { viewModel.disableDebugMode() }
                    )
                }
            }
        }

        item {
            LogoutSection(
                onLogoutClick = { showLogoutDialog = true }
            )
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingsGroupCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSettingsSection(
    currentMode: Int,
    onModeChange: (Int) -> Unit
) {
    Column {
        SectionHeader(
            title = "テーマ設定",
            icon = Icons.Default.DarkMode
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val options = listOf(
                SettingsManager.THEME_MODE_SYSTEM to "自動",
                SettingsManager.THEME_MODE_LIGHT to "ライト",
                SettingsManager.THEME_MODE_DARK to "ダーク"
            )

            options.forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = currentMode == mode,
                    onClick = { onModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                ) {
                    Text(label)
                }
            }
        }
    }
}

@Composable
private fun HomeSettingsSection(
    showHomeNews: Boolean,
    onShowHomeNewsChange: (Boolean) -> Unit
) {
    Column {
        SectionHeader(
            title = stringResource(R.string.settings_home_title),
            icon = Icons.Default.Home
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.settings_show_home_news),
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = showHomeNews,
                onCheckedChange = onShowHomeNewsChange
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TimetableSettingsSection(
    displayWeekDays: Set<Int>,
    onDisplayWeekDaysChange: (Set<Int>) -> Unit,
    periodCount: Int,
    onPeriodCountChange: (Int) -> Unit
) {
    val periodOptions = listOf(4, 5, 6, 7)
    val weekDays = listOf(
        stringResource(R.string.day_mon),
        stringResource(R.string.day_tue),
        stringResource(R.string.day_wed),
        stringResource(R.string.day_thu),
        stringResource(R.string.day_fri),
        stringResource(R.string.day_sat)
    )

    Column {
        SectionHeader(
            title = stringResource(R.string.settings_timetable_title),
            icon = Icons.Default.DateRange
        )

        // 表示する曜日設定
        Text(
            text = stringResource(R.string.settings_display_week_days),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            weekDays.forEachIndexed { index, day ->
                val isSelected = displayWeekDays.contains(index)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        val newSelection = displayWeekDays.toMutableSet()
                        if (isSelected) {
                            // 少なくとも1日は選択必須にする場合
                            if (newSelection.size > 1) {
                                newSelection.remove(index)
                            }
                        } else {
                            newSelection.add(index)
                        }
                        onDisplayWeekDaysChange(newSelection)
                    },
                    label = { Text(day) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 時限数設定
        Text(
            text = stringResource(R.string.settings_period_count),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            periodOptions.forEachIndexed { index, count ->
                SegmentedButton(
                    selected = periodCount == count,
                    onClick = { onPeriodCountChange(count) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = periodOptions.size
                    )
                ) {
                    Text(stringResource(R.string.settings_period_suffix, count))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun NotificationSettingsSection(
    selectedTimings: Set<Int>,
    onTimingsChange: (Set<Int>) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    // デフォルトの選択肢（表示順序のため）
    val defaultOptions = listOf(10, 30, 60, 120, 1440, 2880, 4320)

    // 表示する全てのタイミング（デフォルト + ユーザーが追加したもの）
    // デフォルト値のリストに含まれていなくても、selectedTimingsにあるものは表示する
    val allTimingsToDisplay = (defaultOptions + selectedTimings).distinct().sorted()

    if (showAddDialog) {
        AddNotificationTimingDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { minutes ->
                val newSelection = selectedTimings.toMutableSet()
                newSelection.add(minutes)
                onTimingsChange(newSelection)
                showAddDialog = false
            }
        )
    }

    Column {
        SectionHeader(
            title = stringResource(R.string.settings_notification_title),
            icon = Icons.Default.Notifications
        )

        Text(
            text = stringResource(R.string.settings_notification_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            allTimingsToDisplay.forEach { minutes ->
                val isSelected = selectedTimings.contains(minutes)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        val newSelection = selectedTimings.toMutableSet()
                        if (isSelected) {
                            newSelection.remove(minutes)
                        } else {
                            newSelection.add(minutes)
                        }
                        onTimingsChange(newSelection)
                    },
                    label = { Text(formatNotificationTime(minutes)) }
                )
            }

            // 追加ボタン
            AssistChip(
                onClick = { showAddDialog = true },
                label = { Text("追加") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "時間を追加",
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
fun AddNotificationTimingDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var valueText by remember { mutableStateOf("") }
    var selectedUnit by remember { mutableStateOf(0) } // 0: 分, 1: 時間, 2: 日

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("通知時間を追加") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { if (it.all { char -> char.isDigit() }) valueText = it },
                    label = { Text("数値") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf("分前", "時間前", "日前").forEachIndexed { index, label ->
                        SegmentedButton(
                            selected = selectedUnit == index,
                            onClick = { selectedUnit = index },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 3)
                        ) {
                            Text(label)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val value = valueText.toIntOrNull()
                    if (value != null && value > 0) {
                        val minutes = when (selectedUnit) {
                            0 -> value
                            1 -> value * 60
                            2 -> value * 24 * 60
                            else -> value
                        }
                        onConfirm(minutes)
                    }
                },
                enabled = valueText.isNotBlank() && valueText.toIntOrNull() != null && valueText.toInt() > 0
            ) {
                Text("追加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun formatNotificationTime(minutes: Int): String {
    return when {
        minutes % (24 * 60) == 0 -> "${minutes / (24 * 60)}日前"
        minutes % 60 == 0 -> "${minutes / 60}時間前"
        else -> "${minutes}分前"
    }
}

@Composable
private fun AppInfoSection(
    onVersionClick: () -> Unit
) {
    val context = LocalContext.current
    val versionName = BuildConfig.VERSION_NAME
    val commitHash = BuildConfig.GIT_COMMIT_HASH

    val displayVersion =
        if (versionName.contains("nightly", ignoreCase = true) || BuildConfig.DEBUG) {
            "$versionName ($commitHash)"
        } else {
            versionName
        }

    Column {
        SectionHeader(
            title = stringResource(R.string.settings_app_info_title),
            icon = Icons.Default.Info
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            AndroidView(
                modifier = Modifier.size(56.dp),
                factory = { context ->
                    ImageView(context).apply {
                        setImageResource(R.mipmap.ic_launcher)
                    }
                }
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.settings_version_format, displayVersion),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onVersionClick
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                val intent =
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/atuy1219/SUKOMBU"))
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.settings_github_link))
        }
    }
}

@Composable
private fun DebugSection(
    onTestNotificationClick: () -> Unit,
    onDisableDebugModeClick: () -> Unit
) {
    Column {
        SectionHeader(
            title = stringResource(R.string.settings_debug_title),
            icon = Icons.Default.BugReport
        )

        Button(
            onClick = onTestNotificationClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.settings_test_notification_button))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onDisableDebugModeClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Text(stringResource(R.string.settings_disable_debug_mode))
        }
    }
}

@Composable
private fun LogoutSection(onLogoutClick: () -> Unit) {
    Button(
        onClick = onLogoutClick,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.settings_logout_button))
    }
}

@Composable
fun LogoutDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_logout_dialog_title)) },
        text = { Text(stringResource(R.string.settings_logout_dialog_message)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.settings_logout_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}