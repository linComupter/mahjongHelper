package com.mahjong.guobiao.engine

import com.mahjong.guobiao.engine.tenpai.TenpaiCalculator
import com.mahjong.guobiao.engine.win.WinChecker
import com.mahjong.guobiao.model.Hand
import com.mahjong.guobiao.model.TableState
import com.mahjong.guobiao.model.TileType

/**
 * 手牌发展方向分析：非听牌时计算向听数、改进牌、发展概率。
 *
 * 算法：
 *  1. 计算向听数 = 13 - 3*meldCount - concealed.size
 *  2. 1向听：枚举 34 种牌，若加牌后达到听牌态且能听牌即记为改进牌
 *  3. 概率 = 改进牌剩余张数 / 总剩余张数
 */
object DevelopmentAnalyzer {

    /** 单张改进牌的路径分析。 */
    data class ImprovementPath(
        val drawTile: TileType,             // 摸到的改进牌
        val remainingCount: Int,            // 该牌剩余张数
        val probability: Double,            // 摸到该牌的概率
        val resultingWaits: List<TileType>, // 摸到后听哪些牌
        val improvementType: ImprovementType // 改进后状态
    )

    enum class ImprovementType { TO_TENPAI, TO_WIN }

    /** 总体发展分析结果。 */
    data class DevelopmentResult(
        val currentShanten: Int,            // 当前向听数（0=听牌, -1=和牌）
        val totalRemaining: Int,            // 牌山中剩余总张数
        val improvements: List<ImprovementPath> // 所有可改进手牌的路径（按概率降序）
    )

    /**
     * 分析手牌发展方向。
     * - 听牌态/和牌态返回 empty improvements
     * - 非听牌态返回所有 1向听改进路径
     */
    fun analyze(hand: Hand, tableState: TableState): DevelopmentResult {
        val totalTiles = 136  // 国标用 136 张（不含花）
        val concealedSize = hand.concealed.size
        val meldCount = hand.meldCount
        val tenpaiSize = 13 - 3 * meldCount

        // 计算向听数
        if (concealedSize >= tenpaiSize) {
            // 已达听牌态或和牌态
            val isWin = if (concealedSize == tenpaiSize + 1) {
                WinChecker.isWin(hand)
            } else false
            return DevelopmentResult(
                currentShanten = if (isWin) -1 else 0,
                totalRemaining = calcRemainingTotal(hand, tableState, totalTiles),
                improvements = emptyList()
            )
        }

        val shanten = tenpaiSize - concealedSize

        // 仅支持 1向听分析（更深向听枚举量太大）
        if (shanten != 1) {
            return DevelopmentResult(
                currentShanten = shanten,
                totalRemaining = calcRemainingTotal(hand, tableState, totalTiles),
                improvements = emptyList()
            )
        }

        // 1向听：枚举摸牌 → 检查是否进入听牌态
        val improvements = mutableListOf<ImprovementPath>()
        val totalRemaining = calcRemainingTotal(hand, tableState, totalTiles)

        for (t in TileType.ALL_NON_FLOWER) {
            val remaining = 4 - visibleCount(t, hand, tableState)
            if (remaining <= 0) continue

            val newHand = hand.withConcealed((hand.concealed + t).sorted())
            if (!newHand.isValidTenpaiSize()) continue

            // 检查是否听牌（或直接和牌）
            val isWin = WinChecker.isWin(newHand)
            val waits = if (isWin) emptyList() else TenpaiCalculator.waitingTiles(newHand)

            if (isWin || waits.isNotEmpty()) {
                val prob = remaining.toDouble() / totalRemaining
                improvements.add(ImprovementPath(
                    drawTile = t,
                    remainingCount = remaining,
                    probability = prob,
                    resultingWaits = waits,
                    improvementType = if (isWin) ImprovementType.TO_WIN else ImprovementType.TO_TENPAI
                ))
            }
        }

        improvements.sortByDescending { it.probability }
        return DevelopmentResult(shanten, totalRemaining, improvements)
    }

    /** 计算牌山中剩余总张数。 */
    private fun calcRemainingTotal(hand: Hand, tableState: TableState, total: Int): Int {
        var visible = 0
        for (t in TileType.ALL_NON_FLOWER) {
            // 自家暗手+副露
            var v = hand.concealed.count { it == t } + hand.melds.flatMap { it.tiles }.count { it == t }
            // 全场牌河+他家副露+花
            for (p in tableState.players) {
                if (p.seat == tableState.selfSeat) {
                    v += p.discards.count { it == t }
                } else {
                    v += p.visibleCount(t)
                }
            }
            visible += minOf(v, 4)  // cap at 4
        }
        return (total - visible).coerceAtLeast(0)
    }

    private fun visibleCount(tile: TileType, hand: Hand, tableState: TableState): Int {
        var v = hand.concealed.count { it == tile } +
                hand.melds.flatMap { it.tiles }.count { it == tile }
        for (p in tableState.players) {
            if (p.seat == tableState.selfSeat)
                v += p.discards.count { it == tile }
            else
                v += p.visibleCount(tile)
        }
        return v
    }
}
