package com.mahjong.guobiao.engine.fan

/** 番种注册表：管理全部番种实例。 */
object FanRegistry {

    val rules: List<FanRule> = listOf(
        // 88番
        BigFourWinds,
        BigThreeDragons,
        AllGreen,
        NineGates,
        FourKans,
        ThirteenOrphansFan,
        // 64番
        FourConcealedTriplets,
        ConsecutiveSevenPairs,
        // 24番
        FullFlush,
        SevenPairsFan,
        SmallFourWinds,
        SmallThreeDragons,
        PureTerminalChows,
        // 12番
        ThreeConcealedTriplets,
        ThreeKans,
        AllAboveFive,
        AllBelowFive,
        ThreeWinds,
        ThreeSuitSameSequence,
        ThreeSuitSameTriplet,
        // 8番
        AllTriplets,
        HalfFlush,
        MixedTerminals,
        LastTileDraw,
        LastDiscard,
        KanDrawWin,
        RobbingKan,
        // 6番
        NoTerminals,
        ConcealedKan,
        OpenKan,
        DragonTriplet,
        PrevailingWindTriplet,
        SeatWindTriplet,
        TwoDragonTriplets,
        // 4番
        FullyConcealed,
        AllOpen,
        SingleWait,
        EdgeWait,
        ClosedWait,
        TwoConcealedTriplets,
        // 2番
        NoHonors,
        // 1番
        SelfDraw,
        FlowerTile
    )

    private val byId: Map<String, FanRule> = rules.associateBy { it.id }

    fun byId(id: String): FanRule? = byId[id]

    /** 检测上下文中成立的所有番种。 */
    fun detectAll(ctx: FanContext): List<FanRule> = rules.filter { it.detect(ctx) }
}
