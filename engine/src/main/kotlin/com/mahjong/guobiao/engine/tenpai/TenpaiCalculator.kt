package com.mahjong.guobiao.engine.tenpai

import com.mahjong.guobiao.engine.win.WinChecker
import com.mahjong.guobiao.model.Decomposition
import com.mahjong.guobiao.model.Hand
import com.mahjong.guobiao.model.TileType

/**
 * 听牌计算：对 34 种序数/字牌各试加 1 张，调用 WinChecker 判断是否成和。
 *
 * 天然覆盖所有特殊听形：
 *  - 标准听（单骑/边张/嵌张/双碰）
 *  - 七对听（6 对 + 1 单）
 *  - 十三幺听（13 面听 / 单骑）
 *  - 全不靠听
 *
 * @param hand 听牌态手牌（暗手张数 = concealedCountForTenpai）
 * @param handFullCounts 可选：用于剪枝的"已用牌"计数（自暗手+自副露），超出 4 张的牌型不可听
 */
object TenpaiCalculator {

    /** 一张听牌及其对应的和牌分解。 */
    data class WaitingTile(
        val tile: TileType,
        val decompositions: List<Decomposition>,
        val remainingCount: Int? = null
    )

    /**
     * 计算听牌。返回所有听牌及对应分解。
     * @param usedCounts 已使用牌计数（自暗手+自副露），用于剪枝：某牌已用 4 张则不可听。null 不剪枝。
     */
    fun calculate(
        hand: Hand,
        usedCounts: Map<TileType, Int>? = null
    ): List<WaitingTile> {
        require(hand.isValidTenpaiSize()) {
            "听牌需暗手 ${hand.concealedCountForTenpai()} 张，实际 ${hand.concealed.size}"
        }

        val concealedCounts = hand.concealedCounts()
        val waiting = mutableListOf<WaitingTile>()

        for (t in TileType.ALL_NON_FLOWER) {
            // 剪枝：暗手中该牌已达 4 张，无法再摸第 5 张
            val used = concealedCounts[t] + (hand.melds.flatMap { it.tiles }.count { it == t })
            if (used >= 4) continue
            // 额外剪枝（场况）
            if (usedCounts != null) {
                val totalUsed = used + (usedCounts[t] ?: 0)
                if (totalUsed >= 4) continue
            }

            // 模拟摸到 t
            val testHand = hand.withConcealed(hand.concealed + t)
            val decompositions = WinChecker.getAllDecompositions(testHand)
            if (decompositions.isNotEmpty()) {
                waiting.add(WaitingTile(t, decompositions))
            }
        }

        return waiting
    }

    /** 便捷：返回听牌牌型列表。 */
    fun waitingTiles(hand: Hand, usedCounts: Map<TileType, Int>? = null): List<TileType> =
        calculate(hand, usedCounts).map { it.tile }
}
