package com.mahjong.guobiao.engine.win

import com.mahjong.guobiao.model.Decomposition
import com.mahjong.guobiao.model.DecompositionType
import com.mahjong.guobiao.model.Hand
import com.mahjong.guobiao.model.Meld
import com.mahjong.guobiao.model.Suit
import com.mahjong.guobiao.model.TileType

/**
 * 全不靠 / 七星不靠 / 组合龙。
 *
 * ⚠️ 全不靠与七星不靠的精确定义需对照《中国麻将竞赛规则》官方文本核实。
 * 本实现采用通行理解：
 *  - 14 张牌全部单张（无对子、无面子）
 *  - 序数牌按 147/258/369 三组划分；同花色内不能有同组重复（保证不靠，无可成顺子）
 *  - 字牌各不相同
 *  - 含全部 7 种字牌则为七星不靠（24番），否则全不靠（12番）
 *  - 组合龙（12番）：9 张序数牌，三花色分别取 {1,4,7}、{2,5,8}、{3,6,9} 之一（置换）
 *
 * 全不靠要求手牌中序数牌部分构成组合龙的子集/全集结构。
 */
object AllNonAdjacentChecker {

    /** 147/258/369 三组。rank -> 组索引(0/1/2)。 */
    private fun groupOf(rank: Int): Int = (rank - 1) % 3

    fun check(hand: Hand): Decomposition? {
        // ⚠️ 全不靠/七星不靠精确定义需对照官方规则书核实。
        // 此前宽松实现会误判（如 13 幺+2m 被当作七星不靠，导致听牌假阳性）。
        // 为保证听牌/番种结果正确，MVP 阶段先禁用，待规则核实后启用。
        return null
    }

    /** 判定是否含组合龙：9 张序数牌，三花色分别取 {1,4,7}、{2,5,8}、{3,6,9} 的置换。 */
    fun hasCombinationDragon(counts: com.mahjong.guobiao.model.TileCounts): Boolean {
        val groups = listOf(setOf(1, 4, 7), setOf(2, 5, 8), setOf(3, 6, 9))
        val suits = listOf(Suit.MANZU, Suit.PINZU, Suit.SOUZU)
        // 三花色分别对应 groups 的某种排列
        for (perm in groups.permutations()) {
            val match = suits.indices.all { i ->
                val suit = suits[i]
                val want = perm[i]
                want.all { r -> counts[tileOf(suit, r)] > 0 }
            }
            if (match) return true
        }
        return false
    }

    private fun tileOf(suit: Suit, rank: Int): TileType = when (suit) {
        Suit.MANZU -> TileType.man(rank)
        Suit.PINZU -> TileType.pin(rank)
        Suit.SOUZU -> TileType.sou(rank)
        else -> error("序数牌花色")
    }
}

private fun <T> List<T>.permutations(): List<List<T>> {
    if (size <= 1) return listOf(this)
    val result = mutableListOf<List<T>>()
    for (i in indices) {
        val rest = filterIndexed { idx, _ -> idx != i }
        for (p in rest.permutations()) result.add(listOf(this[i]) + p)
    }
    return result
}
