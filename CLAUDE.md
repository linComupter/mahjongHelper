# 国标麻将助手 (Guobiao Mahjong Assistant)

Android app that analyzes a mahjong hand against Chinese Official (国标) rules:
1. List achievable 国标 fan types (22 番種)
2. Calculate waiting tiles (听牌)
3. Show remaining count of each waiting tile based on visible discards/melds

## Environment

- **Platform**: Android 8.0+ (minSdk 26, targetSdk 36)
- **UI**: Jetpack Compose + Material3 + MVVM
- **Language**: Kotlin 1.9.22
- **Build**: Gradle 8.11.1, AGP 8.7.2, JDK 17
- **Android SDK**: `E:\AndroidStudioSDK` (configured in `local.properties`)
- **Engine tests**: JUnit 5 (116 tests, pure JVM — no Android device needed)
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
│       │   │   ├── FanRules.kt      # 22 fan implementations (24番–3番)
│       │   │   ├── FanScorer.kt     # Scoring: detect all → subsumes deduction → 1-fan minimum
│       │   │   ├── FanRegistry.kt   # All registered fan rules
│       │   │   └── FanSettingsStore.kt  # User-overridable fan values (applied in FanScorer)
│       │   ├── counter/
│       │   │   └── TileCounter.kt   # Remaining = 4 − visible (hand+melds+river)
│       │   ├── DevelopmentAnalyzer.kt   # Shanten + improvement-path analysis
│       │   ├── FanReverseAnalyzer.kt     # Fan-type reverse analysis engine
│       │   ├── AnalysisSettings.kt  # Analysis mode (纯大/赖子) + swap depth + persistence
│       │   └── RulesEngine.kt       # Top-level API
│       └── test/                    # JUnit tests (116 total)
├── app/                             # Android app module
│   └── src/main/java/com/mahjong/guobiao/
│       ├── MainActivity.kt         # Compose UI: bottom nav (手牌分析 / 番数规则 / 分析规则), tile picker, results, edge-to-edge
│       └── ui/MahjongViewModel.kt  # MVVM: hand/meld/discard state, 4-copy limit, addMeld/addKanToPon, discard suggestions, persistence
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

14 张 (winSize) 未和牌时进入**弃牌建议**：`DevelopmentAnalyzer.analyzeDiscard()` 枚举弃掉每张暗手牌，对弃后 13 张计算听牌与可达番种（`TenpaiCalculator` + `FanScorer`），返回 `DiscardSuggestion(discardTile/resultingWaits/reachesMinimum/possibleFans/waitCount)`，按"可起和优先、听牌数多优先、牌型升序"排序。UI 展示列表，点选查看详情弹窗。

替换分析由 `FanReverseAnalyzer` 按番种倒推：对 22 种番种逐一计算不合规牌数 → 跳过超过深度的番种 → 对剩余番种按目标牌池枚举替换组合 → 快速向听预检淘汰无效组合 → 全量 TenpaiCalculator 验证 → 结果按番种聚合。

深度1：浅层先行，找到路径即停止更深尝试
深度2/3：仅对浅层无效的组合递进尝试；预检淘汰 ~75% 无效组合

输出按番种聚合：`SwapTarget` 含番种名、总概率、具体弃牌摸牌路径。每条路径含弃牌/摸牌/剩余张数/概率/后续听牌。

### Analysis Settings
`AnalysisSettings` (singleton, engine layer):
- `analysisMode`: 分析模式（`AnalysisMode` enum：`PURE` 纯大模式 / `WILD` 赖子模式，UI 均可选；赖子模式引擎规则已实现：引擎接口带 `wildcard: TileType?` 参数，ViewModel 传 `AnalysisSettings.activeWildcard`）
- `wildcardTileCode`: 赖子牌编码（0..33，默认 31=红中），仅赖子模式生效
- `swapDepth`: 1~3，控制替换式分析的弃摸张数
- `toProperties()` / `loadFromProperties(text)`: 序列化持久化（每行一个 key=value：`mode`、`wildcard`、`swapDepth`；缺失键保持当前值）
- 与 FanSettings 共用 SharedPreferences key `fan_settings`
- UI: "分析规则" tab 顶部为分析模式区域（FilterChip 选择模式；选赖子模式时下方出现赖子牌单选网格 `WildcardTileGrid`，选中红框高亮，默认红中）+ Slider 调节深度 + 性能提示；模式/赖子牌改动即时写入单例并持久化（切换页面/重启不回退），深度经滑块松手即时生效，"保存设置"按钮可统一写入并持久化全部三项

### Fan Scoring
22 种番种，番数范围 3~24：
- 3番: 混一色, 碰碰胡, 门清
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

门清 (`Menzen`, 3番) 为附加番：`detect` 仅判定"无副露或全为暗杠"，但 `FanScorer` 会在"仅门清一种被检测到"时将其剔除（需同时满足至少一种其他番型才计入）。倒推分析 `FanReverseAnalyzer.reverse()` 对 `menzen` 返回 null，不作为发展目标。

### Fan Settings
`FanSettingsStore` (singleton, engine layer):
- `getValue(rule)` → overridden value or default
- `setOverride(id, value)` → custom fan value; value ≤ 0 clears override
- `isHidden(id)` / `setHidden(id, hide)` / `toggleHidden(id)` → hide rules from analysis
- `toProperties()` / `loadFromProperties(text)` → serialization for SharedPreferences persistence
- Persistence: ViewModel saves via SharedPreferences (`fan_settings.fan_properties`) in `onStop`, loads in `onCreate`
- Fan settings page: two modes — tap to edit value, or "编辑隐藏" mode to toggle hidden state (hidden → filtered from `FanRegistry.detectAll`)
- Hidden rules: grayed out in list, excluded from analysis scoring. Overrides: orange highlight. Both persisted.

### Version Update (app layer)
`update/VersionChecker.kt` (app module, `java.net.HttpURLConnection` + `org.json` — no extra deps):
- `fetchLatestRelease()`: `GET https://api.github.com/repos/linComupter/mahjongHelper/releases/latest`（需 `User-Agent` 头，否则 403）→ 解析 `tag_name`/`html_url`；非 200 / 无网 / 解析失败返回 null 不抛异常。
- `compare(a, b)` / `isNewer(a, b)`: semver 比较（去除 `v` 前缀，非数字段按 0）。
- 流程：`MahjongViewModel.checkForUpdate(context)` 在 `MahjongApp` 的 `LaunchedEffect` 启动时调用，本地 `versionName`（PackageManager）与 `tag_name` 比较；新 → `MahjongUiState.updateAvailable` → `MainActivity` AlertDialog → 去更新 `ACTION_VIEW` 浏览器打开 Release 页。每日最多一次（pref `update_last_check`）。纯提示式，不做下载/安装。

## Known Limitations (MVP)

- **全不靠/七星不靠**: Disabled in `AllNonAdjacentChecker` — the precise definition needs official rulebook verification.
- **番種数量**: 22 种精选番种，删除了断幺/自摸/花牌/箭刻/圈风刻/门风刻等低频番种。
- **Fan values**: 基于自定义规则版番数，可能与传统国标有差异。
- **副露 (Melds) input**: UI supports 碰/吃/明杠/暗杠/加杠 via mode chips in the picker area. Validation in `MahjongViewModel.addMeld`/`addKanToPon`: 剩余张数（碰≥3、明杠/暗杠=4、吃每张≥1）与手牌+副露≤14（杠按3张计）不满足时弹出 `errorMessage` 提示；加杠仅可将已存在的碰副露改为加杠，加杠模式下其他牌不可点击。Current melds shown between hand and picker grid, click to remove.
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
- **Bottom navigation**: Three tabs (手牌分析 Home / 番数规则 Star / 分析规则 Settings) via `Scaffold` + `NavigationBar`. Simple state-based switching (no NavHost), since only 3 screens. Icons use the core material-icons set (no `-extended` dependency).
- **4-copy limit enforced in ViewModel**: `addTile()` and `addDiscard()` both check hand+melds+discards ≤ 4 per tile type.
- **Click-to-remove**: Both hand tiles and discard tiles are clickable for removal. Clear-all buttons for each.
- **Fan overrides persisted via SharedPreferences**: `FanSettingsStore.toProperties()` serializes to text; ViewModel saves on `onStop`, loads on `onCreate`.
- **番种倒推替代通用枚举**: `FanReverseAnalyzer` 从目标番种反向推算所需替换路径，搜索量降低 100~10000 倍。
