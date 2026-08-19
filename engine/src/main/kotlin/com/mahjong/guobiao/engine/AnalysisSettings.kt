package com.mahjong.guobiao.engine

import com.mahjong.guobiao.model.TileType

/** 分析模式：不同模式采用不同的和牌/计番规则。 */
enum class AnalysisMode(val displayName: String) {
    /** 纯大模式：无赖子，按现行国标规则分析。 */
    PURE("纯大模式"),

    /** 赖子模式：指定赖子牌可替代任意牌，满足平胡即可和牌，不计番也可起和。 */
    WILD("赖子模式"),
}

/**
 * 分析设置：分析模式、赖子牌、替换式分析的弃摸深度、持久化。
 *
 * 引擎层不依赖 Android API，序列化为简单 Properties 格式（每行一个 key=value），
 * 由上层（ViewModel）负责 SharedPreferences I/O。
 */
object AnalysisSettings {

    /** 最大可设定的替换深度。 */
    const val MAX_DEPTH = 5

    /** 红中的牌编码（赖子牌默认值）。 */
    const val DEFAULT_WILDCARD = 31

    /** 当前分析模式，默认纯大模式。 */
    var analysisMode: AnalysisMode = AnalysisMode.PURE
        private set

    /** 赖子牌编码（0..33，非花牌），仅赖子模式生效，默认红中。 */
    var wildcardTileCode: Int = DEFAULT_WILDCARD
        private set

    /** 替换深度：弃N摸N，默认1，范围1..MAX_DEPTH。 */
    var swapDepth: Int = 1
        private set

    fun setSwapDepth(depth: Int) {
        swapDepth = depth.coerceIn(1, MAX_DEPTH)
    }

    fun setAnalysisMode(mode: AnalysisMode) {
        analysisMode = mode
    }

    fun setWildcardTile(code: Int) {
        wildcardTileCode = code.coerceIn(0, 33)
    }

    /** 当前生效的赖子牌；纯大模式返回 null（无赖子）。 */
    val activeWildcard: TileType? get() = if (analysisMode == AnalysisMode.WILD) TileType(wildcardTileCode) else null

    fun toProperties(): String =
        "mode=${analysisMode.name}\nwildcard=$wildcardTileCode\nswapDepth=$swapDepth"

    fun loadFromProperties(text: String) {
        text.lines().forEach { line ->
            val parts = line.split("=", limit = 2)
            if (parts.size != 2) return@forEach
            when (parts[0].trim()) {
                "mode" -> parts[1].trim().let { name ->
                    AnalysisMode.entries.firstOrNull { it.name == name }?.let { analysisMode = it }
                }
                "wildcard" -> wildcardTileCode = (parts[1].trim().toIntOrNull() ?: DEFAULT_WILDCARD).coerceIn(0, 33)
                "swapDepth" -> swapDepth = (parts[1].trim().toIntOrNull() ?: 1).coerceIn(1, MAX_DEPTH)
            }
        }
    }
}
