package com.mahjong.guobiao.engine.fan

import com.mahjong.guobiao.model.DecompositionType
import com.mahjong.guobiao.model.MeldType
import com.mahjong.guobiao.model.PlayerSeat
import com.mahjong.guobiao.model.Suit
import com.mahjong.guobiao.model.TileType

/**
 * 国标 81 番种实现。
 *
 * ⚠️ 番值与"不计"关系依据《中国麻将竞赛规则》通行版本编码；
 *    部分低频番种（全不靠/七星不靠/组合龙等）因规则定义待核实，暂未启用。
 *    每条番种一个 object，便于增删与核实。
 */

// region 88番

object BigFourWinds : FanRule {
    override val id = "big_four_winds"
    override val name = "大四喜"
    override val value = 88
    override val subsumes = setOf("three_winds", "prevailing_wind_triplet", "seat_wind_triplet", "all_triplets", "pong")
    override fun detect(ctx: FanContext): Boolean {
        val windTriplets = ctx.decomposition.melds
            .filter { it.isTriplet && it.tiles.first().isWind }
            .map { it.tiles.first() }.toSet()
        return windTriplets.size == 4
    }
}

object BigThreeDragons : FanRule {
    override val id = "big_three_dragons"
    override val name = "大三元"
    override val value = 88
    override val subsumes = setOf("two_dragons", "dragon_triplet", "all_triplets", "pong")
    override fun detect(ctx: FanContext): Boolean {
        val dragonTriplets = ctx.decomposition.melds
            .filter { it.isTriplet && it.tiles.first().isDragon }
            .count()
        return dragonTriplets == 3
    }
}

object AllGreen : FanRule {
    override val id = "all_green"
    override val name = "绿一色"
    override val value = 88
    override val subsumes = setOf("full_flush", "half_flush")
    override fun detect(ctx: FanContext): Boolean =
        FanUtils.fullCounts(ctx).asArray().indices
            .filter { it < 34 && FanUtils.fullCounts(ctx)[TileType(it)] > 0 }
            .all { TileType(it).isGreen }
}

object NineGates : FanRule {
    override val id = "nine_gates"
    override val name = "九莲宝灯"
    override val value = 88
    override val subsumes = setOf("full_flush", "fully_concealed", "single_wait")
    override fun detect(ctx: FanContext): Boolean {
        if (!ctx.hand.isClosed) return false
        val counts = ctx.hand.concealedCounts()
        // 同花色 1112345678999 + X
        val suits = counts.nonZeroTypes().map { it.suit }.toSet()
        if (suits.size != 1 || suits.first() !in setOf(Suit.MANZU, Suit.PINZU, Suit.SOUZU)) return false
        for (suit in listOf(Suit.MANZU, Suit.PINZU, Suit.SOUZU)) {
            val base = when (suit) { Suit.MANZU -> 0; Suit.PINZU -> 9; Suit.SOUZU -> 18; else -> return false }
            val c = IntArray(9) { counts[TileType(base + it)] }
            // 1112345678999: [3,1,1,1,1,1,1,1,3]；和牌14张为该形+1张任意同花色
            // 即 c[0]>=3 && c[8]>=3 && c[1..7]>=1 且总数14
            if (c[0] >= 3 && c[8] >= 3 && (1..7).all { c[it] >= 1 } && c.sum() == 14) return true
        }
        return false
    }
}

object FourKans : FanRule {
    override val id = "four_kans"
    override val name = "四杠"
    override val value = 88
    override val subsumes = setOf("three_kans", "concealed_kan", "open_kan")
    override fun detect(ctx: FanContext): Boolean = FanUtils.kanCount(ctx) == 4
}

object ThirteenOrphansFan : FanRule {
    override val id = "thirteen_orphans"
    override val name = "十三幺"
    override val value = 88
    override val subsumes = setOf("five_gates", "single_wait", "edge_wait", "closed_wait", "self_draw")
    override fun detect(ctx: FanContext): Boolean =
        ctx.decomposition.type == DecompositionType.THIRTEEN_ORPHANS
}

// 连七对 64番（需七对形+同花色连续rank）
object ConsecutiveSevenPairs : FanRule {
    override val id = "consecutive_pairs"
    override val name = "连七对"
    override val value = 64
    override val subsumes = setOf("seven_pairs", "full_flush", "single_wait")
    override fun detect(ctx: FanContext): Boolean {
        if (ctx.decomposition.type != DecompositionType.SEVEN_PAIRS) return false
        val pairs = ctx.decomposition.melds.filter { it.isPair }.map { it.tiles.first() }
        if (pairs.size != 7 || pairs.any { !it.isSuited }) return false
        val suit = pairs.first().suit
        if (pairs.any { it.suit != suit }) return false
        val ranks = pairs.map { it.rank }.sorted()
        return ranks == (ranks.first()..ranks.first() + 6).toList() && ranks.first() in 1..3
    }
}

// endregion

// region 64番

object FourConcealedTriplets : FanRule {
    override val id = "four_concealed_triplets"
    override val name = "四暗刻"
    override val value = 64
    override val subsumes = setOf("three_concealed_triplets", "two_concealed_triplets", "all_triplets")
    override fun detect(ctx: FanContext): Boolean =
        FanUtils.concealedTripletCount(ctx) == 4 && ctx.hand.isClosed
}

// 清一色 24番（部分版本列64番；本实现取24番通行版）
object FullFlush : FanRule {
    override val id = "full_flush"
    override val name = "清一色"
    override val value = 24
    override val subsumes = setOf("no_honors")
    override fun detect(ctx: FanContext): Boolean = FanUtils.isOneSuit(ctx)
}

// 七对 24番
object SevenPairsFan : FanRule {
    override val id = "seven_pairs"
    override val name = "七对"
    override val value = 24
    override val subsumes = setOf("single_wait")
    override fun detect(ctx: FanContext): Boolean =
        ctx.decomposition.type == DecompositionType.SEVEN_PAIRS
}

// endregion

// region 24番

object SmallFourWinds : FanRule {
    override val id = "small_four_winds"
    override val name = "小四喜"
    override val value = 24
    override val subsumes = setOf("three_winds", "prevailing_wind_triplet", "seat_wind_triplet", "all_triplets")
    override fun detect(ctx: FanContext): Boolean {
        val windTriplets = ctx.decomposition.melds
            .filter { it.isTriplet && it.tiles.first().isWind }
            .map { it.tiles.first() }.toSet()
        val pairTile = ctx.decomposition.pair?.tiles?.first()
        val pairIsWind = pairTile?.isWind == true && pairTile !in windTriplets
        return windTriplets.size == 3 && pairIsWind
    }
}

object SmallThreeDragons : FanRule {
    override val id = "small_three_dragons"
    override val name = "小三元"
    override val value = 24
    override val subsumes = setOf("two_dragons", "dragon_triplet")
    override fun detect(ctx: FanContext): Boolean {
        val dragonTriplets = ctx.decomposition.melds
            .filter { it.isTriplet && it.tiles.first().isDragon }
            .map { it.tiles.first() }.toSet()
        val pairTile = ctx.decomposition.pair?.tiles?.first()
        val pairIsDragon = pairTile?.isDragon == true && pairTile !in dragonTriplets
        return dragonTriplets.size == 2 && pairIsDragon
    }
}

object PureTerminalChows : FanRule {
    override val id = "pure_terminal_chows"
    override val name = "一色双龙会"
    override val value = 24
    override val subsumes = setOf("full_flush", "no_honors")
    override fun detect(ctx: FanContext): Boolean {
        // 同花色 2组123 + 2组789 + 5作雀头
        if (ctx.decomposition.type != DecompositionType.STANDARD) return false
        val melds = ctx.decomposition.melds.filter { !it.isPair }
        val pair = ctx.decomposition.pair ?: return false
        if (melds.size != 4 || melds.any { !it.isSequence }) return false
        val suit = melds.first().tiles.first().suit
        if (melds.any { it.tiles.first().suit != suit }) return false
        val starts = melds.map { it.tiles.first().rank }.sorted()
        // 123,123,789,789 -> starts [1,1,7,7]
        val pairTile = pair.tiles.first()
        return starts == listOf(1, 1, 7, 7) && pairTile.suit == suit && pairTile.rank == 5
    }
}

// endregion

// region 12番

object ThreeConcealedTriplets : FanRule {
    override val id = "three_concealed_triplets"
    override val name = "三暗刻"
    override val value = 12
    override val subsumes = setOf("two_concealed_triplets")
    override fun detect(ctx: FanContext): Boolean = FanUtils.concealedTripletCount(ctx) >= 3
}

object ThreeKans : FanRule {
    override val id = "three_kans"
    override val name = "三杠"
    override val value = 12
    override val subsumes = setOf("concealed_kan", "open_kan")
    override fun detect(ctx: FanContext): Boolean = FanUtils.kanCount(ctx) >= 3
}

// 大于五：全部序数 rank>=5
object AllAboveFive : FanRule {
    override val id = "all_above_five"
    override val name = "大于五"
    override val value = 12
    override fun detect(ctx: FanContext): Boolean = FanUtils.allSuitedRanksIn(ctx, setOf(5, 6, 7, 8, 9)) && !FanUtils.hasHonors(ctx)
}

object AllBelowFive : FanRule {
    override val id = "all_below_five"
    override val name = "小于五"
    override val value = 12
    override fun detect(ctx: FanContext): Boolean = FanUtils.allSuitedRanksIn(ctx, setOf(1, 2, 3, 4, 5)) && !FanUtils.hasHonors(ctx)
}

object ThreeWinds : FanRule {
    override val id = "three_winds"
    override val name = "三风刻"
    override val value = 12
    override fun detect(ctx: FanContext): Boolean =
        ctx.decomposition.melds.count { it.isTriplet && it.tiles.first().isWind } >= 3
}

// 三色三同顺：3花色同rank的顺子
object ThreeSuitSameSequence : FanRule {
    override val id = "three_suit_same_sequence"
    override val name = "三色三同顺"
    override val value = 12
    override fun detect(ctx: FanContext): Boolean {
        val seqStarts = ctx.decomposition.melds
            .filter { it.isSequence }
            .groupBy { it.tiles.first().rank }
        return seqStarts.any { (_, seqs) ->
            seqs.map { it.tiles.first().suit }.toSet() == setOf(Suit.MANZU, Suit.PINZU, Suit.SOUZU)
        }
    }
}

// 三色三节高：3花色同rank刻子递增？国标定义为三色同点刻子
object ThreeSuitSameTriplet : FanRule {
    override val id = "three_suit_same_triplet"
    override val name = "三同刻"
    override val value = 12
    override fun detect(ctx: FanContext): Boolean {
        val tripletRanks = ctx.decomposition.melds
            .filter { it.isTriplet }
            .groupBy { it.tiles.first().rank }
        return tripletRanks.any { (_, triplets) ->
            triplets.map { it.tiles.first().suit }.toSet() == setOf(Suit.MANZU, Suit.PINZU, Suit.SOUZU)
        }
    }
}

// endregion

// region 8番

object AllTriplets : FanRule {
    override val id = "all_triplets"
    override val name = "碰碰和"
    override val value = 8
    override fun detect(ctx: FanContext): Boolean =
        ctx.decomposition.type == DecompositionType.STANDARD &&
            ctx.decomposition.melds.filter { !it.isPair }.all { it.isTriplet }
}

object HalfFlush : FanRule {
    override val id = "half_flush"
    override val name = "混一色"
    override val value = 8
    override fun detect(ctx: FanContext): Boolean = FanUtils.isHalfFlush(ctx)
}

object MixedTerminals : FanRule {
    override val id = "mixed_terminals"
    override val name = "混幺九"
    override val value = 8
    override fun detect(ctx: FanContext): Boolean =
        FanUtils.isAllTerminalsOrHonors(ctx) && FanUtils.hasHonors(ctx)
}

object LastTileDraw : FanRule {
    override val id = "last_tile_draw"
    override val name = "妙手回春"
    override val value = 8
    override fun detect(ctx: FanContext): Boolean = ctx.winInfo.method == com.mahjong.guobiao.engine.fan.WinMethod.LAST_TILE_DRAW
}

object LastDiscard : FanRule {
    override val id = "last_discard"
    override val name = "海底捞月"
    override val value = 8
    override fun detect(ctx: FanContext): Boolean = ctx.winInfo.method == com.mahjong.guobiao.engine.fan.WinMethod.LAST_DISCARD
}

object KanDrawWin : FanRule {
    override val id = "kan_draw_win"
    override val name = "杠上开花"
    override val value = 8
    override fun detect(ctx: FanContext): Boolean = false // TODO: 需"补杠后摸牌"标记，暂不实现
}

object RobbingKan : FanRule {
    override val id = "robbing_kan"
    override val name = "抢杠和"
    override val value = 8
    override fun detect(ctx: FanContext): Boolean = ctx.winInfo.isRobbingKan
}

// endregion

// region 6番

object NoTerminals : FanRule {
    override val id = "no_terminals"
    override val name = "断幺"
    override val value = 6
    override fun detect(ctx: FanContext): Boolean = FanUtils.isNoTerminals(ctx)
}

object ConcealedKan : FanRule {
    override val id = "concealed_kan"
    override val name = "暗杠"
    override val value = 6
    override fun detect(ctx: FanContext): Boolean =
        ctx.decomposition.melds.any { it.type == MeldType.KAN_CLOSED }
}

object OpenKan : FanRule {
    override val id = "open_kan"
    override val name = "明杠"
    override val value = 6
    override fun detect(ctx: FanContext): Boolean =
        ctx.decomposition.melds.any { it.type in setOf(MeldType.KAN_OPEN, MeldType.KAN_ADDED) }
}

object DragonTriplet : FanRule {
    override val id = "dragon_triplet"
    override val name = "箭刻"
    override val value = 6
    override fun detect(ctx: FanContext): Boolean =
        ctx.decomposition.melds.any { it.isTriplet && it.tiles.first().isDragon }
}

object PrevailingWindTriplet : FanRule {
    override val id = "prevailing_wind_triplet"
    override val name = "圈风刻"
    override val value = 6
    override fun detect(ctx: FanContext): Boolean {
        val windTile = when (ctx.prevailingWind) {
            PlayerSeat.EAST -> TileType.EAST
            PlayerSeat.SOUTH -> TileType.SOUTH
            PlayerSeat.WEST -> TileType.WEST
            PlayerSeat.NORTH -> TileType.NORTH
        }
        return FanUtils.hasTripletOf(ctx, windTile)
    }
}

object SeatWindTriplet : FanRule {
    override val id = "seat_wind_triplet"
    override val name = "门风刻"
    override val value = 6
    override fun detect(ctx: FanContext): Boolean {
        val windTile = when (ctx.seatWind) {
            PlayerSeat.EAST -> TileType.EAST
            PlayerSeat.SOUTH -> TileType.SOUTH
            PlayerSeat.WEST -> TileType.WEST
            PlayerSeat.NORTH -> TileType.NORTH
        }
        return FanUtils.hasTripletOf(ctx, windTile)
    }
}

object TwoDragonTriplets : FanRule {
    override val id = "two_dragons"
    override val name = "双箭刻"
    override val value = 6
    override fun detect(ctx: FanContext): Boolean =
        ctx.decomposition.melds.count { it.isTriplet && it.tiles.first().isDragon } >= 2
}

// endregion

// region 4番

object FullyConcealed : FanRule {
    override val id = "fully_concealed"
    override val name = "不求人"
    override val value = 4
    override fun detect(ctx: FanContext): Boolean = FanUtils.isFullyConcealed(ctx)
}

object AllOpen : FanRule {
    override val id = "all_open"
    override val name = "全求人"
    override val value = 4
    override fun detect(ctx: FanContext): Boolean = FanUtils.isAllOpen(ctx)
}

object SingleWait : FanRule {
    override val id = "single_wait"
    override val name = "单钓将"
    override val value = 4
    override fun detect(ctx: FanContext): Boolean {
        // 单骑：听牌时仅单张雀头听，和牌方式为单骑
        // 简化判定：和牌前暗手为4面子+1单张（无对子作雀头）
        // 此处需听牌态信息；和牌后判定为：非边张非嵌张的单骑
        // 完整判定需 TenpaiCalculator 配合，此处先返回 false 由调用方补充
        return false // TODO: 需听牌形态信息
    }
}

object EdgeWait : FanRule {
    override val id = "edge_wait"
    override val name = "边张"
    override val value = 4
    override fun detect(ctx: FanContext): Boolean {
        // 边张：和牌完成边张顺子（12听3 或 89听7）
        // 需和牌所在顺子为边张顺子
        val winTile = ctx.winInfo.winTile
        if (!winTile.isSuited) return false
        // 找含和牌的顺子
        val seq = ctx.decomposition.melds.filter { it.isSequence && it.tiles.contains(winTile) }
        return seq.any { s ->
            val ranks = s.tiles.map { it.rank }
            // 123 顺子且和牌为3（边3），或 789 顺子且和牌为7（边7）
            (ranks == listOf(1, 2, 3) && winTile.rank == 3) ||
                (ranks == listOf(7, 8, 9) && winTile.rank == 7)
        }
    }
}

object ClosedWait : FanRule {
    override val id = "closed_wait"
    override val name = "嵌张"
    override val value = 4
    override fun detect(ctx: FanContext): Boolean {
        // 嵌张：和牌为顺子中间张（如13听2、57听6）
        val winTile = ctx.winInfo.winTile
        if (!winTile.isSuited) return false
        val seq = ctx.decomposition.melds.filter { it.isSequence && it.tiles.contains(winTile) }
        return seq.any { s ->
            val ranks = s.tiles.map { it.rank }
            winTile.rank in ranks && winTile.rank !in listOf(ranks.first(), ranks.last())
        }
    }
}

object TwoConcealedTriplets : FanRule {
    override val id = "two_concealed_triplets"
    override val name = "双暗刻"
    override val value = 4
    override fun detect(ctx: FanContext): Boolean = FanUtils.concealedTripletCount(ctx) >= 2
}

// endregion

// region 2番

object NoHonors : FanRule {
    override val id = "no_honors"
    override val name = "无字"
    override val value = 2
    override fun detect(ctx: FanContext): Boolean = FanUtils.hasNoHonors(ctx)
}

// endregion

// region 1番

object SelfDraw : FanRule {
    override val id = "self_draw"
    override val name = "自摸"
    override val value = 1
    override fun detect(ctx: FanContext): Boolean = ctx.winInfo.isSelfDraw
}

object FlowerTile : FanRule {
    override val id = "flower"
    override val name = "花牌"
    override val value = 1
    override fun detect(ctx: FanContext): Boolean = ctx.hand.flowers.isNotEmpty()
}

// endregion

// region 豪华七对系列（8番/16番/24番）

object LuxurySevenPairs : FanRule {
    override val id = "luxury_seven_pairs"
    override val name = "豪华七对"
    override val value = 8
    override val subsumes = setOf("seven_pairs")
    override fun detect(ctx: FanContext): Boolean = ctx.hand.isClosed && countLuxuryGroups(ctx.hand) == 1
}

object DoubleLuxurySevenPairs : FanRule {
    override val id = "double_luxury_seven_pairs"
    override val name = "双豪华七对"
    override val value = 16
    override val subsumes = setOf("seven_pairs", "luxury_seven_pairs")
    override fun detect(ctx: FanContext): Boolean = ctx.hand.isClosed && countLuxuryGroups(ctx.hand) == 2
}

object TripleLuxurySevenPairs : FanRule {
    override val id = "triple_luxury_seven_pairs"
    override val name = "三豪华七对"
    override val value = 24
    override val subsumes = setOf("seven_pairs", "luxury_seven_pairs", "double_luxury_seven_pairs")
    override fun detect(ctx: FanContext): Boolean = ctx.hand.isClosed && countLuxuryGroups(ctx.hand) == 3
}

private fun countLuxuryGroups(hand: com.mahjong.guobiao.model.Hand): Int {
    if (!hand.isValidWinSize()) return -1
    val counts = hand.concealedCounts()
    var four = 0; var two = 0
    for (t in com.mahjong.guobiao.model.TileType.ALL_NON_FLOWER) {
        when (counts[t]) { 2 -> two++; 4 -> four++; 0 -> {} else -> return -1 }
    }
    return if (4 * four + 2 * two == 14) four else -1
}

// endregion

// region 特殊牌型（16番/24番）

object RedPeacock : FanRule {
    override val id = "red_peacock"
    override val name = "红孔雀"
    override val value = 16
    override val subsumes = setOf("all_triplets", "half_flush", "dragon_triplet")
    override fun detect(ctx: FanContext): Boolean {
        val allowed = setOf(18, 22, 24, 26, 31) // 1s,5s,7s,9s,中
        val counts = FanUtils.fullCounts(ctx)
        for (i in 0 until 34) { if (counts[TileType(i)] > 0 && i !in allowed) return false }
        return true
    }
}

object AllBlue : FanRule {
    override val id = "all_blue"
    override val name = "蓝一色"
    override val value = 16
    override val subsumes = setOf("all_triplets", "half_flush")
    override fun detect(ctx: FanContext): Boolean {
        val allowed = setOf(27, 28, 29, 30, 33, 16) // 东南西北白8筒
        val counts = FanUtils.fullCounts(ctx)
        for (i in 0 until 34) { if (counts[TileType(i)] > 0 && i !in allowed) return false }
        return true
    }
}

object BigSevenStars : FanRule {
    override val id = "big_seven_stars"
    override val name = "大七星"
    override val value = 24
    override val subsumes = setOf("seven_pairs", "all_honors")
    override fun detect(ctx: FanContext): Boolean {
        if (!ctx.hand.isClosed) return false
        val counts = ctx.hand.concealedCounts()
        for (c in 27..33) { if (counts[TileType(c)] != 2) return false }
        for (c in 0..26) { if (counts[TileType(c)] > 0) return false }
        return true
    }
}

// endregion
