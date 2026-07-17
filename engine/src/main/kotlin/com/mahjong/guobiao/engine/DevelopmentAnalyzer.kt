package com.mahjong.guobiao.engine

import com.mahjong.guobiao.engine.fan.FanContext
import com.mahjong.guobiao.engine.fan.FanRule
import com.mahjong.guobiao.engine.fan.FanScorer
import com.mahjong.guobiao.engine.fan.WinInfo
import com.mahjong.guobiao.engine.tenpai.TenpaiCalculator
import com.mahjong.guobiao.engine.win.WinChecker
import com.mahjong.guobiao.model.Hand
import com.mahjong.guobiao.model.TableState
import com.mahjong.guobiao.model.TileType

/**
 * 手牌发展方向分析：向听数 → 改进牌 → 可达番种 → 概率。
 */
object DevelopmentAnalyzer {

    data class ImprovementPath(
        val drawTile: TileType,
        val remainingCount: Int,
        val probability: Double,
        val resultingWaits: List<TileType>,
        val improvementType: ImprovementType
    )

    enum class ImprovementType { TO_TENPAI, TO_WIN }

    /** 按番种聚合的发展目标。 */
    data class FanTarget(
        val fanRule: FanRule,                          // 可达番种
        val totalProbability: Double,                  // 发展至此番种的总概率
        val improvementTiles: List<ImprovementTile>    // 能进入此番种的所有摸牌路径
    )

    data class ImprovementTile(
        val tile: TileType,
        val remainingCount: Int,
        val probability: Double,
        val resultingWaits: List<TileType>
    )

    data class DevelopmentResult(
        val currentShanten: Int,
        val totalRemaining: Int,
        val improvements: List<ImprovementPath>,
        val fanTargets: List<FanTarget>  // 按总概率降序排列
    )

    fun analyze(hand: Hand, tableState: TableState): DevelopmentResult {
        val totalTiles = 136
        val concealedSize = hand.concealed.size
        val meldCount = hand.meldCount
        val tenpaiSize = 13 - 3 * meldCount

        if (concealedSize >= tenpaiSize) {
            val isWin = concealedSize == tenpaiSize + 1 && WinChecker.isWin(hand)
            return DevelopmentResult(
                if (isWin) -1 else 0, calcRemainingTotal(hand, tableState, totalTiles),
                emptyList(), emptyList()
            )
        }

        val shanten = tenpaiSize - concealedSize
        if (shanten != 1) {
            return DevelopmentResult(shanten, calcRemainingTotal(hand, tableState, totalTiles), emptyList(), emptyList())
        }

        val totalRemaining = calcRemainingTotal(hand, tableState, totalTiles)
        val improvements = mutableListOf<ImprovementPath>()

        for (t in TileType.ALL_NON_FLOWER) {
            val remaining = 4 - visibleCount(t, hand, tableState)
            if (remaining <= 0) continue

            val newHand = hand.withConcealed((hand.concealed + t).sorted())
            if (!newHand.isValidTenpaiSize()) continue

            val isWin = WinChecker.isWin(newHand)
            val waits = if (isWin) emptyList() else TenpaiCalculator.waitingTiles(newHand)

            if (isWin || waits.isNotEmpty()) {
                improvements.add(ImprovementPath(t, remaining, remaining.toDouble() / totalRemaining, waits, if (isWin) ImprovementType.TO_WIN else ImprovementType.TO_TENPAI))
            }
        }
        improvements.sortByDescending { it.probability }

        // 按番种聚合
        val fanTargets = groupByFans(hand, improvements)

        return DevelopmentResult(shanten, totalRemaining, improvements, fanTargets)
    }

    /** 将改进路径按可达番种聚合。 */
    private fun groupByFans(hand: Hand, improvements: List<ImprovementPath>): List<FanTarget> {
        // fanRule.id -> list of ImprovementTile
        val fanMap = mutableMapOf<String, MutableList<ImprovementTile>>()

        for (imp in improvements) {
            // 摸 imp.drawTile 后听 imp.resultingWaits，每个 wait 可成和牌
            for (wait in imp.resultingWaits) {
                val winHand = hand.withConcealed((hand.concealed + imp.drawTile + wait).sorted())
                if (!winHand.isValidWinSize()) continue
                val decomps = WinChecker.getAllDecompositions(winHand)
                if (decomps.isEmpty()) continue
                // 取第一个分解检测番种
                val ctx = FanContext(decomps.first(), winHand, WinInfo(wait))
                val result = FanScorer.score(ctx)
                for (rule in result.allDetected) {
                    fanMap.getOrPut(rule.id) { mutableListOf() }.add(
                        ImprovementTile(imp.drawTile, imp.remainingCount, imp.probability, imp.resultingWaits)
                    )
                }
            }
        }

        return fanMap.entries.map { (ruleId, tiles) ->
            // 去重：同一摸牌可能通过不同 wait 到达同番种，但概率只算一次
            val uniqueTiles = tiles.distinctBy { it.tile }
            val totalProb = uniqueTiles.sumOf { it.probability }
            val rule = com.mahjong.guobiao.engine.fan.FanRegistry.byId(ruleId) ?: return@map null
            FanTarget(rule, totalProb, uniqueTiles.sortedByDescending { it.probability })
        }.filterNotNull().sortedByDescending { it.totalProbability }
    }

    private fun calcRemainingTotal(hand: Hand, tableState: TableState, total: Int): Int {
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
