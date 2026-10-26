# Implementation Plan: Apple Liquid Glass & Telegram Media Ergonomics

**Spec**: [spec.md](spec.md)  
**Research**: [docs/research/2026-10-26-liquid-glass-telegram-ui-polish.md](../../docs/research/2026-10-26-liquid-glass-telegram-ui-polish.md)

---

## Technical Strategy

The implementation lands in 4 coherent, incrementally testable slices. Each slice preserves existing platform contracts, passes all existing tests, and adheres to `make check`.

---

## Slice 1: Glass Design Tokens & Reusable Modifiers

1. Create `vendor/latte/app/src/main/kotlin/com/azusachino/latte/ui/theme/Glass.kt`:
   - `GlassDefaults` object containing semantic colors, gradients, stroke widths, and corner radii.
   - `Modifier.glassSurface(...)`: Combines clip, translucent background, and specular vertical gradient border. No backdrop blur; for floating chrome over scrolling content use `Modifier.glassBackdrop` (Haze, see Slice 3).
   - `Modifier.pressScale(...)`: Clean pointer-input interaction for `scale(0.97f)` on press with spring return.
2. Verify with unit/compose preview tests.

---

## Slice 2: Immersive Floating Glass Media Viewer (`DetailScreen.kt`)

1. Refactor Top Controls:
   - De-box the full-width `Surface`.
   - Implement floating top island:
     - Circular glass back button (`44.dp`).
     - Center glass title/ID capsule.
     - Circular glass browser and share action buttons.
2. Refactor Bottom Controls:
   - De-box the full-width `Surface`.
   - Implement floating bottom glass dock:
     - Details/Resolution capsule with info icon.
     - Favorite toggle button with animated spring heart icon.
     - Download/Save capsule button with prominent primary container styling.
   - For Pixiv multi-page sets: Floating glass page indicator capsule (`Page X of Y`).
3. Refactor Inspect Bottom Sheet:
   - Frosted glass bottom sheet styling.
   - Grouped glass cards for metadata table.
   - Translucent glass tag capsules with glowing color-coded borders.

---

## Slice 3: Discovery Feed & Segmented Pill Controls (`ExploreScreen.kt`)

1. Top Header Refactoring:
   - Make `Scaffold` container background transparent/bleed-friendly.
   - Top bar container (search field and tabs inside it included) styled with `Modifier.glassBackdrop`: the feed body is the Haze source, so the grid blurs behind the bar on API 31+ and shows a translucent tint on API 29-30. Inner pills and the search field keep `Modifier.glassSurface` and are not blurred again.
2. Apple-Style Segmented Tab Bar:
   - Implement `GlassSegmentedTabs` replacing `TabRow`.
   - Smooth animated pill indicator sliding behind active tab (`Popular`, `Newest`, `Favorites`, `Pools`).
3. Refine Period & Date Controls:
   - Frosted glass capsule chips for Day, Week, Month, Year.
   - Sleek glass date navigation pill.
4. Modernize Media Grid Cards (`PostGridItem`):
   - Update shape to 14dp continuous squircle.
   - Add hairline specular border (`Color.White.copy(0.08f)` in dark mode).
   - Add `Modifier.pressScale()`.
   - Telegram-style circular glass selection badge with spring checkmark.

---

## Slice 4: Settings & Account Center Polish (`SettingsScreen.kt`, `AccountManagerScreen.kt`)

1. Grouped Inset Glass Cards:
   - Update settings sections into iOS-style rounded glass grouped cards.
2. Account Center:
   - Frosted platform cards with emerald glowing connection badge.

---

## Verification & Acceptance

- Unit tests: `cd vendor/latte && ./gradlew testDebugUnitTest` must pass cleanly.
- Markdown check: `cd vendor/latte && mise exec -- rumdl check .` must pass.
- Build & assemble: `cd vendor/latte && ./gradlew assembleDebug` must compile cleanly without errors.
