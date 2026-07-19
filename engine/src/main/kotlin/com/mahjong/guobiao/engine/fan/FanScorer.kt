package com.mahjong.guobiao.engine.fan

/** 单个番种判定结果。 */
data class FanMatch(
    val rule: FanRule,
    val counted: Boolean,        // 是否计入总分（未被高番包含）
    val subsumedBy: String? = null // 被哪个高番包含
)

/** 番种计分结果。 */
data class FanResult(
    val allDetected: List<FanRule>,
    val counted: List<FanRule>,
    val subsumed: List<FanRule>,
    val totalFan: Int,
    val meetsMinimum: Boolean  // 国标 1 番起和
) {
    override fun toString(): String {
        val countedStr = counted.joinToString(", ") { "${it.name}(${FanSettingsStore.getValue(it)})" }
        val subsumedStr = if (subsumed.isEmpty()) "" else "  不计: ${subsumed.joinToString(", ") { it.name }}"
        return "合计 $totalFan 番${if (meetsMinimum) "" else "(不足1番起和)"}: $countedStr$subsumedStr"
    }
}

/** 番种计分器：检测全部番种 -> 按 subsumes 排除被含低番 -> 累加 -> 判起和。 */
object FanScorer {

    private const val MINIMUM_FAN = 1

    fun score(ctx: FanContext): FanResult {
        val detected = FanRegistry.detectAll(ctx)
        val detectedIds = detected.map { it.id }.toSet()

        // 被任一高番 subsumes 的番种标记为不计
        val subsumedIds = mutableSetOf<String>()
        for (rule in detected) {
            for (subId in rule.subsumes) {
                if (subId in detectedIds) subsumedIds.add(subId)
            }
        }
        val counted = detected.filter { it.id !in subsumedIds }
        val subsumed = detected.filter { it.id in subsumedIds }
        val total = counted.sumOf { FanSettingsStore.getValue(it) }
        return FanResult(detected, counted, subsumed, total, total >= MINIMUM_FAN)
    }
}
