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

    /** 14张(winSize)未和牌时的弃牌建议：弃某张后剩余13张的听牌与可达番种。 */
    data class DiscardSuggestion(
        val discardTile: TileType,            // 建议弃掉的牌
        val resultingWaits: List<TileType>,   // 弃后听牌
        val reachesMinimum: Boolean,          // 弃后听牌能否 1 番起和
        val possibleFans: List<FanRule>,      // 弃后可达番种（聚合所有听牌的所有分解）
        val waitCount: Int                    // 听牌张数
    )

    fun hasValidTenpai(hand: Hand, wildcard: TileType? = null): Boolean {
        if (!hand.isValidTenpaiSize()) return false
        return TenpaiCalculator.waitingTiles(hand, wildcard = wildcard).any { wait ->
            val winHand = hand.withConcealed((hand.concealed + wait).sorted())
            WinChecker.getAllDecompositions(winHand, wildcard).any { decomp ->
                FanScorer.score(FanContext.of(decomp, winHand, WinInfo(wait), wildcard)).meetsMinimum
            }
        }
    }

    /**
     * 14张(winSize)未和牌的弃牌建议：枚举弃掉每张暗手牌，分析弃后13张的听牌与可达番种。
     * 调用方需保证 size == winSize 且未和牌。tableState 预留用于后续扩展听牌剩余张数。
     * @param wildcard 赖子牌（赖子模式）；null 为纯大模式。
     */
    fun analyzeDiscard(hand: Hand, @Suppress("UNUSED_PARAMETER") tableState: TableState, wildcard: TileType? = null): List<DiscardSuggestion> {
        val suggestions = mutableListOf<DiscardSuggestion>()
        for (disc in hand.concealed.distinct()) {
            val after = hand.concealed.toMutableList().apply { remove(disc) }
            val newHand = hand.withConcealed(after.sorted())
            if (!newHand.isValidTenpaiSize()) continue
            val waits = TenpaiCalculator.waitingTiles(newHand, wildcard = wildcard)
            if (waits.isEmpty()) {
                suggestions.add(DiscardSuggestion(disc, emptyList(), false, emptyList(), 0))
                continue
            }
            val fans = mutableSetOf<FanRule>()
            var reachesMin = false
            for (wait in waits) {
                val winHand = newHand.withConcealed((after + wait).sorted())
                if (!winHand.isValidWinSize()) continue
                for (decomp in WinChecker.getAllDecompositions(winHand, wildcard)) {
                    val result = FanScorer.score(FanContext.of(decomp, winHand, WinInfo(wait), wildcard))
                    if (result.meetsMinimum) {
                        reachesMin = true
                        fans.addAll(result.allDetected)
                    }
                }
            }
            suggestions.add(DiscardSuggestion(disc, waits, reachesMin, fans.toList(), waits.size))
        }
        return suggestions.sortedWith(
            compareByDescending<DiscardSuggestion> { it.reachesMinimum }
                .thenByDescending { it.waitCount }
                .thenBy { it.discardTile.code }
        )
    }

    fun analyze(hand: Hand, tableState: TableState, wildcard: TileType? = null): DevelopmentResult {
        val totalTiles = 136
        val tenpaiSize = hand.concealedCountForTenpai()
        val winSize = hand.concealedCountForWin()
        val size = hand.concealed.size

        if (size == winSize && WinChecker.isWin(hand, wildcard))
            return DevelopmentResult(-1, remainingTotal(hand, tableState, totalTiles), emptyList(), emptyList(), emptyList())

        if (size == tenpaiSize && hand.isValidTenpaiSize()) {
            val waits = TenpaiCalculator.waitingTiles(hand, wildcard = wildcard)
            val hasValid = waits.isNotEmpty() && hasValidTenpai(hand, wildcard)
            if (hasValid) {
                val imps = waits.map { w ->
                    val r = 4 - visibleCount(w, hand, tableState)
                    ImprovementPath(w, r, r.toDouble() / remainingTotal(hand, tableState, totalTiles), emptyList(), ImprovementType.TO_WIN)
                }
                return DevelopmentResult(0, remainingTotal(hand, tableState, totalTiles), emptyList(), groupByFans(hand, imps, wildcard), emptyList())
            } else if (waits.isNotEmpty()) {
                val (swaps, used) = analyzeSwap(hand, tableState, totalTiles, tenpaiSize, wildcard)
                return DevelopmentResult(0, remainingTotal(hand, tableState, totalTiles), emptyList(), emptyList(), swaps, true, used)
            }
            return DevelopmentResult(0, remainingTotal(hand, tableState, totalTiles), emptyList(), emptyList(), emptyList())
        }

        val shanten = tenpaiSize - size
        val (swaps, used) = analyzeSwap(hand, tableState, totalTiles, tenpaiSize, wildcard)
        return DevelopmentResult(shanten, remainingTotal(hand, tableState, totalTiles), emptyList(), emptyList(), swaps, maxDepthUsed = used)
    }

    /** 替换式分析：由 FanReverseAnalyzer 按番种倒推。 */
    private fun analyzeSwap(hand: Hand, tableState: TableState, totalTiles: Int, targetSize: Int, wildcard: TileType?): Pair<List<SwapTarget>, Int> {
        val depth = AnalysisSettings.swapDepth.coerceIn(1, AnalysisSettings.MAX_DEPTH)
        val targets = FanReverseAnalyzer.analyze(hand, tableState, wildcard)
        val mapped = targets.map { t -> SwapTarget(t.fanRule, t.totalProbability,
            t.swapPaths.map { sp -> SwapPath(sp.discardTiles, sp.drawTiles, sp.remainingCount, sp.probability, sp.resultingWaits, sp.swapCount) }) }
        return mapped to depth
    }

    private fun groupByFans(hand: Hand, improvements: List<ImprovementPath>, wildcard: TileType? = null): List<FanTarget> {
        val fanMap = mutableMapOf<String, MutableList<ImprovementTile>>()
        for (imp in improvements) {
            for (wait in imp.resultingWaits) {
                val winHand = hand.withConcealed((hand.concealed + imp.drawTile + wait).sorted())
                if (!winHand.isValidWinSize()) continue
                val decomps = WinChecker.getAllDecompositions(winHand, wildcard)
                if (decomps.isEmpty()) continue
                val result = FanScorer.score(FanContext.of(decomps.first(), winHand, WinInfo(wait), wildcard))
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
