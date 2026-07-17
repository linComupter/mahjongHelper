package com.mahjong.guobiao.engine.fan

import com.mahjong.guobiao.model.Decomposition
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
 */
data class FanContext(
    val decomposition: Decomposition,
    val hand: Hand,
    val winInfo: WinInfo
) {
    val selfSeat: PlayerSeat get() = winInfo.selfSeat
    val seatWind: PlayerSeat get() = winInfo.selfSeat
    val prevailingWind: PlayerSeat get() = winInfo.prevailingWind
}
