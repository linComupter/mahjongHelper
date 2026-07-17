package com.mahjong.guobiao.engine.win

import com.mahjong.guobiao.model.Decomposition
import com.mahjong.guobiao.model.DecompositionType
import com.mahjong.guobiao.model.Hand
import com.mahjong.guobiao.model.Meld
import com.mahjong.guobiao.model.TileType

/**
 * 十三幺：13 种幺九字（1m,9m,1p,9p,1s,9s,东南西北中发白）各至少 1 张，
 * 且恰有 1 种为 2 张（作雀头），无非幺九字牌。仅限暗手。
 */
object ThirteenOrphansChecker {

    fun check(hand: Hand): Decomposition? {
        if (!hand.isClosed) return null
        if (!hand.isValidWinSize()) return null

        val counts = hand.concealedCounts()

        // 非幺九字牌必须为 0
        for (t in TileType.ALL_NON_FLOWER) {
            if (t !in TileType.TERMINALS_HONORS && counts[t] > 0) return null
        }

        var pairTile: TileType? = null
        for (t in TileType.TERMINALS_HONORS) {
            when (counts[t]) {
                0 -> return null // 缺某种幺九字
                1 -> continue
                2 -> {
                    if (pairTile != null) return null // 超过一个对子
                    pairTile = t
                }
                else -> return null // 超过 2 张
            }
        }

        val pair = pairTile?.let { Meld.pair(it) }
        return Decomposition(DecompositionType.THIRTEEN_ORPHANS, emptyList(), pair)
    }
}
