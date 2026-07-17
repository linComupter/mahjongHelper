package com.mahjong.guobiao.model

/**
 * 手牌状态。
 *
 * 张数不变式：
 *  - 听牌：concealed.size + 3 * meldCount = 13
 *  - 和牌：concealed.size + 3 * meldCount = 14
 *  其中杠占 3 个槽位（第 4 张来自补杠摸牌），不影响公式。
 *
 * @param concealed 暗手（可操作牌）
 * @param melds 副露（已固定面子，含杠）
 * @param flowers 花牌（补花后单独放置）
 */
data class Hand(
    val concealed: List<TileType>,
    val melds: List<Meld>,
    val flowers: List<TileType> = emptyList()
) {
    val meldCount: Int get() = melds.size
    val kanCount: Int get() = melds.count { it.isKan }

    /** 和牌时暗手应有张数 = 14 - 3 * meldCount。 */
    fun concealedCountForWin(): Int = 14 - 3 * meldCount

    /** 听牌时暗手应有张数 = 13 - 3 * meldCount。 */
    fun concealedCountForTenpai(): Int = 13 - 3 * meldCount

    /** 是否已副露（影响能否七对/十三幺/全不靠）。 */
    val isClosed: Boolean get() = meldCount == 0

    /** 暗手的 TileCounts（不含副露、花牌）。 */
    fun concealedCounts(): TileCounts = TileCounts.fromTiles(concealed)

    /** 全部序数/字牌的计数（暗手 + 副露），用于番种判定。花牌不计入。 */
    fun fullCounts(): TileCounts {
        val counts = TileCounts()
        for (t in concealed) if (!t.isFlower) counts.add(t, 1)
        for (meld in melds) for (t in meld.tiles) if (!t.isFlower) counts.add(t, 1)
        return counts
    }

    /** 14 张是否构成和牌的合法张数。 */
    fun isValidWinSize(): Boolean = concealed.size == concealedCountForWin()

    /** 13 张是否构成听牌的合法张数。 */
    fun isValidTenpaiSize(): Boolean = concealed.size == concealedCountForTenpai()

    fun withConcealed(concealed: List<TileType>): Hand = Hand(concealed, melds, flowers)

    companion object {
        fun concealed(tiles: List<TileType>) = Hand(tiles, emptyList())
    }
}
