package com.mahjong.guobiao.engine

import com.mahjong.guobiao.engine.counter.TileCounter
import com.mahjong.guobiao.engine.fan.FanContext
import com.mahjong.guobiao.engine.fan.FanResult
import com.mahjong.guobiao.engine.fan.FanRule
import com.mahjong.guobiao.engine.fan.FanScorer
import com.mahjong.guobiao.engine.fan.WinInfo
import com.mahjong.guobiao.engine.tenpai.TenpaiCalculator
import com.mahjong.guobiao.engine.win.WinChecker
import com.mahjong.guobiao.model.Decomposition
import com.mahjong.guobiao.model.Hand
import com.mahjong.guobiao.model.TableState
import com.mahjong.guobiao.model.TileType

/**
 * 规则引擎总入口，整合和牌判定、听牌计算、番种判定、剩余张数。
 *
 * 三大需求：
 *  1. [listPossibleFans] 罗列可做的国标牌型
 *  2. [calculateTenpai] 计算听牌
 *  3. [calculateTenpaiWithCounts] 听牌 + 剩余张数
 */
class RulesEngine {

    // region 需求1：番种

    /**
     * 罗列可做的国标牌型。
     * - 和牌态（14张）：返回各分解可达的番种（取最优分解的最高番）。
     * - 听牌态（13张）：返回各听牌各分解可达番种的并集。
     *
     * @param winInfo 和牌信息（和牌方式/圈风门风）；听牌态时可省略，仅分析牌型结构。
     * @return 所有可达番种（已去重）。
     */
    fun listPossibleFans(hand: Hand, winInfo: WinInfo? = null): List<FanRule> {
        return if (hand.isValidWinSize()) {
            // 和牌态
            val info = winInfo ?: WinInfo(hand.concealed.last())
            val decomps = WinChecker.getAllDecompositions(hand)
            decomps.flatMap { decomp ->
                FanScorer.score(FanContext(decomp, hand, info)).allDetected
            }.distinctBy { it.id }
        } else if (hand.isValidTenpaiSize()) {
            // 听牌态：各听牌各分解的番种并集
            val waits = TenpaiCalculator.calculate(hand)
            waits.flatMap { wt -> wt.decompositions.flatMap { decomp ->
                val testHand = hand.withConcealed(hand.concealed + wt.tile)
                val info = winInfo?.copy(winTile = wt.tile) ?: WinInfo(wt.tile)
                FanScorer.score(FanContext(decomp, testHand, info)).allDetected
            } }.distinctBy { it.id }
        } else {
            emptyList()
        }
    }

    /** 和牌态的详细番种计分结果（各分解）。 */
    fun scoreHand(hand: Hand, winInfo: WinInfo? = null): List<Pair<Decomposition, FanResult>> {
        if (!hand.isValidWinSize()) return emptyList()
        val info = winInfo ?: WinInfo(hand.concealed.last())
        return WinChecker.getAllDecompositions(hand).map { decomp ->
            decomp to FanScorer.score(FanContext(decomp, hand, info))
        }
    }

    /** 最优分解的番种结果。 */
    fun bestScore(hand: Hand, winInfo: WinInfo? = null): FanResult? =
        scoreHand(hand, winInfo).maxByOrNull { it.second.totalFan }?.second

    // endregion

    // region 需求2：听牌

    /** 计算听牌（不含剩余张数）。 */
    fun calculateTenpai(hand: Hand): List<TenpaiCalculator.WaitingTile> {
        require(hand.isValidTenpaiSize()) { "听牌需暗手 ${hand.concealedCountForTenpai()} 张，实际 ${hand.concealed.size}" }
        return TenpaiCalculator.calculate(hand)
    }

    /** 听牌牌型列表。 */
    fun waitingTiles(hand: Hand): List<TileType> = calculateTenpai(hand).map { it.tile }

    // endregion

    // region 需求3：听牌 + 剩余张数

    /**
     * 计算听牌及每张听牌的剩余张数。
     * @param tableState 场况（各家牌河+副露+花），用于剩余张数与剪枝
     */
    fun calculateTenpaiWithCounts(
        hand: Hand,
        tableState: TableState
    ): List<WaitingTileWithCount> {
        require(hand.isValidTenpaiSize()) { "听牌需暗手 ${hand.concealedCountForTenpai()} 张，实际 ${hand.concealed.size}" }

        // 构造场况已用牌计数（他家牌河+副露+花），用于剪枝
        val otherUsed = mutableMapOf<TileType, Int>()
        for (player in tableState.players) {
            if (player.seat == tableState.selfSeat) continue
            for (t in TileType.ALL_NON_FLOWER) {
                val c = player.visibleCount(t)
                if (c > 0) otherUsed[t] = (otherUsed[t] ?: 0) + c
            }
        }

        val waits = TenpaiCalculator.calculate(hand, otherUsed)
        return waits.map { wt ->
            WaitingTileWithCount(
                tile = wt.tile,
                remainingCount = TileCounter.remainingCount(wt.tile, hand, tableState),
                decompositions = wt.decompositions
            )
        }
    }

    // endregion

    // region 综合

    /** 综合分析：听牌 + 剩余张数 + 各听牌可达番种。 */
    fun fullAnalysis(hand: Hand, tableState: TableState, winInfo: WinInfo? = null): AnalysisResult {
        if (hand.isValidWinSize()) {
            // 和牌态
            val scored = scoreHand(hand, winInfo)
            return AnalysisResult(
                isWin = true,
                waitingTiles = emptyList(),
                fanResults = scored
            )
        }
        val waitsWithCount = calculateTenpaiWithCounts(hand, tableState)
        val waitsWithFans = waitsWithCount.map { wtc ->
            val testHand = hand.withConcealed(hand.concealed + wtc.tile)
            val info = winInfo?.copy(winTile = wtc.tile) ?: WinInfo(wtc.tile)
            val fans = wtc.decompositions.flatMap { decomp ->
                FanScorer.score(FanContext(decomp, testHand, info)).counted
            }.distinctBy { it.id }
            WaitingTileWithFans(
                tile = wtc.tile,
                remainingCount = wtc.remainingCount,
                decompositions = wtc.decompositions,
                possibleFans = fans
            )
        }
        return AnalysisResult(isWin = false, waitingTiles = waitsWithFans, fanResults = emptyList())
    }

    // endregion
}

/** 听牌 + 剩余张数。 */
data class WaitingTileWithCount(
    val tile: TileType,
    val remainingCount: Int,
    val decompositions: List<Decomposition>
)

/** 听牌 + 剩余张数 + 可达番种。 */
data class WaitingTileWithFans(
    val tile: TileType,
    val remainingCount: Int,
    val decompositions: List<Decomposition>,
    val possibleFans: List<FanRule>
)

/** 综合分析结果。 */
data class AnalysisResult(
    val isWin: Boolean,
    val waitingTiles: List<WaitingTileWithFans>,
    val fanResults: List<Pair<Decomposition, com.mahjong.guobiao.engine.fan.FanResult>>
)
