# Settings Screen & Bottom Nav Icon Fix — Design Spec

Part of the "CableManager Pro completeness" initiative (sub-projects 1 and
3 are merged to `main`; this is a follow-on requested directly by the
business owner, not part of the original 4-part roadmap).

## Problem

Business owner reported: "the buttons in down home, settings and question
mark ... first they are not aligned and few buttons doesn't have any
functionality."

Investigation confirmed two distinct issues in `DashboardActivity`'s
`BottomNavigationView`:

1. **Visual mismatch.** `app/src/main/res/menu/bottom_nav_menu.xml` uses
   four ancient stock Android system icons (`ic_menu_view`,
   `ic_menu_myplaces`, `ic_menu_help`, `ic_menu_preferences`) instead of
   the app's own Material-style vector icons. They render at inconsistent
   visual weight next to each other and are semantically wrong: Home shows
   an eye/magnifying-glass glyph, Customers shows a map-pin glyph, and
   Complaints shows a plain question mark instead of the wrench (🔧)
   already used for Complaints everywhere else in the app (Dashboard's
   Quick Actions tile, the "Open Complaints" stat card).

2. **Dead button.** `nav_settings` (`DashboardActivity.kt`,
   `setupBottomNav()`) only shows a `Toast.makeText(this, "Settings screen
   is coming soon", ...)`. There is no Settings screen. Confirmed while
   investigating: **`DashboardActivity.showLogoutDialog()` exists in the
   codebase but is never called from anywhere** — there is currently no
   way to log out of the app through the UI at all.

## Decision: build a real Settings screen

Confirmed with the business owner: Settings should contain Account
(logged-in user + Logout), Appearance (theme toggle), Language (EN/TE
toggle), and About (app version). The theme and language toggles move
here from Dashboard's top bar — Dashboard's top bar keeps just the title
and profile avatar.

## Design

### Bottom nav icons

Replace in `bottom_nav_menu.xml`:

| Item | Old (wrong) | New |
|---|---|---|
| Home | `@android:drawable/ic_menu_view` (eye glyph) | new `ic_home` vector |
| Customers | `@android:drawable/ic_menu_myplaces` (map pin) | existing `@drawable/ic_group` (already used elsewhere in the app for people/customers) |
| Pay | `@drawable/ic_payment` | unchanged — already correct |
| Complaints | `@android:drawable/ic_menu_help` (question mark) | new `ic_build` (wrench) vector, matching the 🔧 used for Complaints elsewhere |
| Settings | `@android:drawable/ic_menu_preferences` | new `ic_settings` (gear) vector |

New vectors follow the existing custom-icon convention seen in
`ic_group.xml`/`ic_payment.xml`: 24dp viewport, single filled path, white
fill (tinted at point of use via the `BottomNavigationView`'s existing
`app:itemIconTint`).

### New SettingsActivity

A new screen, `SettingsActivity`, using the same `MaterialToolbar` +
back-navigation pattern already established in `CustomerListActivity` /
`NewCustomerActivity` (title bar with a back arrow, not Dashboard's
custom top-bar layout — Settings is a destination screen, not a hub).

Sections, top to bottom:

- **Account** — "Logged in as {username} ({role})" (read from the
  existing `vinayaka_prefs` SharedPreferences keys `username` and
  `user_role`, same source `DashboardActivity` already reads), then a
  **Logout** button (styled as a destructive action, red). Tapping it
  shows a real confirmation dialog ("Log out? / Cancel / Log out") —
  this is the missing piece `showLogoutDialog()`'s name always implied
  but never delivered. On confirm, reuses the exact logic already sitting
  in `DashboardActivity.showLogoutDialog()` (clear `vinayaka_prefs`,
  navigate to `LoginActivity`, finish) — moved to live in
  `SettingsActivity` since that's now where the trigger lives, with the
  dead copy in `DashboardActivity` removed.
- **Appearance** — a theme toggle control, functionally identical to
  Dashboard's existing 🌙/☀️ `btnThemeToggle` (same `ThemeManager.
  toggleTheme()` + `recreate()` calls), just relocated.
- **Language** — an EN/తె toggle control, functionally identical to
  Dashboard's existing `btnLangEn`/`btnLangTe` (same `LocaleHelper.
  setLocale()` + `recreate()` calls), just relocated.
- **About** — static text: app version (`BuildConfig.VERSION_NAME`) and
  the business name (`R.string.business_name_caps`, already defined and
  already localized per the previous sub-project).

### Dashboard changes

- `setupBottomNav()`'s `nav_settings` branch changes from the Toast stub
  to `startActivity(Intent(this, SettingsActivity::class.java))`.
- The theme-toggle (`btnThemeToggle`) and language-toggle
  (`btnLangEn`/`btnLangTe`) views and their click listeners are removed
  from `DashboardActivity` and `activity_dashboard.xml`'s top bar — they
  now live exclusively in Settings, avoiding two places to change the
  same preference.
- `showLogoutDialog()` is removed from `DashboardActivity` (dead, and its
  logic moves to `SettingsActivity` per above).

### Manifest

New entry following the existing pattern:

```xml
<!-- SETTINGS -->
<activity
    android:name=".SettingsActivity"
    android:exported="false" />
```

## What's explicitly out of scope

- Any other bottom-nav items beyond icon swaps — Home/Customers/Pay/
  Complaints navigation logic is unchanged and already works correctly.
- Role-based Settings content (e.g. hiding Logout for certain roles) —
  not requested; Settings is the same for every role.
- Further localization of Settings' own text — new strings get both an
  English and a best-effort Telugu translation added at the same time,
  consistent with the app's existing (now-149-of-184-key) Telugu coverage
  from the previous sub-project, not left as a fresh gap.

## Testing

No new pure logic to unit test (this is UI wiring + one new screen,
consistent with how the rest of the app's UI-layer code is untested at
the unit level). Manual verification on the `Pixel_9` emulator:

- Bottom nav: confirm all 5 icons now render as coherent Material-style
  glyphs, semantically matching their labels.
- Tap Settings: confirm the new screen opens (not a Toast).
- Toggle theme and language from Settings: confirm they take effect
  identically to how the old Dashboard top-bar controls did.
- Confirm Dashboard's top bar no longer shows the theme/language toggles.
- Tap Logout, confirm dialog, confirm Cancel does nothing, confirm
  Logout clears the session and returns to Login (and that re-launching
  the app afterward requires signing in again — proves the
  SharedPreferences were actually cleared, not just the screen changed).
