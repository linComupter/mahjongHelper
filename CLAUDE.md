# 国标麻将助手 (Guobiao Mahjong Assistant)

Android app that analyzes a mahjong hand against Chinese Official (国标) rules:
1. List achievable 国标 fan types (21 番種)
2. Calculate waiting tiles (听牌)
3. Show remaining count of each waiting tile based on visible discards/melds

## Environment

- **Platform**: Android 8.0+ (minSdk 26, targetSdk 36)
- **UI**: Jetpack Compose + Material3 + MVVM
- **Language**: Kotlin 1.9.22
- **Build**: Gradle 8.11.1, AGP 8.7.2, JDK 17
- **Android SDK**: `E:\AndroidStudioSDK` (configured in `local.properties`)
- **Engine tests**: JUnit 5 (93 tests, pure JVM — no Android device needed)
- **Git**: `https://github.com/linComupter/mahjongHelper.git` (origin)

## Project Structure

```
v4/
├── engine/                          # Pure Kotlin module (0 Android deps, fully unit-testable)
│   └── src/main/kotlin/com/mahjong/guobiao/
│       ├── model/                   # Data model
│       │   ├── TileType.kt          # Tile (0-41 encoding: man/pin/sou/wind/dragon/flower)
│       │   ├── TileCounts.kt        # IntArray(34) counting array, core DFS structure
│       │   ├── Meld.kt              # Chi/Pon/Kan/Triplet/Sequence/Pair
│       │   ├── Hand.kt              # Concealed tiles + melds + flowers
│       │   ├── TableState.kt        # 4-player discards/melds/flowers + seat info
│       │   ├── Decomposition.kt     # Win decomposition (STANDARD/SEVEN_PAIRS/etc)
│       │   └── TileParser.kt        # String→tile list (e.g. "123m456p东东" → tiles)
│       ├── engine/
│       │   ├── win/                 # Win detection
│       │   │   ├── StandardDecomposer.kt   # DFS: 14 tiles → 4 melds + 1 pair (all decomps)
│       │   │   ├── SevenPairsChecker.kt     # 7 distinct pairs + luxury (4-of-kind as 2 pairs)
│       │   │   ├── ThirteenOrphansChecker.kt # 13 terminals/honors + 1 repeat
│       │   │   ├── AllNonAdjacentChecker.kt  # DISABLED (needs rulebook verification)
│       │   │   └── WinChecker.kt            # Entry: returns all valid decompositions
│       │   ├── tenpai/
│       │   │   └── TenpaiCalculator.kt      # 34-way enumeration + WinChecker
│       │   ├── fan/                 # Fan type detection
│       │   │   ├── FanContext.kt    # Context for fan detection (decomp + hand + win info)
│       │   │   ├── FanUtils.kt      # Helper: suit counts, triplet counts, flush checks
│       │   │   ├── FanRules.kt      # 21 fan implementations (24番–3番)
│       │   │   ├── FanScorer.kt     # Scoring: detect all → subsumes deduction → 1-fan minimum
│       │   │   ├── FanRegistry.kt   # All registered fan rules
│       │   │   └── FanSettingsStore.kt  # User-overridable fan values (applied in FanScorer)
│       │   ├── counter/
│       │   │   └── TileCounter.kt   # Remaining = 4 − visible (hand+melds+river)
│       │   ├── DevelopmentAnalyzer.kt   # Shanten + improvement-path analysis
│       │   ├── FanReverseAnalyzer.kt     # Fan-type reverse analysis engine
│       │   ├── AnalysisSettings.kt  # Swap depth + persistence for analysis settings
│       │   └── RulesEngine.kt       # Top-level API
│       └── test/                    # JUnit tests (93 total)
├── app/                             # Android app module
│   └── src/main/java/com/mahjong/guobiao/
│       ├── MainActivity.kt         # Compose UI: bottom nav (手牌分析 / 番数规则 / 分析规则), tile picker, results
│       └── ui/MahjongViewModel.kt  # MVVM: hand/meld/discard state, 4-copy limit, addMeld/removeMeld, persistence
├── build.gradle.kts                 # Root: AGP 8.7.2 + Kotlin 1.9.22
├── settings.gradle.kts              # Includes :engine and :app
├── gradle.properties                # android.useAndroidX=true
├── local.properties                 # sdk.dir=E:\AndroidStudioSDK
├── .gitignore                       # Excludes build/, .gradle/, local.properties, *.apk
└── CLAUDE.md                        # This file
```

## Key Commands

```bash
# Run all engine tests (no device needed)
./gradlew :engine:test

# Build debug APK
./gradlew :app:assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Build and install on connected device
./gradlew :app:installDebug
```

## Architecture Notes

### Tile Encoding & Display
`TileType(code: Int)` — 0-41:
- 0-8: 万 1m-9m → `toString()` = 一万~九万
- 9-17: 筒 1p-9p → `toString()` = 一筒~九筒
- 18-26: 条 1s-9s → `toString()` = 一条~九条
- 27-30: 风 东/南/西/北
- 31-33: 箭 中/发/白
- 34-41: 花 春/夏/秋/冬/梅/兰/竹/菊

`TileParser` uses 'm'/'p'/'s' for input (separate from `toString()`), so test notation like `"123m456p東"` remains unchanged.

### Win Detection Algorithm (StandardDecomposer)
DFS backtracking with "lowest-first" strategy: at each step, the tile with the smallest code MUST be consumed as pair/triplet/sequence-start. This guarantees no duplicates and no misses. Exhaustive enumeration is required because different decompositions yield different fan types (e.g., Nine Gates requires a specific decomposition).

### Tenpai Calculation
34-way enumeration: try adding each of 34 tile types, then call `WinChecker.getAllDecompositions()`. This naturally covers all special waiting patterns (single wait, edge wait, closed wait, 7-pairs wait, 13-orphans 13-way wait, etc.).

### Hand Development Analysis (向听)
`DevelopmentAnalyzer.analyze()`:
1. **和牌态**：直接计分展示番种
2. **听牌态**：检查是否有等待牌可达1番起和；有→正常展示听牌，无→进入替换分析
3. **非听牌态**：替换式分析，深度由`AnalysisSettings.swapDepth`控制（1~3，默认1），枚举弃N张×摸N张的替换组合

替换分析由 `FanReverseAnalyzer` 按番种倒推：对 21 种番种逐一计算不合规牌数 → 跳过超过深度的番种 → 对剩余番种按目标牌池枚举替换组合 → 快速向听预检淘汰无效组合 → 全量 TenpaiCalculator 验证 → 结果按番种聚合。

深度1：浅层先行，找到路径即停止更深尝试
深度2/3：仅对浅层无效的组合递进尝试；预检淘汰 ~75% 无效组合

输出按番种聚合：`SwapTarget` 含番种名、总概率、具体弃牌摸牌路径。每条路径含弃牌/摸牌/剩余张数/概率/后续听牌。

### Analysis Settings
`AnalysisSettings` (singleton, engine layer):
- `swapDepth`: 1~3，控制替换式分析的弃摸张数
- `toProperties()` / `loadFromProperties(text)`: 序列化持久化
- 与 FanSettings 共用 SharedPreferences key `fan_settings`
- UI: "分析规则" tab 提供 Slider 调节深度 + 性能提示

### Fan Scoring
21 种番种，番数范围 3~24：
- 3番: 混一色, 碰碰胡
- 4番: 七小对
- 6番: 清一色
- 8番: 豪华七对
- 9番: 小三元, 混幺九
- 10番: 四暗刻
- 13番: 十三幺, 小四喜
- 16番: 大三元, 双豪华七对, 红孔雀, 绿一色, 蓝一色
- 20番: 字一色, 清幺九
- 24番: 九莲宝灯, 大四喜, 三豪华七对, 大七星

Each `FanRule` has: `value` (default fan points), `subsumes` (Set of fan IDs that are NOT counted), `detect(ctx)` (detection logic).

`FanScorer.score()`: detect all → remove subsumed → sum (using `FanSettingsStore.getValue()`) → check ≥ 1 minimum.

### Fan Settings
`FanSettingsStore` (singleton, engine layer):
- `getValue(rule)` → overridden value or default
- `setOverride(id, value)` → custom fan value; value ≤ 0 clears override
- `isHidden(id)` / `setHidden(id, hide)` / `toggleHidden(id)` → hide rules from analysis
- `toProperties()` / `loadFromProperties(text)` → serialization for SharedPreferences persistence
- Persistence: ViewModel saves via SharedPreferences (`fan_settings.fan_properties`) in `onStop`, loads in `onCreate`
- Fan settings page: two modes — tap to edit value, or "编辑隐藏" mode to toggle hidden state (hidden → filtered from `FanRegistry.detectAll`)
- Hidden rules: grayed out in list, excluded from analysis scoring. Overrides: orange highlight. Both persisted.

## Known Limitations (MVP)

- **全不靠/七星不靠**: Disabled in `AllNonAdjacentChecker` — the precise definition needs official rulebook verification.
- **番種数量**: 21 种精选番种，删除了断幺/自摸/花牌/箭刻/圈风刻/门风刻等低频番种。
- **Fan values**: 基于自定义规则版番数，可能与传统国标有差异。
- **副露 (Melds) input**: UI supports 碰/吃/明杠/暗杠/加杠 via mode chips in the picker area. Meld creation checks 4-copy limit. Current melds shown between hand and picker grid, click to remove.
- **牌河**: 分行展示（每行最多9张），超过4行高度可上下滚动。
- **ML tile recognition**: Phase 2 (not started).
- **听牌无效提示**: 听牌但无法起和时倒推结果为空时，提示用户增加分析深度。

## Design Decisions

- **Engine = pure Kotlin JVM module**: Testable without Android emulator/device. Android app depends on it.
- **`value class` for TileType and TileCounts**: Zero-overhead wrappers around Int/IntArray for performance in DFS hot paths.
- **Separate `pair` field in Decomposition**: Cleaner for fan detection (e.g., Small Four Winds checks if pair is Wind).
- **`subsumes` as static Set per FanRule**: Declarative, no runtime transitive-closure computation needed.
- **AllNonAdjacentChecker disabled rather than partially-correct**: False positives in tenpai damage the core use case more than false negatives for rare patterns.
- **TileParser for tests**: Readable string notation ("1112345678999m5m") makes test cases self-documenting. Parser uses 'm'/'p'/'s' input notation independently of `toString()` Chinese output.
- **Bottom navigation**: Three tabs (手牌分析 / 番数规则 / 分析规则) via `Scaffold` + `NavigationBar`. Simple state-based switching (no NavHost), since only 3 screens.
- **4-copy limit enforced in ViewModel**: `addTile()` and `addDiscard()` both check hand+melds+discards ≤ 4 per tile type.
- **Click-to-remove**: Both hand tiles and discard tiles are clickable for removal. Clear-all buttons for each.
- **Fan overrides persisted via SharedPreferences**: `FanSettingsStore.toProperties()` serializes to text; ViewModel saves on `onStop`, loads on `onCreate`.
- **番种倒推替代通用枚举**: `FanReverseAnalyzer` 从目标番种反向推算所需替换路径，搜索量降低 100~10000 倍。
