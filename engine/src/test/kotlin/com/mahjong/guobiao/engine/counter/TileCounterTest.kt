package com.mahjong.guobiao.engine.counter

import com.mahjong.guobiao.model.Hand
import com.mahjong.guobiao.model.Meld
import com.mahjong.guobiao.model.PlayerSeat
import com.mahjong.guobiao.model.PlayerState
import com.mahjong.guobiao.model.TableState
import com.mahjong.guobiao.model.TileParser
import com.mahjong.guobiao.model.TileType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TileCounterTest {

    @Test
    fun `全新牌局每牌剩4`() {
        // 手牌不含1m和东
        val hand = TileParser.parseHand("234m456m789m234p5p5p")
        val table = TableState.fresh(PlayerSeat.EAST)
        assertEquals(4, TileCounter.remainingCount(TileType.man(1), hand, table))
        assertEquals(4, TileCounter.remainingCount(TileType.EAST, hand, table))
    }

    @Test
    fun `自家暗手占用`() {
        // 暗手含2张1m
        val hand = TileParser.parseHand("11m234m567m789p5p")
        val table = TableState.fresh(PlayerSeat.EAST)
        assertEquals(2, TileCounter.remainingCount(TileType.man(1), hand, table))
    }

    @Test
    fun `自家副露占用`() {
        // 碰1m(3张)
        val hand = Hand(
            concealed = TileParser.parse("234m567m789p5p5p"),
            melds = listOf(Meld.pon(TileType.man(1)))
        )
        val table = TableState.fresh(PlayerSeat.EAST)
        assertEquals(1, TileCounter.remainingCount(TileType.man(1), hand, table))
    }

    @Test
    fun `他家牌河占用`() {
        // 手牌不含1m，南家牌河打1张1m
        val hand = TileParser.parseHand("234m456m789m234p5p5p")
        val table = TableState(
            players = listOf(
                PlayerState(PlayerSeat.EAST),
                PlayerState(PlayerSeat.SOUTH, discards = listOf(TileType.man(1))),
                PlayerState(PlayerSeat.WEST),
                PlayerState(PlayerSeat.NORTH)
            ),
            selfSeat = PlayerSeat.EAST
        )
        assertEquals(3, TileCounter.remainingCount(TileType.man(1), hand, table))
    }

    @Test
    fun `花牌剩余`() {
        val hand = Hand(
            concealed = TileParser.parse("123m456m789m123p5p"),
            melds = emptyList(),
            flowers = listOf(TileType(34)) // 春
        )
        val table = TableState.fresh(PlayerSeat.EAST)
        assertEquals(0, TileCounter.remainingCount(TileType(34), hand, table), "春已补，剩余0")
        assertEquals(1, TileCounter.remainingCount(TileType(35), hand, table), "夏未出，剩余1")
    }

    @Test
    fun `暗杠4张全计入`() {
        val hand = Hand(
            concealed = TileParser.parse("234m567m789p5p5p"),
            melds = listOf(Meld.kanClosed(TileType.man(1)))
        )
        val table = TableState.fresh(PlayerSeat.EAST)
        assertEquals(0, TileCounter.remainingCount(TileType.man(1), hand, table), "暗杠1m后剩余0")
    }

    @Test
    fun `综合场景`() {
        // 自家2张1m + 对家打1张1m
        val hand = TileParser.parseHand("11m234m567m789p5p")
        val table = TableState(
            players = listOf(
                PlayerState(PlayerSeat.EAST),
                PlayerState(PlayerSeat.SOUTH, discards = listOf(TileType.man(1))),
                PlayerState(PlayerSeat.WEST),
                PlayerState(PlayerSeat.NORTH)
            ),
            selfSeat = PlayerSeat.EAST
        )
        // 1m: 自家2 + 南家牌河1 = 3, 剩余1
        assertEquals(1, TileCounter.remainingCount(TileType.man(1), hand, table))
    }
}
