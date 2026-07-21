package com.mahjong.guobiao.engine.fan

import com.mahjong.guobiao.model.DecompositionType
import com.mahjong.guobiao.model.MeldType
import com.mahjong.guobiao.model.Suit
import com.mahjong.guobiao.model.TileType

// ── 21 种常用番种 ──

// region 3番

object HalfFlush : FanRule {
    override val id = "half_flush"; override val name = "混一色"; override val value = 3
    override fun detect(ctx: FanContext): Boolean = FanUtils.isHalfFlush(ctx)
}

object AllTriplets : FanRule {
    override val id = "pong"; override val name = "碰碰胡"; override val value = 3
    override fun detect(ctx: FanContext): Boolean =
        ctx.decomposition.type == DecompositionType.STANDARD && ctx.decomposition.melds.filter { !it.isPair }.all { it.isTriplet }
}

// endregion

// region 4番

object SevenPairsFan : FanRule {
    override val id = "seven_pairs"; override val name = "七小对"; override val value = 4
    override fun detect(ctx: FanContext): Boolean = ctx.decomposition.type == DecompositionType.SEVEN_PAIRS
}

// endregion

// region 6番

object FullFlush : FanRule {
    override val id = "full_flush"; override val name = "清一色"; override val value = 6
    override fun detect(ctx: FanContext): Boolean = FanUtils.isOneSuit(ctx)
}

// endregion

// region 8番

object LuxurySevenPairs : FanRule {
    override val id = "luxury_seven_pairs"; override val name = "豪华七对"; override val value = 8
    override val subsumes = setOf("seven_pairs")
    override fun detect(ctx: FanContext): Boolean = ctx.hand.isClosed && countLuxuryGroups(ctx.hand) == 1
}

// endregion

// region 9番

object SmallThreeDragons : FanRule {
    override val id = "small_three_dragons"; override val name = "小三元"; override val value = 9
    override fun detect(ctx: FanContext): Boolean {
        val dragons = ctx.decomposition.melds.filter { it.isTriplet && it.tiles.first().isDragon }.map { it.tiles.first() }.toSet()
        val pair = ctx.decomposition.pair?.tiles?.first()
        return dragons.size == 2 && pair?.isDragon == true && pair !in dragons
    }
}

object MixedTerminals : FanRule {
    override val id = "mixed_terminals"; override val name = "混幺九"; override val value = 9
    override val subsumes = setOf("pong")
    override fun detect(ctx: FanContext): Boolean = FanUtils.isAllTerminalsOrHonors(ctx) && FanUtils.hasHonors(ctx)
}

// endregion

// region 10番

object FourConcealedTriplets : FanRule {
    override val id = "four_concealed_triplets"; override val name = "四暗刻"; override val value = 10
    override val subsumes = setOf("pong")
    override fun detect(ctx: FanContext): Boolean = FanUtils.concealedTripletCount(ctx) == 4 && ctx.hand.isClosed
}

// endregion

// region 13番

object ThirteenOrphansFan : FanRule {
    override val id = "thirteen_orphans"; override val name = "十三幺"; override val value = 13
    override fun detect(ctx: FanContext): Boolean = ctx.decomposition.type == DecompositionType.THIRTEEN_ORPHANS
}

object SmallFourWinds : FanRule {
    override val id = "small_four_winds"; override val name = "小四喜"; override val value = 13
    override val subsumes = setOf("pong")
    override fun detect(ctx: FanContext): Boolean {
        val windTriplets = ctx.decomposition.melds.filter { it.isTriplet && it.tiles.first().isWind }.map { it.tiles.first() }.toSet()
        val pair = ctx.decomposition.pair?.tiles?.first()
        return windTriplets.size == 3 && pair?.isWind == true && pair !in windTriplets
    }
}

// endregion

// region 16番

object BigThreeDragons : FanRule {
    override val id = "big_three_dragons"; override val name = "大三元"; override val value = 16
    override val subsumes = setOf("pong")
    override fun detect(ctx: FanContext): Boolean =
        ctx.decomposition.melds.count { it.isTriplet && it.tiles.first().isDragon } == 3
}

object DoubleLuxurySevenPairs : FanRule {
    override val id = "double_luxury_seven_pairs"; override val name = "双豪华七对"; override val value = 16
    override val subsumes = setOf("seven_pairs", "luxury_seven_pairs")
    override fun detect(ctx: FanContext): Boolean = ctx.hand.isClosed && countLuxuryGroups(ctx.hand) == 2
}

object RedPeacock : FanRule {
    override val id = "red_peacock"; override val name = "红孔雀"; override val value = 16
    override val subsumes = setOf("pong", "half_flush")
    override fun detect(ctx: FanContext): Boolean {
        val allowed = setOf(18, 22, 24, 26, 31) // 1s,5s,7s,9s,中
        val counts = FanUtils.fullCounts(ctx)
        for (i in 0 until 34) { if (counts[TileType(i)] > 0 && i !in allowed) return false }
        return true
    }
}

object AllGreen : FanRule {
    override val id = "all_green"; override val name = "绿一色"; override val value = 16
    override fun detect(ctx: FanContext): Boolean = FanUtils.fullCounts(ctx).asArray().indices
        .filter { it < 34 && FanUtils.fullCounts(ctx)[TileType(it)] > 0 }.all { TileType(it).isGreen }
}

object AllBlue : FanRule {
    override val id = "all_blue"; override val name = "蓝一色"; override val value = 16
    override val subsumes = setOf("pong", "half_flush")
    override fun detect(ctx: FanContext): Boolean {
        val allowed = setOf(27, 28, 29, 30, 33, 16) // 东南西北白8筒
        val counts = FanUtils.fullCounts(ctx)
        for (i in 0 until 34) { if (counts[TileType(i)] > 0 && i !in allowed) return false }
        return true
    }
}

// endregion

// region 20番

object AllHonors : FanRule {
    override val id = "all_honors"; override val name = "字一色"; override val value = 20
    override val subsumes = setOf("pong")
    override fun detect(ctx: FanContext): Boolean = FanUtils.isAllHonors(ctx)
}

object PureTerminals : FanRule {
    override val id = "pure_terminals"; override val name = "清幺九"; override val value = 20
    override val subsumes = setOf("pong", "mixed_terminals")
    override fun detect(ctx: FanContext): Boolean = FanUtils.isAllTerminals(ctx)
}

// endregion

// region 24番

object NineGates : FanRule {
    override val id = "nine_gates"; override val name = "九连宝灯"; override val value = 24
    override val subsumes = setOf("full_flush")
    override fun detect(ctx: FanContext): Boolean {
        if (!ctx.hand.isClosed) return false
        val counts = ctx.hand.concealedCounts()
        val suits = counts.nonZeroTypes().map { it.suit }.toSet()
        if (suits.size != 1 || suits.first() !in setOf(Suit.MANZU, Suit.PINZU, Suit.SOUZU)) return false
        for (suit in listOf(Suit.MANZU, Suit.PINZU, Suit.SOUZU)) {
            val base = when (suit) { Suit.MANZU -> 0; Suit.PINZU -> 9; Suit.SOUZU -> 18; else -> return false }
            val c = IntArray(9) { counts[TileType(base + it)] }
            if (c[0] >= 3 && c[8] >= 3 && (1..7).all { c[it] >= 1 } && c.sum() == 14) return true
        }
        return false
    }
}

object BigFourWinds : FanRule {
    override val id = "big_four_winds"; override val name = "大四喜"; override val value = 24
    override val subsumes = setOf("pong", "small_four_winds")
    override fun detect(ctx: FanContext): Boolean {
        val winds = ctx.decomposition.melds.filter { it.isTriplet && it.tiles.first().isWind }.map { it.tiles.first() }.toSet()
        return winds.size == 4
    }
}

object TripleLuxurySevenPairs : FanRule {
    override val id = "triple_luxury_seven_pairs"; override val name = "三豪华七对"; override val value = 24
    override val subsumes = setOf("seven_pairs", "luxury_seven_pairs", "double_luxury_seven_pairs")
    override fun detect(ctx: FanContext): Boolean = ctx.hand.isClosed && countLuxuryGroups(ctx.hand) == 3
}

object BigSevenStars : FanRule {
    override val id = "big_seven_stars"; override val name = "大七星"; override val value = 24
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

// ── 豪华七对计数 ──

internal fun countLuxuryGroups(hand: com.mahjong.guobiao.model.Hand): Int {
    if (!hand.isValidWinSize()) return -1
    val counts = hand.concealedCounts()
    var four = 0; var two = 0
    for (t in TileType.ALL_NON_FLOWER) {
        when (counts[t]) { 2 -> two++; 4 -> four++; 0 -> {} else -> return -1 }
    }
    return if (4 * four + 2 * two == 14) four else -1
}
