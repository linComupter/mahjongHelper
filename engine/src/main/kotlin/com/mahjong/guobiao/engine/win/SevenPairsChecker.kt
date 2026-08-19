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
    fun check(hand: Hand, wildcard: TileType? = null): Decomposition? {
        if (!hand.isClosed) return null
        if (!hand.isValidWinSize()) return null
        if (wildcard == null) return checkPlain(hand)
        return checkWildcard(hand, wildcard, allowLuxury = false)
    }

    /** 豪华七对（含4张同牌拆为两对）：支持1~3组4张。 */
    fun checkLuxury(hand: Hand, wildcard: TileType? = null): Decomposition? {
        if (!hand.isClosed) return null
        if (!hand.isValidWinSize()) return null
        if (wildcard == null) return checkLuxuryPlain(hand)
        return checkWildcard(hand, wildcard, allowLuxury = true)
    }

    private fun checkPlain(hand: Hand): Decomposition? {
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

    private fun checkLuxuryPlain(hand: Hand): Decomposition? {
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

    /**
     * 赖子七对：赖子可替代任意牌凑成对子。
     * - 允许数据：c=1 的单张需 1 个赖子凑对；c=2 直接成对；c=3/c=4 仅在 allowLuxury 时接受（各计 1 组 4 张）。
     * - 剩余的赖子以两两一对的形式补足对子。
     */
    private fun checkWildcard(hand: Hand, wildcard: TileType, allowLuxury: Boolean): Decomposition? {
        val counts = hand.concealedCounts()
        val w = counts[wildcard]
        if (w == 0) return if (allowLuxury) checkLuxuryPlain(hand) else checkPlain(hand)
        counts[wildcard] = 0

        val pairs = mutableListOf<TileType>()
        var singles = 0       // 需要 1 个赖子补足成对的单张
        var fourGroups = 0
        for (t in TileType.ALL_NON_FLOWER) {
            when (counts[t]) {
                0 -> continue
                1 -> { pairs.add(t); singles++ }
                2 -> pairs.add(t)
                3 -> {
                    if (!allowLuxury) return null
                    pairs.add(t); pairs.add(t); fourGroups++; singles++ // 3张中1个单张需赖子 → 同牌4张
                }
                4 -> {
                    if (!allowLuxury) return null
                    pairs.add(t); pairs.add(t); fourGroups++
                }
                else -> return null
            }
        }
        if (w < singles) return null
        val remW = w - singles
        if (remW % 2 != 0) return null
        pairs.addAll(List(remW / 2) { wildcard })  // 剩余赖子两两一对（按自身牌计）
        if (pairs.size != 7) return null
        if (allowLuxury && fourGroups !in 1..3) return null
        if (!allowLuxury && fourGroups != 0) return null
        return Decomposition(DecompositionType.SEVEN_PAIRS, pairs.map { Meld.pair(it) }, null)
    }
}
