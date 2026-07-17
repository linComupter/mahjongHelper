package com.mahjong.guobiao.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mahjong.guobiao.engine.RulesEngine
import com.mahjong.guobiao.engine.fan.FanSettingsStore
import com.mahjong.guobiao.engine.fan.WinInfo
import com.mahjong.guobiao.engine.fan.WinMethod
import com.mahjong.guobiao.model.Hand
import com.mahjong.guobiao.model.Meld
import com.mahjong.guobiao.model.MeldType
import com.mahjong.guobiao.model.PlayerSeat
import com.mahjong.guobiao.model.PlayerState
import com.mahjong.guobiao.model.TableState
import com.mahjong.guobiao.model.TileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 单张听牌的展示数据。 */
data class WaitingTileUi(
    val tile: TileType,
    val remainingCount: Int,
    val possibleFanNames: List<String>
)

/** UI 状态。 */
data class MahjongUiState(
    val concealed: List<TileType> = emptyList(),
    val discards: List<TileType> = emptyList(),         // 全场牌河（简化：合并4家）
    val melds: List<Meld> = emptyList(),
    val selfSeat: PlayerSeat = PlayerSeat.EAST,
    val prevailingWind: PlayerSeat = PlayerSeat.EAST,
    val winMethod: WinMethod = WinMethod.SELF_DRAW,
    val waitingTiles: List<WaitingTileUi> = emptyList(),
    val possibleFans: List<String> = emptyList(),
    val isWin: Boolean = false,
    val totalFan: Int = 0,
    val message: String = ""
)

class MahjongViewModel : ViewModel() {

    private val engine = RulesEngine()

    private val _state = MutableStateFlow(MahjongUiState())
    val state: StateFlow<MahjongUiState> = _state.asStateFlow()

    fun addTile(tile: TileType) {
        val current = _state.value
        // 暗手上限 = 14 - 3*meldCount（和牌态最多14张）
        val maxConcealed = 14 - 3 * current.melds.size
        if (current.concealed.size >= maxConcealed) return
        // 每种牌不超过4张（手牌+副露+牌河）
        val inConcealed = current.concealed.count { it == tile }
        val inMelds = current.melds.flatMap { m -> m.tiles }.count { it == tile }
        val inDiscards = current.discards.count { it == tile }
        if (inConcealed + inMelds + inDiscards >= 4) return
        _state.value = current.copy(concealed = (current.concealed + tile).sorted(), message = "")
    }

    fun removeTileAt(index: Int) {
        val current = _state.value
        if (index !in current.concealed.indices) return
        _state.value = current.copy(
            concealed = current.concealed.toMutableList().apply { removeAt(index) },
            message = ""
        )
    }

    fun addDiscard(tile: TileType) {
        val current = _state.value
        // 每种牌不超过4张（手牌+副露+牌河）
        val inConcealed = current.concealed.count { it == tile }
        val inMelds = current.melds.flatMap { m -> m.tiles }.count { it == tile }
        val inDiscards = current.discards.count { it == tile }
        if (inConcealed + inMelds + inDiscards >= 4) return
        _state.value = current.copy(discards = current.discards + tile, message = "")
    }

    fun removeDiscardAt(index: Int) {
        val current = _state.value
        if (index !in current.discards.indices) return
        _state.value = current.copy(
            discards = current.discards.toMutableList().apply { removeAt(index) },
            message = ""
        )
    }

    fun clearHand() {
        _state.value = _state.value.copy(concealed = emptyList(), message = "")
    }

    fun clearDiscards() {
        _state.value = _state.value.copy(discards = emptyList(), message = "")
    }

    fun setWinMethod(method: WinMethod) {
        _state.value = _state.value.copy(winMethod = method)
    }

    fun setSelfSeat(seat: PlayerSeat) {
        _state.value = _state.value.copy(selfSeat = seat)
    }

    fun clearAll() {
        _state.value = MahjongUiState()
    }

    // ── 持久化 ──

    fun loadSettings(context: Context) {
        val prefs = context.getSharedPreferences("fan_settings", Context.MODE_PRIVATE)
        val props = prefs.getString("fan_properties", "") ?: ""
        if (props.isNotEmpty()) FanSettingsStore.loadFromProperties(props)
    }

    fun saveSettings(context: Context) {
        val prefs = context.getSharedPreferences("fan_settings", Context.MODE_PRIVATE)
        prefs.edit().putString("fan_properties", FanSettingsStore.toProperties()).apply()
    }

    /** 分析当前手牌。 */
    fun analyze() {
        viewModelScope.launch(Dispatchers.Default) {
            val s = _state.value
            val hand = Hand(concealed = s.concealed, melds = s.melds)
            val tableState = buildTableState(s)

            if (!hand.isValidWinSize() && !hand.isValidTenpaiSize()) {
                _state.value = s.copy(
                    message = "暗手张数不符：当前 ${hand.concealed.size} 张，需 13(听牌) 或 14(和牌)"
                )
                return@launch
            }

            val result = engine.fullAnalysis(hand, tableState, WinInfo(
                winTile = s.concealed.lastOrNull() ?: TileType.EAST,
                method = s.winMethod,
                selfSeat = s.selfSeat,
                prevailingWind = s.prevailingWind
            ))

            if (result.isWin) {
                val best = result.fanResults.maxByOrNull { it.second.totalFan }
                val fans = best?.second?.counted?.map { "${it.name}(${FanSettingsStore.getValue(it)})" } ?: emptyList()
                _state.value = s.copy(
                    isWin = true,
                    waitingTiles = emptyList(),
                    possibleFans = fans,
                    totalFan = best?.second?.totalFan ?: 0,
                    message = if (best?.second?.meetsMinimum == true) "和牌！合计 ${best.second.totalFan} 番" else "和牌但不足 8 番起和（${best?.second?.totalFan} 番）"
                )
            } else {
                val waits = result.waitingTiles.map { wt ->
                    WaitingTileUi(
                        tile = wt.tile,
                        remainingCount = wt.remainingCount,
                        possibleFanNames = wt.possibleFans.map { "${it.name}(${FanSettingsStore.getValue(it)})" }
                    )
                }.sortedByDescending { it.remainingCount }
                _state.value = s.copy(
                    isWin = false,
                    waitingTiles = waits,
                    possibleFans = emptyList(),
                    totalFan = 0,
                    message = if (waits.isEmpty()) "未听牌" else "听 ${waits.size} 张"
                )
            }
        }
    }

    // ── 副露管理 ──

    fun addMeld(type: MeldType, tile: TileType) {
        val current = _state.value
        val meld = when (type) {
            MeldType.PON -> Meld.pon(tile)
            MeldType.CHI -> Meld.chi(tile)
            MeldType.KAN_OPEN -> Meld.kanOpen(tile)
            MeldType.KAN_CLOSED -> Meld.kanClosed(tile)
            MeldType.KAN_ADDED -> Meld.kanAdded(tile)
            else -> return
        }
        // 4张上限校验
        for (t in meld.tiles) {
            val total = current.concealed.count { it == t } +
                    current.melds.flatMap { m -> m.tiles }.count { it == t } +
                    current.discards.count { it == t }
            if (total + 1 > 4) return
        }
        _state.value = current.copy(melds = current.melds + meld, message = "")
    }

    fun removeMeld(index: Int) {
        val current = _state.value
        if (index !in current.melds.indices) return
        _state.value = current.copy(
            melds = current.melds.toMutableList().apply { removeAt(index) },
            message = ""
        )
    }

    private fun buildTableState(s: MahjongUiState): TableState {
        // 简化：所有牌河合并到南家（非自家），自家信息由 Hand 提供
        val others = PlayerSeat.entries.filter { it != s.selfSeat }
        return TableState(
            players = PlayerSeat.entries.map { seat ->
                if (seat == s.selfSeat) {
                    PlayerState(seat)
                } else {
                    // 简化均分牌河到第一个非自家
                    val discards = if (seat == others.first()) s.discards else emptyList()
                    PlayerState(seat, discards = discards)
                }
            },
            selfSeat = s.selfSeat,
            prevailingWind = s.prevailingWind
        )
    }
}
