package com.mahjong.guobiao.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TileTypeTest {

    @Test
    fun `万子编码与属性`() {
        val m1 = TileType.man(1)
        assertEquals(Suit.MANZU, m1.suit)
        assertEquals(1, m1.rank)
        assertTrue(m1.isSuited)
        assertTrue(m1.isTerminal)
        assertTrue(m1.isTerminalOrHonor)
        assertFalse(m1.isHonor)
        assertFalse(m1.isGreen)
    }

    @Test
    fun `风箭牌属性`() {
        val zhong = TileType.ZHONG
        assertEquals(Suit.DRAGON, zhong.suit)
        assertTrue(zhong.isHonor)
        assertTrue(zhong.isTerminalOrHonor)
        assertFalse(zhong.isTerminal)
        assertFalse(zhong.isSuited)

        val east = TileType.EAST
        assertEquals(Suit.WIND, east.suit)
        assertEquals(1, east.rank)
    }

    @Test
    fun `绿色牌集合`() {
        val greens = listOf(2, 3, 4, 6, 8).map { TileType.sou(it) } + TileType.FA
        greens.forEach { assertTrue(it.isGreen, "$it 应为绿色") }
        listOf(TileType.sou(1), TileType.sou(5), TileType.sou(7), TileType.sou(9), TileType.ZHONG)
            .forEach { assertFalse(it.isGreen, "$it 不应为绿色") }
    }

    @Test
    fun `nextRank 顺子导航`() {
        assertEquals(TileType.man(2), TileType.man(1).nextRank())
        assertEquals(TileType.man(9), TileType.man(8).nextRank())
        assertNull(TileType.man(9).nextRank())
        assertNull(TileType.EAST.nextRank()) // 字牌无 nextRank
    }

    @Test
    fun `花牌识别`() {
        val spring = TileType(34)
        assertTrue(spring.isFlower)
        assertEquals(Suit.FLOWER, spring.suit)
    }

    @Test
    fun `排序与相等`() {
        val tiles = listOf(TileType.man(3), TileType.man(1), TileType.pin(2))
        val sorted = tiles.sorted()
        assertEquals(listOf(TileType.man(1), TileType.man(3), TileType.pin(2)), sorted)
    }

    @Test
    fun `越界编码抛异常`() {
        assertThrows<IllegalArgumentException> { TileType(-1) }
        assertThrows<IllegalArgumentException> { TileType(42) }
    }
}

class TileCountsTest {

    @Test
    fun `增删与计数`() {
        val counts = TileCounts()
        assertTrue(counts.add(TileType.man(1), 2))
        assertEquals(2, counts[TileType.man(1)])
        assertEquals(2, counts.totalCount())
        assertTrue(counts.remove(TileType.man(1), 1))
        assertEquals(1, counts[TileType.man(1)])
    }

    @Test
    fun `超过4张失败`() {
        val counts = TileCounts()
        counts.add(TileType.man(1), 4)
        assertFalse(counts.add(TileType.man(1)))
    }

    @Test
    fun `firstNonZero 返回最低位`() {
        val counts = TileCounts.fromTiles(TileParser.parse("3m5p9s东"))
        assertEquals(TileType.man(3), counts.firstNonZero())
    }

    @Test
    fun `花牌禁止计入`() {
        assertThrows<IllegalArgumentException> { TileCounts().add(TileType(34)) }
    }

    @Test
    fun `copy 独立`() {
        val counts = TileCounts.fromTiles(TileParser.parse("11m"))
        val copy = counts.copy()
        copy.add(TileType.man(1))
        assertEquals(2, counts[TileType.man(1)])
        assertEquals(3, copy[TileType.man(1)])
    }
}

class TileParserTest {

    @Test
    fun `解析序数牌`() {
        assertEquals(listOf(TileType.man(1), TileType.man(2), TileType.man(3)), TileParser.parse("123m"))
        assertEquals(listOf(TileType.pin(4), TileType.pin(5), TileType.pin(6)), TileParser.parse("456p"))
        assertEquals(listOf(TileType.sou(7), TileType.sou(8), TileType.sou(9)), TileParser.parse("789s"))
    }

    @Test
    fun `解析字牌`() {
        assertEquals(listOf(TileType.EAST, TileType.SOUTH, TileType.WEST, TileType.NORTH), TileParser.parse("东南西北"))
        assertEquals(listOf(TileType.ZHONG, TileType.FA, TileType.BAI), TileParser.parse("中发白"))
    }

    @Test
    fun `解析花牌`() {
        assertEquals(listOf(TileType(34), TileType(38)), TileParser.parse("春梅"))
    }

    @Test
    fun `混合解析`() {
        val tiles = TileParser.parse("123m456p789s东南东")
        assertEquals(12, tiles.size)
        assertEquals(TileType.EAST, tiles.last())
    }

    @Test
    fun `解析为暗手并分离花牌`() {
        val hand = TileParser.parseHand("123m春456p")
        assertEquals(6, hand.concealed.size)
        assertEquals(listOf(TileType(34)), hand.flowers)
    }

    @Test
    fun `尾部数字未匹配花色抛异常`() {
        assertThrows<IllegalArgumentException> { TileParser.parse("123m45") }
    }
}

class MeldTest {

    @Test
    fun `顺子构造`() {
        val seq = Meld.sequenceConcealed(TileType.man(1))
        assertTrue(seq.isSequence)
        assertTrue(seq.isConcealed)
        assertEquals(3, seq.tiles.size)
        assertEquals(listOf(TileType.man(1), TileType.man(2), TileType.man(3)), seq.tiles)
    }

    @Test
    fun `刻子与杠`() {
        val triplet = Meld.tripletConcealed(TileType.EAST)
        assertTrue(triplet.isTriplet)
        assertEquals(1, triplet.slotCount)

        val kan = Meld.kanClosed(TileType.man(5))
        assertTrue(kan.isKan)
        assertEquals(4, kan.tiles.size)
        assertEquals(3, kan.slotCount) // 杠占 3 槽位
    }

    @Test
    fun `雀头`() {
        val pair = Meld.pair(TileType.ZHONG)
        assertTrue(pair.isPair)
        assertEquals(2, pair.tiles.size)
    }

    @Test
    fun `非法顺子抛异常`() {
        assertThrows<IllegalArgumentException> { Meld.sequenceConcealed(TileType.man(9)) }
        assertThrows<IllegalArgumentException> { Meld(MeldType.SEQUENCE_CONCEALED, listOf(TileType.man(1), TileType.man(2), TileType.man(4))) }
    }
}
