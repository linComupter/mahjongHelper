package com.mahjong.guobiao.engine.fan

/**
 * 用户番数配置存储。覆盖番种默认值。
 * 目前为内存存储（TODO: SharedPreferences 持久化）。
 */
object FanSettingsStore {

    private val overrides = mutableMapOf<String, Int>()

    /** 获取番种有效番数（用户覆盖值优先，否则使用默认值）。 */
    fun getValue(rule: FanRule): Int = overrides[rule.id] ?: rule.value

    /** 设置用户自定义番数。value <= 0 清除覆盖。 */
    fun setOverride(ruleId: String, value: Int) {
        if (value <= 0) {
            overrides.remove(ruleId)
        } else {
            overrides[ruleId] = value
        }
    }

    /** 清除所有覆盖。 */
    fun resetAll() { overrides.clear() }

    /** 是否有覆盖。 */
    fun hasOverride(ruleId: String): Boolean = ruleId in overrides

    /** 所有已覆盖的 ID。 */
    val overriddenIds: Set<String> get() = overrides.keys.toSet()
}
