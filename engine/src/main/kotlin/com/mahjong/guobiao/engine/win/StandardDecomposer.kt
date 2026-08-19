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

    // ── 赖子（赖子牌可替代任意牌）标准形分解 ──

    /**
     * 赖子模式的标准形分解：wildcard 指定的牌型在本手牌中全部按"赖子"处理，
     * 可替代任意牌（雀头/刻子/顺子任意位置）。分解结果为具体牌（赖子落位到替代的牌），
     * 供番种判定直接使用。
     */
    fun decomposeWildcard(hand: Hand, wildcard: TileType): List<Decomposition> {
        if (!hand.isValidWinSize()) return emptyList()
        val counts = hand.concealedCounts()
        val w = counts[wildcard]
        if (w == 0) return decompose(hand)
        counts[wildcard] = 0  // 赖子移出实牌计数，作为可替代牌张
        val meldsToFind = 4 - hand.meldCount
        val results = mutableListOf<List<Meld>>()
        val current = mutableListOf<Meld>()
        dfsWild(counts, w, meldsToFind, pairNeeded = true, current, results, wildcard)
        val fixed = hand.melds
        return results.map { found ->
            Decomposition(
                DecompositionType.STANDARD,
                fixed + found.filter { !it.isPair },
                found.first { it.isPair }
            )
        }.distinct()
    }

    private fun dfsWild(
        counts: TileCounts,
        w: Int,
        meldsToFind: Int,
        pairNeeded: Boolean,
        current: MutableList<Meld>,
        results: MutableList<List<Meld>>,
        wildcard: TileType
    ) {
        val remaining = counts.totalCount() + w
        if (remaining == 0) {
            if (meldsToFind == 0 && !pairNeeded) results.add(current.toList())
            return
        }
        val needed = meldsToFind * 3 + (if (pairNeeded) 2 else 0)
        if (remaining < needed) return
        if (meldsToFind == 0 && !pairNeeded) return

        val t = counts.firstNonZero()
        if (t == null) {
            // 只剩赖子：整组拆为面子/雀头（赖子按自身牌型计，供番种判定）
            if (remaining == needed) {
                val groups = current.toMutableList()
                if (pairNeeded) groups.add(Meld.pair(wildcard))
                repeat(meldsToFind) { groups.add(Meld.tripletConcealed(wildcard)) }
                results.add(groups)
            }
            return
        }

        // 雀头：t 作雀头，差几张用赖子补
        if (pairNeeded) {
            val r = counts[t]
            for (tReal in 1..minOf(r, 2)) {
                if (2 - tReal <= w) {
                    counts.remove(t, tReal)
                    current.add(Meld.pair(t))
                    dfsWild(counts, w - (2 - tReal), meldsToFind, false, current, results, wildcard)
                    current.removeAt(current.lastIndex)
                    counts.add(t, tReal)
                }
            }
        }

        // 刻子：t 作刻子
        if (meldsToFind > 0) {
            val r = counts[t]
            for (tReal in 1..minOf(r, 3)) {
                if (3 - tReal <= w) {
                    counts.remove(t, tReal)
                    current.add(Meld.tripletConcealed(t))
                    dfsWild(counts, w - (3 - tReal), meldsToFind - 1, pairNeeded, current, results, wildcard)
                    current.removeAt(current.lastIndex)
                    counts.add(t, tReal)
                }
            }
        }

        // 顺子：t 可位于顺子首位/中间/末尾（中间、末尾的低位由赖子补齐）
        if (meldsToFind > 0 && t.isSuited) {
            // 1) 首位：t, t+1, t+2
            if (t.rank <= 7) {
                val t1 = t.nextRank()!!
                val t2 = t1.nextRank()!!
                val r1 = counts[t1]; val r2 = counts[t2]
                if (r1 > 0 && r2 > 0) {
                    counts.remove(t); counts.remove(t1); counts.remove(t2)
                    current.add(Meld.sequenceConcealed(t))
                    dfsWild(counts, w, meldsToFind - 1, pairNeeded, current, results, wildcard)
                    current.removeAt(current.lastIndex); counts.add(t); counts.add(t1); counts.add(t2)
                }
                if (r1 > 0 && w >= 1) {
                    counts.remove(t); counts.remove(t1)
                    current.add(Meld.sequenceConcealed(t))
                    dfsWild(counts, w - 1, meldsToFind - 1, pairNeeded, current, results, wildcard)
                    current.removeAt(current.lastIndex); counts.add(t); counts.add(t1)
                }
                if (w >= 1 && r2 > 0) {
                    counts.remove(t); counts.remove(t2)
                    current.add(Meld.sequenceConcealed(t))
                    dfsWild(counts, w - 1, meldsToFind - 1, pairNeeded, current, results, wildcard)
                    current.removeAt(current.lastIndex); counts.add(t); counts.add(t2)
                }
                if (w >= 2) {
                    counts.remove(t)
                    current.add(Meld.sequenceConcealed(t))
                    dfsWild(counts, w - 2, meldsToFind - 1, pairNeeded, current, results, wildcard)
                    current.removeAt(current.lastIndex); counts.add(t)
                }
            }
            // 2) 中间：t-1(赖), t, t+1
            if (t.rank >= 2 && t.rank <= 8) {
                val tPrev = t.prevRank()!!
                val tNext = t.nextRank()!!
                val rn = counts[tNext]
                if (w >= 1 && rn > 0) {
                    counts.remove(t); counts.remove(tNext)
                    current.add(Meld.sequenceConcealed(tPrev))
                    dfsWild(counts, w - 1, meldsToFind - 1, pairNeeded, current, results, wildcard)
                    current.removeAt(current.lastIndex); counts.add(t); counts.add(tNext)
                }
                if (w >= 2) {
                    counts.remove(t)
                    current.add(Meld.sequenceConcealed(tPrev))
                    dfsWild(counts, w - 2, meldsToFind - 1, pairNeeded, current, results, wildcard)
                    current.removeAt(current.lastIndex); counts.add(t)
                }
            }
            // 3) 末尾：t-2(赖), t-1(赖), t
            if (t.rank >= 3) {
                if (w >= 2) {
                    counts.remove(t)
                    current.add(Meld.sequenceConcealed(t.prevRank()!!.prevRank()!!))
                    dfsWild(counts, w - 2, meldsToFind - 1, pairNeeded, current, results, wildcard)
                    current.removeAt(current.lastIndex); counts.add(t)
                }
            }
        }
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
