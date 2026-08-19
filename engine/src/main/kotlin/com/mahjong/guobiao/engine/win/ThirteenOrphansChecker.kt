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

    fun check(hand: Hand, wildcard: TileType? = null): Decomposition? {
        if (!hand.isClosed) return null
        if (!hand.isValidWinSize()) return null
        if (wildcard == null) return checkPlain(hand)
        return checkWildcard(hand, wildcard)
    }

    private fun checkPlain(hand: Hand): Decomposition? {
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

    /**
     * 赖子十三幺：缺的幺九字用赖子顶上，雀头也可由赖子充当。
     * 规则要点：
     * - 非幺九字实牌为 0；
     * - 每张实牌幺九字至多 2 张（2 张可作雀头）；
     * - 需用赖子补足的位：非赖子幺九字中实牌为 0 的位，加上赖子自身所在的幺九字位（若赖子为幺九字且 w>0，该位正好由 1 个赖子充当）；
     * - 若实牌无雀头，还需要 1 个赖子当雀头。赖子总数须恰好等于上述两个需求之和。
     */
    private fun checkWildcard(hand: Hand, wildcard: TileType): Decomposition? {
        val counts = hand.concealedCounts()
        val w = counts[wildcard]
        counts[wildcard] = 0

        // 非幺九字实牌必须为 0
        for (t in TileType.ALL_NON_FLOWER) {
            if (t == wildcard) continue
            if (t !in TileType.TERMINALS_HONORS && counts[t] > 0) return null
        }
        for (t in TileType.TERMINALS_HONORS) {
            if (t != wildcard && counts[t] > 2) return null
        }

        var pairTile: TileType? = null
        for (t in TileType.TERMINALS_HONORS) {
            if (t == wildcard) continue
            if (counts[t] == 2) {
                if (pairTile != null) return null
                pairTile = t
            }
        }

        // 需赖子补足的幺九字位：每个缺位的幺九字都需 1 个赖子；
        // 若赖子自身为幺九字，该位也需 1 个赖子充当（缺位与否都计入需求）。
        var missing = 0
        for (t in TileType.TERMINALS_HONORS) {
            if (t == wildcard || counts[t] == 0) missing++
        }
        // 雀头需求：实牌无雀头 → 需 1 个赖子当雀头
        val needW = missing + (if (pairTile == null) 1 else 0)
        if (w != needW) return null

        val pair = pairTile?.let { Meld.pair(it) } ?: Meld.pair(wildcard)
        return Decomposition(DecompositionType.THIRTEEN_ORPHANS, emptyList(), pair)
    }
}
