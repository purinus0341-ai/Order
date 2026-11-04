package com.sitticun.orderapp.data.repository

import com.sitticun.orderapp.data.api.SupabaseClient
import com.sitticun.orderapp.data.local.OrderDatabaseHelper
import com.sitticun.orderapp.data.local.PreferenceManager
import com.sitticun.orderapp.data.model.NoteModel
import com.sitticun.orderapp.util.Formatters
import com.sitticun.orderapp.util.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class NoteRepository(
    private val supabaseClient: SupabaseClient,
    private val dbHelper: OrderDatabaseHelper,
    private val preferenceManager: PreferenceManager,
    private val networkMonitor: NetworkMonitor
) {
    suspend fun getLocalNotes(): List<NoteModel> = withContext(Dispatchers.IO) {
        dbHelper.getAllNotes()
    }

    suspend fun syncNotes(): List<NoteModel> = withContext(Dispatchers.IO) {
        if (networkMonitor.isOnline) {
            val res = supabaseClient.getNotes()
            if (res.isSuccess) {
                val remoteNotes = res.getOrDefault(emptyList())
                dbHelper.saveNotes(remoteNotes)
            }
        }
        dbHelper.getAllNotes()
    }

    suspend fun saveNote(
        title: String,
        content: String,
        isPinned: Boolean,
        existingId: String? = null
    ): NoteModel = withContext(Dispatchers.IO) {
        val currentUserId = preferenceManager.userProfile?.userId
        val note = NoteModel(
            id = existingId ?: UUID.randomUUID().toString(),
            title = title.trim().ifBlank { "ไม่มีหัวข้อ" },
            content = content.trim(),
            isPinned = isPinned,
            createdBy = currentUserId,
            updatedAt = Formatters.getCurrentIsoTimestamp()
        )
        dbHelper.upsertNote(note)
        if (networkMonitor.isOnline) {
            supabaseClient.upsertNote(note)
        }
        note
    }

    suspend fun togglePin(note: NoteModel) = withContext(Dispatchers.IO) {
        val updated = note.copy(
            isPinned = !note.isPinned,
            updatedAt = Formatters.getCurrentIsoTimestamp()
        )
        dbHelper.upsertNote(updated)
        if (networkMonitor.isOnline) {
            supabaseClient.upsertNote(updated)
        }
    }

    suspend fun deleteNote(id: String) = withContext(Dispatchers.IO) {
        dbHelper.deleteNote(id)
        if (networkMonitor.isOnline) {
            supabaseClient.deleteNote(id)
        }
    }
}
