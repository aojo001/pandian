package com.pandian.tobacco

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch

@Composable
fun CategoryManagementScreen(
    activity: Activity,
    store: LedgerStore,
    products: MutableList<Product>,
    categories: MutableList<String>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var draggingName by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("分类管理", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("长按分类并拖动，可调整显示顺序", color = MaterialTheme.colorScheme.secondary, fontSize = 15.sp)
            }
            Button(onClick = { showAdd = true }, modifier = Modifier.height(48.dp)) {
                Text("＋ 新增分类", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(categories, key = { it }) { category ->
                val isDragging = draggingName == category
                val productCount = products.count { it.category.ifBlank { "未分类" } == category }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            if (isDragging) {
                                translationY = dragOffsetY
                                scaleX = 1.02f
                                scaleY = 1.02f
                                shadowElevation = 16.dp.toPx()
                            }
                        }
                        .pointerInput(category) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { draggingName = category; dragOffsetY = 0f },
                                onDragCancel = { draggingName = null; dragOffsetY = 0f },
                                onDragEnd = {
                                    draggingName = null
                                    dragOffsetY = 0f
                                    store.saveCategories(categories)
                                    Toast.makeText(activity, "分类顺序已保存", Toast.LENGTH_SHORT).show()
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffsetY += amount.y
                                    val layout = listState.layoutInfo
                                    val dragged = layout.visibleItemsInfo.firstOrNull { it.key == category }
                                    if (dragged != null) {
                                        val centerY = dragged.offset + dragged.size / 2f + dragOffsetY
                                        val target = layout.visibleItemsInfo.firstOrNull {
                                            it.key != category && centerY >= it.offset && centerY <= it.offset + it.size
                                        }
                                        val targetName = target?.key as? String
                                        if (target != null && targetName != null) {
                                            val from = categories.indexOf(category)
                                            val to = categories.indexOf(targetName)
                                            if (from >= 0 && to >= 0 && from != to) {
                                                dragOffsetY -= (target.offset - dragged.offset)
                                                categories.add(to, categories.removeAt(from))
                                            }
                                        }
                                        val edge = 72.dp.toPx()
                                        when {
                                            centerY < layout.viewportStartOffset + edge -> scope.launch { listState.scrollBy(-42f) }
                                            centerY > layout.viewportEndOffset - edge -> scope.launch { listState.scrollBy(42f) }
                                        }
                                    }
                                }
                            )
                        },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(42.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${categories.indexOf(category) + 1}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(category, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("$productCount 款烟${if (category in DefaultProductCategories) " · 内置分类" else ""}", color = MaterialTheme.colorScheme.secondary)
                        }
                        if (category !in DefaultProductCategories) {
                            TextButton(onClick = { pendingDelete = category }) {
                                Text("删除", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Text("≡", fontSize = 26.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddCategoryDialog(
            onDismiss = { showAdd = false },
            onSave = { name ->
                if (categories.any { it.equals(name, true) }) {
                    Toast.makeText(activity, "这个分类已经存在", Toast.LENGTH_SHORT).show()
                } else {
                    categories.add(name)
                    store.saveCategories(categories)
                    showAdd = false
                    Toast.makeText(activity, "分类已添加", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    pendingDelete?.let { category ->
        val count = products.count { it.category == category }
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除分类“$category”？", fontWeight = FontWeight.Bold) },
            text = { Text("该分类下有 $count 款烟。删除后会移动到“未分类”，商品资料不会丢失。") },
            confirmButton = {
                Button(onClick = {
                    products.indices.forEach { index ->
                        if (products[index].category == category) products[index] = products[index].copy(category = "未分类")
                    }
                    categories.remove(category)
                    store.saveProducts(products)
                    store.saveCategories(categories)
                    pendingDelete = null
                    Toast.makeText(activity, "分类已删除", Toast.LENGTH_SHORT).show()
                }) { Text("确认删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun AddCategoryDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增分类", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(12) },
                label = { Text("分类名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { Button(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
