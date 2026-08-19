package com.pandian.tobacco

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File

@Composable
fun PhotoSourceDialog(
    activity: Activity,
    onDismiss: () -> Unit,
    onSelected: (Uri) -> Unit
) {
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onSelected(uri)
        onDismiss()
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) cameraUri?.let(onSelected)
        onDismiss()
    }

    fun openCamera() {
        runCatching {
            val directory = File(activity.cacheDir, "camera_photos").apply { mkdirs() }
            val file = File(directory, "photo_${System.currentTimeMillis()}.jpg")
            FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        }.onSuccess { uri ->
            cameraUri = uri
            camera.launch(uri)
        }.onFailure {
            Toast.makeText(activity, "无法打开相机，请从相册选择", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择照片来源", fontSize = 23.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { openCamera() }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(Icons.Rounded.CameraAlt, null)
                    Text("  拍照", fontSize = 19.sp)
                }
                OutlinedButton(
                    onClick = { filePicker.launch("image/*") },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Rounded.FolderOpen, null)
                    Text("  从相册或文件选择", fontSize = 19.sp)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", fontSize = 17.sp) } }
    )
}
