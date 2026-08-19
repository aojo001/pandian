package com.pandian.tobacco

import android.app.Activity
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CustomerManagementScreen(
    activity: Activity,
    store: CustomerStore,
    customers: MutableList<Customer>,
    modifier: Modifier = Modifier
) {
    var search by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Customer?>(null) }
    val tablet = LocalConfiguration.current.screenWidthDp >= 700
    val visible = customers.filter { customer ->
        val keyword = search.trim()
        customer.name.contains(keyword, true) || customer.phone.contains(keyword) || customer.note.contains(keyword, true)
    }

    Column(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = if (tablet) 24.dp else 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("固定客户", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("姓名必填，其他资料选填", fontSize = 17.sp, color = MaterialTheme.colorScheme.secondary)
                }
                Button(onClick = { editing = store.newCustomer() }, modifier = Modifier.height(54.dp)) {
                    Text("＋ 添加客户", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            OutlinedTextField(
                value = search,
                onValueChange = { search = it.take(30) },
                label = { Text("搜索姓名、电话或备注") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (customers.isEmpty()) "还没有客户，点击右上角添加" else "没有匹配的客户",
                    fontSize = 18.sp, color = MaterialTheme.colorScheme.secondary
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (tablet) 300.dp else 260.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(if (tablet) 24.dp else 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(visible, key = { it.id }) { customer -> CustomerCard(customer) { editing = customer } }
            }
        }
    }

    editing?.let { customer ->
        CustomerEditDialog(
            activity = activity,
            store = store,
            initial = customer,
            isNew = customers.none { it.id == customer.id },
            onDismiss = { editing = null },
            onSave = { saved ->
                val index = customers.indexOfFirst { it.id == saved.id }
                if (index >= 0) customers[index] = saved else customers.add(saved)
                store.saveCustomers(customers)
                editing = null
                Toast.makeText(activity, "客户资料已保存", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun CustomerCard(customer: Customer, onEdit: () -> Unit) {
    Card(
        onClick = onEdit,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            CustomerAvatar(customer.imagePath, customer.name, 72)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(customer.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (customer.phone.isNotBlank()) Text(customer.phone, fontSize = 17.sp, color = MaterialTheme.colorScheme.secondary)
                if (customer.note.isNotBlank()) Text(customer.note, fontSize = 16.sp, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("编辑", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CustomerEditDialog(
    activity: Activity,
    store: CustomerStore,
    initial: Customer,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Customer) -> Unit
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var phone by remember(initial.id) { mutableStateOf(initial.phone) }
    var note by remember(initial.id) { mutableStateOf(initial.note) }
    var imagePath by remember(initial.id) { mutableStateOf(initial.imagePath) }
    var showPhotoSource by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "添加客户" else "编辑客户", fontSize = 24.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CustomerAvatar(imagePath, name.ifBlank { "客户" }, 88)
                    Spacer(Modifier.width(14.dp))
                    OutlinedButton(onClick = { showPhotoSource = true }, modifier = Modifier.weight(1f).height(50.dp)) {
                        Text(if (imagePath == null) "上传客户头像" else "更换客户头像", fontSize = 16.sp)
                    }
                }
                OutlinedTextField(name, { name = it.take(30) }, label = { Text("姓名") }, textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it.filter { char -> char.isDigit() || char == '-' || char == ' ' }.take(24) }, label = { Text("电话（选填）") }, textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it.take(60) }, label = { Text("备注（选填）") }, textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(enabled = name.isNotBlank(), onClick = { onSave(initial.copy(name = name.trim(), phone = phone.trim(), note = note.trim(), imagePath = imagePath)) }) {
                Text("保存客户", fontSize = 17.sp)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", fontSize = 17.sp) } }
    )

    if (showPhotoSource) {
        PhotoSourceDialog(
            activity = activity,
            onDismiss = { showPhotoSource = false },
            onSelected = { uri ->
                val saved = store.saveCustomerImage(initial.id, uri)
                if (saved == null) Toast.makeText(activity, "头像保存失败，请重新选择", Toast.LENGTH_SHORT).show()
                else imagePath = saved
            }
        )
    }
}

@Composable
private fun CustomerAvatar(imagePath: String?, name: String, size: Int) {
    val bitmap = remember(imagePath) { imagePath?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() } }
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), "$name 头像", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Rounded.Person, "$name 默认头像", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size((size * .58).dp))
        }
    }
}
