package com.mahjong.guobiao.engine.counter

import com.mahjong.guobiao.model.Hand
import com.mahjong.guobiao.model.TableState
import com.mahjong.guobiao.model.TileType

/**
 * 剩余张数计算。
 *
 * 序数/字牌：每种 4 张，剩余 = 4 - (自暗手 + 自副露 + 全家牌河 + 他家副露)
 * 花牌：每种 1 张，剩余 = 1 - 已出
 *
 * 注：自家暗手与副露计入"已用"；他家暗手未知，不计入。
 *     暗杠虽对外不可见，但自家辅助时自信息完整，4 张都计入已用。
 */
object TileCounter {

    /** 某牌的剩余张数。 */
    fun remainingCount(type: TileType, hand: Hand, tableState: TableState): Int {
        val total = if (type.isFlower) 1 else 4
        val visible = visibleCount(type, hand, tableState)
        return (total - visible).coerceAtLeast(0)
    }

    /** 该牌在场上已可见/已用的张数（含自家全部信息）。 */
    fun visibleCount(type: TileType, hand: Hand, tableState: TableState): Int {
        var n = 0
        // 自家暗手
        n += hand.concealed.count { it == type }
        // 自家副露（含暗杠4张）
        n += hand.melds.flatMap { it.tiles }.count { it == type }
        // 自家花牌
        if (type.isFlower) n += hand.flowers.count { it == type }
        // 全家牌河、副露、花牌（含自家明示信息，不重复：自家副露已在上面计，这里只计他家）
        for (player in tableState.players) {
            if (player.seat == tableState.selfSeat) {
                // 自家明示：牌河（副露已计，花牌已计）
                n += player.discards.count { it == type }
            } else {
                n += player.visibleCount(type)
            }
        }
        return n
    }
}
