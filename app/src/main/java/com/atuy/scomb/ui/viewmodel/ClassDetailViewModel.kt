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
import javax.inject.Inject

sealed interface ClassDetailUiState {
    object Loading : ClassDetailUiState
    data class Success(
        val classCell: ClassCell,
        val tasks: List<Task>,
        val customLinks: List<CustomLink> = emptyList()
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
            emptyList()
        }
    }

    private fun customLinksToJson(links: List<CustomLink>): String {
        val type = Types.newParameterizedType(List::class.java, CustomLink::class.java)
        val adapter = moshi.adapter<List<CustomLink>>(type)
        return adapter.toJson(links)
    }

    fun updateUserNote(note: String) {
        val currentState = _uiState.value
        if (currentState is ClassDetailUiState.Success) {
            val updatedClassCell = currentState.classCell.copy(userNote = note)
            viewModelScope.launch {
                classCellDao.insertClassCell(updatedClassCell)
                _uiState.value = currentState.copy(classCell = updatedClassCell)
            }
        }
    }

    fun addCustomLink(title: String, url: String) {
        val currentState = _uiState.value
        if (currentState is ClassDetailUiState.Success) {
            val newLink = CustomLink(title, url)
            val updatedLinks = currentState.customLinks + newLink
            val json = customLinksToJson(updatedLinks)
            val updatedClassCell = currentState.classCell.copy(customLinksJson = json)

            viewModelScope.launch {
                classCellDao.insertClassCell(updatedClassCell)
                _uiState.value =
                    currentState.copy(classCell = updatedClassCell, customLinks = updatedLinks)
            }
        }
    }

    fun removeCustomLink(link: CustomLink) {
        val currentState = _uiState.value
        if (currentState is ClassDetailUiState.Success) {
            val updatedLinks = currentState.customLinks - link
            val json = customLinksToJson(updatedLinks)
            val updatedClassCell = currentState.classCell.copy(customLinksJson = json)

            viewModelScope.launch {
                classCellDao.insertClassCell(updatedClassCell)
                _uiState.value =
                    currentState.copy(classCell = updatedClassCell, customLinks = updatedLinks)
            }
        }
    }

    private val _openUrlEvent = Channel<String>(Channel.BUFFERED)
    val openUrlEvent = _openUrlEvent.receiveAsFlow()

    fun onClassPageClick() {
        viewModelScope.launch {
            try {
                val url = repository.getClassUrl(classId)
                _openUrlEvent.send(url)
            } catch (e: Exception) {
                _uiState.value = ClassDetailUiState.Error(e.message ?: "URLの取得に失敗しました")
            }
        }
    }
}