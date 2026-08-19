# AGENTS.md

Android 国标麻将助手 (Guobiao Mahjong). Two Gradle modules: `:engine` (pure Kotlin JVM, all logic + 116 JUnit tests) and `:app` (Jetpack Compose UI).

**Read `CLAUDE.md` first** — it is the authoritative architecture doc (tile encoding, win-detection algorithm, fan scoring, tenpai, swap analysis). Keep it in sync when you change architecture (commit history follows this convention).

## Commands (PowerShell on Windows)

```powershell
./gradlew :engine:test                                   # all engine tests, no Android device/SDK needed
./gradlew :engine:test --tests "com.mahjong.guobiao.engine.fan.FanDetectorTest"   # single test class
./gradlew :app:assembleDebug                             # build APK (needs Android SDK)
./gradlew :app:installDebug                              # install on connected device
```

No lint/typecheck/CI config exists — `:engine:test` is the verification step. Tests are **JUnit 5** (`org.junit.jupiter`, `useJUnitPlatform()`), not JUnit 4.

## Git workflow

- Push/submit changes to the **`test` branch** (create it locally + on remote if it doesn't exist), not `master`. Remote: `origin` → `https://github.com/linComupter/mahjongHelper.git`.

## Version updates (GitHub Releases)

- The app checks for updates on launch via `update/VersionChecker.kt`: fetches `GET api.github.com/repos/linComupter/mahjongHelper/releases/latest`, compares `tag_name` with local `versionName` (`semver`, strips leading `v`). Newer → `MahjongUiState.updateAvailable` → AlertDialog → 去更新 opens the release page in a browser. Needs `INTERNET` permission (manifest already added). Rate limited to once per 24h via pref `update_last_check`.
- **Release process**: bump `versionCode`/`versionName` in `app/build.gradle.kts`, build `:app:assembleRelease`, upload the APK to a GitHub Release tagged `vX.Y.Z` (e.g. `v0.2.0`). The checker is purely advisory — actual install/downloading is done via the browser release page.

## Setup gotchas

- Android SDK path lives in `local.properties` (`sdk.dir=E:\AndroidStudioSDK`), which is **gitignored**. On a new machine you must recreate it; otherwise `:app` builds fail (engine still works).
- `:engine` has zero Android dependencies — it is the safe/fast place to develop and verify mahjong logic.

## Conventions (differ from defaults)

- Comments, commit messages, and test names are written in **Chinese**. Test method names use backticks, e.g. `` fun `大四喜`() ``.
- Tests build hands with `TileParser`: suit notation `"123m456p789s"`, honors `"东南西北中发白"`, flowers `"春夏秋冬梅兰竹菊"`, concatenatable. Use this in new tests.
- Adding a fan type = define `FanRule` in `engine/.../fan/FanRules.kt` (with `value`, `subsumes`, `detect`) **and** register it in `FanRegistry.rules` ordered by fan value. Fan values are user-overridable via `FanSettingsStore` (singleton, persisted as properties by the app).
- `AllNonAdjacentChecker` (全不靠/七星不靠) is **deliberately disabled** — do not enable without rulebook verification; false positives in tenpai are worse than false negatives.
- `value class` wrapping (TileType=Int, TileCounts=IntArray) is used for hot-path performance — don't "simplify" it to regular classes without measuring.

## Architecture at a glance

- `RulesEngine` is the top-level API (fans / tenpai / tenpai+counts / fullAnalysis); `WinChecker` + `StandardDecomposer` (DFS, lowest-tile-first) + `SevenPairsChecker` + `ThirteenOrphansChecker` handle win detection.
- Tenpai = 34-way enumeration × `WinChecker`; remaining counts = 4 − visible (hand+melds+river).
- Development analysis (`DevelopmentAnalyzer` / `FanReverseAnalyzer`) is fan-type reverse inference with swap depth 1–3 from `AnalysisSettings` — it's the most expensive path, perf-sensitive. 14 张未和牌时 `DevelopmentAnalyzer.analyzeDiscard` 提供弃牌建议（弃后听牌/可达番种/能否起和）。
- UI (`app`) is state-based bottom-nav (手牌分析 Home / 番数规则 Star / 分析规则 Settings, core material-icons only - no `-extended` dependency) in `MainActivity.kt` + MVVM state in `ui/MahjongViewModel.kt`. 4-copy limit + meld validations (剩余张数/手牌+副露≤14/加杠需已有碰) enforced in the ViewModel, errors surfaced via `errorMessage` AlertDialog. The 分析规则 tab has a mode area at top (FilterChips: 纯大模式/赖子模式; selecting 赖子模式 reveals a single-select wild-tile grid `WildcardTileGrid`, default 红中, red-border highlight; mode/wildcard/depth all applied via the 保存设置 button).
- `AnalysisSettings.analysisMode` (`AnalysisMode` enum) selects the rule set: `PURE` (纯大模式) and `WILD` (赖子模式) are both selectable in UI, and `AnalysisSettings.activeWildcard` returns the wild tile (null in PURE). **WILD engine rules ARE implemented**: engine calls take an explicit `wildcard: TileType? = null` parameter (`RulesEngine`/`WinChecker`/`TenpaiCalculator`/`DevelopmentAnalyzer`/`FanReverseAnalyzer`) — the ViewModel passes `AnalysisSettings.activeWildcard`. 赖子牌替代规则：标准型 DFS 补顺/刻（`StandardDecomposer.decomposeWildcard`）、七对（`SevenPairsChecker`）、十三幺（`ThirteenOrphansChecker`）各有赖子版本；`FanContext.of` 将赖子落位为分解中的具体牌后再判番（`FanScorer.meetsMinimum` 在赖子模式恒为 true，即 0 番可起和）。`wildcardTileCode` (0..33, default 31 = 红中) is the wild tile, only meaningful in WILD mode. Persisted per-line as properties (`mode`/`wildcard`/`swapDepth`), backward compatible with the old single-line format; absent keys keep current values.
