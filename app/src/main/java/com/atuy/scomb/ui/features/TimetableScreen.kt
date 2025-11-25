package com.atuy.scomb.ui.features

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
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
    navController: NavController,
    viewModel: TimetableViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Log.d(
        TAG,
        "Recomposing. Current state=${uiState.javaClass.simpleName}"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is TimetableUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is TimetableUiState.Success -> {
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    TimetableGrid(
                        timetable = state.timetable,
                        onClassClick = { classCell ->
                            if (classCell.classId.isNotEmpty()) {
                                navController.navigate("classDetail/${classCell.classId}")
                            } else {
                                Log.w(TAG, "Clicked ClassCell with empty classId: ${classCell.name}")
                            }
                        }
                    )
                }
            }
            is TimetableUiState.Error -> {
                ErrorState(
                    message = state.message,
                    onRetry = { viewModel.refresh() }
                )
            }
        }
    }
}


@Composable
fun TimetableGrid(
    timetable: List<List<ClassCell?>>,
    onClassClick: (ClassCell) -> Unit
) {
    val days = listOf("月", "火", "水", "木", "金")
    val scrollState = rememberScrollState()

    // 今日の曜日を取得 (月=0, ... 金=4, 土=5, 日=6)
    val calendar = Calendar.getInstance()
    val todayIndex = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 16.dp) // 下部に少し余白を追加
    ) {
        // 曜日ヘッダー
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Spacer(modifier = Modifier.width(32.dp)) // 時限カラム分のスペース

            // 曜日部分もカードと同じ間隔で配置するためにRowで囲む
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp), // 本体に合わせて右端にパディングを追加
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                days.forEachIndexed { index, day ->
                    val isToday = index == todayIndex
                    Box(
                        modifier = Modifier
                            .width(0.dp) // 幅を均等にするためのおまじない
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        // 今日の場合のみ背景色をつける
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = day,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // グリッド本体
        Row(Modifier.fillMaxWidth()) {
            // 時限カラム
            Column(
                modifier = Modifier.width(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                (1..7).forEach { period ->
                    Box(
                        modifier = Modifier
                            .height(110.dp) // セルの高さに合わせて調整
                            .fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text(
                            text = period.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            // 授業カラム
            Row(
                Modifier
                    .weight(1f)
                    .padding(end = 8.dp), // 右端に余白
                horizontalArrangement = Arrangement.spacedBy(4.dp) // カード間の隙間を均等に配置
            ) {
                timetable.forEach { dayColumn ->
                    Column(
                        modifier = Modifier
                            .width(0.dp) // 幅を均等にするためのおまじない
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp) // 縦方向のセル間隔
                    ) {
                        dayColumn.forEach { classCell ->
                            ClassCellView(
                                classCell = classCell,
                                onClick = {
                                    if (classCell != null) {
                                        onClassClick(classCell)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ClassCellView(
    classCell: ClassCell?,
    onClick: () -> Unit
) {
    val cellHeight = 106.dp // 間隔(4dp)を含めて110dpになるように調整

    if (classCell == null) {
        // 空きコマ: 背景なし、あるいは薄い区切り線など
        // ここではGoogleカレンダー風に、何もない空間として表現しつつ、
        // グリッド感を出すためにごく薄い背景などを入れても良いが、モダンにするなら空白が良い
        Box(
            modifier = Modifier
                .height(cellHeight)
                .fillMaxWidth()
            // デバッグ用に薄い枠線を入れても良いが、今回は完全な空白にする
            // .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
        )
    } else {
        // 授業あり: カード表示
        Card(
            modifier = Modifier
                .height(cellHeight)
                .fillMaxWidth()
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(12.dp), // 角丸を少し大きめに
            colors = CardDefaults.cardColors(
                // Googleカレンダー風に、primaryContainerなどの色付き背景を使用
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 科目名
                Text(
                    text = classCell.name ?: "",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    ),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )

                if (!classCell.room.isNullOrBlank()) {
                    Text(
                        text = classCell.room,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}