package com.pandian.tobacco

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private enum class DatePreset(val title: String) {
    TODAY("今天"), SEVEN_DAYS("近7天"), MONTH("本月"), ALL("全部"), CUSTOM("自定义")
}

private val ChartIncome = Color(0xFF20A454)
private val ChartExpense = Color(0xFF2878E8)
private val ChartOrange = BrandGold
private val ChartInk = BrandInk
private val ChartMuted = BrandMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(activity: Activity, orders: List<TradeOrder>, modifier: Modifier = Modifier) {
    val accountingEntries = remember { AccountingStore(activity).loadEntries() }
    val today = LocalDate.now()
    var preset by remember { mutableStateOf(DatePreset.SEVEN_DAYS) }
    var customStart by remember { mutableStateOf(today.minusDays(6)) }
    var customEnd by remember { mutableStateOf(today) }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }

    val (startDate, endDate) = when (preset) {
        DatePreset.TODAY -> today to today
        DatePreset.SEVEN_DAYS -> today.minusDays(6) to today
        DatePreset.MONTH -> today.withDayOfMonth(1) to today
        DatePreset.ALL -> null to null
        DatePreset.CUSTOM -> customStart to customEnd
    }
    fun inRange(time: Long): Boolean {
        val date = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate()
        return (startDate == null || !date.isBefore(startDate)) && (endDate == null || !date.isAfter(endDate))
    }

    val filteredOrders = orders.filter { inRange(it.createdAt) }
    val filteredEntries = accountingEntries.filter { inRange(it.createdAt) }
    val tradeAmount = filteredOrders.sumOf { it.total }
    val tradeQuantity = filteredOrders.sumOf { it.totalQuantity }
    val income = filteredEntries.filter { it.direction == MoneyDirection.INCOME }.sumOf { it.amount }
    val expense = filteredEntries.filter { it.direction == MoneyDirection.EXPENSE }.sumOf { it.amount }
    val brandData = filteredOrders.flatMap { it.lines }
        .groupBy { it.product.name }
        .mapValues { (_, lines) -> lines.sumOf { it.quantity } }
        .toList().sortedByDescending { it.second }.take(8)

    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val tablet = maxWidth >= 720.dp
        LazyColumn(
            Modifier.align(Alignment.TopCenter).fillMaxSize().widthIn(max = 1100.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("统计分析", color = ChartInk, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("按日期查看烟草交易和往来记账", color = ChartMuted, fontSize = 13.sp)
            }
            item {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(DatePreset.entries) { item ->
                                FilterChip(selected = preset == item, onClick = { preset = item }, label = { Text(item.title) })
                            }
                        }
                        if (preset == DatePreset.CUSTOM) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = { pickingStart = true }, modifier = Modifier.weight(1f)) { Text("开始 ${customStart.format(DateTimeFormatter.ISO_DATE)}") }
                                Button(onClick = { pickingEnd = true }, modifier = Modifier.weight(1f)) { Text("结束 ${customEnd.format(DateTimeFormatter.ISO_DATE)}") }
                            }
                        } else {
                            Text(
                                if (startDate == null) "时间范围：全部记录" else "时间范围：$startDate 至 $endDate",
                                color = ChartMuted, fontSize = 12.sp
                            )
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatMetricCard("交易金额", "¥${money(tradeAmount)}", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                        StatMetricCard("烟草数量", "$tradeQuantity 件", ChartOrange, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatMetricCard("记账收入", "¥${money(income)}", ChartIncome, Modifier.weight(1f))
                        StatMetricCard("记账支出", "¥${money(expense)}", ChartExpense, Modifier.weight(1f))
                    }
                }
            }
            item {
                if (tablet) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        IncomeExpensePie(income, expense, Modifier.weight(.85f).height(330.dp))
                        BrandBarChart(brandData, Modifier.weight(1.15f).height(330.dp))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        IncomeExpensePie(income, expense, Modifier.fillMaxWidth().height(290.dp))
                        BrandBarChart(brandData, Modifier.fillMaxWidth().height((150 + brandData.size * 36).coerceAtMost(420).dp))
                    }
                }
            }
        }
    }

    if (pickingStart) {
        val state = rememberDatePickerState(initialSelectedDateMillis = customStart.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickingStart = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val selected = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        customStart = selected
                        if (customEnd.isBefore(selected)) customEnd = selected
                    }
                    pickingStart = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { pickingStart = false }) { Text("取消") } }
        ) { DatePicker(state) }
    }
    if (pickingEnd) {
        val state = rememberDatePickerState(initialSelectedDateMillis = customEnd.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickingEnd = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val selected = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        customEnd = selected
                        if (customStart.isAfter(selected)) customStart = selected
                    }
                    pickingEnd = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { pickingEnd = false }) { Text("取消") } }
        ) { DatePicker(state) }
    }
}

@Composable
private fun StatMetricCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(15.dp)) {
            Text(label, color = ChartMuted, fontSize = 12.sp)
            Text(value, color = color, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun IncomeExpensePie(income: Double, expense: Double, modifier: Modifier = Modifier) {
    val total = income + expense
    val incomeSweep = if (total > 0) (income / total * 360f).toFloat() else 0f
    Card(modifier, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("记账收支构成", color = ChartInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(170.dp)) {
                    if (total <= 0) {
                        drawCircle(Color(0xFFE7ECF2), style = Stroke(width = 28.dp.toPx()))
                    } else {
                        drawArc(ChartIncome, -90f, incomeSweep, false, style = Stroke(28.dp.toPx(), cap = StrokeCap.Butt))
                        drawArc(ChartExpense, -90f + incomeSweep, 360f - incomeSweep, false, style = Stroke(28.dp.toPx(), cap = StrokeCap.Butt))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (total > 0) "¥${money(total)}" else "暂无数据", color = ChartInk, fontWeight = FontWeight.Bold)
                    if (total > 0) Text("总收支", color = ChartMuted, fontSize = 11.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ChartLegend("收入", income, ChartIncome)
                ChartLegend("支出", expense, ChartExpense)
            }
        }
    }
}

@Composable
private fun ChartLegend(label: String, value: Double, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Column {
            Text(label, color = ChartMuted, fontSize = 11.sp)
            Text("¥${money(value)}", color = ChartInk, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BrandBarChart(data: List<Pair<String, Int>>, modifier: Modifier = Modifier) {
    val maximum = data.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    Card(modifier, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("品牌数量排行", color = ChartInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("按筛选时间内的交易件数统计", color = ChartMuted, fontSize = 11.sp)
            if (data.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("当前日期范围暂无烟草交易", color = ChartMuted) }
            } else {
                data.forEachIndexed { index, (name, quantity) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, color = ChartInk, fontSize = 12.sp, modifier = Modifier.width(66.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Box(Modifier.weight(1f).height(14.dp).background(Color(0xFFE9EEF5), RoundedCornerShape(7.dp))) {
                            Box(
                                Modifier.fillMaxWidth(quantity.toFloat() / maximum).height(14.dp)
                                    .background(if (index == 0) ChartOrange else MaterialTheme.colorScheme.primary, RoundedCornerShape(7.dp))
                            )
                        }
                        Text("$quantity 件", color = ChartMuted, fontSize = 11.sp, modifier = Modifier.width(48.dp).padding(start = 7.dp))
                    }
                }
            }
        }
    }
}
