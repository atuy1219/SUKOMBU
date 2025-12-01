package com.atuy.scomb.ui.features

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.CreditScore
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.atuy.scomb.R
import com.atuy.scomb.data.db.ClassCell
import com.atuy.scomb.data.db.CustomLink
import com.atuy.scomb.data.db.Task
import com.atuy.scomb.ui.viewmodel.ClassDetailUiState
import com.atuy.scomb.ui.viewmodel.ClassDetailViewModel
import kotlin.math.PI
import kotlin.math.atan2

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ClassDetailScreen(
    navController: NavController,
    viewModel: ClassDetailViewModel = hiltViewModel(),
    classId: String?,
    dayOfWeek: Int,
    period: Int,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showColorDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.openUrlEvent.collect { url: String ->
            try {
                val builder = CustomTabsIntent.Builder()
                val customTabsIntent = builder.build()
                customTabsIntent.launchUrl(context, url.toUri())
            } catch (e: Exception) {
            }
        }
    }

    // ダイアログの表示
    if (showColorDialog && uiState is ClassDetailUiState.Success) {
        val state = uiState as ClassDetailUiState.Success
        ColorPickerDialog(
            currentColorInt = state.classCell.customColorInt,
            onColorChange = { viewModel.updateClassColor(it) },
            onResetColor = { viewModel.resetClassColor() },
            onDismiss = { showColorDialog = false },
            isSaving = state.isSaving
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_class_detail)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    // 色設定ボタンを追加
                    if (uiState is ClassDetailUiState.Success) {
                        IconButton(onClick = { showColorDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.ColorLens,
                                contentDescription = "表示カラー設定",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                windowInsets = WindowInsets(0.dp)
            )
        },
        contentWindowInsets = WindowInsets(0.dp)
    ) { paddingValues ->
        with(sharedTransitionScope) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .then(
                        if (classId != null && dayOfWeek != -1 && period != -1) {
                            Modifier.sharedElement(
                                sharedContentState = rememberSharedContentState(key = "class-$classId-$dayOfWeek-$period"),
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                        } else Modifier
                    )
            ) {
                when (val state = uiState) {
                    is ClassDetailUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    is ClassDetailUiState.Success -> {
                        ClassDetailContent(
                            classCell = state.classCell,
                            tasks = state.tasks,
                            customLinks = state.customLinks,
                            isSaving = state.isSaving,
                            onClassPageClick = { viewModel.onClassPageClick() },
                            onUpdateUserNote = { viewModel.updateUserNote(it) },
                            onAddLink = { title, url -> viewModel.addCustomLink(title, url) },
                            onRemoveLink = { viewModel.removeCustomLink(it) }
                        )
                    }

                    is ClassDetailUiState.Error -> {
                        ErrorState(
                            message = state.message,
                            onRetry = { viewModel.loadClassDetails() })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClassDetailContent(
    classCell: ClassCell,
    tasks: List<Task>,
    customLinks: List<CustomLink>,
    isSaving: Boolean,
    onClassPageClick: () -> Unit = {},
    onUpdateUserNote: (String) -> Unit = {},
    onAddLink: (String, String) -> Unit = { _, _ -> },
    onRemoveLink: (CustomLink) -> Unit = {}
) {
    val context = LocalContext.current
    fun openUrl(url: String?) {
        if (url.isNullOrEmpty()) return
        try {
            val builder = CustomTabsIntent.Builder()
            val customTabsIntent = builder.build()
            customTabsIntent.launchUrl(context, url.toUri())
        } catch (e: Exception) {
        }
    }

    var showAddLinkDialog by remember { mutableStateOf(false) }
    var showEditNoteDialog by remember { mutableStateOf(false) }

    if (showAddLinkDialog) {
        AddLinkDialog(
            onDismiss = { showAddLinkDialog = false },
            onConfirm = { title, url ->
                onAddLink(title, url)
                showAddLinkDialog = false
            }
        )
    }

    if (showEditNoteDialog) {
        EditNoteDialog(
            initialNote = classCell.note ?: "",
            onDismiss = { showEditNoteDialog = false },
            onConfirm = { note ->
                onUpdateUserNote(note)
                showEditNoteDialog = false
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "header") {
            ClassHeaderCard(classCell, onClassPageClick)
        }

        item(key = "info_grid") {
            InfoGridCard(classCell)
        }

        // ColorSettingsCard は削除されました

        item(key = "links") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.class_detail_link_section),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { showAddLinkDialog = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.class_detail_add_link_button))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 公式シラバス
                    FilledTonalButton(
                        onClick = { openUrl(classCell.syllabusUrl) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.class_detail_syllabus))
                    }

                    // カスタムリンク
                    customLinks.forEach { link ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = { openUrl(link.url) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.Link,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(link.title)
                            }
                            IconButton(onClick = { onRemoveLink(link) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.class_detail_delete_link),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        item(key = "memo") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.class_detail_memo_section),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AnimatedVisibility(visible = isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .padding(end = 8.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                            IconButton(
                                onClick = { showEditNoteDialog = true },
                                modifier = Modifier.size(24.dp),
                                enabled = !isSaving
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.class_detail_edit_memo))
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    if (!classCell.note.isNullOrBlank()) {
                        Text(
                            text = classCell.note,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.class_detail_no_memo),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item(key = "task_header") {
            Text(
                text = stringResource(R.string.class_detail_related_tasks),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        if (tasks.isEmpty()) {
            item(key = "no_tasks") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.class_detail_no_related_tasks),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(tasks, key = { it.id }) { task ->
                TaskCard(
                    task = task,
                    onTaskClick = { openUrl(task.url) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
fun ClassHeaderCard(classCell: ClassCell, onClassPageClick: () -> Unit) {
    // TimetableScreen.ktで定義されている関数を使用
    val (containerColor, contentColor) = getDynamicClassColors(
        customColorInt = classCell.customColorInt,
        defaultContainerColor = MaterialTheme.colorScheme.primaryContainer,
        defaultContentColor = MaterialTheme.colorScheme.onPrimaryContainer
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = classCell.name ?: stringResource(R.string.class_detail_no_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = classCell.teachers ?: stringResource(R.string.class_detail_no_teacher),
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onClassPageClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = contentColor,
                    contentColor = containerColor
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.class_detail_open_lms))
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.Default.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ColorPickerDialog(
    currentColorInt: Int?,
    onColorChange: (Int) -> Unit,
    onResetColor: () -> Unit,
    onDismiss: () -> Unit,
    isSaving: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ColorLens,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("表示カラー設定")
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    ColorWheel(
                        modifier = Modifier.size(200.dp),
                        initialColor = currentColorInt?.let { Color(it) } ?: Color.White,
                        onColorSelected = { color ->
                            onColorChange(color.toArgb())
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "タップまたはドラッグで色を選択",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onResetColor,
                enabled = !isSaving && currentColorInt != null && currentColorInt != 0
            ) {
                Text("色をリセット")
            }
        }
    )
}

@Composable
fun ColorWheel(
    modifier: Modifier = Modifier,
    initialColor: Color,
    onColorSelected: (Color) -> Unit
) {

    var selectedColor by remember { mutableStateOf(initialColor) }

    var isInitialized by remember { mutableStateOf(false) }

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.width / 2f
                    val distance = (offset - center).getDistance()

                    if (distance <= radius) {
                        val angle = atan2(offset.y - center.y, offset.x - center.x) * (180 / PI).toFloat()
                        val hue = (angle + 360) % 360

                        val color = Color.hsv(hue, 1f, 1f)
                        selectedColor = color
                        onColorSelected(color)
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val offset = change.position
                    val radius = size.width / 2f
                    val distance = (offset - center).getDistance()

                    val angle = atan2(offset.y - center.y, offset.x - center.x) * (180 / PI).toFloat()
                    val hue = (angle + 360) % 360

                    val color = Color.hsv(hue, 1f, 1f)
                    selectedColor = color
                    onColorSelected(color)
                }
            }
    ) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.width / 2

        if (!isInitialized && selectedColor != Color.White) {
            isInitialized = true
        }

        val colors = (0..360).map { Color.hsv(it.toFloat(), 1f, 1f) }
        drawCircle(
            brush = Brush.sweepGradient(colors, center),
            radius = radius
        )

        drawCircle(
            color = selectedColor,
            radius = radius * 0.4f
        )

        drawCircle(
            color = Color.White,
            radius = radius * 0.4f,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
        )
    }
}

@Composable
fun InfoGridCard(classCell: ClassCell) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val dayPeriodValue = if (classCell.period == 8 || classCell.dayOfWeek == 8) {
                ""
            } else {
                "${(classCell.dayOfWeek).toDayOfWeekString()} ${classCell.period + 1}限"
            }

            InfoRow(
                icon = Icons.Default.CalendarToday,
                label = stringResource(R.string.class_detail_info_day_period),
                value = dayPeriodValue
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            InfoRow(
                icon = Icons.Default.LocationOn,
                label = stringResource(R.string.class_detail_info_room),
                value = classCell.room ?: stringResource(R.string.home_room_unset)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            InfoRow(
                icon = Icons.Default.CreditScore,
                label = stringResource(R.string.class_detail_info_credit),
                value = classCell.numberOfCredit?.toString() ?: "-"
            )
        }
    }
}

@Composable
fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun AddLinkDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.class_detail_dialog_add_link_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.class_detail_dialog_title_label)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.class_detail_dialog_url_label)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (title.isNotBlank() && url.isNotBlank()) onConfirm(title, url) }
            ) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun EditNoteDialog(
    initialNote: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var note by remember { mutableStateOf(initialNote) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.class_detail_dialog_edit_memo_title)) },
        text = {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.class_detail_dialog_content_label)) },
                minLines = 3,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(note) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun Int.toDayOfWeekString(): String {
    return when (this) {
        0 -> stringResource(R.string.day_monday)
        1 -> stringResource(R.string.day_tuesday)
        2 -> stringResource(R.string.day_wednesday)
        3 -> stringResource(R.string.day_thursday)
        4 -> stringResource(R.string.day_friday)
        5 -> stringResource(R.string.day_saturday)
        6 -> stringResource(R.string.day_sunday)
        else -> stringResource(R.string.day_unknown)
    }
}