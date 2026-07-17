package com.mahjong.guobiao.engine

import com.mahjong.guobiao.engine.fan.WinInfo
import com.mahjong.guobiao.engine.fan.WinMethod
import com.mahjong.guobiao.model.Hand
import com.mahjong.guobiao.model.Meld
import com.mahjong.guobiao.model.PlayerSeat
import com.mahjong.guobiao.model.PlayerState
import com.mahjong.guobiao.model.TableState
import com.mahjong.guobiao.model.TileParser
import com.mahjong.guobiao.model.TileType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RulesEngineTest {

    private val engine = RulesEngine()

    @Test
    fun `和牌态罗列番种`() {
        // 九莲宝灯
        val hand = TileParser.parseHand("1112345678999m5m")
        val fans = engine.listPossibleFans(hand)
        assertTrue(fans.any { it.name == "九莲宝灯" })
        assertTrue(fans.any { it.name == "清一色" })
    }

    @Test
    fun `听牌态罗列潜在番种`() {
        // 1112345678999m 听牌，听1-9m
        val hand = TileParser.parseHand("1112345678999m")
        val fans = engine.listPossibleFans(hand)
        // 摸5m可达九莲宝灯
        assertTrue(fans.any { it.name == "九莲宝灯" }, "听牌态应列出可达番种，实际: ${fans.map { it.name }}")
    }

    @Test
    fun `计算听牌`() {
        val hand = TileParser.parseHand("123m456m789m13p5s5s")
        val waits = engine.waitingTiles(hand)
        assertTrue(waits.contains(TileType.pin(2)), "应听2p嵌张")
    }

    @Test
    fun `九莲宝灯听9张`() {
        val hand = TileParser.parseHand("1112345678999m")
        val waits = engine.waitingTiles(hand)
        assertEquals(9, waits.size)
    }

    @Test
    fun `听牌带剩余张数`() {
        // 听2p，场上无2p -> 剩余4
        val hand = TileParser.parseHand("123m456m789m13p5s5s")
        val table = TableState.fresh(PlayerSeat.EAST)
        val waits = engine.calculateTenpaiWithCounts(hand, table)
        val twoP = waits.first { it.tile == TileType.pin(2) }
        assertEquals(4, twoP.remainingCount)
    }

    @Test
    fun `听牌剩余张数扣除他家牌河`() {
        val hand = TileParser.parseHand("123m456m789m13p5s5s")
        val table = TableState(
            players = listOf(
                PlayerState(PlayerSeat.EAST),
                PlayerState(PlayerSeat.SOUTH, discards = listOf(TileType.pin(2), TileType.pin(2))),
                PlayerState(PlayerSeat.WEST),
                PlayerState(PlayerSeat.NORTH)
            ),
            selfSeat = PlayerSeat.EAST
        )
        val waits = engine.calculateTenpaiWithCounts(hand, table)
        val twoP = waits.first { it.tile == TileType.pin(2) }
        // 2p: 自家0 + 南家牌河2 = 2, 剩余2
        assertEquals(2, twoP.remainingCount)
    }

    @Test
    fun `综合分析 听牌态`() {
        val hand = TileParser.parseHand("1112345678999m")
        val table = TableState.fresh(PlayerSeat.EAST)
        val result = engine.fullAnalysis(hand, table)
        assertEquals(false, result.isWin)
        assertEquals(9, result.waitingTiles.size)
        // 每张听牌应有剩余张数
        result.waitingTiles.forEach { assertTrue(it.remainingCount >= 0) }
        // 摸5m可达九莲宝灯，应出现在某听牌的 possibleFans
        val nineGatesWaits = result.waitingTiles.filter { wt ->
            wt.possibleFans.any { it.name == "九莲宝灯" }
        }
        assertTrue(nineGatesWaits.isNotEmpty(), "应有听牌可达九莲宝灯")
    }

    @Test
    fun `综合分析 和牌态`() {
        val hand = TileParser.parseHand("1112345678999m5m")
        val table = TableState.fresh(PlayerSeat.EAST)
        val result = engine.fullAnalysis(hand, table)
        assertTrue(result.isWin)
        assertTrue(result.fanResults.isNotEmpty())
        // 最高番应含九莲宝灯
        val best = result.fanResults.maxByOrNull { it.second.totalFan }!!
        assertTrue(best.second.allDetected.any { it.name == "九莲宝灯" })
    }

    @Test
    fun `剩余张数满4时剪枝不听`() {
        // 听2p，但场上4张2p已出3张+自家0 -> 仅1张，仍可听
        // 改为：4张全出则不可听。南家打2张+西家打2张=4张
        val hand = TileParser.parseHand("123m456m789m13p5s5s")
        val table = TableState(
            players = listOf(
                PlayerState(PlayerSeat.EAST),
                PlayerState(PlayerSeat.SOUTH, discards = listOf(TileType.pin(2), TileType.pin(2))),
                PlayerState(PlayerSeat.WEST, discards = listOf(TileType.pin(2), TileType.pin(2))),
                PlayerState(PlayerSeat.NORTH)
            ),
            selfSeat = PlayerSeat.EAST
        )
        val waits = engine.calculateTenpaiWithCounts(hand, table)
        // 2p 已出4张，不应出现在听牌中
        assertTrue(waits.none { it.tile == TileType.pin(2) }, "2p已4张出完不应可听")
    }
}
