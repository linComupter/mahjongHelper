package com.mahjong.guobiao.engine.tenpai

import com.mahjong.guobiao.model.Hand
import com.mahjong.guobiao.model.Meld
import com.mahjong.guobiao.model.TileParser
import com.mahjong.guobiao.model.TileType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TenpaiCalculatorTest {

    private fun waiting(text: String): List<TileType> =
        TenpaiCalculator.waitingTiles(TileParser.parseHand(text))

    private fun waitingFull(text: String): List<TenpaiCalculator.WaitingTile> =
        TenpaiCalculator.calculate(TileParser.parseHand(text))

    @Test
    fun `九莲宝灯听9张`() {
        // 1112345678999m 听 1m-9m
        val waits = waiting("1112345678999m")
        assertEquals(9, waits.size)
        assertTrue(waits.containsAll((1..9).map { TileType.man(it) }))
    }

    @Test
    fun `十三幺十三面听`() {
        // 1m9m1p9p1s9s东南西北中发白 听13种幺九字
        val waits = waiting("1m9m1p9p1s9s东南西北中发白")
        assertEquals(13, waits.size)
        assertTrue(waits.containsAll(TileType.TERMINALS_HONORS))
    }

    @Test
    fun `十三幺单骑听`() {
        // 12种幺九字 + 重复中，听缺失的白
        val waits = waiting("1m9m1p9p1s9s东南西北中发中")
        assertEquals(listOf(TileType.BAI), waits)
    }

    @Test
    fun `十三幺缺一听`() {
        // 缺白，重复1m，听白
        val waits = waiting("1m9m1p9p1s9s东南西北中发1m")
        assertEquals(listOf(TileType.BAI), waits)
    }

    @Test
    fun `七对听`() {
        // 6对(跨花色，无法成标准顺子) + 1单(7s)，仅听7s
        val waits = waiting("11m22m33m44p55p66s7s")
        assertEquals(listOf(TileType.sou(7)), waits)
    }

    @Test
    fun `标准单骑听`() {
        // 123m 456m 789m 123p 5p：4面子+单5p，听5p
        val waits = waiting("123m456m789m123p5p")
        assertEquals(listOf(TileType.pin(5)), waits)
    }

    @Test
    fun `边张听`() {
        // 123m 456m 789m 12p 3p... 凑13张: 123m456m789m12p3p5s5s = 3+3+3+2+1+2=14?
        // 用 123m 456m 789m 12p 5s5s = 3+3+3+2+2=13. 听3p(边张,12p->123p)
        val waits = waiting("123m456m789m12p5s5s")
        assertTrue(waits.contains(TileType.pin(3)), "应听3p边张")
    }

    @Test
    fun `嵌张听`() {
        // 123m 456m 789m 13p 5s5s：13p听2p(嵌张)
        val waits = waiting("123m456m789m13p5s5s")
        assertTrue(waits.contains(TileType.pin(2)), "应听2p嵌张")
    }

    @Test
    fun `双碰听`() {
        // 123m 456m 789m 11p 22p：听1p或2p(双碰)
        val waits = waiting("123m456m789m11p22p")
        assertTrue(waits.contains(TileType.pin(1)))
        assertTrue(waits.contains(TileType.pin(2)))
        assertEquals(2, waits.size)
    }

    @Test
    fun `副露后听牌`() {
        // 副露3面子, 暗手 12p 5p5p = 4张. 仅听3p(边张,12p->123p + 55p雀头)
        // 加5p得5p×3无法成和，故只听3p
        val hand = Hand(
            concealed = TileParser.parse("12p5p5p"),
            melds = listOf(
                Meld.chi(TileType.man(1)),
                Meld.chi(TileType.man(4)),
                Meld.chi(TileType.man(7))
            )
        )
        val waits = TenpaiCalculator.waitingTiles(hand)
        assertTrue(waits.contains(TileType.pin(3)), "应听3p边张")
    }

    @Test
    fun `散牌不听返回空`() {
        // 13张全散牌，无可成面子，无听牌
        val waits = waiting("1m3m5m7m9m1p3p5p7p9p1s3s5s")
        assertTrue(waits.isEmpty(), "全散牌应无听牌，实际: $waits")
    }

    @Test
    fun `听牌分解非空`() {
        val waits = waitingFull("123m456m789m13p5s5s")
        assertTrue(waits.isNotEmpty())
        waits.forEach { wt ->
            assertTrue(wt.decompositions.isNotEmpty(), "听牌 ${wt.tile} 应有分解")
        }
    }

    @Test
    fun `剪枝 暗手4张不重复听`() {
        // 1111m + 凑听牌：1111m234m567m8m9m? 需要13张
        // 1111m234m567m8m9m = 4+3+3+1+1=12, 加1张=13.
        // 实际 1111m 234m 567m 8m 9m: 听牌态13张? 4+3+3+1+1=12. 还差1张。
        // 用 1111m234m567m789m = 4+3+3+3=13. 这是和牌形(1111m=刻+1, 但1m已4张).
        // 听牌: 1111m234m567m789m -> 加牌后? 1m已4张不能再听1m
        val hand = TileParser.parseHand("1111m234m567m789m")
        // 1m暗手已有4张，不应作为听牌
        val waits = TenpaiCalculator.calculate(hand)
        assertFalse(waits.any { it.tile == TileType.man(1) }, "1m已4张不应可听")
    }
}
