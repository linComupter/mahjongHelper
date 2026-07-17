package com.mahjong.guobiao.model

/**
 * 麻将牌型。国标用 42 种牌型：34 种序数/字牌(每种 4 张) + 8 种花牌(每种 1 张)。
 *
 * 编码：
 *  0..8   万子 1m..9m
 *  9..17  筒子 1p..9p
 *  18..26 条子 1s..9s
 *  27..30 风牌 东/南/西/北
 *  31..33 箭牌 中/发/白
 *  34..41 花牌 春夏秋冬梅兰竹菊
 */
@JvmInline
value class TileType(val code: Int) : Comparable<TileType> {
    init {
        require(code in 0..41) { "TileType code out of range: $code" }
    }

    val suit: Suit
        get() = when {
            code < 9 -> Suit.MANZU
            code < 18 -> Suit.PINZU
            code < 27 -> Suit.SOUZU
            code < 31 -> Suit.WIND
            code < 34 -> Suit.DRAGON
            else -> Suit.FLOWER
        }

    /** 序数牌点数 1-9；风牌 1-4(东南西北)；箭牌 1-3(中发白)；花牌 1-8。 */
    val rank: Int
        get() = when (suit) {
            Suit.MANZU, Suit.PINZU, Suit.SOUZU -> code % 9 + 1
            Suit.WIND -> code - 26
            Suit.DRAGON -> code - 30
            Suit.FLOWER -> code - 33
        }

    val isSuited: Boolean get() = code < 27
    val isHonor: Boolean get() = code in 27..33
    val isWind: Boolean get() = code in 27..30
    val isDragon: Boolean get() = code in 31..33
    val isFlower: Boolean get() = code >= 34

    /** 幺九牌：序数 1/9 或字牌。 */
    val isTerminalOrHonor: Boolean get() = isHonor || (isSuited && (rank == 1 || rank == 9))

    val isTerminal: Boolean get() = isSuited && (rank == 1 || rank == 9)

    /** 绿色牌：2s/3s/4s/6s/8s/发。 */
    val isGreen: Boolean get() = code in setOf(19, 20, 21, 23, 25, 32)

    /** 同花色下一张点数，无则 null（用于顺子判定）。 */
    fun nextRank(): TileType? = if (isSuited && rank < 9) TileType(code + 1) else null

    fun prevRank(): TileType? = if (isSuited && rank > 1) TileType(code - 1) else null

    override fun compareTo(other: TileType): Int = code.compareTo(other.code)

    override fun toString(): String = NOTATION[code]

    companion object {
        const val COUNT = 42
        const val NON_FLOWER_COUNT = 34

        /** 13 种幺九字：1m,9m,1p,9p,1s,9s,东南西北中发白。 */
        val TERMINALS_HONORS: List<TileType> = listOf(
            0, 8, 9, 17, 18, 26, 27, 28, 29, 30, 31, 32, 33
        ).map { TileType(it) }

        /** 全部 34 种序数/字牌。 */
        val ALL_NON_FLOWER: List<TileType> = (0..33).map { TileType(it) }

        /** 全部 8 种花牌。 */
        val ALL_FLOWERS: List<TileType> = (34..41).map { TileType(it) }

        // 简便构造：1m 等记法
        fun man(rank: Int) = TileType(rank - 1)
        fun pin(rank: Int) = TileType(8 + rank)
        fun sou(rank: Int) = TileType(17 + rank)
        fun wind(rank: Int) = TileType(26 + rank)   // 1东 2南 3西 4北
        fun dragon(rank: Int) = TileType(30 + rank) // 1中 2发 3白

        val EAST = TileType(27)
        val SOUTH = TileType(28)
        val WEST = TileType(29)
        val NORTH = TileType(30)
        val ZHONG = TileType(31)
        val FA = TileType(32)
        val BAI = TileType(33)

        private val NOTATION = arrayOf(
            "一万", "二万", "三万", "四万", "五万", "六万", "七万", "八万", "九万",
            "一筒", "二筒", "三筒", "四筒", "五筒", "六筒", "七筒", "八筒", "九筒",
            "一条", "二条", "三条", "四条", "五条", "六条", "七条", "八条", "九条",
            "东", "南", "西", "北", "中", "发", "白",
            "春", "夏", "秋", "冬", "梅", "兰", "竹", "菊"
        )
    }
}
