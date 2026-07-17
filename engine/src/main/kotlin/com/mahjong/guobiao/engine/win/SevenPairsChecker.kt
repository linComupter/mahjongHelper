package com.mahjong.guobiao.engine.win

import com.mahjong.guobiao.model.Decomposition
import com.mahjong.guobiao.model.DecompositionType
import com.mahjong.guobiao.model.Hand
import com.mahjong.guobiao.model.Meld
import com.mahjong.guobiao.model.TileType

/**
 * 七对：7 对不同牌型，4 张同牌不能拆成两对。仅限暗手（无副露）。
 */
object SevenPairsChecker {

    fun check(hand: Hand): Decomposition? {
        if (!hand.isClosed) return null
        if (!hand.isValidWinSize()) return null

        val counts = hand.concealedCounts()
        val pairs = mutableListOf<TileType>()
        for (t in TileType.ALL_NON_FLOWER) {
            when (counts[t]) {
                0 -> continue
                2 -> pairs.add(t)
                4 -> return null // 4 张同牌不能算两对
                else -> return null // 奇数张不可能七对
            }
        }
        if (pairs.size != 7) return null

        val pairMelds = pairs.map { Meld.pair(it) }
        // 七对无单一雀头概念，7 对全放 melds，pair=null
        return Decomposition(DecompositionType.SEVEN_PAIRS, pairMelds, null)
    }
}
