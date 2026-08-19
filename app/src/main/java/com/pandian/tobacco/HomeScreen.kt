package com.pandian.tobacco

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class HomeAction(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val page: AppPage,
    val colors: List<Color>
)

private val simpleActions = listOf(
    HomeAction("登记入库", "只记录烟草和数量", Icons.Rounded.Inventory2, AppPage.INTAKE, listOf(BrandBurgundyDark, BrandBurgundy)),
    HomeAction("结算", "给待结算烟草填写价格", Icons.Rounded.Payments, AppPage.SETTLEMENT, listOf(Color(0xFF9C6813), BrandGold))
)

private val managementActions = listOf(
    HomeAction("登记入库", "品类和数量", Icons.Rounded.Inventory2, AppPage.INTAKE, listOf(BrandBurgundyDark, BrandBurgundy)),
    HomeAction("结算", "填写价格", Icons.Rounded.Payments, AppPage.SETTLEMENT, listOf(Color(0xFF9C6813), BrandGold)),
    HomeAction("收烟 / 卖烟", "直接开单", Icons.Rounded.EditNote, AppPage.TRADE, listOf(Color(0xFF7A2921), Color(0xFFAE4939))),
    HomeAction("烟价管理", "商品、条码和价格", Icons.Rounded.Sell, AppPage.PRICES, listOf(Color(0xFF775018), Color(0xFFA77C2A))),
    HomeAction("客户管理", "客户资料", Icons.Rounded.Groups, AppPage.CUSTOMERS, listOf(Color(0xFF65443B), Color(0xFF936B5B))),
    HomeAction("记一笔账", "收款和付款", Icons.Rounded.AccountBalanceWallet, AppPage.ACCOUNTING, listOf(Color(0xFF713127), Color(0xFF9D5042))),
    HomeAction("交易记录", "历史交易", Icons.AutoMirrored.Rounded.ReceiptLong, AppPage.HISTORY, listOf(BrandBurgundy, BrandCrimson)),
    HomeAction("数据统计", "图表分析", Icons.Rounded.BarChart, AppPage.STATS, listOf(Color(0xFF4F3732), Color(0xFF78564D))),
    HomeAction("设置", "打印和备份", Icons.Rounded.Settings, AppPage.SETTINGS, listOf(Color(0xFF584941), Color(0xFF806E64)))
)

@Composable
fun HomeScreen(
    pendingCount: Int,
    managementMode: Boolean,
    onUnlockManagement: () -> Unit,
    onExitManagement: () -> Unit,
    onNavigate: (AppPage) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val tablet = maxWidth >= 720.dp
        LazyColumn(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxSize().widthIn(max = 1120.dp),
            contentPadding = PaddingValues(horizontal = if (tablet) 28.dp else 16.dp, vertical = if (tablet) 24.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (tablet) 22.dp else 16.dp)
        ) {
            item { HomeHeader(tablet, managementMode, onUnlockManagement, onExitManagement) }
            if (managementMode) {
                item { Text("管理功能", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = BrandInk) }
                managementActions.chunked(if (tablet) 3 else 2).forEach { rowActions ->
                    item {
                        Row(Modifier.fillMaxWidth().height(if (tablet) 142.dp else 126.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            rowActions.forEach { action -> ActionCard(action, Modifier.weight(1f)) { onNavigate(action.page) } }
                            repeat((if (tablet) 3 else 2) - rowActions.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            } else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        simpleActions.forEach { action ->
                            SimpleActionCard(action, if (action.page == AppPage.SETTLEMENT) pendingCount else 0, tablet) { onNavigate(action.page) }
                        }
                    }
                }
            }
            item {
                Text(
                    if (managementMode) "管理模式已开启" else "入库后，在结算中统一填写价格",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun HomeHeader(tablet: Boolean, managementMode: Boolean, onUnlockManagement: () -> Unit, onExitManagement: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.ic_app),
            contentDescription = "烟收宝图标",
            modifier = Modifier.size(if (tablet) 68.dp else 56.dp).clip(RoundedCornerShape(16.dp)).pointerInput(managementMode) {
                detectTapGestures(onLongPress = { if (!managementMode) onUnlockManagement() })
            }
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("烟收宝", color = BrandInk, fontSize = if (tablet) 36.sp else 31.sp, fontWeight = FontWeight.Black)
            Text(if (managementMode) "完整功能" else "入库 · 结算", color = BrandMuted, fontSize = if (tablet) 16.sp else 14.sp)
        }
        if (managementMode) Button(onClick = onExitManagement) { Text("退出管理") }
    }
}

@Composable
private fun SimpleActionCard(action: HomeAction, pendingCount: Int, tablet: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(if (tablet) 220.dp else 176.dp)
            .shadow(8.dp, RoundedCornerShape(26.dp), ambientColor = action.colors.first().copy(alpha = .22f)).clickable(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Row(
            Modifier.fillMaxSize().background(Brush.linearGradient(action.colors)).padding(if (tablet) 34.dp else 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(action.icon, null, tint = Color.White, modifier = Modifier.size(if (tablet) 66.dp else 52.dp))
            Spacer(Modifier.width(if (tablet) 28.dp else 20.dp))
            Column(Modifier.weight(1f)) {
                Text(action.title, color = Color.White, fontSize = if (tablet) 34.sp else 28.sp, fontWeight = FontWeight.Black)
                Text(action.subtitle, color = Color.White.copy(alpha = .86f), fontSize = if (tablet) 18.sp else 16.sp)
            }
            if (pendingCount > 0) {
                Box(Modifier.background(Color.White, RoundedCornerShape(50)).padding(horizontal = 13.dp, vertical = 7.dp)) {
                    Text("待结算 $pendingCount", color = BrandBurgundy, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ActionCard(action: HomeAction, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(action.colors))
            .clickable(onClick = onClick).padding(16.dp)
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Icon(action.icon, null, tint = Color.White, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(8.dp))
            Text(action.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(action.subtitle, color = Color.White.copy(alpha = .82f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
