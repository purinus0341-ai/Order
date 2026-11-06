package com.sitticun.orderapp.ui.screens.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sitticun.orderapp.OrderApplication
import com.sitticun.orderapp.data.model.NoteModel
import com.sitticun.orderapp.data.repository.NoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotesViewModel(
    private val repository: NoteRepository
) : ViewModel() {

    val pendingCount = repository.pendingCount
    val unknownOwnerCount = repository.unknownOwnerCount
    val unknownOwnerTasks = repository.unknownOwnerTasks

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    fun dismissError() { _error.value = null }

    private val _notes = MutableStateFlow<List<NoteModel>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val filteredNotes: StateFlow<List<NoteModel>> = combine(_notes, _searchQuery) { notes, query ->
        if (query.isBlank()) {
            notes
        } else {
            val q = query.trim().lowercase()
            notes.filter {
                it.title.lowercase().contains(q) || it.content.lowercase().contains(q)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadLocalNotes()
        syncNotes()
    }

    fun loadLocalNotes() {
        viewModelScope.launch {
            _notes.value = repository.getLocalNotes()
        }
    }

    fun syncNotes() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _notes.value = repository.syncNotes()
            _isRefreshing.value = false
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun saveNote(title: String, content: String, isPinned: Boolean, existingId: String? = null) {
        if (title.isBlank() && content.isBlank()) return
        viewModelScope.launch {
            try {
                repository.saveNote(title = title, content = content, isPinned = isPinned, existingId = existingId)
            } catch (e: Exception) {
                _error.value = e.message ?: "บันทึกโน้ตไม่สำเร็จ"
            }
            _notes.value = repository.getLocalNotes()
        }
    }

    fun togglePin(note: NoteModel) {
        viewModelScope.launch {
            try { repository.togglePin(note) } catch (e: Exception) { _error.value = e.message ?: "แก้ไขโน้ตไม่สำเร็จ" }
            _notes.value = repository.getLocalNotes()
        }
    }

    fun deleteNote(id: String) {
        viewModelScope.launch {
            try { repository.deleteNote(id) } catch (e: Exception) { _error.value = e.message ?: "ลบโน้ตไม่สำเร็จ" }
            _notes.value = repository.getLocalNotes()
        }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NotesViewModel(OrderApplication.instance.noteRepository) as T
        }
    }
}
