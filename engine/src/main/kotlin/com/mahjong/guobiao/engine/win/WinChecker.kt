package com.mahjong.guobiao.engine.win

import com.mahjong.guobiao.model.Decomposition
import com.mahjong.guobiao.model.Hand

/**
 * 和牌判定总入口。返回手牌所有有效和牌分解（含标准形与特殊形）。
 *
 * 一次和牌可能有多种分解，全部保留以供番种判定（如九莲宝灯需特定分解）。
 */
object WinChecker {

    /** 是否和牌（存在至少一种有效分解）。 */
    fun isWin(hand: Hand): Boolean = getAllDecompositions(hand).isNotEmpty()

    /** 返回全部分解。 */
    fun getAllDecompositions(hand: Hand): List<Decomposition> {
        if (!hand.isValidWinSize()) return emptyList()

        val results = mutableListOf<Decomposition>()
        results += StandardDecomposer.decompose(hand)
        SevenPairsChecker.check(hand)?.let { results.add(it) }
        SevenPairsChecker.checkLuxury(hand)?.let { results.add(it) }
        ThirteenOrphansChecker.check(hand)?.let { results.add(it) }
        return results.distinct()
    }
}
