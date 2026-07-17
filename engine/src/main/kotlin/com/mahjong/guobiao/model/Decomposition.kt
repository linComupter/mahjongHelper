package com.mahjong.guobiao.model

/** 和牌分解类型。 */
enum class DecompositionType {
    STANDARD,          // 4 面子 1 雀头
    SEVEN_PAIRS,       // 七对
    THIRTEEN_ORPHANS,  // 十三幺
    ALL_NON_ADJACENT,  // 全不靠
    SEVEN_STARS        // 七星不靠（全不靠的特例，含 7 种字牌）
}

/**
 * 和牌分解结果。一次和牌可能有多种分解，全部保留以供番种判定。
 *
 * @param type 分解类型
 * @param melds 面子列表（标准形：副露+暗手分解的 4 面子；七对：7 个 PAIR；十三幺/全不靠：特殊）
 * @param pair 雀头（标准形必需；十三幺为重复的那张）
 */
data class Decomposition(
    val type: DecompositionType,
    val melds: List<Meld>,
    val pair: Meld? = null
) {
    override fun toString(): String =
        "$type: ${melds.joinToString(" ")}${if (pair != null && pair !in melds) " 雀头=$pair" else ""}"
}
