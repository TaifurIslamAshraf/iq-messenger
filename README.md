# IQ-Message

Android messaging client for IQ Social, built with Jetpack Compose. The UI is
implemented from the ten mockups in [`Screens/`](Screens/). Three things are live
against real servers — **authentication**, the **address book**, and the **user
profile**; conversations and call history are still sample data.

- **Package** `com.iqsocial.iq_message`
- **minSdk** 24 · **targetSdk** 36 · **compileSdk** 37
- **Toolchain** AGP 9.2.1 · Kotlin 2.2.10 · Compose BOM 2026.02.01

---

## Quick start

```bash
# JDK comes with Android Studio
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"

./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The mockups are drawn at **411 dp** wide. To compare the app against them
pixel-for-pixel on an emulator:

```bash
adb shell wm size 1080x2340
adb shell wm density 420      # 1080/420*160 = 411 dp
```

`adb shell wm size reset && adb shell wm density reset` puts it back.

---

## Permissions

Two, both in [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml):

| Permission | When | Needed for |
|---|---|---|
| `INTERNET` | install time | every API call |
| `READ_CONTACTS` | **asked at runtime, after sign-in** | the People tab and contact matching |

The app is fully usable if contacts are refused — see [Contacts](#contacts).

---

## Screens

Every mockup in `Screens/` is implemented.

| Screen | File |
|---|---|
| Login | [`LoginScreen.kt`](app/src/main/java/com/iqsocial/iq_message/ui/screens/LoginScreen.kt) |
| Sign up (2 steps) | [`SignupScreen.kt`](app/src/main/java/com/iqsocial/iq_message/ui/screens/SignupScreen.kt) |
| Forgot password (3 steps) | [`ForgotPasswordScreen.kt`](app/src/main/java/com/iqsocial/iq_message/ui/screens/ForgotPasswordScreen.kt) |
| Messages list | [`ChatsScreen.kt`](app/src/main/java/com/iqsocial/iq_message/ui/screens/ChatsScreen.kt) |
| Conversation | [`ChatScreen.kt`](app/src/main/java/com/iqsocial/iq_message/ui/screens/ChatScreen.kt) |
| Attachment sheet | [`AttachmentSheet.kt`](app/src/main/java/com/iqsocial/iq_message/ui/screens/AttachmentSheet.kt) |
| Incoming call | [`IncomingCallScreen.kt`](app/src/main/java/com/iqsocial/iq_message/ui/screens/IncomingCallScreen.kt) |
| Video call | [`VideoCallScreen.kt`](app/src/main/java/com/iqsocial/iq_message/ui/screens/VideoCallScreen.kt) |
| Audio call | [`AudioCallScreen.kt`](app/src/main/java/com/iqsocial/iq_message/ui/screens/AudioCallScreen.kt) |
| Profile / Settings | [`ProfileScreen.kt`](app/src/main/java/com/iqsocial/iq_message/ui/screens/ProfileScreen.kt) |

**Calls** and **People** ([`CallsScreen.kt`](app/src/main/java/com/iqsocial/iq_message/ui/screens/CallsScreen.kt),
[`PeopleScreen.kt`](app/src/main/java/com/iqsocial/iq_message/ui/screens/PeopleScreen.kt))
are **not** from the mockups — they were added so the four-tab bottom bar has no
dead ends, and follow the same visual language.

Navigation is a plain in-memory back stack in
[`IqApp.kt`](app/src/main/java/com/iqsocial/iq_message/IqApp.kt) — the flow is small
and fixed, so navigation-compose is not needed.

To reach the incoming-call screen while testing: **Chats → ⋮ → Simulate incoming call**.

---

## App shell — activity, navigation, state

### One activity, no fragments

[`MainActivity`](app/src/main/java/com/iqsocial/iq_message/MainActivity.kt) is a
`ComponentActivity` whose entire body is:

```kotlin
enableEdgeToEdge(
    statusBarStyle = SystemBarStyle.dark(TRANSPARENT),
    navigationBarStyle = SystemBarStyle.dark(TRANSPARENT)
)
setContent { IQMessageTheme { IqApp() } }
```

Both bars are **transparent**, and the icon contrast is not set here — each screen
declares its own, because the app alternates between a crimson hero (light icons)
and white sheets (dark icons):

```kotlin
SystemBarIcons(darkIcons = true)   // first line of most screens
```

That writes through `WindowInsetsControllerCompat` inside a `SideEffect`, and
returns early under `LocalInspectionMode` so `@Preview` never reaches for a window
that does not exist. The activity ships one preview, at 411 × 915 — the mockups'
own width.

### Manifest

```xml
<activity android:name=".MainActivity" android:exported="true"
          android:theme="@style/Theme.IQMessage">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

One activity, one launcher entry. `supportsRtl` and `allowBackup` are the template's
defaults. Note what is **absent**: no `android:configChanges` and no
`android:screenOrientation`, so the system recreates the activity on rotation — see
[Where state lives](#where-state-lives).

### Navigation

A sealed `Route` and a hand-rolled stack in
[`IqApp.kt`](app/src/main/java/com/iqsocial/iq_message/IqApp.kt), not
navigation-compose:

```kotlin
sealed interface Route {
    data object Login : Route
    data object Signup : Route
    data object ForgotPassword : Route
    data object Home : Route
    data class Chat(val conversationId: String) : Route
    data class IncomingCall(val personId: String) : Route
    data class VideoCall(val personId: String) : Route
    data class AudioCall(val personId: String) : Route
}
```

`Navigator` wraps a `SnapshotStateList<Route>` with `push`, `pop` and `replaceAll`;
`current` is simply the last entry. Sign-in uses **`replaceAll`**, so the auth
screens cannot be reached again with the back button. Transitions are an
`AnimatedContent` slide-and-fade.

**The four Home tabs are not routes.** They are one `HomeShell` switching on a `tab`
value, so moving between Chats, Calls, People and Settings never grows the back
stack — back from any tab leaves the app rather than walking through tab history.

**Back is handled at two levels.** The app registers
`BackHandler(enabled = nav.canGoBack) { nav.pop() }`. Screens with internal steps
register their own, which takes precedence because it is nested deeper:

| Screen | Back does |
|---|---|
| Sign up, step 2 | returns to step 1, keeping the verified number |
| Forgot password, steps 2–3 | returns to the previous step |
| anything else | pops the route |

Without those, system back would pop the whole route and throw away a number the
server had already cleared.

### Where state lives

There is no `ViewModel` and no DI container — the same
[no-new-dependency rule](#deliberate-constraints) that shapes the rest of the app.
State sits in three places, and they behave differently:

| State | Held by | Survives rotation | Survives process death | Cleared on log-out |
|---|---|---|---|---|
| Token, user, device id, contacts-prompted flag | `SessionStore` → `SharedPreferences` | ✅ | ✅ | ✅ (device id kept) |
| Selected Home tab | `rememberSaveable` | ✅ | ✅ | — |
| **Route back stack** | `remember` | ❌ | ❌ | — |
| **Screen form fields** | `remember` | ❌ | ❌ | — |
| Address book + matches | `ContactsRepository` (object) | ✅ | ❌ | ✅ |
| Messenger profile | `MeRepository` (object) | ✅ | ❌ | ✅ |

`rememberSaveable` is used exactly once in the codebase, for the tab. Everything
else in the composition is plain `remember`, which is discarded when the activity is
recreated — so **rotating the device returns to the start destination and empties
any half-filled form**. The app is not orientation-locked, so this is reachable; see
[Known gaps](#known-gaps).

The two process-level objects sit *outside* the composition, which is why they
survive rotation for free. They die with the process and are re-fetched on the next
launch, each guarded by its own `loaded` flag so returning to a screen does not
re-request.

### Lifecycle and coroutines

The activity overrides nothing but `onCreate`; there is no `onResume`/`onPause` work,
and nothing is registered that would need unregistering. Every network call is
launched from a composition scope — `rememberCoroutineScope()` for taps,
`LaunchedEffect` for on-appear loads — so in-flight work is cancelled when the
composable leaves. `ApiClient` does its I/O on `Dispatchers.IO` and each call sets
its own connect/read timeout of 20 s.

---

## Two servers

The app talks to two hosts, and they are complementary rather than alternatives:

| | Host | Serves |
|---|---|---|
| **Messenger API** *(the default)* | `messanger-server.gd2zih.easypanel.host` | `/v1/me`, `/v1/contacts/sync`, `/v1/contacts`, health |
| **Account API** *(auth only)* | `connect.iqamasocial.com/v1/api` | login, signup, `check_phone`, OTP, password reset |

The messenger host is the **default** base URL in
[`ApiClient`](app/src/main/java/com/iqsocial/iq_message/data/remote/ApiClient.kt);
the account API is the exception, reached only through `AuthApi.accountPost`, which
is the single place to change if auth ever moves.

Auth cannot move today: the messenger server exposes exactly five routes and
nothing else. `/v1/auth/login`, `/v1/login`, `/v1/signup`, `/v1/check_phone`,
`/v1/send_otp_code`, `/v1/chats`, `/v1/messages` and `/v1/users` were all probed
and every one returns `Route not found`. Its own spec settles the intent: the
bearer token it expects is *"Token issued by the MAIN IQ Social backend (POST
/v1/api/login)"*.

---

## Backend integration

### The live API is not the same as the local `iq-backend-web` checkout

`https://connect.iqamasocial.com` is a deployment **ahead of** the repo at
`../iq-backend-web`. The request bodies match `auth.controller.js`, but the paths do
not. Everything below was verified by calling the host directly.

| | Local repo says | **Live (verified)** |
|---|---|---|
| Base | `/api` | **`/v1/api`** |
| Login | `/api/auth/login` | **`/v1/api/login`** |
| Register | `/api/auth/register` | **`/v1/api/signup`** |
| Current user | `/api/auth/me` | does not exist |

`/v1/api/auth/*` on the same host is a **separate admin API** keyed by email — it
answers `"Admin not found with this email"`. The app must not call it.

Base URL lives in one place:
[`ApiConfig.BASE_URL`](app/src/main/java/com/iqsocial/iq_message/data/remote/ApiClient.kt).

### Phone numbers are E.164 — this matters

The server matches `phone_number` by **exact string** and does **not** normalise.
Verified against a real account:

| Sent | Result |
|---|---|
| `+8801XXXXXXXXX` | ✅ success |
| `01XXXXXXXXX` | ❌ *Invalid password* — resolves to a **different** account |
| `880XXXXXXXXXX` | ❌ not found |
| `1XXXXXXXXX` | ❌ not found |

So [`Country.composeForApi`](app/src/main/java/com/iqsocial/iq_message/data/Country.kt)
always emits `+<dial><national>`, dropping the national trunk `0`.

> ⚠️ The local repo's `normalisePhone` / `isValidPhone` (`/^01\d{9}$/`) live in the
> **deposit and recharge** controllers, not the auth path, and describe the older
> national format. Do not use them as a guide for auth.

> ⚠️ Because `01…` and `+880…` resolve to *different rows*, accounts created in the
> older national format **cannot sign in through this app**. Worth auditing the
> `users` collection for duplicate numbers.

### Sign-up requires `country` and `device_id`

The deployed `/v1/api/signup` demands two fields the local repo treats as optional
(`country || ''`, `device_id || ''`). Verified field by field:

| Body | Response |
|---|---|
| `{phone, password}` | `Country can't be empty` |
| `{phone, password, country}` | `Device ID can't be empty` |
| `{phone, password, country, device_id}` | ✅ creates the account |

`country` accepts either an ISO code or a full name; the app sends the ISO code
(`BD`). `device_id` must be non-empty **and is bound to the account it creates** —
reusing one returns `This device is already registered to another account`.

So it has to be stable across launches. The app uses `Settings.Secure.ANDROID_ID`
with a persisted UUID fallback
([`SessionStore.deviceId`](app/src/main/java/com/iqsocial/iq_message/data/auth/SessionStore.kt)),
and deliberately **keeps it through log-out** — it identifies the install, not the
session.

> ⚠️ Because the server binds one device to one account, a device that has already
> registered cannot create a second account. Signing out and signing up again will
> fail with the device error. Logging back in is unaffected.

> ⚠️ `/signup` does **not** reject a phone number that already exists — it creates
> another row. Duplicate-phone protection is the caller's job.

### Sign-up asks the server first

Sign-up is two steps: the number is checked before the rest of the form is shown.

```
POST /v1/api/check_phone   { phone, device_id }
```

| Body | Response |
|---|---|
| taken number | `{"status":"error","msg":"This phone number has been taken"}` |
| free number | `{"status":"success","result":{"expires_in":60},"msg":"Otp has been sent."}` |
| free number, bound `device_id` | `{"status":"error","msg":"This device is already registered to another account"}` |

Both things this catches — a registered number and a device already bound to
another account — are knowable only to the server, and either one makes the whole
form wasted typing. Asking first turns that into one tap, and the error shown is
the server's own wording, because the two problems have different fixes.

The route stores a signup OTP as a side effect. The app never asks for it: sign-up
still has no verification step.

> ⚠️ `user.controller.js` returns the taken case as `successMsg(null, "This phone
> number has been taken")` — a *success* envelope carrying a rejection — while the
> deployed build returns `status:"error"`. [`AuthApi.checkPhone`](app/src/main/java/com/iqsocial/iq_message/data/remote/AuthApi.kt)
> treats both as taken, so redeploying that source cannot let someone through.

### Response envelopes

The host mixes two shapes, and [`ApiClient`](app/src/main/java/com/iqsocial/iq_message/data/remote/ApiClient.kt)
accepts both:

```jsonc
{"status": "success", "result": {…}, "msg": null}   // user API
{"status": true,      "message": "…"}               // admin API
{"status": "error",   "msg": "…", "errors": [{"path":…, "message":…}]}
```

Note failures often arrive with **HTTP 200**, so the envelope's `status` decides the
outcome — not the status code. Validation detail is pulled out of `errors[]`.

### Password reset is snake_case

These three routes exist and were driven end-to-end with a real SMS code. They are
**not** hyphenated, which is why an earlier pass concluded they were missing — every
`/forgot-password`-style path 404s. They live in `routes/app.routes.js` in the main
backend (`../Main Server/iq-social-backend`), not in `iq-backend-web`.

```
POST /v1/api/send_otp_code   { phone }              -> userController.sendOtp
POST /v1/api/verify_code     { phone, code }        -> userController.verifyCode
POST /v1/api/reset_password  { phone, password }    -> userController.forgetPassword
```

Two things to note: the last one takes `password`, not `newPassword`, and
`send_otp_code` requires the number to **exist** (the separate `/send_otp` is the
signup one and refuses a registered number).

> 🔴 **`reset_password` never verifies the OTP.** It takes `{phone, password}` and
> writes the new password, so any unauthenticated caller can change any account's
> password given only its phone number. The app's UI gates it behind `verify_code`,
> but nothing on the server enforces that. **Reported, not fixed** — this repo does
> not touch backend code.

> 🟠 **OTPs are never invalidated.** `otpModel.deleteMany({ phone_number: phone })`
> is never awaited or `.exec()`'d, so Mongoose never runs the query. Confirmed by
> verifying the same code twice and getting `"Matched"` both times. A code stays
> usable until it is overwritten.

### The login response returns a `password` field

`POST /v1/api/login` includes `password` in its result object, alongside ~50 other
fields (`balance`, `kyc`, `roles`, `blocked_users`, `device_id`, `chat_contacts`…).

Observed on a real account: it is a **4-character string** — neither the plaintext
submitted nor a bcrypt hash, so it does not appear to be the credential itself. It
should still not be in a login response under that name.

> 🟠 **Reported, not fixed** — this repo does not touch backend code. Note the
> README's earlier claim that `User.toJSON` "strips the password" does not hold for
> the deployed build.

The app stores only what it needs: token, uid, name, phone, picture, ref code
([`SessionStore.save`](app/src/main/java/com/iqsocial/iq_message/data/auth/SessionStore.kt)).

### Session

The token is kept in `SharedPreferences`
([`SessionStore`](app/src/main/java/com/iqsocial/iq_message/data/auth/SessionStore.kt)),
so relaunching opens straight into the chat list. **Log out** clears it.

---

## Contacts

The address book is asked for **once the user signs in**, not at launch and not
behind the People tab: that is the first moment it is worth anything.

| | |
|---|---|
| Permission | `READ_CONTACTS`, requested at runtime |
| Popup | [`ContactsAccess.kt`](app/src/main/java/com/iqsocial/iq_message/ui/components/ContactsAccess.kt) — **Skip** in the top-right corner |
| Reader | [`DeviceContacts.kt`](app/src/main/java/com/iqsocial/iq_message/data/contacts/DeviceContacts.kt) |
| Shown in | People tab — **only the contacts who matched** |

The gate decides without bothering the user where it can: already granted loads
silently, already asked stays quiet, and only a fresh sign-in sees the popup. The
"asked" flag lives with the session, so signing out and back in asks again.

Skip is in the top-right because it is the way out of a request the user never
made — it should be the first thing their thumb finds. Skipping is not final: the
People tab then shows a **Sync your contacts** row that re-opens the same prompt.

Reading is one row per contact rather than per number — somebody saved with a
mobile and a work line is one person in a messenger — and maps straight onto the
existing `Person` model, so device contacts and sample data render identically.

### Contacts are synced to the messenger API

The address book goes to a **second host**, which is not the account API:

```
POST https://messanger-server.gd2zih.easypanel.host/v1/contacts/sync
Authorization: Bearer <token from the MAIN backend's /v1/api/login>
{ "phones": ["01712345678", ...], "region": "BD" }
```

It authenticates with the **same** token the app already stores — the spec says so
outright, so there is no second sign-in. Its full surface is small: `/v1/contacts/sync`,
`/v1/contacts` (one contact at a time, not used yet), `/v1/me` and two health probes.
Spec: [`/docs/swagger.json`](https://messanger-server.gd2zih.easypanel.host/docs/swagger.json).

Three things the contract dictates, each of which shapes the code:

| The spec says | So the app |
|---|---|
| numbers not sent are **removed** | always sends the whole address book, never a delta |
| `region` is used to read numbers saved without a country code | sends the SIM's country and lets the server normalise |
| max 20 000 phones, 40 chars each | trims to those limits rather than risking a rejection |

The reply is `{synced, invalid, removed, matches[]}`, where each match is an IQ user
(`uid`, `phone` in E.164, `fullname`, `profile_picture`, `verified`, `badge_key`).
Matches are tied back to device contacts by the **last nine digits** of the number,
since the server answers in E.164 and the phone stores whatever someone typed.

**The People tab lists only matches.** The address book is filtered down to the
numbers the server recognised — people you cannot message do not belong on a
messaging app's People tab. Rows therefore carry no "On IQ Social" badge: the whole
list is that, so saying it on every row would be noise, and the number identifies
them instead.

When the address book is read but nothing matches, the tab says so rather than
looking broken, and distinguishes the two reasons:

| State | Shown |
|---|---|
| synced, nobody matched | *"No contacts on IQ Social yet"* — none of your N contacts have an account |
| sync never succeeded | *"Couldn't check your contacts"* + a **Try again** action |

The two are told apart by `ContactsRepository.lastSync`, which is only set on a
successful sync.

Note this host's envelope is its own — `{success, message, responseObject, statusCode}`
— and unlike the main backend it uses real HTTP status codes (401 for a bad token).
[`ApiClient`](app/src/main/java/com/iqsocial/iq_message/data/remote/ApiClient.kt) now
takes a base URL and accepts all three envelope shapes.

> **Only numbers are sent, never names.** The request body carries `phones` and
> nothing else, which is exactly what the popup promises. Keep those two in step:
> if the payload ever grows, the copy has to change with it.

> A failed sync is not fatal — the contacts have been read, and the People tab is
> useful before the server has said who is on IQ Social.

---

## Profile — `/v1/me`

The only source for the user's real profile: the account API has no `/me`, and
login returns just name, phone and picture.

```
GET /v1/me        Authorization: Bearer <token from /v1/api/login>
```

Returns `uid`, `fullname`, `phone_number`, `bio`, `profile_picture`, `verified`,
`badge_key`, `chat_status` (`online`/`offline`/`away`/`busy`), `chat_last_seen`,
`show_active_status`, `privacy_settings {who_can_message, who_can_call}`, and the
three counts `followers`, `following`, `total_post`.

> `total_post` counts **posts only** — reels live in a separate collection and are
> not included.

It drives the Profile screen through
[`MeRepository`](app/src/main/java/com/iqsocial/iq_message/data/profile/MeRepository.kt),
fetched once per launch and cached:

| Was hardcoded | Now |
|---|---|
| "Rafi Hasan" / "@rafihasan" | `fullname` / `phone_number` |
| "Creating smarter connections." | `bio` |
| verified tick, always shown | only when `verified` is non-zero |
| presence dot, always shown | `show_active_status` + `chat_status` |
| Privacy: "Security and visibility" | the two `privacy_settings` values |
| stats: 128 / 12.6K / 584 | `total_post` / `followers` / `following` |

> **Read-only.** `PUT`, `PATCH` and `POST` on `/v1/me` all return 404, so the
> Privacy row *reports* those settings rather than pretending to change them. The
> Notifications switch is still local-only state for the same reason.

`badge_key` and `chat_last_seen` are fetched but not shown — the badge is an opaque
key (`coin_agent`) with no display name to render, and there is nowhere in the card
for a "last seen" line.

A failed fetch falls back to the sample identity, so the screen renders offline.

**What a real account actually returns**, and what each value taught us:

| Field | Real value | Consequence |
|---|---|---|
| `verified` | `0` | **no tick** — the mockup showed one unconditionally |
| `bio` | `""` | shows a muted *"Add a bio"*, not the mockup's invented line |
| `fullname` | `"Mithun sarker "` | trailing space, so the parser trims |
| `privacy_settings` | both `following` | the row collapses to "Messages & calls: following" |
| `chat_status` | `online` + `show_active_status: true` | green presence dot |
| `followers` / `following` / `total_post` | `2` / `1` / `9` | the stat row, formerly hardcoded |

Two of those were bugs found only by running it against real data: an empty bio
inherited the mockup's sentence, and the untrimmed name pushed against the tick.

**The stat row is live.** `followers`, `following` and `total_post` were added to
the endpoint after the first pass and are now wired to the three cells that used to
read a hardcoded *128 / 12.6K / 584*. Those mockup numbers survive only as the
placeholder shown before `/v1/me` answers, so the card never flashes a row of zeroes.

Counts render through `compactCount` — `9`, `1.2K`, `12.6K`, `3.4M` — built from
integer maths rather than `String.format`, which would follow the device locale and
turn `12.6K` into `12,6K`.

---

## Design system

### Responsive sizing

Every dimension derives from the window size rather than being a literal.
[`Dimens.kt`](app/src/main/java/com/iqsocial/iq_message/ui/theme/Dimens.kt) builds
~40 tokens from a 411 dp reference and provides them through `IqTheme.dimens`.

```kotlin
val d = IqTheme.dimens
Modifier.size(d.avatarLg)        // token
Modifier.padding(d.scaled(18))   // one-off on the same curve
```

Type scales on a **gentler curve** than boxes (`fontScale` is clamped tighter than
`scale`) — headlines that track layout width one-for-one become unreadable on small
phones.

Layouts adapt structurally, not just by shrinking:

| Screen | Adaptation |
|---|---|
| Audio call | avatar + name side-by-side ≥ 340 dp, **stacks** below |
| Attachment sheet | 3 → 4 → 5 gallery columns |
| Incoming call | avatar is a share of width, capped at 240 dp |
| Video call | self-view 27 % of width, clamped 88–150 dp |
| Voice note | waveform bar count follows available width |
| OTP cells | `aspectRatio` + weights, not a fixed height |
| Tablets | content capped at 600 dp and centred |

Hairlines, minimum touch targets (48 dp) and row heights (64 dp) deliberately do
**not** scale.

### Icons

[`IqIcons.kt`](app/src/main/java/com/iqsocial/iq_message/ui/icons/IqIcons.kt) — 59
icons drawn in-project from circles, capsules and round-capped strokes, so the set
reads as one rounded family.

They are hand-authored because `material-icons-extended` is not on the classpath and
several glyphs the mockups need (waveform, flip camera, QR, double-tick) are not in
the core set either.

### Call-control containers

The two call bars have a container edge that cuts around the end-call button —
measured off the mockups, not eyeballed:

- **Video** — the pill *bulges up* in a ~92 × 11 dp S-curved arc
  ([`HumpedBarShape`](app/src/main/java/com/iqsocial/iq_message/ui/components/Common.kt))
- **Audio** — the panel is *scooped out* beneath the button, which straddles the edge
  ([`EdgeCutShape`](app/src/main/java/com/iqsocial/iq_message/ui/components/Common.kt))

### Form fields

Everything below is in
[`AuthCommon.kt`](app/src/main/java/com/iqsocial/iq_message/ui/screens/AuthCommon.kt).

**The border is drawn with a gap, not masked.** The usual trick — painting the label
over the border on a matching chip — only works while the field, the chip and
whatever sits behind them are the same colour, and shows a rectangle the moment they
are not. `drawNotchedOutline` instead strokes a `Path` that stops either side of the
label, so there is nothing to mask and nothing to match.

The notch is sized by measuring the label with `rememberTextMeasurer`, and its left
edge starts at `radiusMd + gapSm` — inside the corner arc, the path clamps the gap
away and the label sits on a solid border.

**The field has no fill of its own.** Its interior is `SheetSurface`, the same
(252,252,252) as the sheet, exactly as the mockup has it.

**Focus is visible.** On focus the outline animates to crimson and 2 dp, and the
label's colour follows it — `MutableInteractionSource` + `collectIsFocusedAsState`,
with `animateColorAsState` / `animateDpAsState` so it eases rather than snaps.

### Country selector

[`Country.kt`](app/src/main/java/com/iqsocial/iq_message/data/Country.kt) +
[`CountryPicker.kt`](app/src/main/java/com/iqsocial/iq_message/ui/components/CountryPicker.kt) —
98 countries, searchable by name, ISO code or dial code. Flags are derived from the
ISO code via regional-indicator code points, so **no flag assets ship**.

---

## Project structure

```
app/src/main/java/com/iqsocial/iq_message/
├── IqApp.kt                 route graph + back stack
├── MainActivity.kt          edge-to-edge host
├── data/
│   ├── Country.kt           dial codes, flags, E.164 composition
│   ├── SampleData.kt        demo conversations, calls, contacts
│   ├── auth/
│   │   ├── AuthRepository.kt    validation + session persistence
│   │   └── SessionStore.kt      token, user, device id, contacts flag
│   ├── contacts/
│   │   └── DeviceContacts.kt    ContactsContract reader + ContactsRepository
│   ├── profile/
│   │   └── MeRepository.kt      /v1/me, cached per launch
│   └── remote/
│       ├── ApiClient.kt         one HTTP path, three envelope shapes
│       ├── AuthApi.kt           the account API (the exception)
│       └── MessengerApi.kt      /v1/me, /v1/contacts/sync
└── ui/
    ├── components/
    │   ├── Common.kt            Avatar, bubbles, call-bar shapes
    │   ├── BottomBar.kt         the four-tab bar
    │   ├── ContactsAccess.kt    permission popup + gate
    │   └── CountryPicker.kt     searchable country sheet
    ├── icons/IqIcons.kt     the rounded icon set
    ├── screens/             one file per screen
    └── theme/               Color, Type, Dimens, Theme
```

---

## Testing notes

Driving the app over `adb` has two traps worth knowing:

- **`input keyevent 4` navigates back** when the keyboard is not up. Used to dismiss
  a keyboard that had already closed, it walks out of the screen — or out of the app
  — and every following tap lands somewhere unintended. Screenshot between steps
  rather than firing a batch of taps.
- **Screenshots race the network.** A capture taken right after a tap shows the
  pre-response state; `/v1/me` twice looked like it had failed when it simply had
  not returned yet. Capture again before concluding anything.

LDPlayer also drops its adb connection when the window closes; `adb connect
127.0.0.1:5555` brings it back, and `-s <device>` matters once two entries appear
for the same emulator.

---

## Deliberate constraints

**No new Gradle dependencies.** Networking is `HttpURLConnection` + `org.json`;
storage is `SharedPreferences`; navigation is a hand-rolled back stack; icons and
flags are drawn, not bundled. The dependency block is unchanged from the template.

**Avatars are generated.** No portrait assets shipped with the mockups, so
[`Avatar`](app/src/main/java/com/iqsocial/iq_message/ui/components/Common.kt) renders
a stable seeded gradient with initials. Swapping in an image loader touches only that
composable.

**`compileSdk` raised 36.1 → 37.** Not a preference — `androidx.core:1.19.0`,
`lifecycle:2.11.0` and `activity:1.13.0` all refuse to compile against 36.1 and the
build failed until it was bumped. `targetSdk` stays at 36.

**Theme is fixed light.** The template's dynamic-color scheme was removed; it would
repaint the crimson brand on Android 12+. The call screens paint themselves dark.

---

## Known gaps

**Sign-up is not run end-to-end.** Step one (`check_phone`) is verified on-device in
both directions. Step two is not: submitting it creates a real account in the
production database. The payload is confirmed correct — sending the app's exact body
against an already-bound `device_id` reaches the device-uniqueness check, meaning
every field passed validation.

**Contacts sync has not been run against the real account.** `/v1/contacts/sync` is
a *full replace* — "numbers not sent are removed" — and the account carries 15
entries in `chat_contacts`. Running it from an emulator seeded with five fake
contacts would overwrite that list, so only the failure path (401) has been
exercised. Testing it safely needs either a throwaway account or a device holding
the real address book.

**Nothing in the profile can be edited.** `/v1/me` is read-only (`PUT`/`PATCH`/`POST`
all 404), so the edit button, the Account row and the Privacy row are display-only,
and the Notifications switch is local state that persists nowhere.

**Chat and calls are still `SampleData`.** Auth, contacts and the profile talk to
the server; conversations, messages and call history do not, and the messenger API
has no routes for them yet.

**Rotation resets navigation.** The back stack and every screen's form state are
held in `remember`, and the activity has no `configChanges`, so turning the device
sends the user back to the start destination with their typing gone. Fixing it means
either a `Saver` for the stack plus `rememberSaveable` on the form fields, or locking
the activity to portrait — the mockups are portrait-only, so the second is the
cheaper honest answer. *(Read off the code — `remember` does not survive activity
recreation — rather than reproduced on a device; it is not in the
[verification table](#verification).)*

**`PhoneScreen.kt` / `OtpScreen.kt` are orphaned.** Their composables are no longer
routed (login replaced them), but `PhoneScreen.kt` still defines shared helpers
(`CenteredColumn`, `StepPill`, `BrandLockup`, `PrimaryButton`) that the auth screens
use. Move those out before deleting either file.

**Non-Bangladesh numbers may not work downstream.** The backend is BD-only in
practice — `isValidPhone` is `/^01\d{9}$/`, plus bKash/Nagad and a Dhaka timezone.
A foreign number will register but later features may reject it.

---

## Verification

Everything below was driven on an LDPlayer emulator at 411 dp against the live
servers, not asserted from reading code.

| Flow | Result |
|---|---|
| Login | ✅ end to end, real credentials, session persists across restart |
| Wrong password | ✅ shows the server's own *"Invalid password"* |
| Password reset, 3 steps | ✅ end to end with a real SMS code (set to the existing password, so the write was a no-op) |
| Sign-up step 1, taken number | ✅ *"This phone number has been taken"* |
| Sign-up step 1, free number | ✅ advances to step 2 showing the E.164 number |
| Sign-up step 2 | ❌ not submitted — would create a real account |
| Back / system back, both multi-step flows | ✅ steps back, keeps the entered number |
| Contacts popup | ✅ appears after sign-in, Skip dismisses and persists |
| Contacts read, no matches | ✅ shows the empty state, not the unmatched contacts |
| Contacts read + People list | ✅ five seeded contacts, grouped A–E (before match-filtering) |
| Contacts sync, 401 | ✅ degrades gracefully, list still renders |
| Contacts sync, success | ❌ not run — it would overwrite the real address book |
| `/v1/me` | ✅ real profile renders; parser matched every field and type |
| `/v1/me`, 401 | ✅ falls back to the sample identity, no crash |
| Messenger health | ✅ `/health/ready` reports `mongo: up` |
| Auth routing after the base-URL flip | ✅ still reaches the account API |

The icon set and every screen were compared against the source mockups by cropping
both at matched scale.

**Production was treated as production.** The local backend was never started — its
`DB_URL` points at a real database — and no write was made to the real account
beyond signing in.

> ⚠️ One exception, disclosed at the time: probing `/signup` with an
> already-registered number, on the assumption it would be rejected, **created a
> stray account** — the duplicate check does not exist. The row has `device_id:
> "abc123"` and an empty `fullname`; it should be deleted. The real account on that
> number was verified intact.
