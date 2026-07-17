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
        val drawTile: TileType,
        val remainingCount: Int,
        val probability: Double,
        val resultingWaits: List<TileType>,
        val improvementType: ImprovementType
    )
    enum class ImprovementType { TO_TENPAI, TO_WIN }

    data class FanTarget(
        val fanRule: FanRule,
        val totalProbability: Double,
        val improvementTiles: List<ImprovementTile>
    )
    data class ImprovementTile(
        val tile: TileType,
        val remainingCount: Int,
        val probability: Double,
        val resultingWaits: List<TileType>
    )

    /** 替换式发展路径（弃X摸Y）。 */
    data class SwapPath(
        val discardTile: TileType,
        val drawTile: TileType,
        val remainingCount: Int,
        val probability: Double,
        val resultingWaits: List<TileType>
    )

    /** 按番种聚合的替换式目标。 */
    data class SwapTarget(
        val fanRule: FanRule,
        val totalProbability: Double,
        val swapPaths: List<SwapPath>
    )

    data class DevelopmentResult(
        val currentShanten: Int,
        val totalRemaining: Int,
        val improvements: List<ImprovementPath>,
        val fanTargets: List<FanTarget>,
        val swapTargets: List<SwapTarget>,     // 替换式分析结果
        val isTenpaiNoFan: Boolean = false      // 听牌但无有效番种
    )

    /** 听牌态是否有任意等待牌可达成 8 番起和。 */
    fun hasValidTenpai(hand: Hand): Boolean {
        if (!hand.isValidTenpaiSize()) return false
        val waits = TenpaiCalculator.waitingTiles(hand)
        return waits.any { wait ->
            val winHand = hand.withConcealed((hand.concealed + wait).sorted())
            WinChecker.getAllDecompositions(winHand).any { decomp ->
                FanScorer.score(FanContext(decomp, winHand, WinInfo(wait))).meetsMinimum
            }
        }
    }

    /** 主入口：听牌/和非听牌，返回统一结果。 */
    fun analyze(hand: Hand, tableState: TableState): DevelopmentResult {
        val totalTiles = 136
        val tenpaiSize = hand.concealedCountForTenpai()
        val winSize = hand.concealedCountForWin()
        val size = hand.concealed.size

        // 已和牌
        if (size == winSize && WinChecker.isWin(hand)) {
            return DevelopmentResult(-1, remainingTotal(hand, tableState, totalTiles), emptyList(), emptyList(), emptyList())
        }

        // 听牌态：检查是否有有效番种
        if (size == tenpaiSize && hand.isValidTenpaiSize()) {
            val waits = TenpaiCalculator.waitingTiles(hand)
            val hasValid = waits.isNotEmpty() && hasValidTenpai(hand)
            if (hasValid) {
                // 听牌且有有效番种 → 正常听牌路径
                val imps = waits.map { wait ->
                    val rem = 4 - visibleCount(wait, hand, tableState)
                    ImprovementPath(wait, rem, rem.toDouble() / remainingTotal(hand, tableState, totalTiles), emptyList(), ImprovementType.TO_WIN)
                }
                val fans = groupByFans(hand, imps, false)
                return DevelopmentResult(0, remainingTotal(hand, tableState, totalTiles), emptyList(), fans, emptyList())
            } else if (waits.isNotEmpty()) {
                // 听牌但无效 → swap 分析
                val swaps = analyzeSwap(hand, tableState, totalTiles, tenpaiSize)
                return DevelopmentResult(0, remainingTotal(hand, tableState, totalTiles), emptyList(), emptyList(), swaps, true)
            }
            return DevelopmentResult(0, remainingTotal(hand, tableState, totalTiles), emptyList(), emptyList(), emptyList())
        }

        // 非听牌：1向听先试 add-one
        val shanten = tenpaiSize - size
        val totalRem = remainingTotal(hand, tableState, totalTiles)

        // always run swap analysis for non-tenpai
        val swaps = analyzeSwap(hand, tableState, totalTiles, tenpaiSize)
        return DevelopmentResult(shanten, totalRem, emptyList(), emptyList(), swaps)
    }

    /** 替换式分析：暗手每张牌尝试弃→摸。 */
    private fun analyzeSwap(hand: Hand, tableState: TableState, totalTiles: Int, targetSize: Int): List<SwapTarget> {
        val totalRem = remainingTotal(hand, tableState, totalTiles)
        val swapPaths = mutableListOf<SwapPath>()

        // 去重：暗手中每张牌只试一次
        val discardCandidates = hand.concealed.distinct()

        for (discard in discardCandidates) {
            val afterDiscard = hand.concealed.toMutableList()
            afterDiscard.remove(discard)

            for (draw in TileType.ALL_NON_FLOWER) {
                if (draw == discard) continue // 换同样的牌无意义
                val remaining = 4 - visibleCount(draw, hand, tableState)
                if (remaining <= 0) continue

                val newConcealed = (afterDiscard + draw).sorted()
                val newHand = hand.withConcealed(newConcealed)

                if (newConcealed.size == targetSize && newHand.isValidTenpaiSize()) {
                    val waits = TenpaiCalculator.waitingTiles(newHand)
                    if (waits.isNotEmpty()) {
                        swapPaths.add(SwapPath(discard, draw, remaining, remaining.toDouble() / totalRem, waits))
                    }
                } else if (newConcealed.size == targetSize + 1 && WinChecker.isWin(newHand)) {
                    swapPaths.add(SwapPath(discard, draw, remaining, remaining.toDouble() / totalRem, emptyList()))
                }
            }
        }

        swapPaths.sortByDescending { it.probability }
        return groupSwapsByFans(hand, swapPaths)
    }

    /** 将 swap 路径按番种聚合。 */
    private fun groupSwapsByFans(hand: Hand, swaps: List<SwapPath>): List<SwapTarget> {
        val fanMap = mutableMapOf<String, MutableList<SwapPath>>()

        for (sw in swaps) {
            val afterDiscard = hand.concealed.toMutableList()
            afterDiscard.remove(sw.discardTile)
            val baseConcealed = (afterDiscard + sw.drawTile).sorted()

            if (sw.resultingWaits.isEmpty()) {
                // 直接和牌
                val winHand = hand.withConcealed(baseConcealed)
                val decomps = WinChecker.getAllDecompositions(winHand)
                for (decomp in decomps) {
                    val result = FanScorer.score(FanContext(decomp, winHand, WinInfo(sw.drawTile)))
                    if (result.meetsMinimum) {
                        for (rule in result.allDetected) {
                            fanMap.getOrPut(rule.id) { mutableListOf() }.add(sw)
                        }
                    }
                }
            } else {
                for (wait in sw.resultingWaits) {
                    val winHand = hand.withConcealed((baseConcealed + wait).sorted())
                    if (!winHand.isValidWinSize()) continue
                    val decomps = WinChecker.getAllDecompositions(winHand)
                    for (decomp in decomps) {
                        val result = FanScorer.score(FanContext(decomp, winHand, WinInfo(wait)))
                        if (result.meetsMinimum) {
                            for (rule in result.allDetected) {
                                fanMap.getOrPut(rule.id) { mutableListOf() }.add(sw)
                            }
                        }
                    }
                }
            }
        }

        return fanMap.entries.map { (id, paths) ->
            val uniquePaths = paths.distinctBy { Pair(it.discardTile, it.drawTile) }
            val totalProb = uniquePaths.sumOf { it.probability }
            val rule = FanRegistry.byId(id) ?: return@map null
            SwapTarget(rule, totalProb, uniquePaths.sortedByDescending { it.probability })
        }.filterNotNull().sortedByDescending { it.totalProbability }
    }

    /** add-one 模式的番种聚合。 */
    private fun groupByFans(hand: Hand, improvements: List<ImprovementPath>, isAddMode: Boolean): List<FanTarget> {
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
            val uniqueTiles = tiles.distinctBy { it.tile }
            val rule = FanRegistry.byId(ruleId) ?: return@map null
            FanTarget(rule, uniqueTiles.sumOf { it.probability }, uniqueTiles.sortedByDescending { it.probability })
        }.filterNotNull().sortedByDescending { it.totalProbability }
    }

    private fun remainingTotal(hand: Hand, tableState: TableState, total: Int): Int {
        var visible = 0
        for (t in TileType.ALL_NON_FLOWER) {
            var v = hand.concealed.count { it == t } + hand.melds.flatMap { it.tiles }.count { it == t }
            for (p in tableState.players) {
                if (p.seat == tableState.selfSeat) v += p.discards.count { it == t }
                else v += p.visibleCount(t)
            }
            visible += minOf(v, 4)
        }
        return (total - visible).coerceAtLeast(0)
    }

    private fun visibleCount(tile: TileType, hand: Hand, tableState: TableState): Int {
        var v = hand.concealed.count { it == tile } + hand.melds.flatMap { it.tiles }.count { it == tile }
        for (p in tableState.players) {
            if (p.seat == tableState.selfSeat) v += p.discards.count { it == tile }
            else v += p.visibleCount(tile)
        }
        return v
    }
}
