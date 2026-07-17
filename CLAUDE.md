# 国标麻将助手 (Guobiao Mahjong Assistant)

Android app that analyzes a mahjong hand against Chinese Official (国标) rules:
1. List achievable 国标 fan types (81 番種)
2. Calculate waiting tiles (听牌)
3. Show remaining count of each waiting tile based on visible discards/melds

## Environment

- **Platform**: Android 8.0+ (minSdk 26, targetSdk 36)
- **UI**: Jetpack Compose + Material3 + MVVM
- **Language**: Kotlin 1.9.22
- **Build**: Gradle 8.11.1, AGP 8.7.2, JDK 17
- **Android SDK**: `E:\AndroidStudioSDK` (configured in `local.properties`)
- **Engine tests**: JUnit 5 (89 tests, pure JVM — no Android device needed)
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
│       │   │   ├── SevenPairsChecker.kt     # 7 distinct pairs, no open melds
│       │   │   ├── ThirteenOrphansChecker.kt # 13 terminals/honors + 1 repeat
│       │   │   ├── AllNonAdjacentChecker.kt  # DISABLED (needs rulebook verification)
│       │   │   └── WinChecker.kt            # Entry: returns all valid decompositions
│       │   ├── tenpai/
│       │   │   └── TenpaiCalculator.kt      # 34-way enumeration + WinChecker
│       │   ├── fan/                 # Fan type detection
│       │   │   ├── FanContext.kt    # Context for fan detection (decomp + hand + win info)
│       │   │   ├── FanUtils.kt      # Helper: suit counts, triplet counts, flush checks
│       │   │   ├── FanRules.kt      # ~40 fan implementations (88番–1番)
│       │   │   ├── FanScorer.kt     # Scoring: detect all → subsumes deduction → 8-fan minimum
│       │   │   ├── FanRegistry.kt   # All registered fan rules
│       │   │   └── FanSettingsStore.kt  # User-overridable fan values (applied in FanScorer)
│       │   ├── counter/
│       │   │   └── TileCounter.kt   # Remaining = 4 − visible (hand+melds+river)
│       ├── DevelopmentAnalyzer.kt   # Shanten + improvement-path analysis
│       └── RulesEngine.kt           # Top-level API
└── test/                            # JUnit tests (89 total)
├── app/                             # Android app module
│   └── src/main/java/com/mahjong/guobiao/
│       ├── MainActivity.kt         # Compose UI: bottom nav (手牌分析 / 番数规则), tile picker, results
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
`DevelopmentAnalyzer.analyze()` for non-tenpai hands:
1. Calculate shanten (向听数) = `13 - 3*meldCount - concealed.size`
2. 1-shanten: enumerate 34 tile types, check if adding makes hand tenpai (or win)
3. For each improvement tile: calculate resulting waits, then for each wait + improvement = winning hand, run FanScorer to detect reachable fan types
4. Group improvement paths by fan type, sort by aggregate probability (sum of all improvement-tile probabilities)
5. UI: fan target cards (name + 番数 + total probability) → tap to see popup with individual improvement tiles and their probabilities
6. Probability = `remainingCount(tile) / totalUnseen(136 - visible)`

### Fan Scoring
Each `FanRule` has:
- `value`: default fan points
- `subsumes`: Set of fan IDs that are NOT counted when this fan is detected (e.g., 大四喜 subsumes 碰碰和)
- `detect(ctx)`: detection logic

`FanScorer.score()`: detect all → remove subsumed → sum (using `FanSettingsStore.getValue()`) → check ≥8 minimum.

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

- **全不靠/七星不靠**: Disabled in `AllNonAdjacentChecker` — the precise definition needs official rulebook verification. Current loose implementation caused false positives in tenpai (e.g., 13 orphans + 2m was incorrectly deemed a win).
- **81 番種 coverage**: ~40 implemented (88番: 大四喜/大三元/绿一色/九莲宝灯/四杠/十三幺; 64番: 四暗刻/连七对; 24番: 清一色/七对/小四喜/小三元; 12番: 三暗刻/三杠/大于五/小于五/三风刻/三色三同顺/三同刻; 8番: 碰碰和/混一色/混幺九/妙手回春/海底捞月/抢杠; 6番: 断幺/暗杠/明杠/箭刻/圈风刻/门风刻/双箭刻; 4番: 不求人/全求人/边张/嵌张/双暗刻; 2番: 无字; 1番: 自摸/花牌). Remaining types (组合龙/全不靠 subtypes/step-ascending sequences etc.) need official rulebook verification.
- **Fan values**: Based on commonly-cited GuoBiao distributions. Some values may differ across rulebook editions.
- **副露 (Melds) input**: UI supports 碰/吃/明杠/暗杠/加杠 via mode chips in the picker area. Meld creation checks 4-copy limit. Current melds shown between hand and picker grid, click to remove.
- **ML tile recognition**: Phase 2 (not started).

## Design Decisions

- **Engine = pure Kotlin JVM module**: Testable without Android emulator/device. Android app depends on it.
- **`value class` for TileType and TileCounts**: Zero-overhead wrappers around Int/IntArray for performance in DFS hot paths.
- **Separate `pair` field in Decomposition**: Cleaner for fan detection (e.g., Small Four Winds checks if pair is Wind).
- **`subsumes` as static Set per FanRule**: Declarative, no runtime transitive-closure computation needed.
- **AllNonAdjacentChecker disabled rather than partially-correct**: False positives in tenpai damage the core use case more than false negatives for rare patterns.
- **TileParser for tests**: Readable string notation ("1112345678999m5m") makes test cases self-documenting. Parser uses 'm'/'p'/'s' input notation independently of `toString()` Chinese output.
- **Bottom navigation**: Two tabs (手牌分析 / 番数规则) via `Scaffold` + `NavigationBar`. Simple state-based switching (no NavHost), since only 2 screens.
- **4-copy limit enforced in ViewModel**: `addTile()` and `addDiscard()` both check hand+melds+discards ≤ 4 per tile type.
- **Click-to-remove**: Both hand tiles and discard tiles are clickable for removal. Clear-all buttons for each.
- **Fan overrides persisted via SharedPreferences**: `FanSettingsStore.toProperties()` serializes to text; ViewModel saves on `onStop`, loads on `onCreate`.
