package com.mahjong.guobiao.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AnalysisSettingsTest {

    @Test
    fun `序列化往返保留模式赖子牌与深度`() {
        AnalysisSettings.setAnalysisMode(AnalysisMode.WILD)
        AnalysisSettings.setWildcardTile(8) // 九万
        AnalysisSettings.setSwapDepth(2)

        val text = AnalysisSettings.toProperties()
        AnalysisSettings.loadFromProperties(text)

        assertEquals(AnalysisMode.WILD, AnalysisSettings.analysisMode)
        assertEquals(8, AnalysisSettings.wildcardTileCode)
        assertEquals(2, AnalysisSettings.swapDepth)
    }

    @Test
    fun `旧单行格式兼容缺失键保持当前值`() {
        AnalysisSettings.setAnalysisMode(AnalysisMode.WILD)
        AnalysisSettings.setWildcardTile(0)
        AnalysisSettings.setSwapDepth(3)

        // 旧格式无 mode/wildcard 键：对应设置保持不变（应用启动时单例默认赖子牌即红中）
        AnalysisSettings.loadFromProperties("swapDepth=2")

        assertEquals(AnalysisMode.WILD, AnalysisSettings.analysisMode)
        assertEquals(0, AnalysisSettings.wildcardTileCode)
        assertEquals(2, AnalysisSettings.swapDepth)
    }

    @Test
    fun `非法值被钳制到合法范围`() {
        AnalysisSettings.setWildcardTile(-5)
        assertEquals(0, AnalysisSettings.wildcardTileCode)
        AnalysisSettings.setWildcardTile(99)
        assertEquals(33, AnalysisSettings.wildcardTileCode)
        AnalysisSettings.setSwapDepth(9)
        assertEquals(AnalysisSettings.MAX_DEPTH, AnalysisSettings.swapDepth)
    }

    @Test
    fun `未知模式名不改变当前模式`() {
        AnalysisSettings.setAnalysisMode(AnalysisMode.PURE)
        AnalysisSettings.loadFromProperties("mode=HACKED\nwildcard=5")
        assertEquals(AnalysisMode.PURE, AnalysisSettings.analysisMode)
        assertEquals(5, AnalysisSettings.wildcardTileCode)
    }
}
