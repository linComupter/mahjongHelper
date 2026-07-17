package com.mahjong.guobiao.model

/** 面子/副露类型。 */
enum class MeldType {
    CHI,                  // 吃（顺子，明）
    PON,                  // 碰（刻子，明）
    KAN_OPEN,             // 明杠（大明杠）
    KAN_CLOSED,           // 暗杠
    KAN_ADDED,            // 加杠（碰后补杠）
    TRIPLET_CONCEALED,    // 暗刻（手牌中三同，分解结果）
    SEQUENCE_CONCEALED,   // 暗顺（手牌中顺子，分解结果）
    PAIR;                 // 雀头（分解结果）

    val isConcealed: Boolean
        get() = this in setOf(KAN_CLOSED, TRIPLET_CONCEALED, SEQUENCE_CONCEALED, PAIR)

    val isOpen: Boolean get() = !isConcealed

    val isKan: Boolean get() = this in setOf(KAN_OPEN, KAN_CLOSED, KAN_ADDED)

    val isTriplet: Boolean get() = this in setOf(PON, KAN_OPEN, KAN_CLOSED, KAN_ADDED, TRIPLET_CONCEALED)

    val isSequence: Boolean get() = this in setOf(CHI, SEQUENCE_CONCEALED)

    val isPair: Boolean get() = this == PAIR
}

/**
 * 面子或副露。
 * @param type 面子类型
 * @param tiles 3 张（顺子/刻子）或 4 张（杠）或 2 张（雀头），按点数升序
 * @param sourcePlayer 副露来源座位（吃/碰/明杠时非空），暗杠/暗刻/暗顺/雀头为 null
 */
data class Meld(
    val type: MeldType,
    val tiles: List<TileType>,
    val sourcePlayer: PlayerSeat? = null
) {
    init {
        when (type) {
            MeldType.PAIR -> require(tiles.size == 2 && tiles[0] == tiles[1]) { "雀头需 2 张相同: $tiles" }
            MeldType.CHI, MeldType.SEQUENCE_CONCEALED -> {
                require(tiles.size == 3) { "顺子需 3 张: $tiles" }
                require(tiles[0].isSuited && tiles[0].suit == tiles[1].suit && tiles[1].suit == tiles[2].suit) { "顺子同花色: $tiles" }
                require(tiles[0].nextRank() == tiles[1] && tiles[1].nextRank() == tiles[2]) { "顺子连续: $tiles" }
            }
            MeldType.PON, MeldType.TRIPLET_CONCEALED -> {
                require(tiles.size == 3 && tiles.all { it == tiles[0] }) { "刻子需 3 张相同: $tiles" }
            }
            MeldType.KAN_OPEN, MeldType.KAN_CLOSED, MeldType.KAN_ADDED -> {
                require(tiles.size == 4 && tiles.all { it == tiles[0] }) { "杠需 4 张相同: $tiles" }
            }
        }
    }

    val isKan: Boolean get() = type.isKan
    val isTriplet: Boolean get() = type.isTriplet
    val isSequence: Boolean get() = type.isSequence
    val isPair: Boolean get() = type.isPair
    val isConcealed: Boolean get() = type.isConcealed

    /** 面子占用的"槽位数"：杠占 3（第 4 张来自补杠摸牌），其余按张数/3。 */
    val slotCount: Int get() = if (isKan) 3 else tiles.size / 3

    companion object {
        fun pair(tile: TileType) = Meld(MeldType.PAIR, listOf(tile, tile))
        fun tripletConcealed(tile: TileType) = Meld(MeldType.TRIPLET_CONCEALED, listOf(tile, tile, tile))
        fun sequenceConcealed(start: TileType): Meld {
            require(start.isSuited && start.rank <= 7)
            return Meld(MeldType.SEQUENCE_CONCEALED, listOf(start, start.nextRank()!!, start.nextRank()!!.nextRank()!!))
        }
        fun pon(tile: TileType, source: PlayerSeat? = null) = Meld(MeldType.PON, listOf(tile, tile, tile), source)
        fun chi(start: TileType, source: PlayerSeat? = null): Meld {
            require(start.isSuited && start.rank <= 7)
            return Meld(MeldType.CHI, listOf(start, start.nextRank()!!, start.nextRank()!!.nextRank()!!), source)
        }
        fun kanClosed(tile: TileType) = Meld(MeldType.KAN_CLOSED, listOf(tile, tile, tile, tile))
        fun kanOpen(tile: TileType, source: PlayerSeat? = null) = Meld(MeldType.KAN_OPEN, listOf(tile, tile, tile, tile), source)
        fun kanAdded(tile: TileType, source: PlayerSeat? = null) = Meld(MeldType.KAN_ADDED, listOf(tile, tile, tile, tile), source)
    }
}
