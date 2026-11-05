package com.sitticun.orderapp.data.repository

import com.sitticun.orderapp.data.api.SupabaseClient
import com.sitticun.orderapp.data.local.OrderDatabaseHelper
import com.sitticun.orderapp.data.local.PreferenceManager
import com.sitticun.orderapp.data.model.NoteModel
import com.sitticun.orderapp.util.Formatters
import com.sitticun.orderapp.util.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

class NoteRepository(
    private val supabaseClient: SupabaseClient,
    private val dbHelper: OrderDatabaseHelper,
    private val preferenceManager: PreferenceManager,
    private val networkMonitor: NetworkMonitor
) {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            networkMonitor.isOnlineFlow.collect { online -> if (online) syncNotes() }
        }
    }

    suspend fun getLocalNotes(): List<NoteModel> = withContext(Dispatchers.IO) { dbHelper.getAllNotes() }

    suspend fun syncNotes(): List<NoteModel> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (networkMonitor.isOnline) {
                val local = dbHelper.getAllNotes().associateBy { it.id }
                for ((id, operation) in dbHelper.pendingNotes()) {
                    val result = when (operation) {
                        "delete" -> supabaseClient.deleteNote(id)
                        "upsert" -> local[id]?.let { supabaseClient.upsertNote(it) }
                        else -> null
                    }
                    if (result?.isSuccess == true) dbHelper.clearPendingNote(id)
                }
                val pending = dbHelper.pendingNotes()
                val remote = supabaseClient.getNotes()
                if (remote.isSuccess) dbHelper.saveNotes(remote.getOrThrow().filter { it.id !in pending })
            }
            dbHelper.getAllNotes()
        }
    }

    suspend fun saveNote(title: String, content: String, isPinned: Boolean, existingId: String? = null): NoteModel = withContext(Dispatchers.IO) {
        val note = mutex.withLock {
            val existing = dbHelper.getAllNotes().firstOrNull { it.id == existingId }
            val updated = NoteModel(
                id = existingId ?: UUID.randomUUID().toString(),
                title = title.trim().ifBlank { "ไม่มีหัวข้อ" }, content = content.trim(), isPinned = isPinned,
                createdBy = existing?.createdBy ?: preferenceManager.userProfile?.userId,
                updatedAt = Formatters.getCurrentIsoTimestamp()
            )
            dbHelper.inTransaction { dbHelper.upsertNote(updated); dbHelper.queueNote(updated.id, "upsert") }
            updated
        }
        if (networkMonitor.isOnline) syncNotes()
        note
    }

    suspend fun togglePin(note: NoteModel) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = dbHelper.getAllNotes().firstOrNull { it.id == note.id } ?: return@withLock
            val updated = current.copy(isPinned = !current.isPinned, updatedAt = Formatters.getCurrentIsoTimestamp())
            dbHelper.inTransaction { dbHelper.upsertNote(updated); dbHelper.queueNote(updated.id, "upsert") }
        }
        if (networkMonitor.isOnline) syncNotes()
    }

    suspend fun deleteNote(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock { dbHelper.inTransaction { dbHelper.deleteNote(id); dbHelper.queueNote(id, "delete") } }
        if (networkMonitor.isOnline) syncNotes()
    }
}
