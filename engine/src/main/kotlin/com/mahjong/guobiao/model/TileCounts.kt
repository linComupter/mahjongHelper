package com.mahjong.guobiao.model

/**
 * 34 种序数/字牌的计数数组，DFS 回溯的核心数据结构。
 * 花牌不参与和牌形，单独处理。
 */
@JvmInline
value class TileCounts(private val arr: IntArray = IntArray(34)) {

    operator fun get(type: TileType): Int {
        require(!type.isFlower) { "花牌不参与 TileCounts: $type" }
        return arr[type.code]
    }

    operator fun set(type: TileType, value: Int) {
        require(!type.isFlower) { "花牌不参与 TileCounts: $type" }
        arr[type.code] = value
    }

    /** 加 n 张，返回是否成功（不超 4）。 */
    fun add(type: TileType, n: Int = 1): Boolean {
        require(!type.isFlower)
        val v = arr[type.code] + n
        if (v > 4) return false
        arr[type.code] = v
        return true
    }

    fun remove(type: TileType, n: Int = 1): Boolean {
        require(!type.isFlower)
        val v = arr[type.code] - n
        if (v < 0) return false
        arr[type.code] = v
        return true
    }

    fun totalCount(): Int = arr.sum()

    /** 有牌的牌型（升序）。 */
    fun nonZeroTypes(): List<TileType> = arr.indices.filter { arr[it] > 0 }.map { TileType(it) }

    /** 最低位有牌的牌型，无则 null。DFS 起点。 */
    fun firstNonZero(): TileType? {
        for (i in arr.indices) {
            if (arr[i] > 0) return TileType(i)
        }
        return null
    }

    fun copy(): TileCounts = TileCounts(arr.copyOf())

    /** 返回可变底层数组（仅供需要直接遍历的场景）。 */
    fun asArray(): IntArray = arr

    override fun toString(): String =
        arr.indices.filter { arr[it] > 0 }.joinToString(" ") { "${TileType(it)}×${arr[it]}" }

    companion object {
        fun fromTiles(tiles: List<TileType>): TileCounts {
            val counts = TileCounts()
            for (t in tiles) {
                if (!t.isFlower) counts.add(t, 1)
            }
            return counts
        }
    }
}
