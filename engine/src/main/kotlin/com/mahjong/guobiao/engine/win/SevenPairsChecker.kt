package com.mahjong.guobiao.engine.win

import com.mahjong.guobiao.model.Decomposition
import com.mahjong.guobiao.model.DecompositionType
import com.mahjong.guobiao.model.Hand
import com.mahjong.guobiao.model.Meld
import com.mahjong.guobiao.model.TileType

/**
 * 七对：7 对不同牌型（不含4张拆两对）。
 * 豪华七对（含4张同牌）由本类中的 checkLuxury 处理。
 */
object SevenPairsChecker {

    /** 标准七对：每种牌恰好2张，7种不同。 */
    fun check(hand: Hand): Decomposition? {
        if (!hand.isClosed) return null
        if (!hand.isValidWinSize()) return null
        val counts = hand.concealedCounts()
        val pairs = mutableListOf<TileType>()
        for (t in TileType.ALL_NON_FLOWER) {
            when (counts[t]) {
                0 -> continue
                2 -> pairs.add(t)
                else -> return null
            }
        }
        if (pairs.size != 7) return null
        return Decomposition(DecompositionType.SEVEN_PAIRS, pairs.map { Meld.pair(it) }, null)
    }

    /** 豪华七对（含4张同牌拆为两对）：支持1~3组4张。 */
    fun checkLuxury(hand: Hand): Decomposition? {
        if (!hand.isClosed) return null
        if (!hand.isValidWinSize()) return null
        val counts = hand.concealedCounts()
        val pairs = mutableListOf<TileType>()
        var fourGroups = 0
        for (t in TileType.ALL_NON_FLOWER) {
            when (counts[t]) {
                0 -> continue
                2 -> pairs.add(t)
                4 -> { pairs.add(t); pairs.add(t); fourGroups++ }
                else -> return null
            }
        }
        if (fourGroups !in 1..3) return null
        if (pairs.size != 7) return null
        return Decomposition(DecompositionType.SEVEN_PAIRS, pairs.map { Meld.pair(it) }, null)
    }
}
