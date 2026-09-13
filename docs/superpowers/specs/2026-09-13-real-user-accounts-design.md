# Real User Accounts — Design Spec

Sub-project 1 of a 3-part employee-management initiative requested by the
business owner (2 and 3 — Add Employee/role-based access, and
daily/monthly wage tracking — follow as separate specs once this lands).

## Problem

Login (`LoginActivity.handleLogin()`) hardcodes three username/password/
role triples directly in Kotlin (`admin`/`1234`/ADMIN, `ravi`/`1234`/
EMPLOYEE, `tech`/`1234`/TECHNICIAN). There is no Firestore record of
users at all — which is why the business owner sees no user data there.
Consequently:

- There's no way to add a new employee account.
- There's no way to change a password.
- There's no way to deactivate an account, so there's no way to
  block a former employee from logging in or force-log-out someone
  currently signed in.
- No way to track which role can do what beyond the two-way ADMIN/
  EMPLOYEE split already used for Dashboard section visibility.

## Related pre-existing gap (flagged, not fixed here)

This app has no Firebase Authentication integration anywhere, and no
`firestore.rules` file in the repo — Firestore access rules exist only
in the Firebase Console, in an unknown state. Combined with hardcoded
client-side credentials, this means Firestore reads/writes are not
actually gated by login at the database level — only by the app's own
UI. The business owner was given this tradeoff explicitly and chose the
simpler path below over adopting real Firebase Authentication. This spec
does not change Firestore security rules or add Firebase Auth; it only
replaces the hardcoded credential list with a real (if lighter-weight)
Firestore-backed account system, which is a genuine improvement over
today (hashed passwords instead of literal strings in the APK, ability
to deactivate/add accounts) without closing the database-level gap.

## Design

### Data model

New Firestore collection `users`, one document per account, **document
ID = lowercased username** (so lookups are a direct `get()`, no query/
index needed):

```
users/{lowercased-username}
  username: string       (lowercased, same as doc ID, kept as a field too for convenience)
  name: string           (display name)
  passwordHash: string   (Base64, SHA-256 of salt+password)
  passwordSalt: string   (Base64, random 16 bytes, unique per user)
  role: string            ("ADMIN" | "EMPLOYEE" | "TECHNICIAN")
  active: boolean         (default true)
  createdAt: number       (epoch millis)
```

Passwords are salted and hashed client-side before ever reaching
Firestore — never stored or transmitted in plain text, even though this
is the "simple" (non-Firebase-Auth) path.

### One-time bootstrap

On app start, before any login attempt, check whether `users` is empty
(`limit(1)` query). If empty, seed the three existing accounts (same
usernames, same `1234` password, same roles) via a single batch write —
so nothing breaks for the business owner or current staff on upgrade.
This runs at most once, ever, for a given Firestore project.

### Login rewrite

`LoginActivity.handleLogin()` becomes an async Firestore lookup instead
of an in-memory `when` check:

1. Look up `users/{username.lowercase()}`.
2. Doc doesn't exist, or password hash doesn't match → "Invalid Username
   or Password" (existing message, existing behavior for this case).
3. Doc exists, password matches, but `active == false` → a new, distinct
   message: "Your account has been deactivated. Contact your
   administrator." (Not the generic invalid-credentials message — a
   deactivated employee typing their correct password should not think
   they mistyped it.)
4. Success → same as today: persist username/role to
   `vinayaka_prefs`, navigate to Dashboard.

### Real-time forced logout

While any screen is in the foreground, a lightweight Firestore snapshot
listener watches the current session's own `users/{username}` document
(attached in `BaseActivity.onResume()`, detached in `onPause()` — cheap,
and every screen already extends `BaseActivity`). If that document's
`active` field flips to `false` while the app is open — an admin
deactivating them from another device, for instance — the session is
cleared and the app navigates straight to `LoginActivity` immediately,
without waiting for the user to background and reopen the app. This is
a real Firestore feature independent of Firebase Auth — listeners work
over the existing SDK connection regardless of the auth gap noted above.

The listener is skipped entirely when there's no active session (no
`username` in `vinayaka_prefs`) — which naturally also means it's a
no-op on `LoginActivity` itself.

### Change Password

New option in `SettingsActivity`'s existing Account section: a dialog
asking for current password, new password, and confirmation. Verifies
the current password's hash before allowing the change (re-using the
same hashing logic), re-hashes with a fresh random salt, and updates the
Firestore document. Distinct error messages for "current password
wrong" vs. "new passwords don't match."

## What's explicitly out of scope

- Adding new employee accounts through the UI (that's sub-project 2 —
  this spec only builds the account *storage and login* foundation it
  needs).
- Any change to Firestore security rules or adoption of Firebase Auth
  (explicitly declined by the business owner, tradeoff documented
  above).
- Per-role screen/feature gating beyond what already exists (Dashboard's
  ADMIN vs EMPLOYEE section visibility) — that's sub-project 2.
- Wage/payment tracking — sub-project 3.

## Testing

No pure business logic worth a JUnit test beyond the hashing function
itself (deterministic, no I/O) — that gets one. Everything else
(Firestore lookups, the real-time listener, the login flow) is verified
manually on the `Pixel_9` emulator:

- Fresh Firestore project (or after manually clearing the `users`
  collection): confirm the bootstrap seeds all three accounts and login
  with `admin`/`1234` still works exactly as before.
- Log in, use the Firebase console (or a temporary manual doc edit via
  emulator/`adb`) to flip that user's `active` to `false` while the app
  is open on some other screen: confirm it kicks to Login immediately
  without any user action.
- Attempt to log in as a deactivated account: confirm the distinct
  "deactivated" message, not the generic invalid-credentials one.
- Change password from Settings: confirm the old password stops working
  and the new one logs in successfully afterward.
- Confirm wrong-current-password and mismatched-confirmation cases each
  show their own clear error and do not change anything.
