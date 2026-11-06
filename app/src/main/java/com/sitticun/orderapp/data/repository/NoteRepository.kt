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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val _pendingCount = MutableStateFlow(dbHelper.pendingNotes().size)
    val pendingCount = _pendingCount.asStateFlow()
    private val _unknownOwnerCount = MutableStateFlow(dbHelper.pendingNotes().count { it.value.second == null })
    val unknownOwnerCount = _unknownOwnerCount.asStateFlow()
    private val _unknownOwnerTasks = MutableStateFlow(dbHelper.pendingNotes().filterValues { it.second == null }.mapValues { it.value.first })
    val unknownOwnerTasks = _unknownOwnerTasks.asStateFlow()
    private val _unverifiedBaseCount = MutableStateFlow(dbHelper.pendingNoteBases().count { it.value == null })
    val unverifiedBaseCount = _unverifiedBaseCount.asStateFlow()
    private fun refreshPendingCount() {
        val pending = dbHelper.pendingNotes()
        _pendingCount.value = pending.size
        _unknownOwnerCount.value = pending.count { it.value.second == null }
        _unknownOwnerTasks.value = pending.filterValues { it.second == null }.mapValues { it.value.first }
        _unverifiedBaseCount.value = dbHelper.pendingNoteBases().count { it.value == null }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            networkMonitor.isOnlineFlow.collect { online -> if (online) syncNotes() }
        }
    }

    private fun currentOwner(): String {
        check(preferenceManager.isLoggedIn) { "กรุณาเข้าสู่ระบบก่อนแก้ไขโน้ต" }
        return preferenceManager.userProfile?.userId?.takeIf { it.isNotBlank() }
            ?: error("ไม่พบข้อมูลบัญชีผู้ใช้")
    }

    private fun checkPendingOwner(id: String, owner: String) {
        val pending = dbHelper.pendingNotes()[id]
        check(pending == null || pending.second == owner) { "โน้ตนี้มีรายการรอซิงก์จากบัญชีอื่นหรือไม่ทราบเจ้าของ กรุณาติดต่อผู้ดูแล" }
    }

    suspend fun getLocalNotes(): List<NoteModel> = withContext(Dispatchers.IO) { dbHelper.getAllNotes() }

    suspend fun syncNotes(): List<NoteModel> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val owner = preferenceManager.userProfile?.userId
            if (networkMonitor.isOnline && preferenceManager.isLoggedIn && !owner.isNullOrBlank()) {
                val local = dbHelper.getAllNotes().associateBy { it.id }
                val bases = dbHelper.pendingNoteBases()
                for ((id, task) in dbHelper.pendingNotes()) {
                    if (task.second != owner || preferenceManager.userProfile?.userId != owner || !preferenceManager.isLoggedIn) continue
                    val base = bases[id] ?: continue
                    val desired = local[id]
                    val remoteResult = supabaseClient.getNote(id)
                    if (remoteResult.isFailure) continue
                    val remote = remoteResult.getOrNull()
                    val alreadyApplied = when (task.first) {
                        "delete" -> remote == null
                        "upsert" -> desired != null && remote != null && desired.id == remote.id &&
                            desired.title == remote.title && desired.content == remote.content &&
                            desired.isPinned == remote.isPinned && desired.createdBy == remote.createdBy
                        else -> false
                    }
                    if (alreadyApplied) { dbHelper.clearPendingNote(id); continue }
                    if ((base.isEmpty() && remote != null) || (base.isNotEmpty() && remote == null)) continue
                    val result = when (task.first) {
                        "delete" -> if (base.isEmpty()) null else supabaseClient.writeNoteIfUnchanged(id, "delete", null, base)
                        "upsert" -> desired?.let { supabaseClient.writeNoteIfUnchanged(id, "upsert", it, base) }
                        else -> null
                    }
                    if (result?.getOrNull() == true) dbHelper.clearPendingNote(id)
                }
                val pending = dbHelper.pendingNotes()
                val remote = supabaseClient.getNotes()
                if (remote.isSuccess) dbHelper.saveNotes(remote.getOrThrow().filter { it.id !in pending })
            }
            refreshPendingCount()
            dbHelper.getAllNotes()
        }
    }

    suspend fun saveNote(title: String, content: String, isPinned: Boolean, existingId: String? = null): NoteModel = withContext(Dispatchers.IO) {
        val note = mutex.withLock {
            val owner = currentOwner()
            if (existingId != null) checkPendingOwner(existingId, owner)
            val existing = dbHelper.getAllNotes().firstOrNull { it.id == existingId }
            val updated = NoteModel(
                id = existingId ?: UUID.randomUUID().toString(),
                title = title.trim().ifBlank { "ไม่มีหัวข้อ" }, content = content.trim(), isPinned = isPinned,
                createdBy = existing?.createdBy ?: preferenceManager.userProfile?.userId,
                updatedAt = Formatters.getCurrentIsoTimestamp()
            )
            dbHelper.inTransaction { dbHelper.upsertNote(updated); dbHelper.queueNote(updated.id, "upsert", owner, existing?.updatedAt?.takeIf { it.isNotBlank() } ?: if (existing == null) "" else null) }
            refreshPendingCount()
            updated
        }
        if (networkMonitor.isOnline) syncNotes()
        note
    }

    suspend fun togglePin(note: NoteModel) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val owner = currentOwner()
            checkPendingOwner(note.id, owner)
            val current = dbHelper.getAllNotes().firstOrNull { it.id == note.id } ?: return@withLock
            val updated = current.copy(isPinned = !current.isPinned, updatedAt = Formatters.getCurrentIsoTimestamp())
            dbHelper.inTransaction { dbHelper.upsertNote(updated); dbHelper.queueNote(updated.id, "upsert", owner, current.updatedAt.takeIf { it.isNotBlank() }) }
            refreshPendingCount()
        }
        if (networkMonitor.isOnline) syncNotes()
    }

    suspend fun deleteNote(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val owner = currentOwner()
            checkPendingOwner(id, owner)
            val current = dbHelper.getAllNotes().firstOrNull { it.id == id }
            dbHelper.inTransaction { dbHelper.deleteNote(id); dbHelper.queueNote(id, "delete", owner, current?.updatedAt?.takeIf { it.isNotBlank() }) }
            refreshPendingCount()
        }
        if (networkMonitor.isOnline) syncNotes()
    }
}
