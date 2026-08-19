package com.mahjong.guobiao.engine.fan

import com.mahjong.guobiao.model.Decomposition
import com.mahjong.guobiao.model.DecompositionType
import com.mahjong.guobiao.model.Hand
import com.mahjong.guobiao.model.PlayerSeat
import com.mahjong.guobiao.model.TableState
import com.mahjong.guobiao.model.TileType

/** 和牌方式。 */
enum class WinMethod {
    SELF_DRAW,       // 自摸
    DISCARD,         // 点炮（他人打出）
    ROBBING_KAN,     // 抢杠和
    LAST_TILE_DRAW,  // 海底捞月（自摸最后一张）
    LAST_DISCARD     // 河底捞鱼（他人最后一张点炮）
}

/** 和牌信息，参与番种判定。 */
data class WinInfo(
    val winTile: TileType,
    val method: WinMethod = WinMethod.SELF_DRAW,
    val selfSeat: PlayerSeat = PlayerSeat.EAST,
    val prevailingWind: PlayerSeat = PlayerSeat.EAST,
    val tableState: TableState? = null
) {
    val isSelfDraw: Boolean get() = method == WinMethod.SELF_DRAW || method == WinMethod.LAST_TILE_DRAW
    val isDiscardWin: Boolean get() = method == WinMethod.DISCARD || method == WinMethod.LAST_DISCARD
    val isRobbingKan: Boolean get() = method == WinMethod.ROBBING_KAN
    val isLastTile: Boolean get() = method == WinMethod.LAST_TILE_DRAW || method == WinMethod.LAST_DISCARD
}

/**
 * 番种判定上下文。一个 Decomposition + Hand + WinInfo 对应一个 FanContext。
 * 不同分解可能满足不同番种，需分别判定取并集/最优。
 *
 * 赖子模式下，[wildcard] 非 null，[hand] 为"赖子落位后的具体牌手牌"
 * （赖子已按分解替换为它们替代的具体牌），番种规则可直接按常规判型。
 */
data class FanContext(
    val decomposition: Decomposition,
    val hand: Hand,
    val winInfo: WinInfo,
    val wildcard: TileType? = null
) {
    val selfSeat: PlayerSeat get() = winInfo.selfSeat
    val seatWind: PlayerSeat get() = winInfo.selfSeat
    val prevailingWind: PlayerSeat get() = winInfo.prevailingWind

    companion object {
        /**
         * 构造番种判定上下文。赖子模式下将赖子替换为分解中指代的具体牌后再判番。
         */
        fun of(decomposition: Decomposition, hand: Hand, winInfo: WinInfo, wildcard: TileType? = null): FanContext {
            if (wildcard == null || hand.concealed.none { it == wildcard }) {
                return FanContext(decomposition, hand, winInfo, wildcard)
            }
            return FanContext(decomposition, substitute(hand, decomposition, wildcard), winInfo, wildcard)
        }

        private fun substitute(hand: Hand, decomp: Decomposition, wildcard: TileType): Hand {
            return if (decomp.type == DecompositionType.THIRTEEN_ORPHANS) {
                val counts = hand.concealedCounts()
                val tiles = mutableListOf<TileType>()
                for (t in TileType.TERMINALS_HONORS) {
                    tiles.add(if (counts[t] > 0) t else wildcard)
                }
                val pairTile = decomp.pair?.tiles?.first() ?: return Hand(tiles.sorted(), hand.melds, hand.flowers)
                tiles.add(if (pairTile == wildcard || counts[pairTile] < 2) wildcard else pairTile)
                Hand(tiles.sorted(), hand.melds, hand.flowers)
            } else {
                val decompTiles = decomp.melds.flatMap { it.tiles } + (decomp.pair?.tiles ?: emptyList())
                val fixedTiles = hand.melds.flatMap { it.tiles }
                val concealed = decompTiles.toMutableList()
                for (t in fixedTiles) concealed.removeAt(concealed.indexOf(t))
                Hand(concealed.sorted(), hand.melds, hand.flowers)
            }
        }
    }
}
