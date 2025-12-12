package com.atuy.scomb.ui.features

import android.util.Log
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.atuy.scomb.R
import com.atuy.scomb.data.db.ClassCell
import com.atuy.scomb.ui.viewmodel.TimetableUiState
import com.atuy.scomb.ui.viewmodel.TimetableViewModel
import java.util.Calendar

private const val TAG = "TimetableScreen"

data class TimetableTerm(val year: Int, val term: String) {
    @Composable
    fun getDisplayName(): String {
        val termString = if (term == "1") stringResource(R.string.timetable_term_1) else stringResource(R.string.timetable_term_2)
        return stringResource(R.string.timetable_term_display_format, year, termString)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun TimetableScreen(
    navController: NavController,
    viewModel: TimetableViewModel = hiltViewModel(),
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
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
                        otherClasses = state.otherClasses,
                        undoneTaskClassIds = state.undoneTaskClassIds,
                        displayWeekDays = state.displayWeekDays,
                        periodCount = state.periodCount,
                        onClassClick = { classCell ->
                            if (classCell.classId.isNotEmpty()) {
                                navController.navigate("classDetail/${classCell.classId}?dayOfWeek=${classCell.dayOfWeek}&period=${classCell.period}")
                            } else {
                                Log.w(
                                    TAG,
                                    "Clicked ClassCell with empty classId: ${classCell.name}"
                                )
                            }
                        },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope
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


@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun TimetableGrid(
    timetable: List<List<ClassCell?>>,
    otherClasses: List<ClassCell>,
    undoneTaskClassIds: Set<String>,
    displayWeekDays: Set<Int>,
    periodCount: Int,
    onClassClick: (ClassCell) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val allWeekDays = listOf(
        stringResource(R.string.day_mon),
        stringResource(R.string.day_tue),
        stringResource(R.string.day_wed),
        stringResource(R.string.day_thu),
        stringResource(R.string.day_fri),
        stringResource(R.string.day_sat)
    )
    val targetDayIndices = displayWeekDays.sorted().filter { it < allWeekDays.size }

    val displayPeriods = (1..periodCount).toList()

    val scrollState = rememberScrollState()

    val calendar = Calendar.getInstance()
    val todayIndex = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 16.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Spacer(modifier = Modifier.width(32.dp))

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                targetDayIndices.forEach { dayIndex ->
                    val isToday = dayIndex == todayIndex
                    Box(
                        modifier = Modifier
                            .width(0.dp)
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
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
                                text = allWeekDays.getOrElse(dayIndex) { "?" },
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

        Row(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.width(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                displayPeriods.forEach { period ->
                    Box(
                        modifier = Modifier
                            .height(110.dp)
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

            Row(
                Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                targetDayIndices.forEach { dayIndex ->
                    val dayColumn = timetable.getOrNull(dayIndex) ?: emptyList()

                    Column(
                        modifier = Modifier
                            .width(0.dp)
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        displayPeriods.forEach { period ->
                            val classCell = dayColumn.getOrNull(period - 1)
                            ClassCellView(
                                classCell = classCell,
                                hasUndoneTasks = classCell != null && undoneTaskClassIds.contains(classCell.classId),
                                onClick = {
                                    if (classCell != null) {
                                        onClassClick(classCell)
                                    }
                                },
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                        }
                    }
                }
            }
        }

        if (otherClasses.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "その他",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                otherClasses.forEach { classCell ->
                    OtherClassCellView(
                        classCell = classCell,
                        hasUndoneTasks = undoneTaskClassIds.contains(classCell.classId),
                        onClick = { onClassClick(classCell) },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun getDynamicClassColors(
    customColorInt: Int?,
    defaultContainerColor: Color,
    defaultContentColor: Color
): Pair<Color, Color> {
    // 現在のテーマ（アプリ設定含む）の背景輝度を取得してダーク判定
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val surfaceColor = MaterialTheme.colorScheme.surface

    return remember(customColorInt, isDarkTheme, defaultContainerColor, defaultContentColor) {
        if (customColorInt != null && customColorInt != 0) {
            val seedColor = Color(customColorInt)
            if (isDarkTheme) {
                val container = seedColor.copy(alpha = 0.2f).compositeOver(surfaceColor)
                container to seedColor
            } else {
                val container = seedColor.copy(alpha = 0.12f).compositeOver(surfaceColor)
                container to seedColor
            }
        } else {
            defaultContainerColor to defaultContentColor
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ClassCellView(
    classCell: ClassCell?,
    hasUndoneTasks: Boolean,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val cellHeight = 106.dp

    if (classCell == null) {
        Box(
            modifier = Modifier
                .height(cellHeight)
                .fillMaxWidth()
        )
    } else {
        // 色の決定ロジック呼び出し
        val (containerColor, contentColor) = getDynamicClassColors(
            customColorInt = classCell.customColorInt,
            defaultContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
            defaultContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )

        with(sharedTransitionScope) {
            Box(
                modifier = Modifier
                    .height(cellHeight)
                    .fillMaxWidth()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .sharedElement(
                            sharedContentState = rememberSharedContentState(key = "class-${classCell.classId}-${classCell.dayOfWeek}-${classCell.period}"),
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                        .clickable(onClick = onClick),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = containerColor,
                        contentColor = contentColor
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
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
                                color = contentColor.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }

                if (hasUndoneTasks) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(MaterialTheme.colorScheme.error, CircleShape)
                            .align(Alignment.TopEnd)
                            .offset(x = (-6).dp, y = 6.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun OtherClassCellView(
    classCell: ClassCell,
    hasUndoneTasks: Boolean,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val (containerColor, contentColor) = getDynamicClassColors(
        customColorInt = classCell.customColorInt,
        defaultContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        defaultContentColor = Color.Unspecified
    )

    with(sharedTransitionScope) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .sharedElement(
                        sharedContentState = rememberSharedContentState(key = "class-${classCell.classId}-${classCell.dayOfWeek}-${classCell.period}"),
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                    .clickable(onClick = onClick),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = containerColor,
                    contentColor = contentColor
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = classCell.name ?: "",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        if (!classCell.teachers.isNullOrBlank()) {
                            Text(
                                text = classCell.teachers,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (contentColor != Color.Unspecified) contentColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!classCell.room.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = classCell.room,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (contentColor != Color.Unspecified) contentColor else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (hasUndoneTasks) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(MaterialTheme.colorScheme.error, CircleShape)
                        .align(Alignment.TopEnd)
                        .offset(x = (-8).dp, y = 8.dp)
                )
            }
        }
    }
}