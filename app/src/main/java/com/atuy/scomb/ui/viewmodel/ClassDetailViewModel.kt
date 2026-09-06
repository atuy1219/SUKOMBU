package com.atuy.scomb.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atuy.scomb.data.db.ClassCell
import com.atuy.scomb.data.db.ClassCellDao
import com.atuy.scomb.data.db.CustomLink
import com.atuy.scomb.data.db.Task
import com.atuy.scomb.data.db.TaskDao
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

sealed interface ClassDetailUiState {
    object Loading : ClassDetailUiState
    data class Success(
        val classCell: ClassCell,
        val tasks: List<Task>,
        val customLinks: List<CustomLink> = emptyList(),
        val isSaving: Boolean = false // 保存中状態を追加
    ) : ClassDetailUiState

    data class Error(val message: String) : ClassDetailUiState
}

@HiltViewModel
class ClassDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val classCellDao: ClassCellDao,
    private val taskDao: TaskDao,
    private val repository: com.atuy.scomb.data.repository.ScombzRepository,
    private val moshi: Moshi
) : ViewModel() {

    private val editMutex = Mutex()

    private val classId: String = savedStateHandle.get<String>("classId")!!

    private val _uiState = MutableStateFlow<ClassDetailUiState>(ClassDetailUiState.Loading)
    val uiState: StateFlow<ClassDetailUiState> = _uiState.asStateFlow()

    init {
        loadClassDetails()
    }

    fun loadClassDetails() {
        viewModelScope.launch {
            _uiState.value = ClassDetailUiState.Loading
            try {
                val classCells = classCellDao.getClassCellsById(classId)
                if (classCells.isEmpty()) {
                    _uiState.value = ClassDetailUiState.Error("授業情報が見つかりません。")
                } else {
                    val classCell = classCells.first()
                    val tasks = taskDao.getTasksByClassId(classId)

                    val customLinks = parseCustomLinks(classCell.customLinksJson)

                    _uiState.value = ClassDetailUiState.Success(classCell, tasks, customLinks)
                }
            } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.value =
                    ClassDetailUiState.Error(e.message ?: "データの読み込みに失敗しました。")
            }
        }
    }

    private fun parseCustomLinks(json: String?): List<CustomLink> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = Types.newParameterizedType(List::class.java, CustomLink::class.java)
            val adapter = moshi.adapter<List<CustomLink>>(type)
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            emptyList()
        }
    }

    private fun customLinksToJson(links: List<CustomLink>): String {
        val type = Types.newParameterizedType(List::class.java, CustomLink::class.java)
        val adapter = moshi.adapter<List<CustomLink>>(type)
        return adapter.toJson(links)
    }

    private fun editClass(transform: suspend (ClassDetailUiState.Success) -> ClassDetailUiState.Success) {
        viewModelScope.launch {
            editMutex.withLock {
                val state = _uiState.value as? ClassDetailUiState.Success ?: return@withLock
                _uiState.value = state.copy(isSaving = true)
                try {
                    _uiState.value = transform(state).copy(isSaving = false)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.value = state.copy(isSaving = false)
                    _errorEvent.send(e.message ?: "保存に失敗しました")
                }
            }
        }
    }

    fun updateUserNote(note: String) = editClass { state ->
        repository.updateClassInfo(state.classCell, note, state.classCell.customColorInt)
        state.copy(classCell = state.classCell.copy(note = note))
    }

    fun updateClassColor(colorInt: Int) = updateColorInternal(colorInt)

    fun resetClassColor() = updateColorInternal(null)

    private fun updateColorInternal(colorInt: Int?) = editClass { state ->
        repository.updateClassInfo(state.classCell, state.classCell.note, colorInt)
        state.copy(classCell = state.classCell.copy(customColorInt = colorInt))
    }

    fun addCustomLink(title: String, url: String) = updateLinks { it + CustomLink(title, url) }

    fun removeCustomLink(link: CustomLink) = updateLinks { it - link }

    private fun updateLinks(transform: (List<CustomLink>) -> List<CustomLink>) = editClass { state ->
        val links = transform(state.customLinks)
        val json = customLinksToJson(links)
        repository.updateCustomLinks(state.classCell, json)
        state.copy(classCell = state.classCell.copy(customLinksJson = json), customLinks = links)
    }

    private val _errorEvent = Channel<String>(Channel.BUFFERED)
    val errorEvent = _errorEvent.receiveAsFlow()

    private val _openUrlEvent = Channel<String>(Channel.BUFFERED)
    val openUrlEvent = _openUrlEvent.receiveAsFlow()

    fun onClassPageClick() {
        viewModelScope.launch {
            try {
                val url = repository.getClassUrl(classId)
                _openUrlEvent.send(url)
            } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.value = ClassDetailUiState.Error(e.message ?: "URLの取得に失敗しました")
            }
        }
    }

    fun onTaskClick(task: Task) {
        viewModelScope.launch {
            try {
                val url = repository.getTaskUrl(task)
                _openUrlEvent.send(url)
            } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
                // エラー時はトーストなどで通知したいが、ここではUI Stateのエラーにはしない（画面全体がエラーになるため）
                // 簡易的にコンソールに出力し、失敗したらtask.urlをフォールバックとして開くようイベントを送る手もあるが
                // ここではエラーメッセージを表示せずに既存のURLを試すようにする
                 _openUrlEvent.send(task.url)
            }
        }
    }
}
