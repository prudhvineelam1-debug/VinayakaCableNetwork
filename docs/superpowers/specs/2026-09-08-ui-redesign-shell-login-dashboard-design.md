# UI Redesign — Design System, Login & Dashboard

**Sub-project 1 of 5** in the full app UI redesign matching the
`cable_tv_billing_v2.html` mockup. This sub-project builds the shared
navigation shell (drawer + bottom nav + topbar), the color/theme system,
and restyles Login and Dashboard. Later sub-projects (Customers,
Payments, Reports & Complaints, STB/Employees/Settings) each get their
own design doc and reuse what's built here.

## Source of truth

Visual reference: `/Users/nagneelam/Downloads/cable_tv_billing_v2.html`
(dark-theme CSS variables at top of `<style>`, screens `#sc-dashboard`
and `#loginScreen`, JS behavior at bottom for role/theme/lang logic).

## Goals

- Native rebuild (not a WebView wrapper) matching the mockup's visual
  language: rounded cards, colored accent icons, chip-style status
  badges, dark sidebar, gradient CTA banners.
- Reuse existing app infrastructure wherever it already does the job:
  multi-Activity architecture (`startActivity` navigation), existing
  `CustomerModel`/`CustomerRepository` data layer, existing
  `LocaleHelper` for EN/Telugu switching, existing `BaseActivity`.
- Explicit in-app theme toggle (not system-following), persisted.
- Emoji glyphs as the icon system — matches mockup 1:1, zero new
  vector assets.

## Non-goals (deferred to later sub-projects)

- Customer list/detail/add screens, filtered lists (paid/unpaid/
  partial/outstanding) — sub-project 2.
- Make payment, receipt, payment history, today's collection —
  sub-project 3.
- Reports, complaints — sub-project 4.
- STB management, employees, settings detail screens — sub-project 5.
- Real authentication backend — role stays derived from the existing
  hardcoded credential check in `LoginActivity` (admin/ravi/tech).

## Architecture

**Navigation shell:** keep the existing multi-Activity architecture.
Build one reusable shell used by every Activity:
- A custom topbar (replaces `MaterialToolbar`): hamburger button,
  title + subtitle (role · business name), EN/తె language switch,
  sun/moon theme toggle, avatar circle (role initial + notification
  dot).
- `DrawerLayout` + `NavigationView` sidebar: dark background
  regardless of app theme (matches mockup's fixed `--sidebar` color),
  logo header, role pill, "Main" section (Dashboard; expandable
  Customers group — All/Add/Paid/Unpaid/Partial/Outstanding;
  expandable Payments group — Make Payment/Today's Collection/Payment
  History; expandable Reports group — Daily/Monthly[admin]/Area[admin];
  Complaints with badge count; STB Management; Employees
  [admin-only]), "System" section (Settings, Logout).
- `BottomNavigationView` (restyle existing): Home, Customers, Pay,
  Complaints, Settings.

Rejected alternative: single-Activity + Jetpack Navigation Component
(Fragments) to mirror the mockup's SPA screen-swapping exactly. More
faithful transitions, but requires converting all 12 existing
Activities to Fragments — too large a refactor for a redesign task.

**Role gating:** role stays read from `vinayaka_prefs` /
`user_role` (`ADMIN`/`EMPLOYEE`/`TECHNICIAN`), as set today by
`LoginActivity.handleLogin()`. Shell components (drawer sections,
dashboard finance blocks, topbar subtitle) show/hide based on this
value — no new auth system.

**Theme:** explicit in-app toggle via
`AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_YES/MODE_NIGHT_NO)`,
persisted in `vinayaka_prefs`. This still resolves to the existing
`values/` (light) vs `values-night/` (dark) resource sets — no new
resource-qualifier mechanism needed, just forcing the mode instead of
following the system.

**Language:** reuse `LocaleHelper.setLocale()` +
`activity.recreate()` as-is; wire the mockup's EN/తె topbar toggle to
call it.

## Color tokens

Replace `colors.xml` and `values-night/colors.xml` with the mockup's
exact palette (replacing the current ad-hoc purple/pink/orange accent
set):

| Token | Dark | Light |
|---|---|---|
| bg (app background) | `#0c0f1a` | `#eef1fb` |
| card | `#181c2e` | `#ffffff` |
| card2 | `#1e2238` | `#f0f3ff` |
| border | `#262b45` | `#d8dff5` |
| text-primary | `#dde1f0` | `#1a1e35` |
| text-secondary | `#7e87a8` | `#4d5580` |
| text-tertiary | `#454d6a` | `#9aa0c0` |
| sidebar (fixed, both themes) | `#0a0d18` | `#1a1e35` |

Accent family (same hue both themes, dark values shown), each with a
~13% tint for icon/chip backgrounds and a 2-color gradient for
banners/CTAs: blue `#4f80ff`→`#2d5fe8`, coral `#ff6b6b`→`#e84545`,
amber `#ffb347`→`#f08c00`, teal `#38c5d4`→`#1a9aaa`, sky `#60a5fa`.

## Screens

### Login (`LoginActivity` / `activity_login.xml`)

Restyle only — no new screens, no role-picker tabs (mockup's tabs are
inert in the demo; this app already derives role from credentials, so
tabs would be redundant).

- Centered gradient rounded-square logo (📺), "CableManager Pro"-style
  title, "Sai Cable Network — Billing & Collection" subtitle.
- Rounded card (`bg-card`, 18dp corners) holding restyled inputs
  (rounded, dark input bg, blue focus border) for username/password.
- Full-width gradient blue "🔐 Sign In" button.
- Existing "Remember me" checkbox / "Forgot password" / "Contact
  support" kept, restyled smaller/muted to match mockup's minimal
  footer treatment.
- Footer version text.

### Dashboard (`DashboardActivity` / `activity_dashboard.xml`)

Full rebuild — current immersive-gradient-header layout is replaced,
not preserved. `DashboardActivity.kt`'s existing data-binding logic
(today/paid/unpaid/total counts, etc.) carries over, re-wired to new
view IDs; the current layout's "hidden compatibility" placeholder
views are removed.

- Wrapped in the shared `DrawerLayout` shell (topbar + sidebar +
  bottom nav described above).
- Month filter chip row (current/prior months + "Custom Range").
- Search bar.
- 2×2 colored stat grid: Total Customers (blue), Paid This Month
  (teal), Unpaid This Month (coral), Partially Paid (amber) — each
  tappable, launching the existing `CustomerListActivity` with its
  `FILTER_TYPE` intent extra set accordingly (`all`/`paid`/`unpaid`/
  `partial`). The list screen's own visual redesign is sub-project 2;
  this sub-project only wires the navigation.
- Admin-only finance section: wide "This Month Billing" / "This Month
  Collection" cards, "Today's Collection" / "Outstanding" cards, a
  collection-progress bar (percentage + amounts), a 6-month collection
  trend rendered as plain weighted `View` bars (no new charting
  library).
- Employee-only: single "My Today's Collection" card in place of the
  finance section.
- Complaints / Active STBs stat row.
- 3×2 Quick Actions grid: Make Payment, Add Customer, Paid, Unpaid,
  Complaints, Report.

## Testing

No existing instrumented UI tests cover these screens. Verification
is manual, via the `run` skill on an emulator:
- Log in as each role (`admin`/`ravi`/`tech`, password `1234`) and
  confirm drawer contents, admin-only sections, and topbar subtitle
  match the role.
- Toggle theme, restart the app, confirm the choice persisted (not
  reverted to system default).
- Toggle EN/తె, confirm sidebar/topbar/dashboard labels switch via
  the existing `LocaleHelper` recreate flow.
- Tap each stat card and quick-action, confirm navigation targets are
  unchanged from today's behavior.
