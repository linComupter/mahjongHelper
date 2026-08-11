# AGENTS.md

Android 国标麻将助手 (Guobiao Mahjong). Two Gradle modules: `:engine` (pure Kotlin JVM, all logic + 101 JUnit tests) and `:app` (Jetpack Compose UI).

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
- UI (`app`) is state-based bottom-nav (手牌分析 / 番数规则 / 分析规则) in `MainActivity.kt` + MVVM state in `ui/MahjongViewModel.kt`. 4-copy limit + meld validations (剩余张数/手牌+副露≤14/加杠需已有碰) enforced in the ViewModel, errors surfaced via `errorMessage` AlertDialog.
