package com.sitticun.orderapp.ui.screens.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sitticun.orderapp.data.model.NoteModel
import com.sitticun.orderapp.ui.components.IosNavigationHeader
import com.sitticun.orderapp.ui.components.IosSearchBar
import com.sitticun.orderapp.ui.theme.*
import com.sitticun.orderapp.util.Formatters

@Composable
fun NotesScreen(
    viewModel: NotesViewModel = viewModel(factory = NotesViewModel.Factory())
) {
    val notes by viewModel.filteredNotes.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    var showEditorDialog by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<NoteModel?>(null) }
    var noteToDelete by remember { mutableStateOf<NoteModel?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IosGroupedBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            IosNavigationHeader(
                title = "สมุดโน้ต",
                subtitle = "จดบันทึกร้าน แจ้งเตือนกะถัดไป และรายการกันลืม (${notes.size} รายการ)",
                trailingContent = {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = IosBlueLight,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { viewModel.syncNotes() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    color = IosBlue,
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "ซิงค์",
                                    tint = IosBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = "ซิงค์",
                                color = IosBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                bottomContent = {
                    IosSearchBar(
                        query = searchQuery,
                        onQueryChange = { viewModel.onSearchQueryChange(it) },
                        placeholder = "ค้นหาหัวข้อ หรือเนื้อหาโน้ต...",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )

            if (notes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(IosBlueLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.EditNote,
                                contentDescription = null,
                                tint = IosBlue,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isBlank()) "ยังไม่มีรายการจดโน้ต" else "ไม่พบโน้ตที่ค้นหา",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (searchQuery.isBlank()) "แตะปุ่ม + มุมขวาล่างเพื่อสร้างโน้ตใหม่" else "ลองค้นหาด้วยคำสำคัญอื่น",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 92.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        NoteCardItem(
                            note = note,
                            onClick = {
                                editingNote = note
                                showEditorDialog = true
                            },
                            onTogglePin = { viewModel.togglePin(note) },
                            onDelete = { noteToDelete = note }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                editingNote = null
                showEditorDialog = true
            },
            containerColor = IosBlue,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "เพิ่มโน้ต")
        }
    }

    if (showEditorDialog) {
        NoteEditorDialog(
            initialNote = editingNote,
            onDismiss = { showEditorDialog = false },
            onSave = { title, content, isPinned ->
                viewModel.saveNote(
                    title = title,
                    content = content,
                    isPinned = isPinned,
                    existingId = editingNote?.id
                )
                showEditorDialog = false
            }
        )
    }

    noteToDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text("ยืนยันการลบโน้ต", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
  Text(
      text = "ต้องการลบโน้ต '${note.title}' ใช่หรือไม่?",
      color = TextSecondary
  )
},
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteNote(note.id)
                        noteToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDanger),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("ลบโน้ต", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) {
                    Text("ยกเลิก", color = TextSecondary)
                }
            },
            containerColor = CardWhite,
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@Composable
private fun NoteCardItem(
    note: NoteModel,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (note.isPinned) IosOrangeLight else CardWhite
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (note.isPinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "ปักหมุด",
                            tint = IosOrange,
                            modifier = Modifier
                                .size(17.dp)
                                .padding(end = 4.dp)
                        )
                    }
                    Text(
                        text = note.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onTogglePin,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "ปักหมุด",
                            tint = if (note.isPinned) IosOrange else IosSystemGray2,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "ลบโน้ต",
                            tint = StatusDanger,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            if (note.content.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = note.content,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "อัปเดต: ${Formatters.formatIsoDateTime(note.updatedAt)}",
                fontSize = 11.sp,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun NoteEditorDialog(
    initialNote: NoteModel?,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, isPinned: Boolean) -> Unit
) {
    var title by remember { mutableStateOf(initialNote?.title ?: "") }
    var content by remember { mutableStateOf(initialNote?.content ?: "") }
    var isPinned by remember { mutableStateOf(initialNote?.isPinned ?: false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = if (initialNote == null) "เพิ่มโน้ตใหม่" else "แก้ไขโน้ต",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("หัวข้อโน้ต") },
                    placeholder = { Text("เช่น รายการของต้องสั่งเพิ่ม / แจ้งกะเช้า") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("รายละเอียด") },
                    placeholder = { Text("พิมพ์ข้อความบันทึก...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp, max = 260.dp),
                    maxLines = 10,
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isPinned = !isPinned }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("ปักหมุดไว้ด้านบนสุด", fontSize = 14.sp, color = TextPrimary)
                    Switch(
                        checked = isPinned,
                        onCheckedChange = { isPinned = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = IosOrange,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = IosSystemGray4
                        )
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("ยกเลิก")
                    }

                    Button(
                        onClick = { onSave(title, content, isPinned) },
                        enabled = title.isNotBlank() || content.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = IosBlue),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("บันทึก", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
