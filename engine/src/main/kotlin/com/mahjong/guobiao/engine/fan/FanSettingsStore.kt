package com.mahjong.guobiao.engine.fan

/**
 * 用户番数配置存储：番数覆盖 + 隐藏番种 + 持久化序列化。
 *
 * 引擎层不直接依赖 Android API，序列化为简单 Map/Properties 格式，
 * 由上层（ViewModel）负责实际的 SharedPreferences I/O。
 */
object FanSettingsStore {

    private val overrides = mutableMapOf<String, Int>()
    private val hidden = mutableSetOf<String>()

    // ── 番数覆盖 ──

    fun getValue(rule: FanRule): Int = overrides[rule.id] ?: rule.value
    fun setOverride(ruleId: String, value: Int) {
        if (value <= 0) overrides.remove(ruleId) else overrides[ruleId] = value
    }
    fun hasOverride(ruleId: String): Boolean = ruleId in overrides
    val overriddenIds: Set<String> get() = overrides.keys.toSet()

    // ── 隐藏番种 ──

    fun isHidden(ruleId: String): Boolean = ruleId in hidden
    fun setHidden(ruleId: String, hide: Boolean) {
        if (hide) hidden.add(ruleId) else hidden.remove(ruleId)
    }
    fun toggleHidden(ruleId: String) {
        if (ruleId in hidden) hidden.remove(ruleId) else hidden.add(ruleId)
    }
    val hiddenIds: Set<String> get() = hidden.toSet()

    // ── 重置 ──

    fun resetAll() { overrides.clear(); hidden.clear() }

    // ── 序列化（供 SharedPreferences 持久化） ──

    /** 导出为简单 Properties 文本格式。id=value 或 id=#hidden */
    fun toProperties(): String = buildString {
        overrides.forEach { (id, v) -> appendLine("$id=$v") }
        hidden.forEach { id -> appendLine("$id=#") }
    }

    /** 从 Properties 文本加载。 */
    fun loadFromProperties(text: String) {
        overrides.clear()
        hidden.clear()
        text.lines().forEach { line ->
            val parts = line.split("=", limit = 2)
            if (parts.size == 2) {
                val id = parts[0].trim()
                val raw = parts[1].trim()
                if (raw == "#") hidden.add(id)
                else raw.toIntOrNull()?.let { overrides[id] = it }
            }
        }
    }
}
