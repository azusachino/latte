# Plugin abstraction probe: adding Konachan

**Date**: 2026-09-26
**Branch**: `experiment/konachan-plugin` (folded into `feat/favorite-tags-and-following`)
**Question**: does the 0.0.4 plugin architecture actually absorb a third
platform, or does it only look that way?

## What was done

Added `konachan.net` as a third platform with the minimum code a new site
should need, then drove it on a real device (OnePlus 8) with
uiautomator-guided taps, screenshotting state through the UI dump instead of
manual poking.

Code added or changed:

- `PlatformId.KONACHAN` (enum entry; `konachan.net`, Moebooru capabilities).
- `KonachanPlugin` — subclasses `YandePlugin`, passing site identity through
  the constructor. No transport code.
- `YandePlugin`/`YandeApi` parameterized by `platform`/`name`/`baseUrl`
  (constructor injection, not open vals).
- `ExploreViewModel.moebooruApi()` — one map lookup choosing the site's API.
- One `DropdownMenuItem` in the platform switcher.

Net: roughly 80 lines for a working, browsable, searchable third site —
popular feeds, detail pager, tag search, pools, favorite tags all work
against konachan.net on device.

## Findings

1. **Open identity vals break constructor-time storage** (blocker, fixed).
   The first attempt overrode `platform` as an open val in a subclass; the
   superclass init calls `storage.get(id, …)` while the override is still
   null — instant `NullPointerException` at launch. Lesson: identity that is
   used during construction must be a constructor parameter, never an open
   val. The fix also reads better: `YandePlugin(…, platform = KONACHAN,
   name = "Konachan")`.

2. **The UI layer is still binary** (should-fix, follow-up). 32 `isPixiv`
   branches in the ViewModel and 27 in ExploreScreen. Konachan works because
   it rides the yande path (`isPixiv == false`), but the toolbar
   content-description literally announced "Current platform: Yande" while
   Konachan was selected — the first "verification failure" of the probe was
   the verifier's own binary assumption. The direction is a platform
   capability/profile object replacing `isPixiv` checks.

3. **Moebooru-to-Moebooru switches didn't reload feeds** (bug, fixed).
   `selectPlatform` only triggered a load for Pixiv; switching yande →
   konachan kept rendering the previous site's cached posts. Now any
   non-Pixiv switch reloads popular and newest.

4. **`konachan.com` is Cloudflare-walled** (roadmap, not a defect). Plain
   OkHttp gets a JS challenge on `konachan.com`; `konachan.net` (SFW mirror,
   same engine) answers normally and is what ships. Boorusama solves this
   with webview challenge handling — a deliberate future option.

5. **Naming debt**: `YandeApi` is really a Moebooru client. The probe
   parameterized its base URL instead of renaming; the rename to
   `MoebooruApi` should land before a fourth site makes it awkward.

## Verdict

The plugin seam held: a new site cost one enum entry, one thin plugin class,
one API instance, and one menu item. The friction was not in the plugin
layer but in the binary platform assumptions above it — exactly where the
`PluginFeed*` neutralization of 0.0.4 should continue next.
