package com.atuy.scomb.ui.features

import android.content.Intent
import android.net.Uri
import android.widget.ImageView
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.atuy.scomb.BuildConfig
import com.atuy.scomb.R
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
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            HomeSettingsSection(
                showHomeNews = uiState.showHomeNews,
                onShowHomeNewsChange = { viewModel.updateShowHomeNews(it) }
            )
        }

        item {
            HorizontalDivider()
        }

        item {
            NotificationSettingsSection(
                selectedTimings = uiState.notificationTimings,
                onTimingsChange = { viewModel.updateNotificationTimings(it) }
            )
        }

        item {
            HorizontalDivider()
        }

        item {
            AppInfoSection()
        }

        item {
            HorizontalDivider()
        }

        item {
            DebugSection(
                onTestNotificationClick = { viewModel.scheduleTestNotification() }
            )
        }

        item {
            HorizontalDivider()
        }

        item {
            LogoutSection(
                onLogoutClick = { showLogoutDialog = true }
            )
        }
    }
}

@Composable
private fun HomeSettingsSection(
    showHomeNews: Boolean,
    onShowHomeNewsChange: (Boolean) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.settings_home_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
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
private fun NotificationSettingsSection(
    selectedTimings: Set<Int>,
    onTimingsChange: (Set<Int>) -> Unit
) {
    val notificationOptions = mapOf(
        10 to stringResource(R.string.settings_time_10min),
        30 to stringResource(R.string.settings_time_30min),
        60 to stringResource(R.string.settings_time_1hour),
        120 to stringResource(R.string.settings_time_2hours),
        1440 to stringResource(R.string.settings_time_1day),
        2880 to stringResource(R.string.settings_time_2days)
    )

    Column {
        Text(
            text = stringResource(R.string.settings_notification_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = stringResource(R.string.settings_notification_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            notificationOptions.forEach { (minutes, label) ->
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
                    label = { Text(label) }
                )
            }
        }
    }
}

@Composable
private fun AppInfoSection() {
    val context = LocalContext.current
    val versionName = BuildConfig.VERSION_NAME
    val commitHash = BuildConfig.GIT_COMMIT_HASH

    val displayVersion = if (versionName.contains("nightly", ignoreCase = true) || BuildConfig.DEBUG) {
        "$versionName ($commitHash)"
    } else {
        versionName
    }

    Column {
        Text(
            text = stringResource(R.string.settings_app_info_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AndroidView(
                        modifier = Modifier.size(64.dp),
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/atuy1219/SUKOMBU"))
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
    }
}

@Composable
private fun DebugSection(onTestNotificationClick: () -> Unit) {
    Column {
        Text(
            text = stringResource(R.string.settings_debug_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Button(
            onClick = onTestNotificationClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.settings_test_notification_button))
        }
    }
}

@Composable
private fun LogoutSection(onLogoutClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onLogoutClick,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text(stringResource(R.string.settings_logout_button))
        }
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