---
description: "Tasks for Apple Liquid Glass & Telegram Media Ergonomics"
---

# Tasks: Liquid Glass UI/UX Polish

**Input**: [spec.md](spec.md) and [plan.md](plan.md)  
**Research Reference**: [docs/research/2026-10-26-liquid-glass-telegram-ui-polish.md](../../docs/research/2026-10-26-liquid-glass-telegram-ui-polish.md)

---

## Phase 1: Foundation — Glass Tokens & Modifiers

- [x] T001 Create `ui/theme/Glass.kt` with `GlassDefaults` design tokens (translucent dark/light surfaces, specular gradients, corner radii).
- [x] T002 Implement `Modifier.glassSurface(...)` combining clip, translucent tint, and specular rim border stroke. Backdrop blur is not part of `glassSurface`: Compose `Modifier.blur` blurs the element's own content, not what is behind it. Floating chrome gets blur from `Modifier.glassBackdrop` (Haze, 2026-10-26).
- [x] T003 Implement `Modifier.pressScale(targetScale = 0.97f)` for tactile micro-interaction feedback.

## Phase 2: Immersive Media Viewer (`DetailScreen.kt`)

- [x] T004 Refactor top controls overlay into floating frosted glass pill islands (circular back button, center title/id capsule, browser/share buttons).
- [x] T005 Refactor bottom controls overlay into a floating frosted glass dock (details/resolution pill, animated favorite heart, save button).
- [x] T006 Implement floating glass page indicator capsule for Pixiv multi-page works.
- [x] T007 Refactor Inspect bottom sheet with frosted glass styling, grouped glass cards, and glowing tag capsules.

## Phase 3: Discovery Feed & Segmented Pill Controls (`ExploreScreen.kt`)

- [x] T008 Implement `GlassSegmentedTabs` with spring physics pill indicator replacing standard M3 underline `TabRow`.
- [x] T009 Refactor `ExploreScreen` top header with translucent frosted glass styling allowing grid to scroll underneath.
- [x] T010 Refactor feed period filter chips (Day/Week/Month/Year) and date controls into frosted glass capsules.
- [x] T011 Modernize `PostGridItem` media cards with 14dp squircle corners, 1px specular hairline rim, `Modifier.pressScale()`, and circular glass selection badges.

## Phase 4: Settings & Account Center Polish

- [x] T012 Refactor `SettingsScreen` rows into Apple-style inset grouped glass cards.
- [x] T013 Polish `AccountManagerScreen` with frosted platform cards and glowing connection status badges.

## Phase 5: Verification & Acceptance

- [x] T014 Run `./gradlew testDebugUnitTest` and ensure all tests pass.
- [x] T015 Run `mise exec -- rumdl check .` and ensure markdown formatting is clean.
- [x] T016 Run `./gradlew assembleDebug` and verify clean APK compilation.
