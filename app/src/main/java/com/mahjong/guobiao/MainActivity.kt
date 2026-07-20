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
import com.mahjong.guobiao.engine.AnalysisSettings
import com.mahjong.guobiao.engine.DevelopmentAnalyzer
import com.mahjong.guobiao.engine.fan.FanRegistry
import com.mahjong.guobiao.engine.fan.FanRule
import com.mahjong.guobiao.engine.fan.FanSettingsStore
import com.mahjong.guobiao.engine.fan.WinMethod
import com.mahjong.guobiao.model.MeldType
import com.mahjong.guobiao.model.TileType
import com.mahjong.guobiao.ui.FanTargetUi
import com.mahjong.guobiao.ui.MahjongViewModel
import com.mahjong.guobiao.ui.SwapTargetUi

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
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("分析规则") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            0 -> AnalysisScreen(vm, Modifier.padding(innerPadding))
            1 -> FanSettingsScreen(Modifier.padding(innerPadding))
            2 -> AnalysisSettingsScreen(Modifier.padding(innerPadding))
        }
    }
}

// ── 手牌分析页 ──

@Composable
fun AnalysisScreen(vm: MahjongViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsState()
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)) {

        Text("雀门助手", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))

        // 手牌区
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader("手牌（点牌移除）", Modifier.weight(1f))
            TextButton(onClick = vm::clearHand) { Text("清空手牌") }
        }
        HandRow(state.concealed, onRemove = vm::removeTileAt)

        // 副露区
        if (state.melds.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            SectionHeader("副露（点副露可移除）")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(state.melds.size) { idx ->
                    val m = state.melds[idx]
                    val label = when (m.type) {
                        MeldType.PON -> "碰"
                        MeldType.CHI -> "吃"
                        MeldType.KAN_OPEN -> "明杠"
                        MeldType.KAN_CLOSED -> "暗杠"
                        MeldType.KAN_ADDED -> "加杠"
                        else -> "?"
                    }
                    Surface(
                        onClick = { vm.removeMeld(idx) },
                        color = Color(0xFFE8E0D0),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("$label ${m.tiles.joinToString("") { it.toString() }}",
                            fontSize = 13.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // 牌型选择网格
        SectionHeader("点牌加入手牌 / 记入牌河 / 创建副露")
        var pickerMode by remember { mutableStateOf(PickerMode.HAND) }
        var meldKind by remember { mutableStateOf<MeldType?>(null) }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(selected = pickerMode == PickerMode.HAND, onClick = { pickerMode = PickerMode.HAND; meldKind = null }, label = { Text("加入手牌") })
            FilterChip(selected = pickerMode == PickerMode.DISCARD, onClick = { pickerMode = PickerMode.DISCARD; meldKind = null }, label = { Text("记入牌河") })
            FilterChip(selected = pickerMode == PickerMode.MELD && meldKind == MeldType.PON, onClick = { pickerMode = PickerMode.MELD; meldKind = MeldType.PON }, label = { Text("碰") })
            FilterChip(selected = pickerMode == PickerMode.MELD && meldKind == MeldType.CHI, onClick = { pickerMode = PickerMode.MELD; meldKind = MeldType.CHI }, label = { Text("吃") })
            FilterChip(selected = pickerMode == PickerMode.MELD && meldKind == MeldType.KAN_OPEN, onClick = { pickerMode = PickerMode.MELD; meldKind = MeldType.KAN_OPEN }, label = { Text("明杠") })
            FilterChip(selected = pickerMode == PickerMode.MELD && meldKind == MeldType.KAN_CLOSED, onClick = { pickerMode = PickerMode.MELD; meldKind = MeldType.KAN_CLOSED }, label = { Text("暗杠") })
            FilterChip(selected = pickerMode == PickerMode.MELD && meldKind == MeldType.KAN_ADDED, onClick = { pickerMode = PickerMode.MELD; meldKind = MeldType.KAN_ADDED }, label = { Text("加杠") })
        }
        TilePickerGrid(
            onPick = { tile ->
                when {
                    pickerMode == PickerMode.MELD && meldKind != null -> {
                        vm.addMeld(meldKind!!, tile)
                        meldKind = null; pickerMode = PickerMode.HAND
                    }
                    pickerMode == PickerMode.HAND -> vm.addTile(tile)
                    pickerMode == PickerMode.DISCARD -> vm.addDiscard(tile)
                }
            },
            remaining = { tile -> 4 - state.concealed.count { it == tile } - state.melds.flatMap { m -> m.tiles }.count { it == tile } - state.discards.count { it == tile } }
        )

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
        } else if (state.swapTargets.isNotEmpty()) {
            val label = if (state.isTenpaiNoFan) "已听牌但无法1番起和 — 点选牌型查看可替换的牌" else "弃一张摸一张可发展的牌型（点击查看详情）"
            SectionHeader(label)
            Text("总剩余: ${state.totalRemaining} 张", fontSize = 12.sp, color = Color.Gray)
            var detailSwap by remember { mutableStateOf<SwapTargetUi?>(null) }
            state.swapTargets.forEach { st ->
                Surface(
                    onClick = { detailSwap = st },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                    color = Color(0xFFF0F4F8),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${st.name} (${st.fanValue}番)", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("${st.probabilityPercent}", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            detailSwap?.let { st ->
                AlertDialog(
                    onDismissRequest = { detailSwap = null },
                    title = { Text("${st.name} (${st.fanValue}番) — 总概率 ${st.probabilityPercent}") },
                    text = {
                        Column {
                            val sc = st.swapPaths.firstOrNull()?.swapCount ?: 1
                            Text("弃${sc}张 → 摸${sc}张的替换路径：", fontSize = 13.sp, color = Color.Gray)
                            Spacer(Modifier.height(4.dp))
                            st.swapPaths.forEach { sp ->
                                val waitStr = if (sp.resultingWaits.isNotEmpty())
                                    " → 听 ${sp.resultingWaits.joinToString("") { it.toString() }}" else ""
                                val discStr = sp.discardTiles.joinToString("") { it.toString() }
                                val drawStr = sp.drawTiles.joinToString("") { it.toString() }
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("弃 $discStr→摸 $drawStr  剩${sp.remainingCount}张", fontSize = if (sp.swapCount > 1) 11.sp else 13.sp)
                                    Text("${sp.probabilityPercent}$waitStr", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { detailSwap = null }) { Text("关闭") }
                    }
                )
            }
        } else if (state.fanTargets.isNotEmpty()) {
            SectionHeader("可发展牌型（按概率排序，点击查看改进牌）")
            Text("总剩余: ${state.totalRemaining} 张", fontSize = 12.sp, color = Color.Gray)
            var detailTarget by remember { mutableStateOf<FanTargetUi?>(null) }
            state.fanTargets.forEach { ft ->
                Surface(
                    onClick = { detailTarget = ft },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                    color = Color(0xFFF0F4F8),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${ft.name} (${ft.fanValue}番)", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("${ft.probabilityPercent}", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            detailTarget?.let { ft ->
                AlertDialog(
                    onDismissRequest = { detailTarget = null },
                    title = { Text("${ft.name} (${ft.fanValue}番) — 总概率 ${ft.probabilityPercent}") },
                    text = {
                        Column {
                            Text("摸以下牌可进入听牌态并达成此番种：", fontSize = 13.sp, color = Color.Gray)
                            Spacer(Modifier.height(4.dp))
                            ft.improvementTiles.forEach { tile ->
                                val waitStr = if (tile.resultingWaits.isNotEmpty())
                                    " → 听 ${tile.resultingWaits.joinToString("") { it.toString() }}" else ""
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("摸 ${tile.drawTile}  剩${tile.remainingCount}张", fontSize = 14.sp)
                                    Text("${tile.probabilityPercent}$waitStr", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { detailTarget = null }) { Text("关闭") } }
                )
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
fun TilePickerGrid(onPick: (TileType) -> Unit, remaining: (TileType) -> Int = { 0 }) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(9),
        modifier = Modifier.heightIn(max = 260.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(TileType.ALL_NON_FLOWER) { tile -> TileView(tile, remaining = remaining(tile), onClick = { onPick(tile) }) }
    }
}

@Composable
fun TileView(tile: TileType, remaining: Int = -1, onClick: () -> Unit) {
    val bg = when {
        remaining == 0 -> Color(0xFFE0E0E0)
        tile.isHonor -> Color(0xFFE8DCC8)
        tile.isTerminal -> Color(0xFFDCE8DC)
        else -> Color(0xFFF5F5F5)
    }
    Box(
        modifier = Modifier.size(36.dp).background(bg, RoundedCornerShape(4.dp)).border(1.dp, Color.Gray, RoundedCornerShape(4.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(tile.toString(), fontSize = if (remaining >= 0) 11.sp else 13.sp, textAlign = TextAlign.Center, color = if (remaining == 0) Color.LightGray else if (tile.isHonor) Color.Black else Color.DarkGray)
            if (remaining >= 0) Text("($remaining)", fontSize = 9.sp, color = Color.Gray)
        }
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
    val scrollState = rememberScrollState()
    LaunchedEffect(discards.size) {
        if (discards.isNotEmpty()) scrollState.animateScrollTo(scrollState.maxValue)
    }
    Row(verticalAlignment = Alignment.Top) {
        Column(
            modifier = Modifier
                .weight(1f)
                .heightIn(max = 152.dp)
                .verticalScroll(scrollState)
        ) {
            val rows = discards.chunked(9)
            rows.forEach { rowTiles ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(vertical = 1.dp)) {
                    rowTiles.forEach { tile ->
                        TileView(tile, onClick = { onRemove(discards.indexOf(tile)) })
                    }
                }
            }
        }
        TextButton(onClick = onClear) { Text("清空牌河") }
    }
    Text("点牌可移除，切换上方模式后点网格加入", fontSize = 11.sp, color = Color.Gray)
}

// ── 分析规则页 ──

@Composable
fun AnalysisSettingsScreen(modifier: Modifier = Modifier) {
    var depth by remember { mutableIntStateOf(AnalysisSettings.swapDepth) }

    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
        Text("分析规则", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
        Text("调整替换式分析的模拟深度（弃N摸N）", fontSize = 13.sp, color = Color.Gray)

        Spacer(Modifier.height(16.dp))

        Text("替换深度：弃${depth}张 → 摸${depth}张", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("1", fontSize = 14.sp)
            Slider(
                value = depth.toFloat(),
                onValueChange = { depth = it.toInt() },
                valueRange = 1f..3f,
                steps = 1,
                modifier = Modifier.weight(1f)
            )
            Text("3", fontSize = 14.sp)
        }

        Spacer(Modifier.height(8.dp))

        val warning = when (depth) {
            1 -> "仅替换1张牌，适合1向听分析"
            2 -> "替换2张牌，计算量增大，可能较慢"
            3 -> "替换3张牌，计算量极大，不推荐常规使用"
            else -> ""
        }
        Text(warning, fontSize = 12.sp, color = if (depth >= 2) Color(0xFFFF9800) else Color.Gray)

        Spacer(Modifier.height(12.dp))

        Button(onClick = {
            AnalysisSettings.setSwapDepth(depth)
        }) {
            Text("保存设置")
        }

        Spacer(Modifier.height(16.dp))

        Text("说明", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text("- 深度=1：枚举每张暗手弃牌 × 34种摸牌（~300组合）", fontSize = 13.sp)
        Text("- 深度=2：弃2张的组合 × 摸2张的组合（数千组合）", fontSize = 13.sp)
        Text("- 深度=3：弃3张 × 摸3张（百万组合，按200条结果截断）", fontSize = 13.sp)
        Text("- 深度越大，分析越慢但覆盖更多可能性", fontSize = 13.sp)
        Text("- 设置自动持久化，重启有效", fontSize = 13.sp)
    }
}

enum class PickerMode { HAND, DISCARD, MELD }
