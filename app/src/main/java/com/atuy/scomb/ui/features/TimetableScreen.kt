package com.atuy.scomb.ui.features

import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import java.util.Calendar

private const val TAG = "TimetableScreen"

data class TimetableTerm(val year: Int, val term: String) {
    fun getDisplayName(): String {
        val termString = if (term == "1") "前期" else "後期"
        return "$year 年度 $termString"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    viewModel: TimetableViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentYear by viewModel.currentYear.collectAsStateWithLifecycle()
    val currentTerm by viewModel.currentTerm.collectAsStateWithLifecycle()

    Log.d(TAG, "Recomposing. Current state=${uiState.javaClass.simpleName}, Year=$currentYear, Term=$currentTerm")

    Scaffold(
        topBar = {
            TimetableTopBar(
                current = TimetableTerm(currentYear, currentTerm),
                onTermSelected = { newTerm ->
                    // ▼▼▼ 修正点 ▼▼▼
                    Log.d(TAG, "onTermSelected called. New selection: Year=${newTerm.year}, Term=${newTerm.term}")
                    // ▲▲▲ 修正点 ▲▲▲
                    viewModel.changeYearAndTerm(newTerm.year, newTerm.term)
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)) {
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
                            viewModel.changeYearAndTerm(currentYear, currentTerm)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableTopBar(
    current: TimetableTerm,
    onTermSelected: (TimetableTerm) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Box {
                Row(
                    modifier = Modifier.clickable { menuExpanded = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (current.year != 0) current.getDisplayName() else "読み込み中...")
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "学期選択")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    val startYear = Calendar.getInstance().get(Calendar.YEAR)
                    for (i in 0..5) {
                        val displayYear = startYear - i
                        DropdownMenuItem(
                            text = { Text(TimetableTerm(displayYear, "2").getDisplayName()) },
                            onClick = {
                                onTermSelected(TimetableTerm(displayYear, "2"))
                                menuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(TimetableTerm(displayYear, "1").getDisplayName()) },
                            onClick = {
                                onTermSelected(TimetableTerm(displayYear, "1"))
                                menuExpanded = false
                            }
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background
        )
    )
}

@Composable
fun TimetableGrid(timetable: Array<Array<ClassCell?>>) {
    val days = listOf("月", "火", "水", "木", "金")
    Column(Modifier.fillMaxSize()) {
        // 曜日ヘッダー
        Row(Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.width(24.dp))
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
                        modifier = Modifier.height(100.dp),
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