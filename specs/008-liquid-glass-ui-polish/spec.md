# Feature Specification: Apple Liquid Glass & Telegram Media Ergonomics

**Milestone**: 008  
**Feature Name**: Liquid Glass UI/UX Polish  
**Status**: In Progress  
**Author**: Antigravity  
**Research Reference**: [docs/research/2026-10-26-liquid-glass-telegram-ui-polish.md](../../docs/research/2026-10-26-liquid-glass-telegram-ui-polish.md)

---

## 1. Problem Statement

Latte's functional architecture is mature across yande.re, konachan.net, and Pixiv. However, its visual hierarchy and tactile feedback rely on rigid, opaque Material 3 containers:

1. Discovery feeds are abruptly clipped at top boundaries by solid rectangular `TopAppBar` and `TabRow` bars.
2. The full-screen media viewer in `DetailScreen` is boxed in by full-width black overlay strips (`Surface(color = Color.Black.copy(0.65f))`), reducing perceived immersion and canvas size.
3. Media cards in the grid lack depth, specular beveling, and tactile feedback on press.
4. Tab controls use stock M3 underline indicators rather than modern segmented glass controls.

---

## 2. Goals & Non-Goals

### Goals

- **Liquid Glass Design System**: Establish reusable glass primitives (`Modifier.glassSurface`, `GlassDefaults`) supporting translucent tinting and specular gradient rim highlights. Floating chrome over scrolling content adds Haze backdrop blur through `Modifier.glassBackdrop` (API 31+, tint fallback below); `Modifier.blur` itself is not used because it blurs the element's own content.
- **Telegram-Style Media Viewer**: De-box `DetailScreen` chrome into floating glass pill islands (top navigation pill and bottom action dock).
- **Apple-Style Discovery Feeds**: Refactor `ExploreScreen` with frosted glass headers, a smooth segmented pill tab bar with spring physics, and modernized 14dp squircle media cards with hairline outlines.
- **Micro-Interactions**: Implement tactile scale-on-press (`scale(0.97f)`) on cards and buttons, spring animations on tab indicators, and spring bounce on favorite actions.
- **Graceful Compatibility**: Clean execution on API 29 through 35 with zero runtime crashes or performance regressions.

### Non-Goals

- Altering core data models (`Post`, `PlatformId`, `SitePlugin`), network models, or storage layers.
- Changing authentication mechanisms or adding new platform endpoints.
- Introducing heavy 3rd-party UI libraries (everything built using native Jetpack Compose). Exception, owner decision 2026-10-26: Haze (`dev.chrisbanes.haze:haze`) is adopted for backdrop blur on floating chrome only.

---

## 3. User Stories & Acceptance Criteria

### User Story 1: Immersive Floating Glass Media Viewer

*As a user browsing artworks in full-screen detail,*  
*I want media controls to hover as elegant, floating translucent glass pills,*  
*So that the artwork feels expansive and edge-to-edge without being blocked by dark bounding slabs.*

- **Acceptance Criteria**:
  1. Top navigation in `DetailScreen` is rendered as floating frosted glass pills (circular back button, artwork ID/title pill, and compact action pills) with top status bar padding.
  2. Bottom controls in `DetailScreen` are rendered as a floating frosted glass dock (details/resolution pill, circular animated favorite button, and save pill button) with bottom navigation bar padding.
  3. For Pixiv multi-page sets, a floating glass page counter (`Page X of Y`) provides comfortable touch stepping.
  4. Tapping the canvas smoothly toggles visibility of all floating glass islands with fade transitions.
  5. The Inspect bottom sheet uses frosted glass styling with grouped glass cards and translucent tag capsules.

### User Story 2: Frosted Glass Feed Navigation & Segmented Tabs

*As a user navigating discovery feeds,*  
*I want the header and feed tabs to stay frosted and legible as artwork scrolls underneath,*  
*So that discovery feels fluid and modern like iOS and Telegram media galleries.*

- **Acceptance Criteria**:
  1. The top header and tab bar in `ExploreScreen` render with frosted glass translucency (Haze backdrop blur over the scrolling feed on API 31+; translucent tint fallback on API 29-30, plus the specular rim).
  2. The staggered grid scrolls smoothly underneath the translucent header without harsh cutoff lines.
  3. The feed tabs (`Popular`, `Newest`, `Favorites`, `Pools`) render as an Apple-style segmented control with an animated pill indicator sliding behind the active tab using spring physics.
  4. Feed period chips (`Day`, `Week`, `Month`, `Year`) render as frosted capsule chips.

### User Story 3: Tactile & Refined Media Grid Cards

*As a user scrolling through image cards,*  
*I want the cards to feel tactile, well-proportioned, and visually distinct,*  
*So that images pop cleanly even against pure dark or light backgrounds.*

- **Acceptance Criteria**:
  1. Grid cards use 14dp continuous squircle corners.
  2. Cards feature a 1px specular hairline rim (`Color.White.copy(0.08f)` in dark mode, `Color.Black.copy(0.04f)` in light mode) preventing dark artwork from blending invisibly into dark surfaces.
  3. Pressing a card applies a tactile `scale(0.97f)` feedback before opening detail.
  4. In selection mode, selection indicators render as Telegram-style circular glass badges with animated spring checkmarks.

---

## 4. Visual & Interaction Contracts

### 4.1 Glass Tokens Contract

- `GlassDefaults.RimStrokeWidth`: `1.dp`
- `GlassDefaults.CardCornerRadius`: `14.dp`
- `GlassDefaults.PillCornerRadius`: `24.dp`
- `GlassDefaults.DarkSurface`: `Color(0xCC14171A)`
- `GlassDefaults.LightSurface`: `Color(0xD9FFFFFF)`
- `GlassDefaults.DarkRimGradient`: `Brush.verticalGradient(listOf(Color.White.copy(0.22f), Color.White.copy(0.05f)))`
- `GlassDefaults.LightRimGradient`: `Brush.verticalGradient(listOf(Color.White.copy(0.80f), Color.Black.copy(0.06f)))`

### 4.2 Motion Specs

- **Scale on Press**: `0.97f` scale factor via `graphicsLayer`.
- **Spring Spec**: `spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)`.
- **Control Overlay Fade**: `tween(durationMillis = 200, easing = FastOutSlowInEasing)`.
