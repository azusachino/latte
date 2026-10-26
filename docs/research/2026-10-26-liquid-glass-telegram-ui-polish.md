# Research: Apple Liquid Glass & Telegram Media Ergonomics for Latte

**Date**: 2026-10-26  
**Author**: Antigravity  
**Status**: Approved for specification (Milestone 008)  
**Target Repository**: `vendor/latte` (`com.azusachino.latte`)

---

## 1. Executive Summary

Latte currently provides a stable multi-platform Moebooru & Pixiv browsing experience, but its presentation layer is visually and tactilely bound to stock, opaque Material 3 conventions:
- Solid `TopAppBar` and flat `TabRow` surfaces that abruptly terminate image feeds at top boundaries.
- Full-width opaque black banners (`Surface(color = Color.Black.copy(0.65f))`) in `DetailScreen` that box in full-screen artworks.
- Heavy card containers (`surfaceVariant`) with basic 8dp corners, flat elevation, and no tactile press response.
- Lack of optical depth, specular highlights, and edge-to-edge bleed.

This research evaluates integrating **Telegram's media viewer ergonomics** (floating translucent control islands, minimal blocking chrome, fluid gestures) and **Apple's Liquid Glass visual language** (frosted acrylic materials, backdrop blur, specular rim highlights, squircle curvature, and spring physics) natively into Jetpack Compose.

---

## 2. Comparative Analysis: Current Latte vs. Telegram & Apple Liquid Glass

| Dimension | Current Latte UI | Telegram (iOS/Android) | Apple Liquid Glass Standard | Target for Latte (v0.3.0) |
|---|---|---|---|---|
| **Chrome Geometry** | Full-width rectangular slabs (`TopAppBar`, bottom bar) | Floating pill islands and capsule action docks | Translucent floating capsules with continuous curvature | Floating glass pills & dock; zero full-width solid overlays |
| **Surface Material** | Opaque `MaterialTheme.colorScheme.surface` | Ultra-thin translucent frosted glass (`UIBlurEffect`) | Multi-layered translucent acrylic with backdrop blur & sheen | `Modifier.glassSurface` (translucent tint + specular gradient rim; no backdrop blur), `Modifier.glassBackdrop` for floating chrome (Haze blur + tint) |
| **Edge & Depth** | 0 elevation or flat 1dp card shadow; no border highlights | Borderless media with subtle divider lines | Specular rim borders (`0.5dp–1dp` gradient highlight) | Specular gradient stroke reflecting light on glass bevels |
| **Card Geometry** | 8dp standard rounded rectangles | Seamless edge-to-edge or 12–14dp squircle bubbles | Smooth continuous squircles (14–16dp for cards, 24dp for pills) | 14dp continuous squircles for cards; 24dp capsules for pills |
| **Media Bleed** | Hard clipping beneath top app bar | Content scrolls seamlessly behind frosted header | Complete edge-to-edge flow behind dynamic navigation chrome | Scrimmed top bar with transparent background over grid |
| **Feed Tabs** | Default M3 underline `TabRow` | Segmented pill controls with sliding pill indicator | Inset segmented pill control with spring animation | Translucent glass segmented control with spring slider |
| **Tactile Motion** | Instant color change or default ripple | Spring-based button compression and bouncing hearts | Tactile scale-on-press (`scale(0.97f)`) and spring settle | `scale(0.97f)` on tap; spring physics for sheet and tab slider |

---

## 3. Technical Feasibility in Jetpack Compose

### 3.1 Android Version Compatibility
- **`minSdk = 29` (Android 10), `targetSdk = 35` (Android 15)**.
- **Backdrop blur (Haze 2.0.1, owner decision 2026-10-26)**:
  - `Modifier.blur` blurs the element's own content, not what is behind it, so it is not used. Floating chrome over scrolling content uses `Modifier.glassBackdrop`, which wraps Haze `hazeBlur(HazeInput.Sources(state))`; the scrolling feed or image pager is the `hazeSource`.
  - **Android 12+ (API 31+)**: Haze blurs the captured content behind the surface and applies a translucent tint (Haze's default blur is enabled from `SDK_INT >= 31`).
  - **Android 10–11 (API 29–30)**: No blur; Haze draws the fallback scrim.
  - **Translucent fallback**: On the scrim path the chrome uses the elevated glass surface colour (`DarkSurfaceElevated` / `LightSurfaceElevated`), the same look as before blur was added.
  - **Translucent Surface**: `Modifier.glassSurface` (non-floating surfaces such as settings cards and chips) keeps the translucent tint and specular rim on every API level, with no blur.

### 3.2 Performance & Compose Recomposition Guidelines
1. **Zero Runtime Allocation in Draw Loops**:
   - `Brush.verticalGradient(...)` instances must be remembered or stored in static token singletons, never instantiated during layout or drawing passes.
2. **GPU Compositor Transforms**:
   - Scale-on-press and opacity transitions must use `graphicsLayer { scaleX = ...; scaleY = ... }` rather than layout-invalidating modifiers.
3. **Decoupled Overlays**:
   - Floating glass pills in `DetailScreen` must be rendered in separate overlay boxes layered over `ZoomableBox` to prevent canvas redraws when controls toggle visibility.

---

## 4. Design Tokens & Component Architecture

### 4.1 Glass Design Tokens (`ui/theme/Glass.kt`)
```kotlin
object GlassDefaults {
    val RimStrokeWidth = 1.dp
    val CardCornerRadius = 14.dp
    val PillCornerRadius = 24.dp

    // Dark Mode Translucencies
    val DarkSurface = Color(0xCC121418)       // 80% opacity deep neutral
    val DarkSurfaceElevated = Color(0xD91E2228) // 85% opacity elevated
    val DarkRimGradient = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.22f),   // Top specular highlight
            Color.White.copy(alpha = 0.05f),   // Subtle bottom rim
        )
    )

    // Light Mode Translucencies
    val LightSurface = Color(0xCCF6F8FA)      // 80% opacity milky white
    val LightSurfaceElevated = Color(0xE6FFFFFF)// 90% opacity elevated
    val LightRimGradient = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.80f),   // Crisp top reflection
            Color.Black.copy(alpha = 0.06f),   // Soft bottom grounding
        )
    )
}
```

### 4.2 Reusable Glass Surface / Modifier
```kotlin
@Composable
fun Modifier.glassSurface(
    shape: Shape = RoundedCornerShape(GlassDefaults.PillCornerRadius),
    isElevated: Boolean = false,
    rimStroke: Dp = GlassDefaults.RimStrokeWidth,
): Modifier
```
Combines:
1. `clip(shape)`
2. `background(if (darkTheme) DarkSurface else LightSurface)`
3. `border(rimStroke, if (darkTheme) DarkRimGradient else LightRimGradient, shape)`
(`glassSurface` has no backdrop blur; floating chrome uses `glassBackdrop`, see section 3.1.)

---

## 5. Screen-Level Interaction Blueprints

### 5.1 Media Viewer (`DetailScreen`)
- **Top Bar**: Floating pill capsule:
  - Back circular glass pill: 44dp circular glass button with `Icons.AutoMirrored.Filled.ArrowBack`.
  - Title/ID capsule: Glass pill displaying `#<id> · <platform>` or artwork title.
  - Browser & Share pills: Compact 44dp circular glass buttons.
- **Bottom Dock**: Floating glass capsule island:
  - Details trigger: Glass pill with `Icons.Default.Info` and `width × height`.
  - Favorite toggle: Glass circular button with animated spring scale bounce on tap.
  - Save button: Pill button with primary container gradient and download icon.
  - Multi-page indicator: Floating glass capsule with left/right page stepping.
- **Inspect Sheet**: Translucent glass bottom sheet with 24dp top squircle corners, grouped glass cards for metadata, and glowing glass tag capsules.

### 5.2 Discovery Feed (`ExploreScreen`)
- **Transparent Scaffold & Under-Bleed**: Scaffold container color set to transparent; staggered grid content extends under the status bar and top bar.
- **Glass Segmented Control**: Inset pill container with smooth animated spring slider behind the selected tab (`Popular / Newest / Favorites / Pools`).
- **Media Cards**:
  - 14dp squircle corners.
  - Hairline specular outline (`Color.White.copy(alpha = 0.08f)` in dark mode).
  - Telegram-style circular glass selection checkmark badge.
  - Scale-on-press interaction (`scale(0.97f)` on pointer press).

---

## 6. Verification & Acceptance Journey

1. **Unit & Codec Tests**: All existing tests in `vendor/latte` continue to pass (`make check` / `./gradlew testDebugUnitTest`).
2. **Visual Contract Verification**:
   - Zero visual regressions on existing data bindings, platform switching, or download queues.
   - Smooth 60fps+ scrolling through infinite staggered grid.
   - Clean gesture response on double-tap zoom, swipe navigation, and bottom sheet expansion.
3. **API Compatibility**: Zero runtime crashes on API 29 through API 35.
