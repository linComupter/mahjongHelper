package com.mahjong.guobiao.engine.fan

object FanRegistry {

    val rules: List<FanRule> = listOf(
        // 3番
        HalfFlush,
        AllTriplets,
        // 4番
        SevenPairsFan,
        // 6番
        FullFlush,
        // 8番
        LuxurySevenPairs,
        // 9番
        SmallThreeDragons,
        MixedTerminals,
        // 10番
        FourConcealedTriplets,
        // 13番
        ThirteenOrphansFan,
        SmallFourWinds,
        // 16番
        BigThreeDragons,
        DoubleLuxurySevenPairs,
        RedPeacock,
        AllGreen,
        AllBlue,
        // 20番
        AllHonors,
        PureTerminals,
        // 24番
        NineGates,
        BigFourWinds,
        TripleLuxurySevenPairs,
        BigSevenStars
    )

    private val byId: Map<String, FanRule> = rules.associateBy { it.id }

    fun byId(id: String): FanRule? = byId[id]

    fun detectAll(ctx: FanContext): List<FanRule> =
        rules.filter { !FanSettingsStore.isHidden(it.id) && it.detect(ctx) }
}
