package com.mahjong.guobiao.model

/** 单家明示状态（牌河 + 副露 + 花牌）。 */
data class PlayerState(
    val seat: PlayerSeat,
    val discards: List<TileType> = emptyList(),
    val melds: List<Meld> = emptyList(),
    val flowers: List<TileType> = emptyList()
) {
    /** 该家已明示的某牌张数。 */
    fun visibleCount(type: TileType): Int {
        var n = discards.count { it == type }
        if (type.isFlower) {
            n += flowers.count { it == type }
        } else {
            n += melds.flatMap { it.tiles }.count { it == type }
        }
        return n
    }
}

/**
 * 场况：4 家明示信息 + 圈风 + 自家座位。
 * 用于剩余张数计算。自家暗手信息单独由 Hand 提供。
 */
data class TableState(
    val players: List<PlayerState>,
    val selfSeat: PlayerSeat,
    val prevailingWind: PlayerSeat = PlayerSeat.EAST
) {
    init {
        require(players.size == 4) { "需 4 家" }
    }

    fun player(seat: PlayerSeat): PlayerState = players.first { it.seat == seat }

    fun self(): PlayerState = player(selfSeat)

    /** 场上（4 家牌河+副露+花）已明示的某牌张数。不含自家暗手。 */
    fun visibleCount(type: TileType): Int = players.sumOf { it.visibleCount(type) }

    companion object {
        /** 全新牌局：4 家空状态。 */
        fun fresh(selfSeat: PlayerSeat, prevailingWind: PlayerSeat = PlayerSeat.EAST): TableState =
            TableState(PlayerSeat.entries.map { PlayerState(it) }, selfSeat, prevailingWind)
    }
}
