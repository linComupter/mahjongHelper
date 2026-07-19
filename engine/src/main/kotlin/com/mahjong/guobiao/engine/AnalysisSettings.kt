package com.mahjong.guobiao.engine

/**
 * 分析设置：替换式分析的弃摸深度、持久化。
 *
 * 引擎层不依赖 Android API，序列化为简单 Properties 格式，
 * 由上层（ViewModel）负责 SharedPreferences I/O。
 */
object AnalysisSettings {

    /** 替换深度：弃N摸N，默认1，范围1~3。 */
    var swapDepth: Int = 1
        private set

    fun setSwapDepth(depth: Int) {
        swapDepth = depth.coerceIn(1, 3)
    }

    fun toProperties(): String = "swapDepth=$swapDepth"

    fun loadFromProperties(text: String) {
        text.lines().firstOrNull()?.let { line ->
            val parts = line.split("=", limit = 2)
            if (parts.size == 2 && parts[0].trim() == "swapDepth") {
                swapDepth = (parts[1].trim().toIntOrNull() ?: 1).coerceIn(1, 3)
            }
        }
    }
}
