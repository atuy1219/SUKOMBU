package com.atuy.scomb.ui.features

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.atuy.scomb.ui.Screen
import com.atuy.scomb.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
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
            LogoutSection(
                onLogout = {
                    viewModel.logout()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                }
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
        10 to "10分前",
        30 to "30分前",
        60 to "1時間前",
        120 to "2時間前",
        1440 to "1日前",
        2880 to "2日前"
    )

    Column {
        Text(
            text = "通知設定",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "課題の締め切り何分前に通知を受け取るか設定します。",
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
private fun LogoutSection(onLogout: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onLogout,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("ログアウト")
        }
    }
}
