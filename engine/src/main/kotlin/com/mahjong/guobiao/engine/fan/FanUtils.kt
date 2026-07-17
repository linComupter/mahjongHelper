package com.mahjong.guobiao.engine.fan

import com.mahjong.guobiao.model.DecompositionType
import com.mahjong.guobiao.model.Meld
import com.mahjong.guobiao.model.MeldType
import com.mahjong.guobiao.model.Suit
import com.mahjong.guobiao.model.TileType

/** 番种规则。 */
interface FanRule {
    val id: String
    val name: String
    val value: Int
    /** 此番种成立时不再单独计分的低番番种 id 集合（"不计"关系）。 */
    val subsumes: Set<String> get() = emptySet()
    fun detect(ctx: FanContext): Boolean
}

/** 番种检测辅助函数。 */
object FanUtils {

    /** 全部牌（暗手+副露）的计数。 */
    fun fullCounts(ctx: FanContext) = ctx.hand.fullCounts()

    /** 全部分解中的面子（含副露+暗手分解的面子，不含雀头）。 */
    fun melds(ctx: FanContext): List<Meld> = ctx.decomposition.melds

    /** 雀头。 */
    fun pair(ctx: FanContext): Meld? = ctx.decomposition.pair

    /** 暗刻数（含暗杠）。 */
    fun concealedTripletCount(ctx: FanContext): Int =
        ctx.decomposition.melds.count { it.type == MeldType.TRIPLET_CONCEALED || it.type == MeldType.KAN_CLOSED }

    /** 明刻数（含明杠/加杠/碰）。 */
    fun openTripletCount(ctx: FanContext): Int =
        ctx.decomposition.melds.count { it.type in setOf(MeldType.PON, MeldType.KAN_OPEN, MeldType.KAN_ADDED) }

    /** 刻子总数（含杠）。 */
    fun tripletCount(ctx: FanContext): Int =
        ctx.decomposition.melds.count { it.isTriplet }

    /** 顺子数。 */
    fun sequenceCount(ctx: FanContext): Int =
        ctx.decomposition.melds.count { it.isSequence }

    /** 杠数。 */
    fun kanCount(ctx: FanContext): Int =
        ctx.decomposition.melds.count { it.isKan }

    /** 是否全为字牌。 */
    fun isAllHonors(ctx: FanContext): Boolean = fullCounts(ctx).asArray().indices
        .filter { it < 34 }
        .filter { fullCounts(ctx)[TileType(it)] > 0 }
        .all { TileType(it).isHonor }

    /** 是否含字牌。 */
    fun hasHonors(ctx: FanContext): Boolean = fullCounts(ctx).asArray().indices
        .filter { it < 34 }
        .any { TileType(it).let { t -> t.isHonor && fullCounts(ctx)[t] > 0 } }

    /** 是否全为一花色（无字牌）。 */
    fun isOneSuit(ctx: FanContext): Boolean {
        val suits = fullCounts(ctx).asArray().indices
            .filter { it < 34 && fullCounts(ctx)[TileType(it)] > 0 }
            .map { TileType(it).suit }
        return suits.isNotEmpty() && suits.all { it == suits.first() } && suits.first() in setOf(Suit.MANZU, Suit.PINZU, Suit.SOUZU)
    }

    /** 是否一花色+字牌（混一色）。 */
    fun isHalfFlush(ctx: FanContext): Boolean {
        val suits = fullCounts(ctx).asArray().indices
            .filter { it < 34 && fullCounts(ctx)[TileType(it)] > 0 }
            .map { TileType(it).suit }
            .filter { it in setOf(Suit.MANZU, Suit.PINZU, Suit.SOUZU) }
            .toSet()
        val hasHonor = suits.size > 0 && hasHonors(ctx)
        return suits.size == 1 && hasHonor
    }

    /** 是否全为幺九牌（1/9/字）。 */
    fun isAllTerminalsOrHonors(ctx: FanContext): Boolean = fullCounts(ctx).asArray().indices
        .filter { it < 34 && fullCounts(ctx)[TileType(it)] > 0 }
        .all { TileType(it).isTerminalOrHonor }

    /** 是否全为幺九序数（1/9，无字）。 */
    fun isAllTerminals(ctx: FanContext): Boolean {
        val types = fullCounts(ctx).asArray().indices
            .filter { it < 34 && fullCounts(ctx)[TileType(it)] > 0 }
            .map { TileType(it) }
        return types.isNotEmpty() && types.all { it.isTerminal }
    }

    /** 是否无字牌。 */
    fun hasNoHonors(ctx: FanContext): Boolean = !hasHonors(ctx)

    /** 是否断幺（全为 2-8 序数，无 1/9/字）。 */
    fun isNoTerminals(ctx: FanContext): Boolean {
        val types = fullCounts(ctx).asArray().indices
            .filter { it < 34 && fullCounts(ctx)[TileType(it)] > 0 }
            .map { TileType(it) }
        return types.isNotEmpty() && types.all { it.isSuited && !it.isTerminal }
    }

    /** 面子中某牌型是否有刻子（含杠）。 */
    fun hasTripletOf(ctx: FanContext, tile: TileType): Boolean =
        ctx.decomposition.melds.any { it.isTriplet && it.tiles.contains(tile) }

    /** 标准形分解。 */
    fun isStandard(ctx: FanContext): Boolean = ctx.decomposition.type == DecompositionType.STANDARD

    /** 是否门清（无副露）。 */
    fun isFullyConcealed(ctx: FanContext): Boolean = ctx.hand.isClosed && ctx.winInfo.isSelfDraw

    /** 是否全求人（4 副露，暗手仅单张和牌）。 */
    fun isAllOpen(ctx: FanContext): Boolean =
        ctx.hand.meldCount == 4 && ctx.winInfo.isDiscardWin

    /** 序数牌 rank 全在某集合内。 */
    fun allSuitedRanksIn(ctx: FanContext, ranks: Set<Int>): Boolean {
        val types = fullCounts(ctx).asArray().indices
            .filter { it < 34 && fullCounts(ctx)[TileType(it)] > 0 }
            .map { TileType(it) }
        return types.filter { it.isSuited }.all { it.rank in ranks }
    }
}
