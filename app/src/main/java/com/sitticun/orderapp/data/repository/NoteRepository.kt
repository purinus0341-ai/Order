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

data class PendingNoteReview(
    val id: String, val operation: String, val owner: String?,
    val local: NoteModel?, val remote: NoteModel?
)

class NoteRepository(
    private val supabaseClient: SupabaseClient,
    private val dbHelper: OrderDatabaseHelper,
    private val preferenceManager: PreferenceManager,
    private val networkMonitor: NetworkMonitor
) {
    private val mutex = Mutex()
    private val _syncError = MutableStateFlow<String?>(null)
    val syncError = _syncError.asStateFlow()
    private val _pendingTasks = MutableStateFlow(dbHelper.pendingNotes())
    val pendingTasks = _pendingTasks.asStateFlow()
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
        _pendingTasks.value = pending
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

    suspend fun reviewPendingNote(id: String): PendingNoteReview = withContext(Dispatchers.IO) {
        mutex.withLock {
            val owner = currentOwner()
            check(networkMonitor.isOnline) { "กรุณาเชื่อมต่ออินเทอร์เน็ตก่อนตรวจสอบ" }
            val task = dbHelper.pendingNotes()[id] ?: error("รายการนี้ไม่ได้รอซิงก์แล้ว")
            check(task.second == null || task.second == owner) { "รายการนี้เป็นของบัญชีอื่น กรุณาเข้าสู่ระบบด้วยบัญชีเจ้าของ" }
            val local = dbHelper.getAllNotes().firstOrNull { it.id == id }
            val remote = supabaseClient.getNote(id).getOrThrow()
            check(currentOwner() == owner) { "บัญชีผู้ใช้เปลี่ยนระหว่างตรวจสอบ กรุณาลองใหม่" }
            PendingNoteReview(id, task.first, task.second, local, remote)
        }
    }

    suspend fun resolvePendingNote(review: PendingNoteReview, keepCopy: Boolean) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val owner = currentOwner()
            check(networkMonitor.isOnline) { "กรุณาเชื่อมต่ออินเทอร์เน็ตก่อนจัดการ" }
            val task = dbHelper.pendingNotes()[review.id] ?: error("รายการนี้ไม่ได้รอซิงก์แล้ว")
            check(task.first == review.operation && task.second == review.owner && (task.second == null || task.second == owner)) { "รายการเปลี่ยนไป กรุณาตรวจสอบใหม่" }
            val local = dbHelper.getAllNotes().firstOrNull { it.id == review.id }
            check(local == review.local) { "ข้อมูลในเครื่องเปลี่ยนไป กรุณาตรวจสอบใหม่" }
            val remote = supabaseClient.getNote(review.id).getOrThrow()
            check(remote == review.remote && currentOwner() == owner) { "ข้อมูลบนคลาวด์หรือบัญชีเปลี่ยนไป กรุณาตรวจสอบใหม่" }
            check(!keepCopy || local != null) { "ไม่มีเนื้อหาในเครื่องให้เก็บเป็นสำเนา" }
            dbHelper.inTransaction {
                if (keepCopy && local != null) {
                    val copy = local.copy(id = UUID.randomUUID().toString(), createdBy = owner, revision = null, updatedAt = Formatters.getCurrentIsoTimestamp())
                    dbHelper.upsertNote(copy)
                    dbHelper.queueNote(copy.id, "upsert", owner, 0L)
                }
                if (remote == null) dbHelper.deleteNote(review.id) else dbHelper.upsertNote(remote)
                dbHelper.clearPendingNote(review.id)
            }
            refreshPendingCount()
        }
        if (networkMonitor.isOnline) syncNotes()
    }

    suspend fun getLocalNotes(): List<NoteModel> = withContext(Dispatchers.IO) { dbHelper.getAllNotes() }

    suspend fun syncNotes(): List<NoteModel> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val owner = preferenceManager.userProfile?.userId
            var failedOperations = 0
            if (networkMonitor.isOnline && preferenceManager.isLoggedIn && !owner.isNullOrBlank()) {
                val local = dbHelper.getAllNotes().associateBy { it.id }
                val bases = dbHelper.pendingNoteBases()
                for ((id, task) in dbHelper.pendingNotes()) {
                    if (task.second != owner || preferenceManager.userProfile?.userId != owner || !preferenceManager.isLoggedIn) continue
                    val base = bases[id] ?: continue
                    val desired = local[id]
                    val remoteResult = supabaseClient.getNote(id)
                    if (remoteResult.isFailure) { failedOperations++; continue }
                    val remote = remoteResult.getOrNull()
                    val alreadyApplied = when (task.first) {
                        "delete" -> remote == null
                        "upsert" -> desired != null && remote != null && desired.id == remote.id &&
                            desired.title == remote.title && desired.content == remote.content &&
                            desired.isPinned == remote.isPinned && desired.createdBy == remote.createdBy
                        else -> false
                    }
                    if (alreadyApplied) {
                        dbHelper.inTransaction { if (remote != null) dbHelper.upsertNote(remote); dbHelper.clearPendingNote(id) }
                        continue
                    }
                    if ((base == 0L && remote != null) || (base != 0L && remote == null)) { failedOperations++; continue }
                    val result = when (task.first) {
                        "delete" -> if (base == 0L) null else supabaseClient.writeNoteIfUnchanged(id, "delete", null, base)
                        "upsert" -> desired?.let { supabaseClient.writeNoteIfUnchanged(id, "upsert", it, base) }
                        else -> null
                    }
                    if (result?.getOrNull() == true) {
                        if (task.first == "delete") dbHelper.clearPendingNote(id)
                        else {
                            val confirmed = supabaseClient.getNote(id).getOrNull()
                            if (confirmed != null && desired != null && confirmed.title == desired.title &&
                                confirmed.content == desired.content && confirmed.isPinned == desired.isPinned && confirmed.createdBy == desired.createdBy) {
                                dbHelper.inTransaction { dbHelper.upsertNote(confirmed); dbHelper.clearPendingNote(id) }
                            } else failedOperations++
                        }
                    } else failedOperations++
                }
                val pending = dbHelper.pendingNotes()
                val remote = supabaseClient.getNotes()
                if (remote.isSuccess) {
                    val remoteNotes = remote.getOrThrow()
                    val remoteIds = remoteNotes.mapTo(mutableSetOf()) { it.id }
                    val confirmedMissing = mutableSetOf<String>()
                    for (note in dbHelper.getAllNotes()) {
                        if (note.id in pending || note.id in remoteIds) continue
                        val confirmed = supabaseClient.getNote(note.id)
                        if (confirmed.isSuccess && confirmed.getOrNull() == null) confirmedMissing.add(note.id)
                        else if (confirmed.isFailure) failedOperations++
                    }
                    dbHelper.reconcileNotes(remoteNotes, confirmedMissing)
                    _syncError.value = if (failedOperations > 0) "บางรายการยังซิงก์ไม่ได้หรือข้อมูลบนคลาวด์เปลี่ยนไป กรุณาตรวจสอบการเชื่อมต่อและติดต่อผู้ดูแล" else null
                } else {
                    val detail = remote.exceptionOrNull()?.message.orEmpty()
                    _syncError.value = if (detail.contains("PGRST205", true) || detail.contains("42P01", true) || detail.contains("schema cache", true))
                        "ไม่พบตารางโน้ตบนเซิร์ฟเวอร์ โน้ตยังอยู่ในเครื่อง กรุณาติดต่อผู้ดูแล"
                    else "โหลดโน้ตจากเซิร์ฟเวอร์ไม่สำเร็จ โน้ตยังอยู่ในเครื่อง กรุณาตรวจสอบการเชื่อมต่อหรือสิทธิ์"
                }
            } else if (!networkMonitor.isOnline) {
                _syncError.value = "ออฟไลน์: โน้ตใหม่และการแก้ไขจะรอซิงก์"
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
                updatedAt = Formatters.getCurrentIsoTimestamp(), revision = existing?.revision
            )
            dbHelper.inTransaction { dbHelper.upsertNote(updated); dbHelper.queueNote(updated.id, "upsert", owner, existing?.revision ?: if (existing == null) 0L else null) }
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
            dbHelper.inTransaction { dbHelper.upsertNote(updated); dbHelper.queueNote(updated.id, "upsert", owner, current.revision) }
            refreshPendingCount()
        }
        if (networkMonitor.isOnline) syncNotes()
    }

    suspend fun deleteNote(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val owner = currentOwner()
            checkPendingOwner(id, owner)
            val current = dbHelper.getAllNotes().firstOrNull { it.id == id }
            dbHelper.inTransaction { dbHelper.deleteNote(id); dbHelper.queueNote(id, "delete", owner, current?.revision) }
            refreshPendingCount()
        }
        if (networkMonitor.isOnline) syncNotes()
    }
}
