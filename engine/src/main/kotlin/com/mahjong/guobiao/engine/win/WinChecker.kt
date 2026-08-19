package com.mahjong.guobiao.engine.win

import com.mahjong.guobiao.model.Decomposition
import com.mahjong.guobiao.model.Hand
import com.mahjong.guobiao.model.TileType

/**
 * 和牌判定总入口。返回手牌所有有效和牌分解（含标准形与特殊形）。
 *
 * 一次和牌可能有多种分解，全部保留以供番种判定（如九莲宝灯需特定分解）。
 *
 * @param wildcard 赖子牌（赖子模式）。非 null 时该牌型的牌张视为赖子，可替代任意牌；
 *                 满足平胡（标准形/七对/十三幺）即算和牌。null 表示纯大模式（无赖子）。
 */
object WinChecker {

    /** 是否和牌（存在至少一种有效分解）。 */
    fun isWin(hand: Hand, wildcard: TileType? = null): Boolean = getAllDecompositions(hand, wildcard).isNotEmpty()

    /** 返回全部分解。 */
    fun getAllDecompositions(hand: Hand, wildcard: TileType? = null): List<Decomposition> {
        if (!hand.isValidWinSize()) return emptyList()

        val results = mutableListOf<Decomposition>()
        results += if (wildcard != null) StandardDecomposer.decomposeWildcard(hand, wildcard)
            else StandardDecomposer.decompose(hand)
        SevenPairsChecker.check(hand, wildcard)?.let { results.add(it) }
        SevenPairsChecker.checkLuxury(hand, wildcard)?.let { results.add(it) }
        ThirteenOrphansChecker.check(hand, wildcard)?.let { results.add(it) }
        return results.distinct()
    }
}
