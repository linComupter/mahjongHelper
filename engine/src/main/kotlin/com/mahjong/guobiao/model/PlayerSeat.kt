package com.mahjong.guobiao.model

/** 座位/风位。 */
enum class PlayerSeat {
    EAST, SOUTH, WEST, NORTH;

    /** 逆时针下一家（国标顺序：东→南→西→北）。 */
    fun next(): PlayerSeat = entries[(ordinal + 1) % 4]
}
