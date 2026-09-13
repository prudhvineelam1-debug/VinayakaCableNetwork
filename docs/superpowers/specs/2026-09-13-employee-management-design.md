# Add Employee & Role-Based Access — Design Spec

Sub-project 2 of the employee-management initiative (sub-project 1, real
user accounts, is merged to `main`).

## Problem

`AuthRepository`/`users` collection exist (sub-project 1), but there's no
UI to create an employee account — the only accounts that exist are the
three bootstrap-seeded ones. Deactivating an account is currently only
possible by editing Firestore directly (used a REST API workaround to
verify sub-project 1's behavior). Role (`ADMIN`/`EMPLOYEE`/`TECHNICIAN`)
is stored on every account but is checked in exactly one place in the
whole app (`DashboardActivity`'s admin-only financial section) — nothing
else is actually gated by role today.

## Design

### Settings: new "Team" section (ADMIN-only)

`SettingsActivity` gains a new card, visible only when the logged-in
role is `ADMIN` (read from `vinayaka_prefs`'s `user_role`, same source
already used for Dashboard's admin section): a single "Manage Employees"
button that opens `EmployeeListActivity`.

### EmployeeListActivity

Lists every document in the `users` collection: username, display name,
role, and an active/inactive badge. Rows are simple inflated views in a
`LinearLayout` (not a `RecyclerView`+adapter — a cable business's staff
list is a handful of people, not worth the extra machinery for this
count).

Tapping a row opens an edit dialog: a role dropdown (ADMIN/EMPLOYEE/
TECHNICIAN) and an active/inactive switch, with a Save button that
writes both fields back to that user's document. **Safety guard:** if
the row being edited is the currently logged-in account, the
active/inactive switch is disabled with an explanatory note — an admin
can't accidentally lock themselves out by deactivating their own active
session from this screen.

A "+ Add Employee" button opens a second dialog: username, display
name, initial password + confirm, and a role dropdown. Before creating,
checks whether that username (lowercased) already exists as a document
ID in `users` — since the document ID *is* the username, silently
proceeding would overwrite an existing account's credentials. If it
exists, refuses with a clear "username already taken" message instead.
On success, hashes the password the same way `AuthRepository.
ensureSeeded()` already does and writes the new document.

### Add Customer restricted to ADMIN

`DashboardActivity`'s "Add Customer" Quick Actions tile (`qaAddCustomer`)
is hidden (`View.GONE`) when the logged-in role isn't `ADMIN`.
`NewCustomerActivity` itself also checks the role in `onCreate()` and
immediately finishes with a Toast if a non-admin somehow navigates there
directly (deep link, restored task state, etc.) — defense in depth, not
relying solely on the tile being hidden.

## What's explicitly out of scope

- No restriction added to Reports, Generate Bills, or Data Import — the
  business owner confirmed these stay open to every role.
- No protection against deactivating the *last* remaining active admin
  account (only against deactivating *yourself*) — a small business with
  one clear owner doesn't need this extra safeguard, and it can be added
  later if the situation changes.
- No password-reset-by-email flow — an admin can already fix a forgotten
  password by editing that employee's role/re-creating... actually, this
  spec doesn't add an admin-driven "reset password for someone else"
  action either; that's a reasonable follow-up but wasn't asked for.
  Employees change their own password via the existing Settings flow.

## Testing

No new pure logic beyond what sub-project 1 already unit-tests
(`PasswordHasher`). Everything here is Firestore-backed UI, verified
manually on the `Pixel_9` emulator:

- As ADMIN: confirm "Manage Employees" appears in Settings; as EMPLOYEE
  or TECHNICIAN, confirm it does not.
- Add a new employee with a fresh username: confirm it appears in the
  list, and confirm logging in with that username/password works.
- Attempt to add an employee with a username that already exists
  (e.g. `ravi`): confirm it's refused, and confirm the existing
  `ravi` account's password is unchanged afterward.
- Edit an employee's role and confirm it takes effect (their Dashboard
  admin-section visibility changes on next login, matching the new
  role).
- Toggle an employee's active switch off from this screen: confirm the
  same real-time forced-logout behavior from sub-project 1 still fires
  if they're logged in elsewhere.
- Attempt to toggle your own (currently logged in) row's active switch:
  confirm it's disabled/blocked.
- As EMPLOYEE or TECHNICIAN: confirm the Dashboard's "Add Customer" tile
  is gone, and confirm directly constructing an Intent to
  `NewCustomerActivity` (if reachable, e.g. via adb) is rejected.
