package com.mahjong.guobiao.engine.win

import com.mahjong.guobiao.model.DecompositionType
import com.mahjong.guobiao.model.Hand
import com.mahjong.guobiao.model.Meld
import com.mahjong.guobiao.model.MeldType
import com.mahjong.guobiao.model.PlayerSeat
import com.mahjong.guobiao.model.TileParser
import com.mahjong.guobiao.model.TileType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StandardDecomposerTest {

    private fun decompose(text: String) =
        StandardDecomposer.decompose(TileParser.parseHand(text))

    @Test
    fun `基本和牌单分解`() {
        // 123m 456m 789m 123p 55p
        val decomps = decompose("123m456m789m123p55p")
        assertEquals(1, decomps.size)
        val d = decomps.first()
        assertEquals(DecompositionType.STANDARD, d.type)
        assertEquals(4, d.melds.size)
        assertEquals(MeldType.PAIR, d.pair?.type)
        assertEquals(TileType.pin(5), d.pair?.tiles?.first())
    }

    @Test
    fun `碰碰和 111222333 有2种分解`() {
        // 111m222m333m 可分解为 3 刻或 3 顺；444p 刻 + 55s 雀头
        val decomps = decompose("111m222m333m444p55s")
        assertEquals(2, decomps.size)
        // 验证存在全刻子分解
        assertTrue(decomps.any { d -> d.melds.filter { it.isTriplet }.size == 4 })
    }

    @Test
    fun `多分解枚举 111222333m`() {
        // 111m222m333m + 456p + 77p：111222333 可分解为 3 刻或 3 顺
        val decomps = decompose("111m222m333m456p77p")
        assertTrue(decomps.size >= 2, "应至少 2 种分解，实际 ${decomps.size}")
        // 验证存在全刻子分解与全顺子分解
        val allTriplets = decomps.any { d -> d.melds.filter { it.isTriplet }.size == 3 }
        val allSequences = decomps.any { d -> d.melds.filter { it.isSequence }.size == 4 }
        assertTrue(allTriplets, "应存在全刻子分解")
        assertTrue(allSequences, "应存在全顺子分解")
    }

    @Test
    fun `九莲宝灯和牌`() {
        // 1112345678999m + 5m
        val decomps = decompose("1112345678999m5m")
        assertTrue(decomps.isNotEmpty(), "九莲宝灯应可和牌")
    }

    @Test
    fun `副露后和牌`() {
        // 副露: 123m, 456m, 789m；暗手 123p 5p5p
        val hand = Hand(
            concealed = TileParser.parse("123p5p5p"),
            melds = listOf(
                Meld.chi(TileType.man(1)),
                Meld.chi(TileType.man(4)),
                Meld.chi(TileType.man(7))
            )
        )
        val decomps = StandardDecomposer.decompose(hand)
        assertEquals(1, decomps.size)
        // 3 个固定副露 + 1 暗顺 + 雀头
        val d = decomps.first()
        assertEquals(4, d.melds.size)
        assertEquals(MeldType.PAIR, d.pair?.type)
    }

    @Test
    fun `暗杠后和牌`() {
        // 暗杠 1m + 暗手 234m 567m 89m + 7m... 凑14张: 暗杠1m占3槽, 暗手需5张(14-3*1=11? 不对)
        // meldCount=1 -> concealedCountForWin = 14-3 = 11. 暗手11张 = 3面子+1雀头
        val hand = Hand(
            concealed = TileParser.parse("234m567m234p5p5p"),
            melds = listOf(Meld.kanClosed(TileType.man(1)))
        )
        val decomps = StandardDecomposer.decompose(hand)
        assertEquals(1, decomps.size)
        val d = decomps.first()
        // 固定暗杠 + 3 暗面子
        assertEquals(4, d.melds.size)
        assertTrue(d.melds.any { it.isKan })
    }

    @Test
    fun `不和牌返回空`() {
        val decomps = decompose("13m47m9m1p3p5p7p9p1s3s")
        assertTrue(decomps.isEmpty())
    }

    @Test
    fun `张数不符返回空`() {
        // 13 张（听牌态）不应被当作和牌
        val decomps = decompose("123m456m789m123p5p")
        assertTrue(decomps.isEmpty())
    }
}

class SevenPairsCheckerTest {

    private fun check(text: String) = SevenPairsChecker.check(TileParser.parseHand(text))

    @Test
    fun `标准七对`() {
        // 11m 22m 33m 44m 55m 66m 77m
        val d = check("11m22m33m44m55m66m77m")!!
        assertEquals(DecompositionType.SEVEN_PAIRS, d.type)
        assertEquals(7, d.melds.size)
        d.melds.forEach { assertTrue(it.isPair) }
    }

    @Test
    fun `四张同牌不算两对`() {
        // 11m11m 22m 33m 44m 55m 66m（4 张 1m）
        assertEquals(null, check("11m11m22m33m44m55m66m"))
    }

    @Test
    fun `非七对`() {
        // 12m33m44m55m66m77m88m（12m 非对子）
        assertEquals(null, check("12m33m44m55m66m77m88m"))
    }

    @Test
    fun `副露后不能七对`() {
        val hand = Hand(
            concealed = TileParser.parse("11m22m33m44p55p66s77s"),
            melds = listOf(Meld.chi(TileType.man(1)))
        )
        assertEquals(null, SevenPairsChecker.check(hand))
    }
}

class ThirteenOrphansCheckerTest {

    private fun check(text: String) = ThirteenOrphansChecker.check(TileParser.parseHand(text))

    @Test
    fun `标准十三幺`() {
        // 1m9m1p9p1s9s 东南西北中发白 + 重复1m
        val d = check("1m9m1p9p1s9s东南西北中发白1m")!!
        assertEquals(DecompositionType.THIRTEEN_ORPHANS, d.type)
        assertEquals(TileType.man(1), d.pair?.tiles?.first())
    }

    @Test
    fun `十三幺中作雀头`() {
        val d = check("1m9m1p9p1s9s东南西北中发白中")!!
        assertEquals(TileType.ZHONG, d.pair?.tiles?.first())
    }

    @Test
    fun `缺一种幺九字非十三幺`() {
        assertEquals(null, check("1m9m1p9p1s9s东南西北中发1m"))
    }

    @Test
    fun `含非幺九字非十三幺`() {
        // 多了 5m
        assertEquals(null, check("1m9m1p9p1s9s东南西北中发白5m"))
    }

    @Test
    fun `两个对子非十三幺`() {
        // 1m 和 9m 都重复
        assertEquals(null, check("1m9m1p9p1s9s东南西北中发1m9m"))
    }
}

class WinCheckerTest {

    private fun isWin(text: String) = WinChecker.isWin(TileParser.parseHand(text))

    @Test
    fun `标准形和牌`() {
        assertTrue(isWin("123m456m789m123p55p"))
    }

    @Test
    fun `七对和牌`() {
        assertTrue(isWin("11m22m33m44m55m66m77m"))
    }

    @Test
    fun `十三幺和牌`() {
        assertTrue(isWin("1m9m1p9p1s9s东南西北中发白白"))
    }

    @Test
    fun `不和牌`() {
        assertFalse(isWin("123m456m789m123p56p"))
    }

    @Test
    fun `多形和牌返回多分解`() {
        // 111m222m333m 既能七对也能标准
        // 11m22m33m44m55m66m77m 是七对；111m222m333m444p55p 是标准
        // 用一个能多分解的标准形
        val decomps = WinChecker.getAllDecompositions(TileParser.parseHand("111m222m333m456p77p"))
        assertTrue(decomps.size >= 2)
    }
}
