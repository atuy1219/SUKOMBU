package com.atuy.scomb.ui.features

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.atuy.scomb.ui.viewmodel.AdvancedNewsSearchInput
import com.atuy.scomb.ui.viewmodel.NewsFilter

@Composable
fun FilterBar(
    filter: NewsFilter,
    categories: List<String>,
    searchError: String?,
    onFilterChanged: (NewsFilter) -> Unit
) {
    var showAdvancedSearch by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NewsSearchField(
                value = filter.searchQuery,
                placeholder = "タイトル・発信元を検索",
                onValueChange = {
                    onFilterChanged(filter.copy(searchQuery = it))
                },
                modifier = Modifier.weight(1f),
                isError = searchError != null
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = { showAdvancedSearch = true }) {
                Icon(Icons.Default.Tune, contentDescription = "詳細検索")
            }
        }

        if (searchError != null) {
            Text(
                text = searchError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filter.unreadOnly,
                onClick = {
                    onFilterChanged(filter.copy(unreadOnly = !filter.unreadOnly))
                },
                label = { Text("未読のみ") }
            )

            MultiSelectFilterChip(
                label = "カテゴリ",
                dialogTitle = "カテゴリを選択",
                options = categories,
                selectedOptions = filter.selectedCategories,
                onSelectionChanged = {
                    onFilterChanged(filter.copy(selectedCategories = it))
                }
            )
        }
    }

    if (showAdvancedSearch) {
        AdvancedSearchDialog(
            onDismiss = { showAdvancedSearch = false },
            onApply = { input ->
                onFilterChanged(filter.copy(searchQuery = input.toQuery()))
                showAdvancedSearch = false
            }
        )
    }
}

@Composable
private fun NewsSearchField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        modifier = modifier,
        singleLine = true,
        isError = isError,
        shape = RoundedCornerShape(12.dp),
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null)
        },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "検索をクリア")
                }
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = { focusManager.clearFocus() }
        )
    )
}

@Composable
private fun AdvancedSearchDialog(
    onDismiss: () -> Unit,
    onApply: (AdvancedNewsSearchInput) -> Unit
) {
    var allWords by remember { mutableStateOf("") }
    var exactPhrase by remember { mutableStateOf("") }
    var anyWords by remember { mutableStateOf("") }
    var excludedWords by remember { mutableStateOf("") }
    var titlePhrase by remember { mutableStateOf("") }
    var authorPhrase by remember { mutableStateOf("") }
    var since by remember { mutableStateOf("") }
    var until by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("詳細検索") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        "入力内容から検索式を生成します。未入力の項目は無視されます。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                item {
                    AdvancedSearchField(
                        value = allWords,
                        onValueChange = { allWords = it },
                        label = "すべて含む語",
                        placeholder = "空白区切り"
                    )
                }
                item {
                    AdvancedSearchField(
                        value = exactPhrase,
                        onValueChange = { exactPhrase = it },
                        label = "完全一致フレーズ"
                    )
                }
                item {
                    AdvancedSearchField(
                        value = anyWords,
                        onValueChange = { anyWords = it },
                        label = "いずれかを含む語",
                        placeholder = "空白区切り"
                    )
                }
                item {
                    AdvancedSearchField(
                        value = excludedWords,
                        onValueChange = { excludedWords = it },
                        label = "含まない語",
                        placeholder = "空白区切り"
                    )
                }
                item {
                    AdvancedSearchField(
                        value = titlePhrase,
                        onValueChange = { titlePhrase = it },
                        label = "タイトルに含むフレーズ"
                    )
                }
                item {
                    AdvancedSearchField(
                        value = authorPhrase,
                        onValueChange = { authorPhrase = it },
                        label = "発信元に含むフレーズ"
                    )
                }
                item {
                    AdvancedSearchField(
                        value = since,
                        onValueChange = { since = it },
                        label = "開始日",
                        placeholder = "YYYY-MM-DD"
                    )
                }
                item {
                    AdvancedSearchField(
                        value = until,
                        onValueChange = { until = it },
                        label = "終了日",
                        placeholder = "YYYY-MM-DD"
                    )
                }
                item {
                    Text(
                        "検索バーへ直接入力する場合は \"フレーズ\"、-除外、OR、since:、until:、title:(...)、author:(...) を使用できます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(
                        AdvancedNewsSearchInput(
                            allWords = allWords,
                            exactPhrase = exactPhrase,
                            anyWords = anyWords,
                            excludedWords = excludedWords,
                            titlePhrase = titlePhrase,
                            authorPhrase = authorPhrase,
                            since = since,
                            until = until
                        )
                    )
                }
            ) {
                Text("検索式を適用")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

@Composable
private fun AdvancedSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (placeholder == null) null else {
            { Text(placeholder) }
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
fun MultiSelectFilterChip(
    label: String,
    dialogTitle: String,
    options: List<String>,
    selectedOptions: Set<String>,
    onSelectionChanged: (Set<String>) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    val chipLabel = if (selectedOptions.isEmpty()) {
        label
    } else {
        "$label (${selectedOptions.size})"
    }

    AssistChip(
        onClick = { showDialog = true },
        label = { Text(chipLabel) },
        trailingIcon = {
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        },
        enabled = options.isNotEmpty()
    )

    if (showDialog) {
        var temporarySelection by remember(selectedOptions) {
            mutableStateOf(selectedOptions)
        }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(dialogTitle) },
            text = {
                LazyColumn {
                    items(options, key = { it }) { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    temporarySelection = temporarySelection.toggle(option)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = option in temporarySelection,
                                onCheckedChange = {
                                    temporarySelection = temporarySelection.toggle(option)
                                }
                            )
                            Text(
                                text = option,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSelectionChanged(temporarySelection)
                        showDialog = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }
}

private fun Set<String>.toggle(value: String): Set<String> {
    return if (value in this) this - value else this + value
}
