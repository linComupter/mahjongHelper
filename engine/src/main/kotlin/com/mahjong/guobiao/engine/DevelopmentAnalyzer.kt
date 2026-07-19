package com.mahjong.guobiao.engine

import com.mahjong.guobiao.engine.fan.FanContext
import com.mahjong.guobiao.engine.fan.FanRegistry
import com.mahjong.guobiao.engine.fan.FanRule
import com.mahjong.guobiao.engine.fan.FanScorer
import com.mahjong.guobiao.engine.fan.WinInfo
import com.mahjong.guobiao.engine.tenpai.TenpaiCalculator
import com.mahjong.guobiao.engine.win.WinChecker
import com.mahjong.guobiao.model.Hand
import com.mahjong.guobiao.model.TableState
import com.mahjong.guobiao.model.TileType

object DevelopmentAnalyzer {

    data class ImprovementPath(
        val drawTile: TileType, val remainingCount: Int,
        val probability: Double, val resultingWaits: List<TileType>,
        val improvementType: ImprovementType
    )
    enum class ImprovementType { TO_TENPAI, TO_WIN }

    data class FanTarget(
        val fanRule: FanRule, val totalProbability: Double,
        val improvementTiles: List<ImprovementTile>
    )
    data class ImprovementTile(
        val tile: TileType, val remainingCount: Int,
        val probability: Double, val resultingWaits: List<TileType>
    )

    /** 替换式发展路径。弃N摸N时 discardTiles/drawTiles 含多个元素。 */
    data class SwapPath(
        val discardTiles: List<TileType>,
        val drawTiles: List<TileType>,
        val remainingCount: Int,
        val probability: Double,
        val resultingWaits: List<TileType>,
        val swapCount: Int = 1
    )

    data class SwapTarget(
        val fanRule: FanRule, val totalProbability: Double,
        val swapPaths: List<SwapPath>
    )

    data class DevelopmentResult(
        val currentShanten: Int, val totalRemaining: Int,
        val improvements: List<ImprovementPath>, val fanTargets: List<FanTarget>,
        val swapTargets: List<SwapTarget>, val isTenpaiNoFan: Boolean = false,
        val maxDepthUsed: Int = 1
    )

    fun hasValidTenpai(hand: Hand): Boolean {
        if (!hand.isValidTenpaiSize()) return false
        return TenpaiCalculator.waitingTiles(hand).any { wait ->
            val winHand = hand.withConcealed((hand.concealed + wait).sorted())
            WinChecker.getAllDecompositions(winHand).any { decomp ->
                FanScorer.score(FanContext(decomp, winHand, WinInfo(wait))).meetsMinimum
            }
        }
    }

    fun analyze(hand: Hand, tableState: TableState): DevelopmentResult {
        val totalTiles = 136
        val tenpaiSize = hand.concealedCountForTenpai()
        val winSize = hand.concealedCountForWin()
        val size = hand.concealed.size

        if (size == winSize && WinChecker.isWin(hand))
            return DevelopmentResult(-1, remainingTotal(hand, tableState, totalTiles), emptyList(), emptyList(), emptyList())

        if (size == tenpaiSize && hand.isValidTenpaiSize()) {
            val waits = TenpaiCalculator.waitingTiles(hand)
            val hasValid = waits.isNotEmpty() && hasValidTenpai(hand)
            if (hasValid) {
                val imps = waits.map { w ->
                    val r = 4 - visibleCount(w, hand, tableState)
                    ImprovementPath(w, r, r.toDouble() / remainingTotal(hand, tableState, totalTiles), emptyList(), ImprovementType.TO_WIN)
                }
                return DevelopmentResult(0, remainingTotal(hand, tableState, totalTiles), emptyList(), groupByFans(hand, imps), emptyList())
            } else if (waits.isNotEmpty()) {
                val (swaps, used) = analyzeSwap(hand, tableState, totalTiles, tenpaiSize)
                return DevelopmentResult(0, remainingTotal(hand, tableState, totalTiles), emptyList(), emptyList(), swaps, true, used)
            }
            return DevelopmentResult(0, remainingTotal(hand, tableState, totalTiles), emptyList(), emptyList(), emptyList())
        }

        val shanten = tenpaiSize - size
        val (swaps, used) = analyzeSwap(hand, tableState, totalTiles, tenpaiSize)
        return DevelopmentResult(shanten, remainingTotal(hand, tableState, totalTiles), emptyList(), emptyList(), swaps, maxDepthUsed = used)
    }

    /** 替换式分析：根据 AnalysisSettings.swapDepth 控制深度。 */
    private fun analyzeSwap(hand: Hand, tableState: TableState, totalTiles: Int, targetSize: Int): Pair<List<SwapTarget>, Int> {
        val depth = AnalysisSettings.swapDepth.coerceIn(1, 3)
        val totalRem = remainingTotal(hand, tableState, totalTiles)
        val paths = mutableListOf<SwapPath>()
        val discCandidates = hand.concealed.distinct()

        if (depth == 1) {
            swapDepth1(hand, tableState, discCandidates, totalRem, targetSize, paths)
        } else {
            swapDepthN(hand, tableState, discCandidates, totalRem, targetSize, depth, paths)
        }

        paths.sortByDescending { it.probability }
        return groupSwapsByFans(hand, paths) to depth
    }

    /** 单层替换（深度=1）。 */
    private fun swapDepth1(
        hand: Hand, tableState: TableState, discCandidates: List<TileType>,
        totalRem: Int, targetSize: Int, results: MutableList<SwapPath>
    ) {
        for (discard in discCandidates) {
            val afterDiscard = hand.concealed.toMutableList().apply { remove(discard) }
            for (draw in TileType.ALL_NON_FLOWER) {
                if (draw == discard) continue
                val remaining = 4 - visibleCount(draw, hand, tableState)
                if (remaining <= 0) continue
                val newConc = (afterDiscard + draw).sorted()
                val newHand = hand.withConcealed(newConc)

                if (newConc.size == targetSize && newHand.isValidTenpaiSize()) {
                    val waits = TenpaiCalculator.waitingTiles(newHand)
                    if (waits.isNotEmpty())
                        results.add(SwapPath(listOf(discard), listOf(draw), remaining, remaining.toDouble() / totalRem, waits, 1))
                } else if (newConc.size == targetSize + 1 && WinChecker.isWin(newHand)) {
                    results.add(SwapPath(listOf(discard), listOf(draw), remaining, remaining.toDouble() / totalRem, emptyList(), 1))
                }
            }
        }
    }

    /** 多层替换（深度=2,3）：组合枚举。 */
    private fun swapDepthN(
        hand: Hand, tableState: TableState, discCandidates: List<TileType>,
        totalRem: Int, targetSize: Int, depth: Int, results: MutableList<SwapPath>
    ) {
        val allTiles = TileType.ALL_NON_FLOWER
        val discCombos = combinations(discCandidates, depth)
        val drawCombos = combinations(allTiles.toList(), depth)

        // 限制搜索结果数量防止响应过慢
        val maxResults = 200
        for (discs in discCombos) {
            val afterDiscard = hand.concealed.toMutableList()
            for (d in discs) afterDiscard.remove(d)
            for (draws in drawCombos) {
                // 跳过的组合：摸牌全同弃牌 或 有重复摸牌
                if (draws.toSet().size < depth) continue
                if (draws.any { it in discs }) continue
                // 检查每张摸牌剩余
                var ok = true
                var remMin = 999
                for (draw in draws) {
                    val remaining = 4 - visibleCount(draw, hand, tableState)
                    if (remaining <= 0) { ok = false; break }
                    if (remaining < remMin) remMin = remaining
                }
                if (!ok) continue

                val newConc = (afterDiscard + draws).sorted()
                val newHand = hand.withConcealed(newConc)
                if (newConc.size == targetSize && newHand.isValidTenpaiSize()) {
                    val waits = TenpaiCalculator.waitingTiles(newHand)
                    if (waits.isNotEmpty()) {
                        // 简化概率：取最小剩余 / totalRem
                        results.add(SwapPath(discs, draws, remMin, remMin.toDouble() / totalRem, waits, depth))
                        if (results.size >= maxResults) return
                    }
                } else if (newConc.size == targetSize + 1 && WinChecker.isWin(newHand)) {
                    results.add(SwapPath(discs, draws, remMin, remMin.toDouble() / totalRem, emptyList(), depth))
                    if (results.size >= maxResults) return
                }
            }
        }
    }

    /** 生成 n 组合。 */
    private fun <T> combinations(list: List<T>, n: Int): List<List<T>> {
        if (n <= 0) return listOf(emptyList())
        if (list.size < n) return emptyList()
        val result = mutableListOf<List<T>>()
        fun dfs(start: Int, current: MutableList<T>) {
            if (current.size == n) { result.add(current.toList()); return }
            for (i in start until list.size) {
                current.add(list[i])
                dfs(i + 1, current)
                current.removeAt(current.lastIndex)
            }
        }
        dfs(0, mutableListOf())
        return result
    }

    private fun groupSwapsByFans(hand: Hand, swaps: List<SwapPath>): List<SwapTarget> {
        val fanMap = mutableMapOf<String, MutableList<SwapPath>>()
        for (sw in swaps) {
            val afterDiscard = hand.concealed.toMutableList()
            for (d in sw.discardTiles) afterDiscard.remove(d)
            val baseConc = (afterDiscard + sw.drawTiles).sorted()

            if (sw.resultingWaits.isEmpty()) {
                val winHand = hand.withConcealed(baseConc)
                for (decomp in WinChecker.getAllDecompositions(winHand)) {
                    val result = FanScorer.score(FanContext(decomp, winHand, WinInfo(sw.drawTiles.last())))
                    if (result.meetsMinimum)
                        for (rule in result.allDetected) fanMap.getOrPut(rule.id) { mutableListOf() }.add(sw)
                }
            } else {
                for (wait in sw.resultingWaits) {
                    val winHand = hand.withConcealed((baseConc + wait).sorted())
                    if (!winHand.isValidWinSize()) continue
                    for (decomp in WinChecker.getAllDecompositions(winHand)) {
                        val result = FanScorer.score(FanContext(decomp, winHand, WinInfo(wait)))
                        if (result.meetsMinimum)
                            for (rule in result.allDetected) fanMap.getOrPut(rule.id) { mutableListOf() }.add(sw)
                    }
                }
            }
        }
        return fanMap.entries.map { (id, paths) ->
            val unique = paths.distinctBy { Pair(it.discardTiles, it.drawTiles) }
            val rule = FanRegistry.byId(id) ?: return@map null
            SwapTarget(rule, unique.sumOf { it.probability }, unique.sortedByDescending { it.probability })
        }.filterNotNull().sortedByDescending { it.totalProbability }
    }

    private fun groupByFans(hand: Hand, improvements: List<ImprovementPath>): List<FanTarget> {
        val fanMap = mutableMapOf<String, MutableList<ImprovementTile>>()
        for (imp in improvements) {
            for (wait in imp.resultingWaits) {
                val winHand = hand.withConcealed((hand.concealed + imp.drawTile + wait).sorted())
                if (!winHand.isValidWinSize()) continue
                val decomps = WinChecker.getAllDecompositions(winHand)
                if (decomps.isEmpty()) continue
                val result = FanScorer.score(FanContext(decomps.first(), winHand, WinInfo(wait)))
                for (rule in result.allDetected) {
                    fanMap.getOrPut(rule.id) { mutableListOf() }
                        .add(ImprovementTile(imp.drawTile, imp.remainingCount, imp.probability, imp.resultingWaits))
                }
            }
        }
        return fanMap.entries.map { (ruleId, tiles) ->
            val unique = tiles.distinctBy { it.tile }
            val rule = FanRegistry.byId(ruleId) ?: return@map null
            FanTarget(rule, unique.sumOf { it.probability }, unique.sortedByDescending { it.probability })
        }.filterNotNull().sortedByDescending { it.totalProbability }
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
