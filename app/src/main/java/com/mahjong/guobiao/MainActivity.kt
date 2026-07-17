package com.mahjong.guobiao

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.ExperimentalUnitApi
import com.mahjong.guobiao.engine.fan.FanRegistry
import com.mahjong.guobiao.engine.fan.FanRule
import com.mahjong.guobiao.engine.fan.FanSettingsStore
import com.mahjong.guobiao.engine.fan.WinMethod
import com.mahjong.guobiao.model.TileType
import com.mahjong.guobiao.ui.MahjongViewModel

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: MahjongViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vm: MahjongViewModel by viewModels()
        viewModel = vm
        viewModel.loadSettings(this)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MahjongApp(viewModel)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (::viewModel.isInitialized) viewModel.saveSettings(this)
    }
}

@Composable
fun MahjongApp(vm: MahjongViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("手牌分析") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("番数规则") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            0 -> AnalysisScreen(vm, Modifier.padding(innerPadding))
            1 -> FanSettingsScreen(Modifier.padding(innerPadding))
        }
    }
}

// ── 手牌分析页 ──

@Composable
fun AnalysisScreen(vm: MahjongViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsState()
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)) {

        Text("国标麻将助手", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))

        // 手牌区
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader("手牌（点牌移除）", Modifier.weight(1f))
            TextButton(onClick = vm::clearHand) { Text("清空手牌") }
        }
        HandRow(state.concealed, onRemove = vm::removeTileAt)

        Spacer(Modifier.height(8.dp))

        // 牌型选择网格
        SectionHeader("点牌加入手牌 / 记入牌河")
        var pickerMode by remember { mutableStateOf(PickerMode.HAND) }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(selected = pickerMode == PickerMode.HAND, onClick = { pickerMode = PickerMode.HAND }, label = { Text("加入手牌") })
            FilterChip(selected = pickerMode == PickerMode.DISCARD, onClick = { pickerMode = PickerMode.DISCARD }, label = { Text("记入牌河") })
        }
        TilePickerGrid(onPick = { tile ->
            when (pickerMode) {
                PickerMode.HAND -> vm.addTile(tile)
                PickerMode.DISCARD -> vm.addDiscard(tile)
            }
        })

        Spacer(Modifier.height(8.dp))

        // 和牌方式
        SectionHeader("和牌方式")
        WinMethodSelector(state.winMethod, onSelect = vm::setWinMethod)

        Spacer(Modifier.height(8.dp))

        // 牌河
        SectionHeader("场上牌河（点牌记录已出）")
        DiscardRow(state.discards, onRemove = vm::removeDiscardAt, onClear = vm::clearDiscards)

        Spacer(Modifier.height(12.dp))

        // 操作按钮
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::analyze, modifier = Modifier.weight(1f)) { Text("分析") }
            OutlinedButton(onClick = vm::clearAll, modifier = Modifier.weight(1f)) { Text("清空") }
        }

        Spacer(Modifier.height(8.dp))

        // 结果
        if (state.message.isNotEmpty()) {
            Text(state.message, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        if (state.isWin) {
            SectionHeader("番种（合计 ${state.totalFan} 番）")
            state.possibleFans.forEach { fan -> Text("• $fan", fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp)) }
        } else if (state.waitingTiles.isNotEmpty()) {
            SectionHeader("听牌（剩余张数）")
            state.waitingTiles.forEach { wt ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${wt.tile}  剩余 ${wt.remainingCount} 张", fontSize = 15.sp)
                    Text(wt.possibleFanNames.joinToString(" "), fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

// ── 番数设置页 ──

@Composable
fun FanSettingsScreen(modifier: Modifier = Modifier) {
    val rules = remember { FanRegistry.rules }
    var editRule by remember { mutableStateOf<FanRule?>(null) }
    var editValue by remember { mutableStateOf("") }
    var hideMode by remember { mutableStateOf(false) }

    var refreshKey by remember { mutableStateOf(0) }
    @Suppress("UNUSED_EXPRESSION")
    refreshKey

    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
        Text("番种规则", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
        Text(
            if (hideMode) "隐藏模式：点选番种即可隐藏，隐藏后不参与分析" else "点选番种可调整番数，调整后在[手牌分析]中生效",
            fontSize = 13.sp, color = Color.Gray
        )

        Spacer(Modifier.height(4.dp))

        // 操作按钮行
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { FanSettingsStore.resetAll(); refreshKey++ }) { Text("重置所有") }

            val hiddenCount = FanSettingsStore.hiddenIds.size
            FilterChip(
                selected = hideMode,
                onClick = { hideMode = !hideMode },
                label = { Text(if (hideMode) "退出隐藏" else "编辑隐藏") }
            )

            Text(
                "覆盖:${FanSettingsStore.overriddenIds.size} 隐藏:$hiddenCount",
                fontSize = 11.sp, color = Color.Gray, modifier = Modifier.align(Alignment.CenterVertically)
            )
        }

        Spacer(Modifier.height(4.dp))

        LazyColumn {
            items(rules.filter { !FanSettingsStore.isHidden(it.id) || hideMode }) { rule ->
                val effectiveValue = FanSettingsStore.getValue(rule)
                val isOverridden = FanSettingsStore.hasOverride(rule.id)
                val isHiddenItem = FanSettingsStore.isHidden(rule.id)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp)
                        .clickable {
                            if (hideMode) {
                                FanSettingsStore.toggleHidden(rule.id)
                                refreshKey++
                            } else {
                                editRule = rule
                                editValue = effectiveValue.toString()
                            }
                        },
                    color = when {
                        isHiddenItem -> Color(0xFFE0E0E0)
                        isOverridden -> Color(0xFFFFF3E0)
                        else -> Color.Transparent
                    },
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            if (isHiddenItem) "[隐藏] ${rule.name}" else rule.name,
                            fontSize = 14.sp,
                            color = if (isHiddenItem) Color.LightGray else Color.Black
                        )
                        Text(
                            "$effectiveValue 番",
                            fontSize = 14.sp,
                            fontWeight = if (isOverridden) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                isHiddenItem -> Color.LightGray
                                isOverridden -> MaterialTheme.colorScheme.primary
                                else -> Color.DarkGray
                            }
                        )
                    }
                }
            }
        }
    }

    // 编辑对话框
    editRule?.let { rule ->
        AlertDialog(
            onDismissRequest = { editRule = null },
            title = { Text(rule.name) },
            text = {
                Column {
                    Text("默认: ${rule.value} 番", fontSize = 13.sp, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editValue,
                        onValueChange = { v -> if (v.isEmpty() || v.all { it.isDigit() }) editValue = v },
                        label = { Text("番数") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = editValue.toIntOrNull() ?: 0
                    FanSettingsStore.setOverride(rule.id, v)
                    refreshKey++
                    editRule = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { editRule = null }) { Text("取消") }
            }
        )
    }
}

// ── 通用组件 ──

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = modifier.padding(top = 4.dp, bottom = 2.dp))
}

@Composable
fun HandRow(tiles: List<TileType>, onRemove: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(tiles) { tile -> TileView(tile, onClick = { onRemove(tiles.indexOf(tile)) }) }
    }
}

@Composable
fun TilePickerGrid(onPick: (TileType) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(9),
        modifier = Modifier.heightIn(max = 260.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(TileType.ALL_NON_FLOWER) { tile -> TileView(tile, onClick = { onPick(tile) }) }
    }
}

@Composable
fun TileView(tile: TileType, onClick: () -> Unit) {
    val bg = when {
        tile.isHonor -> Color(0xFFE8DCC8)
        tile.isTerminal -> Color(0xFFDCE8DC)
        else -> Color(0xFFF5F5F5)
    }
    Box(
        modifier = Modifier.size(36.dp).background(bg, RoundedCornerShape(4.dp)).border(1.dp, Color.Gray, RoundedCornerShape(4.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(tile.toString(), fontSize = 13.sp, textAlign = TextAlign.Center, color = if (tile.isHonor) Color.Black else Color.DarkGray)
    }
}

@Composable
fun WinMethodSelector(method: WinMethod, onSelect: (WinMethod) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        WinMethod.entries.take(2).forEach { m ->
            FilterChip(
                selected = method == m,
                onClick = { onSelect(m) },
                label = { Text(when (m) { WinMethod.SELF_DRAW -> "自摸"; WinMethod.DISCARD -> "点炮"; else -> m.name }) }
            )
        }
    }
}

@Composable
fun DiscardRow(discards: List<TileType>, onRemove: (Int) -> Unit, onClear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            items(discards) { tile -> TileView(tile, onClick = { onRemove(discards.indexOf(tile)) }) }
        }
        TextButton(onClick = onClear) { Text("清空牌河") }
    }
    Text("点牌可移除，切换上方模式后点网格加入", fontSize = 11.sp, color = Color.Gray)
}

enum class PickerMode { HAND, DISCARD }
