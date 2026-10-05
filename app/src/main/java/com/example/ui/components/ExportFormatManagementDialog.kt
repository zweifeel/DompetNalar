package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.ExportFormatConfig
import com.example.ui.theme.FinSmartPrimary
import com.example.util.ExportFormatManager
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportFormatManagementDialog(
    onDismiss: () -> Unit,
    onFormatSelected: (ExportFormatConfig) -> Unit = {}
) {
    val context = LocalContext.current
    var formats by remember { mutableStateOf(ExportFormatManager.getAllFormats(context)) }
    var activeFormatId by remember { mutableStateOf(ExportFormatManager.getActiveFormatId(context)) }

    // Dialog state for adding/editing custom format
    var editingFormat by remember { mutableStateOf<ExportFormatConfig?>(null) }
    var isEditorOpen by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 680.dp)
                .padding(16.dp)
                .testTag("export_format_management_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(FinSmartPrimary.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = FinSmartPrimary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Kelola Format Ekspor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Atur format bawaan & kustom laporan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        editingFormat = null
                        isEditorOpen = true
                    },
                    modifier = Modifier.fillMaxWidth().testTag("btn_add_custom_export_format"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FinSmartPrimary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tambah Format Kustom Baru", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(formats, key = { it.id }) { format ->
                        val isSelected = format.id == activeFormatId
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    activeFormatId = format.id
                                    ExportFormatManager.setActiveFormatId(context, format.id)
                                    onFormatSelected(format)
                                    Toast.makeText(context, "Format aktif: ${format.name}", Toast.LENGTH_SHORT).show()
                                },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) FinSmartPrimary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, FinSmartPrimary) else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (format.type == "PDF") Icons.Default.Print else Icons.Default.GridOn,
                                        contentDescription = null,
                                        tint = if (isSelected) FinSmartPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = format.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = if (format.isBuiltIn) Color(0xFF6B7280).copy(alpha = 0.2f) else FinSmartPrimary.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = if (format.isBuiltIn) "Bawaan" else "Kustom",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (format.isBuiltIn) Color(0xFF374151) else FinSmartPrimary,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val details = if (format.type == "PDF") {
                                            "PDF Formal • ${format.columns.size} Kolom"
                                        } else {
                                            "CSV • Delimiter '${format.delimiter}' • ${format.columns.size} Kolom"
                                        }
                                        Text(text = details, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isSelected) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Aktif", tint = FinSmartPrimary, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }

                                    if (!format.isBuiltIn) {
                                        IconButton(
                                            onClick = {
                                                editingFormat = format
                                                isEditorOpen = true
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp), tint = FinSmartPrimary)
                                        }

                                        IconButton(
                                            onClick = {
                                                ExportFormatManager.deleteFormat(context, format.id)
                                                formats = ExportFormatManager.getAllFormats(context)
                                                activeFormatId = ExportFormatManager.getActiveFormatId(context)
                                                Toast.makeText(context, "Format dihapus", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Hapus", modifier = Modifier.size(16.dp), tint = Color(0xFFDC2626))
                                        }
                                    } else {
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = "Format bawaan dilindungi",
                                            modifier = Modifier.size(16.dp).padding(4.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Tutup")
                }
            }
        }
    }

    if (isEditorOpen) {
        EditExportFormatDialog(
            initialFormat = editingFormat,
            onDismiss = { isEditorOpen = false },
            onSave = { newFormat ->
                ExportFormatManager.saveFormat(context, newFormat)
                formats = ExportFormatManager.getAllFormats(context)
                isEditorOpen = false
                Toast.makeText(context, "Format berhasil disimpan", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditExportFormatDialog(
    initialFormat: ExportFormatConfig?,
    onDismiss: () -> Unit,
    onSave: (ExportFormatConfig) -> Unit
) {
    var name by remember { mutableStateOf(initialFormat?.name ?: "") }
    var delimiter by remember { mutableStateOf(initialFormat?.delimiter ?: ",") }
    var dateFormat by remember { mutableStateOf(initialFormat?.dateFormat ?: "yyyy-MM-dd HH:mm:ss") }
    var includeHeader by remember { mutableStateOf(initialFormat?.includeHeader ?: true) }

    val selectedColumns = remember {
        mutableStateListOf<String>().apply {
            addAll(initialFormat?.columns ?: ExportFormatConfig.DEFAULT_COLUMNS)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 640.dp)
                .padding(16.dp)
                .testTag("edit_export_format_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = if (initialFormat == null) "Tambah Format Kustom" else "Edit Format Ekspor",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Nama Format") },
                            placeholder = { Text("Contoh: CSV Analisis Akuntansi") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }

                    item {
                        Text("Pemisah Nilai (Delimiter):", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "," to "Koma (,)",
                                ";" to "Titik Koma (;)",
                                "\t" to "Tab (\\t)",
                                "|" to "Garis (|)"
                            ).forEach { (delim, label) ->
                                val isSel = delimiter == delim
                                OutlinedButton(
                                    onClick = { delimiter = delim },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isSel) FinSmartPrimary.copy(alpha = 0.12f) else Color.Transparent
                                    ),
                                    border = if (isSel) androidx.compose.foundation.BorderStroke(1.5.dp, FinSmartPrimary) else null,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(label, fontSize = 11.sp, color = if (isSel) FinSmartPrimary else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }

                    item {
                        Text("Format Tanggal:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "yyyy-MM-dd HH:mm:ss",
                                "dd-MM-yyyy HH:mm",
                                "dd/MM/yyyy"
                            ).forEach { df ->
                                val isSel = dateFormat == df
                                OutlinedButton(
                                    onClick = { dateFormat = df },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isSel) FinSmartPrimary.copy(alpha = 0.12f) else Color.Transparent
                                    ),
                                    border = if (isSel) androidx.compose.foundation.BorderStroke(1.5.dp, FinSmartPrimary) else null,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(df, fontSize = 10.sp, color = if (isSel) FinSmartPrimary else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }

                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { includeHeader = !includeHeader }
                        ) {
                            Checkbox(
                                checked = includeHeader,
                                onCheckedChange = { includeHeader = it },
                                colors = CheckboxDefaults.colors(checkedColor = FinSmartPrimary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sertakan baris header pada baris pertama", fontSize = 12.sp)
                        }
                    }

                    item {
                        Text("Pilih Kolom Data:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ExportFormatConfig.ALL_AVAILABLE_COLUMNS.forEach { col ->
                                val isColChecked = selectedColumns.contains(col)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(
                                            if (isColChecked) FinSmartPrimary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            if (isColChecked) {
                                                if (selectedColumns.size > 1) selectedColumns.remove(col)
                                            } else {
                                                selectedColumns.add(col)
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Checkbox(
                                        checked = isColChecked,
                                        onCheckedChange = { checked ->
                                            if (checked) {
                                                selectedColumns.add(col)
                                            } else if (selectedColumns.size > 1) {
                                                selectedColumns.remove(col)
                                            }
                                        },
                                        modifier = Modifier.size(20.dp),
                                        colors = CheckboxDefaults.colors(checkedColor = FinSmartPrimary)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(col, fontSize = 11.sp, fontWeight = if (isColChecked) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Batal")
                    }
                    Button(
                        onClick = {
                            if (name.isBlank()) {
                                return@Button
                            }
                            val format = ExportFormatConfig(
                                id = initialFormat?.id ?: "custom_${UUID.randomUUID().toString().take(8)}",
                                name = name.trim(),
                                type = "CSV",
                                isBuiltIn = false,
                                delimiter = delimiter,
                                dateFormat = dateFormat,
                                columns = selectedColumns.toList(),
                                includeHeader = includeHeader
                            )
                            onSave(format)
                        },
                        modifier = Modifier.weight(1f).testTag("btn_save_export_format"),
                        enabled = name.isNotBlank() && selectedColumns.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FinSmartPrimary)
                    ) {
                        Text("Simpan", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
