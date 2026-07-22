package com.mahjong.guobiao.engine

import com.mahjong.guobiao.engine.fan.FanRegistry
import com.mahjong.guobiao.engine.fan.FanRule
import com.mahjong.guobiao.engine.fan.FanScorer
import com.mahjong.guobiao.engine.fan.FanContext
import com.mahjong.guobiao.engine.fan.WinInfo
import com.mahjong.guobiao.engine.tenpai.TenpaiCalculator
import com.mahjong.guobiao.engine.win.WinChecker
import com.mahjong.guobiao.model.Hand
import com.mahjong.guobiao.model.TableState
import com.mahjong.guobiao.model.TileType

/**
 * 番种倒推式发展分析：从目标番种反向推算需要的替换路径。
 * 替代通用组合枚举，搜索量降低 100~10000 倍。
 */
object FanReverseAnalyzer {

    /** 替换路径。 */
    data class SwapPath(
        val discardTiles: List<TileType>, val drawTiles: List<TileType>,
        val remainingCount: Int, val probability: Double,
        val resultingWaits: List<TileType>, val swapCount: Int = 1
    )
    data class SwapTarget(
        val fanRule: FanRule, val totalProbability: Double, val swapPaths: List<SwapPath>
    )

    /**
     * 主入口：按番种倒推 + 渐进深度 + 快速向听预检。
     */
    fun analyze(hand: Hand, tableState: TableState): List<SwapTarget> {
        val maxDepth = AnalysisSettings.swapDepth.coerceIn(1, 3)
        val totalRem = remainingTotal(hand, tableState, 136)
        val tenpaiSize = hand.concealedCountForTenpai()
        val winSize = hand.concealedCountForWin()
        val allTargets = mutableListOf<SwapTarget>()

        for (rule in FanRegistry.rules) {
            val rev = reverse(rule, hand) ?: continue
            val ncCount = rev.nonCompliant.size
            if (ncCount == 0) continue

            // 渐进深度：从 min(1, ncCount) 逐层尝试到 min(maxDepth, ncCount)
            val paths = mutableListOf<SwapPath>()
            val found = mutableSetOf<Pair<List<TileType>, List<TileType>>>()

            for (d in 1..maxDepth.coerceAtMost(ncCount)) {
                if (paths.isNotEmpty() && d > 1) break // 浅层已找到路径，不需加深
                val discCombos = combinations(rev.nonCompliant, d)
                val drawCombos = combinations(rev.targetPool.toList(), d)
                for (discs in discCombos) {
                    val afterDiscard = hand.concealed.toMutableList()
                    for (dd in discs) afterDiscard.remove(dd)
                    for (draws in drawCombos) {
                        if (draws.toSet().size < d) continue
                        if (draws.any { it in discs }) continue
                        val key = Pair(discs, draws)
                        if (key in found) continue
                        var ok = true; var remMin = 999
                        for (draw in draws) {
                            val rem = 4 - visibleCount(draw, hand, tableState)
                            if (rem <= 0) { ok = false; break }
                            if (rem < remMin) remMin = rem
                        }
                        if (!ok) continue
                        val newConc = (afterDiscard + draws).sorted()
                        val newHand = hand.withConcealed(newConc)
                        if (newConc.size == tenpaiSize && newHand.isValidTenpaiSize()) {
                            // 快速预检：贪婪面子计数，淘汰明显不能听牌的
                            if (!preCheckTenpai(newHand)) continue
                            val waits = TenpaiCalculator.waitingTiles(newHand)
                            if (waits.isNotEmpty() && reachesTarget(rule, hand, discs, draws, waits)) {
                                found.add(key)
                                paths.add(SwapPath(discs, draws, remMin, remMin.toDouble() / totalRem, waits, d))
                            }
                        } else if (newConc.size == winSize && WinChecker.isWin(newHand)) {
                            if (reachesTarget(rule, hand, discs, draws, emptyList())) {
                                found.add(key)
                                paths.add(SwapPath(discs, draws, remMin, remMin.toDouble() / totalRem, emptyList(), d))
                            }
                        }
                    }
                }
            }
            if (paths.isNotEmpty()) {
                val unique = paths.distinctBy { Pair(it.discardTiles, it.drawTiles) }.sortedByDescending { it.probability }
                allTargets.add(SwapTarget(rule, unique.sumOf { it.probability }, unique))
            }
        }
        return allTargets.sortedByDescending { it.totalProbability }
    }

    /** 快速预检：贪婪形成 melds，淘汰明显无法听牌的手牌。 */
    private fun preCheckTenpai(hand: Hand): Boolean {
        val meldsToFind = 4 - hand.meldCount
        val counts = hand.concealedCounts().copy()
        // 方案1: 先刻子后顺子
        var found = countGreedyMelds(counts, tripletsFirst = true)
        if (found >= meldsToFind) return true
        // 方案2: 先顺子后刻子
        val counts2 = hand.concealedCounts().copy()
        found = countGreedyMelds(counts2, tripletsFirst = false)
        return found >= meldsToFind
    }

    private fun countGreedyMelds(counts: com.mahjong.guobiao.model.TileCounts, tripletsFirst: Boolean): Int {
        var found = 0
        if (tripletsFirst) {
            for (t in TileType.ALL_NON_FLOWER) {
                while (counts[t] >= 3) { counts.remove(t, 3); found++ }
            }
        }
        for (rank in 0..6) {
            for (base in listOf(0, 9, 18)) {
                val a = TileType(base + rank); val b = TileType(base + rank + 1); val c = TileType(base + rank + 2)
                while (counts[a] > 0 && counts[b] > 0 && counts[c] > 0) {
                    counts.remove(a); counts.remove(b); counts.remove(c); found++
                }
            }
        }
        if (!tripletsFirst) {
            for (t in TileType.ALL_NON_FLOWER) {
                while (counts[t] >= 3) { counts.remove(t, 3); found++ }
            }
        }
        return found
    }

    /** 验证替换后的手牌能到达目标番种。 */
    private fun reachesTarget(rule: FanRule, hand: Hand, discs: List<TileType>, draws: List<TileType>, waits: List<TileType>): Boolean {
        val afterDiscard = hand.concealed.toMutableList()
        for (d in discs) afterDiscard.remove(d)
        val baseConc = (afterDiscard + draws).sorted()
        val waitsToCheck = if (waits.isEmpty()) listOf(TileType.EAST) else waits  // dummy for win case
        return waitsToCheck.any { wait ->
            val winHand = if (waits.isEmpty()) hand.withConcealed(baseConc)
                else hand.withConcealed((baseConc + wait).sorted())
            if (!winHand.isValidWinSize() && waits.isNotEmpty()) return@any false
            WinChecker.getAllDecompositions(winHand).any { decomp ->
                val result = FanScorer.score(FanContext(decomp, winHand, WinInfo(wait)))
                result.meetsMinimum && result.allDetected.any { it.id == rule.id }
            }
        }
    }

    // ── 番种倒推器 ──

    data class ReverseInfo(val nonCompliant: List<TileType>, val targetPool: Set<TileType>)

    private fun reverse(rule: FanRule, hand: Hand): ReverseInfo? {
        return when (rule.id) {
            "full_flush" -> reverseTileSet(hand) { t -> t.isSuited && t.suit == dominantSuit(hand) }
            "half_flush" -> reverseTileSet(hand) { t -> t.isSuited && t.suit == dominantSuit(hand) || t.isHonor }
            "all_green" -> reverseTileSet(hand) { it.isGreen }
            "red_peacock" -> reverseTileSet(hand) { it.code in setOf(18, 22, 24, 26, 31) }
            "all_blue" -> reverseTileSet(hand) { it.code in setOf(27, 28, 29, 30, 33, 16) }
            "all_honors" -> reverseTileSet(hand) { it.isHonor }
            "pure_terminals" -> reverseTileSet(hand) { it.isTerminal }
            "mixed_terminals" -> reverseTileSet(hand) { it.isTerminalOrHonor }
            "big_three_dragons" -> reverseTileSet(hand) { it.isDragon }
            "big_four_winds" -> reverseTileSet(hand) { it.isWind }
            "small_three_dragons" -> reverseTileSet(hand) { it.isDragon }
            "small_four_winds" -> reverseTileSet(hand) { it.isWind }
            "nine_gates" -> reverseFullFlush(hand)  // same as 清一色
            "thirteen_orphans" -> reverseTileSet(hand) { it in TileType.TERMINALS_HONORS }
            "pong" -> reversePong(hand)
            "seven_pairs" -> reverseSevenPairs(hand)
            "luxury_seven_pairs" -> reverseSevenPairsLuxury(hand, 1)
            "double_luxury_seven_pairs" -> reverseSevenPairsLuxury(hand, 2)
            "triple_luxury_seven_pairs" -> reverseSevenPairsLuxury(hand, 3)
            "four_concealed_triplets" -> reversePong(hand)  // same as 碰碰胡
            "big_seven_stars" -> reverseTileSet(hand) { it.isHonor }
            else -> null
        }
    }

    /** 简单的牌型集合倒推：非目标牌 → 换为目成牌。 */
    private fun reverseTileSet(hand: Hand, isTarget: (TileType) -> Boolean): ReverseInfo {
        val counts = hand.concealedCounts()
        val nonCompliant = mutableListOf<TileType>()
        for (t in TileType.ALL_NON_FLOWER) {
            repeat(counts[t]) { if (!isTarget(t)) nonCompliant.add(t) }
        }
        val targetPool = TileType.ALL_NON_FLOWER.filter { isTarget(it) }.toSet()
        return ReverseInfo(nonCompliant, targetPool)
    }

    /** 清一色：找主导花色。 */
    private fun reverseFullFlush(hand: Hand): ReverseInfo? {
        val suit = dominantSuit(hand) ?: return null
        val isTarget: (TileType) -> Boolean = { it.suit == suit }
        return reverseTileSet(hand, isTarget)
    }

    /** 碰碰胡倒推：孤张是弃牌，需摸的牌是已有对子的配对。 */
    private fun reversePong(hand: Hand): ReverseInfo {
        val counts = hand.concealedCounts()
        val loners = mutableListOf<TileType>()       // 孤张（count=1）
        val targetTiles = mutableSetOf<TileType>()   // 已有对子或刻子，需补全
        for (t in TileType.ALL_NON_FLOWER) {
            when (counts[t]) {
                1 -> repeat(1) { loners.add(t) }
                2 -> targetTiles.add(t)  // 有对子，可摸第3张成刻
            }
        }
        if (targetTiles.isEmpty()) targetTiles.addAll(TileType.ALL_NON_FLOWER)
        return ReverseInfo(loners, targetTiles)
    }

    /** 七小对倒推：孤张是弃牌，目标牌是已有单张的配对。 */
    private fun reverseSevenPairs(hand: Hand): ReverseInfo {
        val counts = hand.concealedCounts()
        val loners = mutableListOf<TileType>()
        val targetTiles = mutableSetOf<TileType>()
        for (t in TileType.ALL_NON_FLOWER) {
            when (counts[t]) {
                1 -> { loners.add(t); targetTiles.add(t) }  // 单张需要配对
                3 -> loners.add(t)  // 3张中多余1张需弃掉
            }
        }
        if (targetTiles.isEmpty()) targetTiles.addAll(TileType.ALL_NON_FLOWER)
        return ReverseInfo(loners, targetTiles)
    }

    /** 豪华七对倒推：允许N组4张。 */
    private fun reverseSevenPairsLuxury(hand: Hand, maxFourGroups: Int): ReverseInfo? {
        val counts = hand.concealedCounts()
        val loners = mutableListOf<TileType>()
        val targetTiles = mutableSetOf<TileType>()
        var fourCount = 0
        for (t in TileType.ALL_NON_FLOWER) {
            when (counts[t]) {
                1 -> { loners.add(t); targetTiles.add(t) }
                2 -> targetTiles.add(t)
                3 -> loners.add(t) // 需弃1张成2张，或摸1张成4张
                4 -> fourCount++
                else -> {}
            }
        }
        if (fourCount < maxFourGroups) { /* still needs more 4-groups, accept */ }
        if (targetTiles.isEmpty()) targetTiles.addAll(TileType.ALL_NON_FLOWER)
        return ReverseInfo(loners, targetTiles)
    }

    /** 找暗手中最多的花色。 */
    private fun dominantSuit(hand: Hand): com.mahjong.guobiao.model.Suit? {
        val counts = hand.concealedCounts()
        val suits = listOf(com.mahjong.guobiao.model.Suit.MANZU, com.mahjong.guobiao.model.Suit.PINZU, com.mahjong.guobiao.model.Suit.SOUZU)
        return suits.maxByOrNull { suit ->
            val base = when (suit) { com.mahjong.guobiao.model.Suit.MANZU -> 0; com.mahjong.guobiao.model.Suit.PINZU -> 9; com.mahjong.guobiao.model.Suit.SOUZU -> 18; else -> 0 }
            (0..8).sumOf { counts[TileType(base + it)] }
        }
    }

    // ── 工具 ──

    private fun combinations(list: List<TileType>, n: Int): List<List<TileType>> {
        if (n <= 0 || list.size < n) return if (n == 0) listOf(emptyList()) else emptyList()
        val result = mutableListOf<List<TileType>>()
        fun dfs(start: Int, current: MutableList<TileType>) {
            if (current.size == n) { result.add(current.toList()); return }
            for (i in start until list.size) {
                current.add(list[i]); dfs(i + 1, current); current.removeAt(current.lastIndex)
            }
        }
        dfs(0, mutableListOf())
        return result
    }

    private fun remainingTotal(hand: Hand, tableState: TableState, total: Int): Int {
        var visible = 0
        for (t in TileType.ALL_NON_FLOWER) {
            var v = hand.concealed.count { it == t } + hand.melds.flatMap { it.tiles }.count { it == t }
            for (p in tableState.players) {
                if (p.seat == tableState.selfSeat) v += p.discards.count { it == t } else v += p.visibleCount(t)
            }
            visible += minOf(v, 4)
        }
        return (total - visible).coerceAtLeast(0)
    }

    private fun visibleCount(tile: TileType, hand: Hand, tableState: TableState): Int {
        var v = hand.concealed.count { it == tile } + hand.melds.flatMap { it.tiles }.count { it == tile }
        for (p in tableState.players) {
            if (p.seat == tableState.selfSeat) v += p.discards.count { it == tile } else v += p.visibleCount(tile)
        }
        return v
    }
}
