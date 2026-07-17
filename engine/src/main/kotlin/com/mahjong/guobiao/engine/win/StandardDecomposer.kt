package com.mahjong.guobiao.engine.win

import com.mahjong.guobiao.model.Decomposition
import com.mahjong.guobiao.model.DecompositionType
import com.mahjong.guobiao.model.Hand
import com.mahjong.guobiao.model.Meld
import com.mahjong.guobiao.model.MeldType
import com.mahjong.guobiao.model.TileCounts
import com.mahjong.guobiao.model.TileType

/**
 * 标准形分解器：将 14 张暗手分解为 (4 - meldCount) 个面子 + 1 个雀头。
 *
 * 算法：DFS 回溯，每次取最低位有牌的牌型，该牌必须被以下之一消耗：
 *  - 雀头（取 2 张，仅当尚未确定雀头）
 *  - 刻子（取 3 张）
 *  - 顺子起点（取该牌 + 后续两连续点数，仅序数牌 rank <= 7）
 *
 * 最低位优先保证不漏不重：最低位牌只能作为顺子起点（无更低牌可作其中/末），
 * 三种消耗方式互斥地确定第一个面子，递归处理剩余。
 *
 * 枚举全部分解，因为不同分解影响番种判定（如九莲宝灯）。
 */
object StandardDecomposer {

    fun decompose(hand: Hand): List<Decomposition> {
        if (!hand.isValidWinSize()) return emptyList()
        val counts = hand.concealedCounts()
        val meldsToFind = 4 - hand.meldCount
        val results = mutableListOf<List<Meld>>()
        val current = mutableListOf<Meld>()
        dfs(counts, meldsToFind, pairNeeded = true, current, results)
        val fixed = hand.melds
        // 固定副露面子拼入每个结果（雀头单独存放）
        return results.map { found ->
            Decomposition(
                DecompositionType.STANDARD,
                fixed + found.filter { !it.isPair },
                found.first { it.isPair }
            )
        }.distinct()
    }

    private fun dfs(
        counts: TileCounts,
        meldsToFind: Int,
        pairNeeded: Boolean,
        current: MutableList<Meld>,
        results: MutableList<List<Meld>>
    ) {
        val remaining = counts.totalCount()
        if (remaining == 0) {
            if (meldsToFind == 0 && !pairNeeded) {
                results.add(current.toList())
            }
            return
        }
        val needed = meldsToFind * 3 + (if (pairNeeded) 2 else 0)
        if (remaining < needed) return
        if (meldsToFind == 0 && !pairNeeded) return

        val t = counts.firstNonZero() ?: return

        // 分支1：作雀头
        if (pairNeeded && counts[t] >= 2) {
            counts.remove(t, 2)
            current.add(Meld(MeldType.PAIR, listOf(t, t)))
            dfs(counts, meldsToFind, false, current, results)
            current.removeAt(current.lastIndex)
            counts.add(t, 2)
        }

        // 分支2：作暗刻
        if (meldsToFind > 0 && counts[t] >= 3) {
            counts.remove(t, 3)
            current.add(Meld.tripletConcealed(t))
            dfs(counts, meldsToFind - 1, pairNeeded, current, results)
            current.removeAt(current.lastIndex)
            counts.add(t, 3)
        }

        // 分支3：作顺子起点（仅序数牌 rank<=7）
        if (meldsToFind > 0 && t.isSuited && t.rank <= 7) {
            val t1 = t.nextRank()!!
            val t2 = t1.nextRank()!!
            if (counts[t1] > 0 && counts[t2] > 0) {
                counts.remove(t); counts.remove(t1); counts.remove(t2)
                current.add(Meld.sequenceConcealed(t))
                dfs(counts, meldsToFind - 1, pairNeeded, current, results)
                current.removeAt(current.lastIndex)
                counts.add(t); counts.add(t1); counts.add(t2)
            }
        }
    }
}
