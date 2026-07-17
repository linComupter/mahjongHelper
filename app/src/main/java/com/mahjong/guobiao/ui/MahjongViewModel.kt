package com.mahjong.guobiao.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mahjong.guobiao.engine.RulesEngine
import com.mahjong.guobiao.engine.counter.TileCounter
import com.mahjong.guobiao.engine.fan.WinInfo
import com.mahjong.guobiao.engine.fan.WinMethod
import com.mahjong.guobiao.model.Hand
import com.mahjong.guobiao.model.Meld
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
        if (current.concealed.size >= 14) return
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
                val fans = best?.second?.counted?.map { "${it.name}(${it.value})" } ?: emptyList()
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
                        possibleFanNames = wt.possibleFans.map { "${it.name}(${it.value})" }
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
