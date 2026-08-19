package com.mahjong.guobiao.engine

import com.mahjong.guobiao.engine.fan.WinInfo
import com.mahjong.guobiao.engine.fan.WinMethod
import com.mahjong.guobiao.engine.tenpai.TenpaiCalculator
import com.mahjong.guobiao.engine.win.WinChecker
import com.mahjong.guobiao.model.DecompositionType
import com.mahjong.guobiao.model.Hand
import com.mahjong.guobiao.model.TableState
import com.mahjong.guobiao.model.TileParser
import com.mahjong.guobiao.model.TileType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WildcardModeTest {

    @AfterEach
    fun resetAnalysisMode() {
        // 避免污染其它测试（AnalysisSettings 为全局单例）
        AnalysisSettings.setAnalysisMode(AnalysisMode.PURE)
        AnalysisSettings.setWildcardTile(AnalysisSettings.DEFAULT_WILDCARD)
        AnalysisSettings.setSwapDepth(1)
    }

    private val zhong = TileType.ZHONG  // 赖子=红中(31)
    private val engine = RulesEngine()

    @Test
    fun `赖子模式 activeWildcard 返回选区`() {
        AnalysisSettings.setAnalysisMode(AnalysisMode.WILD)
        AnalysisSettings.setWildcardTile(TileType.pin(5).code)
        assertEquals(TileType.pin(5), AnalysisSettings.activeWildcard)

        AnalysisSettings.setAnalysisMode(AnalysisMode.PURE)
        Assertions.assertNull(AnalysisSettings.activeWildcard)
    }

    @Test
    fun `赖子平胡可和牌_纯大模式不行`() {
        // 123m456m789p + 12s99m + 中：中作赖子替代3p? 实为 123s 中的 3s
        val hand = TileParser.parseHand("123m456m789p12s99m中")
        assertEquals(14, hand.concealed.size)

        assertFalse(WinChecker.isWin(hand), "无赖子时不应和牌")
        assertTrue(WinChecker.isWin(hand, zhong), "赖子替代后应可和牌")
    }

    @Test
    fun `赖子顺子中位_赖子替代低位`() {
        // 2m 3m + 中：中作1m补顺 1m2m3m；余下拼成和牌
        // 123m456m789p + 2m3m中 + 11s
        val hand = TileParser.parseHand("123m456m789p23m中11s")
        assertEquals(14, hand.concealed.size)
        assertFalse(WinChecker.isWin(hand), "无赖子时不应和牌")
        assertTrue(WinChecker.isWin(hand, zhong), "赖子补低位成顺可和牌")
    }

    @Test
    fun `赖子零番起和`() {
        // 平胡但无番型：纯大需≥1番，赖子模式可不计番起和
        val hand = TileParser.parseHand("123m123p123s456m99p")
        val info = WinInfo(TileType.pin(9), WinMethod.SELF_DRAW)
        val pureBest = engine.bestScore(hand, info, null)!!
        val wildBest = engine.bestScore(hand, info, zhong)!!
        assertTrue(pureBest.totalFan == 0 && !pureBest.meetsMinimum, "纯大模式0番不可起和")
        assertTrue(wildBest.totalFan == 0 && wildBest.meetsMinimum, "赖子模式0番可起和")
    }

    @Test
    fun `赖子替代成清一色`() {
        // 1112345678999m + 中：中作9m → 九莲宝灯/清一色
        val hand = TileParser.parseHand("1112345678999m中")
        val info = WinInfo(TileType.ZHONG, WinMethod.SELF_DRAW)
        val result = engine.bestScore(hand, info, zhong)
        assertTrue(result != null && result.allDetected.any { it.name == "清一色" }, "替代后应判清一色")
        assertTrue(result!!.allDetected.any { it.name == "九莲宝灯" }, "替代后应判九莲宝灯")
    }

    @Test
    fun `赖子七对`() {
        val hand = TileParser.parseHand("11m22m33m44p55p66s中中")
        assertEquals(14, hand.concealed.size)
        val decomps = WinChecker.getAllDecompositions(hand, zhong)
        assertTrue(decomps.any { it.type == DecompositionType.SEVEN_PAIRS }, "应有三七对分解")
        val info = WinInfo(TileType.ZHONG, WinMethod.SELF_DRAW)
        val result = engine.bestScore(hand, info, zhong)
        assertTrue(result != null && result.allDetected.any { it.name == "七小对" }, "应判七小对")
    }

    @Test
    fun `赖子十三幺`() {
        // 12种幺九字 + 中中中：中补"白"缺位 + 作雀头
        val hand = TileParser.parseHand("1m9m1p9p1s9s东南西北发中中中")
        assertEquals(14, hand.concealed.size)
        assertFalse(WinChecker.isWin(hand), "无赖子时不应和牌")
        val decomps = WinChecker.getAllDecompositions(hand, zhong)
        assertTrue(decomps.any { it.type == DecompositionType.THIRTEEN_ORPHANS }, "赖子补位应为十三幺")
    }

    @Test
    fun `赖子听牌包含赖子张`() {
        // 123m456m789p12s99m：纯大听3s；赖子模式另听中(替代3s)
        val hand = TileParser.parseHand("123m456m789p12s99m")
        val pureWaits = TenpaiCalculator.waitingTiles(hand)
        assertEquals(listOf(TileType.sou(3)), pureWaits, "纯大仅听3s")
        val wildWaits = TenpaiCalculator.waitingTiles(hand, wildcard = zhong)
        assertEquals(listOf(TileType.sou(3), zhong), wildWaits, "赖子模式多听中")
    }

    @Test
    fun `赖子有效听牌判定`() {
        // 平胡零番听牌：纯大不算有效；赖子算有效
        val hand = TileParser.parseHand("123m123p123s456m9p")
        assertFalse(DevelopmentAnalyzer.hasValidTenpai(hand), "纯大零番听牌无效")
        assertTrue(DevelopmentAnalyzer.hasValidTenpai(hand, zhong), "赖子零番听牌有效")
    }

    @Test
    fun `赖子综合分析`() {
        val hand = TileParser.parseHand("123m456m789p12s99m中")
        val table = TableState.fresh(com.mahjong.guobiao.model.PlayerSeat.EAST)
        val result = engine.fullAnalysis(hand, table, null, zhong)
        assertTrue(result.isWin)
        assertTrue(result.fanResults.isNotEmpty())
        assertEquals(0, result.waitingTiles.size)
    }

    @Test
    fun `赖子弃牌建议`() {
        // 14张未和牌：弃3m后恢复 123m456m789p12s99m 的赖子听牌(3s/中)
        val hand = TileParser.parseHand("123m456m789p12s99m3m")
        assertEquals(14, hand.concealed.size)
        assertFalse(WinChecker.isWin(hand, zhong), "14张本手不应和牌")
        val table = TableState.fresh(com.mahjong.guobiao.model.PlayerSeat.EAST)
        val suggestions = DevelopmentAnalyzer.analyzeDiscard(hand, table, zhong)
        assertTrue(suggestions.isNotEmpty())
        val reachable = suggestions.filter { it.reachesMinimum }
        assertTrue(reachable.isNotEmpty(), "赖子模式应有可起和的弃牌建议")
        assertTrue(reachable.any { it.waitCount == 2 && it.resultingWaits.contains(TileType.sou(3)) && it.resultingWaits.contains(zhong) },
            "弃3m后应听 3s/中")
    }
}