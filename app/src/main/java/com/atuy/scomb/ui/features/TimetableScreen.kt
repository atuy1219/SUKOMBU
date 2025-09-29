package com.atuy.scomb.ui.features

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atuy.scomb.data.db.ClassCell
import com.atuy.scomb.ui.viewmodel.TimetableUiState
import com.atuy.scomb.ui.viewmodel.TimetableViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    viewModel: TimetableViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is TimetableUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is TimetableUiState.Success -> {
                TimetableGrid(timetable = state.timetable)
            }

            is TimetableUiState.Error -> {
                ErrorState(
                    message = state.message,
                    onRetry = {
                        // TODO: ViewModelに再取得用のメソッドを実装
                    }
                )
            }
        }
    }
}



@Composable
fun TimetableGrid(timetable: Array<Array<ClassCell?>>) {
    val days = listOf("月", "火", "水", "木", "金")
    Column(Modifier.fillMaxSize()) {
        // 曜日ヘッダー
        Row(Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.width(24.dp)) // 時限表示用のスペース
            days.forEach { day ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = day, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // 時間割本体
        Row(Modifier.fillMaxWidth()) {
            // 時限表示
            Column {
                (1..7).forEach { period ->
                    Box(
                        modifier = Modifier.height(100.dp), // セルの高さ
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = period.toString(),
                            modifier = Modifier.width(24.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            // 授業セル表示
            Row(Modifier.weight(1f)) {
                timetable.forEach { dayColumn ->
                    Column(modifier = Modifier.weight(1f)) {
                        dayColumn.forEach { classCell ->
                            ClassCellView(classCell = classCell)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ClassCellView(classCell: ClassCell?) {
    Box(
        modifier = Modifier
            .height(100.dp)
            .fillMaxWidth()
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        if (classCell != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = classCell.name ?: "",
                    fontSize = 12.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = classCell.room ?: "",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = classCell.teachers ?: "",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}