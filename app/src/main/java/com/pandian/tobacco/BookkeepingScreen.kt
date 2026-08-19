package com.pandian.tobacco

import android.app.Activity
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val IncomeGreen = Color(0xFF20A454)
private val ExpenseBlue = Color(0xFF2878E8)
private val AccountInk = BrandInk
private val AccountMuted = BrandMuted

@Composable
fun BookkeepingScreen(
    activity: Activity,
    customerStore: CustomerStore,
    customers: MutableList<Customer>,
    modifier: Modifier = Modifier
) {
    val store = remember { AccountingStore(activity) }
    val people = customers.map { customer ->
        AccountPerson(customer.id, customer.name, customer.note, customer.imagePath, customer.createdAt)
    }
    val entries = remember { mutableStateListOf<AccountEntry>().apply { addAll(store.loadEntries()) } }
    var selectedPersonId by remember { mutableStateOf<String?>(people.firstOrNull()?.id) }
    var showPersonDialog by remember { mutableStateOf(false) }
    var editingPerson by remember { mutableStateOf<AccountPerson?>(null) }
    var draftPersonId by remember { mutableStateOf("") }
    var draftImagePath by remember { mutableStateOf<String?>(null) }
    var draggedEntryRequest by remember { mutableStateOf<Pair<AccountPerson, MoneyDirection>?>(null) }
    var showPhotoSource by remember { mutableStateOf(false) }

    fun openAddPerson() {
        editingPerson = null
        draftPersonId = customerStore.newCustomer().id
        draftImagePath = null
        showPersonDialog = true
    }

    fun openEditPerson(person: AccountPerson) {
        editingPerson = person
        draftPersonId = person.id
        draftImagePath = person.imagePath
        showPersonDialog = true
    }

    val today = LocalDate.now()
    val todayEntries = entries.filter {
        Instant.ofEpochMilli(it.createdAt).atZone(ZoneId.systemDefault()).toLocalDate() == today
    }
    val income = todayEntries.filter { it.direction == MoneyDirection.INCOME }.sumOf { it.amount }
    val expense = todayEntries.filter { it.direction == MoneyDirection.EXPENSE }.sumOf { it.amount }

    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val tablet = maxWidth >= 720.dp && maxHeight >= 600.dp
        if (tablet) {
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxSize().widthIn(max = 1180.dp).padding(22.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                PeoplePanel(
                    people, selectedPersonId, true,
                    onSelect = { selectedPersonId = it.id }, onEdit = ::openEditPerson,
                    onAdd = ::openAddPerson, modifier = Modifier.weight(.36f).fillMaxHeight()
                )
                Column(Modifier.weight(.64f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    AccountSummary(income, expense)
                    DragAccountingBoard(people, { openAddPerson() }, { person, direction -> draggedEntryRequest = person to direction })
                    RecordsPanel(entries, true, onDelete = { entry -> store.deleteEntry(entry.id); entries.remove(entry) }, modifier = Modifier.weight(1f))
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { AccountSummary(income, expense) }
                item { DragAccountingBoard(people, { openAddPerson() }, { person, direction -> draggedEntryRequest = person to direction }) }
                item {
                    PeoplePanel(
                        people, selectedPersonId, false,
                        onSelect = { selectedPersonId = it.id }, onEdit = ::openEditPerson,
                        onAdd = ::openAddPerson, modifier = Modifier.fillMaxWidth()
                    )
                }
                item { RecordsPanel(entries, false, onDelete = { entry -> store.deleteEntry(entry.id); entries.remove(entry) }) }
            }
        }
    }

    if (showPersonDialog) {
        PersonDialog(
            person = editingPerson,
            imagePath = draftImagePath,
            onPickImage = { showPhotoSource = true },
            onDismiss = { showPersonDialog = false },
            onSave = { name, note ->
                val previous = customers.firstOrNull { it.id == draftPersonId }
                val customer = Customer(
                    id = draftPersonId,
                    name = name,
                    phone = previous?.phone.orEmpty(),
                    note = note,
                    imagePath = draftImagePath,
                    createdAt = previous?.createdAt ?: System.currentTimeMillis()
                )
                val index = customers.indexOfFirst { it.id == customer.id }
                if (index >= 0) customers[index] = customer else customers.add(customer)
                customerStore.saveCustomers(customers)
                selectedPersonId = customer.id
                showPersonDialog = false
            }
        )
    }

    if (showPhotoSource) {
        PhotoSourceDialog(
            activity = activity,
            onDismiss = { showPhotoSource = false },
            onSelected = { uri ->
                if (draftPersonId.isNotBlank()) {
                    val path = customerStore.saveCustomerImage(draftPersonId, uri)
                    if (path != null) draftImagePath = path
                    else Toast.makeText(activity, "头像保存失败，请重新选择", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    draggedEntryRequest?.let { (person, direction) ->
        QuickEntryDialog(
            person = person,
            direction = direction,
            onDismiss = { draggedEntryRequest = null },
            onSave = { amount, note ->
                val entry = AccountEntry(store.newId(), person.id, person.name, direction, amount, note, System.currentTimeMillis())
                store.addEntry(entry)
                entries.add(0, entry)
                draggedEntryRequest = null
            }
        )
    }
}

@Composable
private fun AccountSummary(income: Double, expense: Double) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SummaryMoneyCard("今日收入", income, IncomeGreen, Modifier.weight(1f))
        SummaryMoneyCard("今日支出", expense, ExpenseBlue, Modifier.weight(1f))
        SummaryMoneyCard("今日结余", income - expense, AccountInk, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryMoneyCard(label: String, amount: Double, color: Color, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = AccountMuted, fontSize = 12.sp, maxLines = 1)
            Text("¥${money(amount)}", color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PeoplePanel(
    people: List<AccountPerson>,
    selectedId: String?,
    vertical: Boolean,
    onSelect: (AccountPerson) -> Unit,
    onEdit: (AccountPerson) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("客户", color = AccountInk, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onAdd) { Icon(Icons.Rounded.Add, null); Text("添加") }
            }
            if (people.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    Text("先添加姓名和头像", color = AccountMuted)
                }
            } else if (vertical) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.weight(1f)) {
                    items(people, key = { it.id }) { person ->
                        PersonCard(person, selectedId == person.id, true, { onSelect(person) }, { onEdit(person) })
                    }
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(people, key = { it.id }) { person ->
                        PersonCard(person, selectedId == person.id, false, { onSelect(person) }, { onEdit(person) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonCard(person: AccountPerson, selected: Boolean, vertical: Boolean, onSelect: () -> Unit, onEdit: () -> Unit) {
    Row(
        Modifier
            .then(if (vertical) Modifier.fillMaxWidth() else Modifier.width(190.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onSelect)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PersonAvatar(person.imagePath, person.name, 46)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(person.name, color = AccountInk, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(person.note.ifBlank { "暂无备注" }, color = AccountMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onClick = onEdit, contentPadding = PaddingValues(4.dp)) { Text("编辑", fontSize = 11.sp) }
    }
}

@Composable
private fun DragAccountingBoard(
    people: List<AccountPerson>,
    onAddPerson: () -> Unit,
    onDrop: (AccountPerson, MoneyDirection) -> Unit
) {
    var boardBounds by remember { mutableStateOf(Rect.Zero) }
    var incomeBounds by remember { mutableStateOf(Rect.Zero) }
    var expenseBounds by remember { mutableStateOf(Rect.Zero) }
    var draggingPerson by remember { mutableStateOf<AccountPerson?>(null) }
    var pointerInRoot by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val previewHalfWidth = with(density) { 82.dp.toPx() }
    val previewHalfHeight = with(density) { 28.dp.toPx() }

    fun finishDrag() {
        val person = draggingPerson
        val direction = when {
            incomeBounds.contains(pointerInRoot) -> MoneyDirection.INCOME
            expenseBounds.contains(pointerInRoot) -> MoneyDirection.EXPENSE
            else -> null
        }
        if (person != null && direction != null) onDrop(person, direction)
        draggingPerson = null
        pointerInRoot = Offset.Zero
    }

    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Box(
            Modifier.fillMaxWidth().height(238.dp).onGloballyPositioned { boardBounds = it.boundsInRoot() }
        ) {
            Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("拖拽记账", color = AccountInk, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text("长按客户，拖到下方收款或付款区域", color = AccountMuted, fontSize = 11.sp)
                    }
                    TextButton(onClick = onAddPerson) { Icon(Icons.Rounded.Add, null); Text("添加客户") }
                }
                if (people.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(62.dp), contentAlignment = Alignment.Center) {
                        Text("请先在这里或客户管理中添加客户", color = AccountMuted)
                    }
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(62.dp)) {
                        items(people, key = { it.id }) { person ->
                            DraggablePersonChip(
                                person = person,
                                onStart = { point -> draggingPerson = person; pointerInRoot = point },
                                onMove = { delta -> pointerInRoot += delta },
                                onEnd = ::finishDrag
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DropZone(
                        MoneyDirection.INCOME,
                        draggingPerson != null && incomeBounds.contains(pointerInRoot),
                        Modifier.weight(1f).fillMaxHeight().onGloballyPositioned { incomeBounds = it.boundsInRoot() }
                    )
                    DropZone(
                        MoneyDirection.EXPENSE,
                        draggingPerson != null && expenseBounds.contains(pointerInRoot),
                        Modifier.weight(1f).fillMaxHeight().onGloballyPositioned { expenseBounds = it.boundsInRoot() }
                    )
                }
            }

            draggingPerson?.let { person ->
                val localX = pointerInRoot.x - boardBounds.left - previewHalfWidth
                val localY = pointerInRoot.y - boardBounds.top - previewHalfHeight
                Row(
                    Modifier
                        .offset { IntOffset(localX.toInt(), localY.toInt()) }
                        .width(164.dp).height(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .padding(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PersonAvatar(person.imagePath, person.name, 40)
                    Spacer(Modifier.width(8.dp))
                    Text(person.name, color = AccountInk, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun DraggablePersonChip(
    person: AccountPerson,
    onStart: (Offset) -> Unit,
    onMove: (Offset) -> Unit,
    onEnd: () -> Unit
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier
            .width(164.dp).height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .pointerInput(person.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { localPoint ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStart(bounds.topLeft + localPoint)
                    },
                    onDrag = { change, dragAmount -> change.consume(); onMove(dragAmount) },
                    onDragEnd = onEnd,
                    onDragCancel = onEnd
                )
            }
            .padding(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PersonAvatar(person.imagePath, person.name, 40)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(person.name, color = AccountInk, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("长按拖动", color = AccountMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun DropZone(direction: MoneyDirection, highlighted: Boolean, modifier: Modifier = Modifier) {
    val color = if (direction == MoneyDirection.INCOME) IncomeGreen else ExpenseBlue
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (highlighted) color.copy(alpha = .18f) else color.copy(alpha = .07f))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(if (direction == MoneyDirection.INCOME) "↓" else "↑", color = color, fontSize = 25.sp, fontWeight = FontWeight.Black)
        Text(direction.title, color = color, fontWeight = FontWeight.Bold)
        Text(if (highlighted) "松手记账" else "拖到这里", color = color.copy(alpha = .75f), fontSize = 10.sp)
    }
}

@Composable
private fun QuickEntryDialog(
    person: AccountPerson,
    direction: MoneyDirection,
    onDismiss: () -> Unit,
    onSave: (Double, String) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val amount = amountText.toDoubleOrNull() ?: 0.0
    val color = if (direction == MoneyDirection.INCOME) IncomeGreen else ExpenseBlue
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${direction.title} · ${person.name}") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PersonAvatar(person.imagePath, person.name, 72)
                Text(if (direction == MoneyDirection.INCOME) "记录收到的钱" else "记录付出的钱", color = color, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    amountText, { amountText = it.filter { char -> char.isDigit() || char == '.' }.take(12) },
                    label = { Text("金额") }, prefix = { Text("¥") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(note, { note = it.take(60) }, label = { Text("备注（选填）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onSave(amount, note.trim()) }, enabled = amount > 0) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun EntryPanel(selectedPerson: AccountPerson?, onSave: (MoneyDirection, Double, String) -> Unit) {
    var direction by remember { mutableStateOf(MoneyDirection.INCOME) }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val amount = amountText.toDoubleOrNull() ?: 0.0

    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AccountBalanceWallet, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("记一笔账", color = AccountInk, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(selectedPerson?.name ?: "请先选择客户", color = if (selectedPerson == null) AccountMuted else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                DirectionButton(MoneyDirection.INCOME, direction == MoneyDirection.INCOME, IncomeGreen, Modifier.weight(1f)) { direction = it }
                DirectionButton(MoneyDirection.EXPENSE, direction == MoneyDirection.EXPENSE, ExpenseBlue, Modifier.weight(1f)) { direction = it }
            }
            OutlinedTextField(
                amountText, { amountText = it.filter { char -> char.isDigit() || char == '.' }.take(12) },
                label = { Text("金额") }, prefix = { Text("¥") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(note, { note = it.take(60) }, label = { Text("备注（选填）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = { onSave(direction, amount, note.trim()); amountText = ""; note = "" },
                enabled = selectedPerson != null && amount > 0,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("保存这笔账") }
        }
    }
}

@Composable
private fun DirectionButton(direction: MoneyDirection, selected: Boolean, color: Color, modifier: Modifier, onClick: (MoneyDirection) -> Unit) {
    if (selected) {
        Button(onClick = { onClick(direction) }, modifier = modifier) { Text(direction.title) }
    } else {
        OutlinedButton(onClick = { onClick(direction) }, modifier = modifier) { Text(direction.title, color = color) }
    }
}

@Composable
private fun RecordsPanel(entries: List<AccountEntry>, scrollable: Boolean, onDelete: (AccountEntry) -> Unit, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Text("记账记录", color = AccountInk, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (entries.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { Text("暂无记账记录", color = AccountMuted) }
            } else if (scrollable) {
                LazyColumn(Modifier.weight(1f)) {
                    items(entries, key = { it.id }) { entry -> EntryRow(entry) { onDelete(entry) } }
                }
            } else {
                entries.take(20).forEach { entry -> EntryRow(entry) { onDelete(entry) } }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: AccountEntry, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (entry.direction == MoneyDirection.INCOME) "↓" else "↑", color = if (entry.direction == MoneyDirection.INCOME) IncomeGreen else ExpenseBlue, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.personName, color = AccountInk, fontWeight = FontWeight.SemiBold)
            Text(entry.note.ifBlank { entry.displayTime }, color = AccountMuted, fontSize = 11.sp, maxLines = 1)
        }
        Text("¥${money(entry.amount)}", color = if (entry.direction == MoneyDirection.INCOME) IncomeGreen else ExpenseBlue, fontWeight = FontWeight.Bold)
        IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, "删除记录", tint = Color(0xFF9AA5B3), modifier = Modifier.size(19.dp)) }
    }
}

@Composable
private fun PersonDialog(
    person: AccountPerson?,
    imagePath: String?,
    onPickImage: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember(person?.id) { mutableStateOf(person?.name ?: "") }
    var note by remember(person?.id) { mutableStateOf(person?.note ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (person == null) "添加客户" else "编辑客户") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PersonAvatar(imagePath, name.ifBlank { "客户" }, 82)
                OutlinedButton(onClick = onPickImage, modifier = Modifier.fillMaxWidth()) { Text(if (imagePath == null) "上传客户头像" else "更换客户头像") }
                OutlinedTextField(name, { name = it.take(20) }, label = { Text("姓名（必填）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it.take(50) }, label = { Text("备注（选填）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onSave(name.trim(), note.trim()) }, enabled = name.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun PersonAvatar(imagePath: String?, name: String, size: Int) {
    val bitmap = remember(imagePath) { imagePath?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() } }
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), "$name 头像", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size((size * .55f).dp))
        }
    }
}
