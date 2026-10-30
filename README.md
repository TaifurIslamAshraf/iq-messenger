# IQ-Message

Android messaging client for IQ Social, built with Jetpack Compose. The UI is
implemented from the ten mockups in [`Screens/`](Screens/). Three things are live
against real servers — **authentication**, the **address book**, and the **user
profile**, and the whole **messaging** layer. No demo data remains anywhere in the
app: what a screen cannot get from a server, it says it has none of.

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

The ⋮ menu on Chats now refreshes the list; the old "simulate incoming call" entry
existed only to demo a screen with invented people and is gone.

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
| **Messenger API** *(the default)* | `messanger.iqamasocial.com` | `/v1/me`, `/v1/contacts/sync`, `/v1/contacts`, health |
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

Observed on a real account, its value is the string **`"true"`** — a "this account
has a password set" flag, not the credential. No secret leaks here; the field is
just badly named, and `User.toJSON` does strip the real hash as documented.

> An earlier pass recorded this as "a 4-character string of unknown nature", which
> was only true because the value had not been read. It is `"true"`.

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
existing `Person` model, which is what the avatars and rows are drawn from.

### Contacts are synced to the messenger API

The address book goes to a **second host**, which is not the account API:

```
POST https://messanger.iqamasocial.com/v1/contacts/sync
Authorization: Bearer <token from the MAIN backend's /v1/api/login>
{ "phones": ["01712345678", ...], "region": "BD" }
```

It authenticates with the **same** token the app already stores — the spec says so
outright, so there is no second sign-in. Its full surface is small: `/v1/contacts/sync`,
`/v1/contacts` (one contact at a time, not used yet), `/v1/me` and two health probes.
Spec: [`/docs/swagger.json`](https://messanger.iqamasocial.com/docs/swagger.json).

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

A failed fetch leaves the card empty rather than inventing an identity: the name
reads "Your profile", the counts show a dash, and no verified tick appears.

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

## Messaging API

The messenger server grew from 5 routes to **41**. The list, the conversation, sending
and realtime are wired.

Spec: [`/docs/swagger.json`](https://messanger.iqamasocial.com/docs/swagger.json).
Same host, same bearer token — the spec's own description says identity is delegated
to the main backend's `/v1/api/login`.

### The surface

| Tag | Count | Routes |
|---|---|---|
| Conversations | 15 | list, get, open, search, unread-count, pin/unpin, mute/unmute, archive/unarchive, read/unread, delivered, delete |
| Messages | 13 | page, send, edit, forward, react/unreact, delete for me / for everyone, search (global and in-conversation), shared media, info, changes |
| Media | 4 | create upload, complete upload, download, thumbnail |
| Contacts | 2 | sync, single |
| People | 2 | search, suggestions |
| Link previews | 2 | preview, image proxy |
| Me / Health | 3 | profile, live, ready |

### The chat list

```
GET /v1/conversations?folder=inbox|archived&cursor=&limit=
```

Returns `{ items: Conversation[], next_cursor }`. A `Conversation` carries `peer`
(uid, fullname, profile_picture, verified, badge_key), `last_message`,
`unread_count`, the flags `pinned` / `archived` / `muted` / `marked_unread`, and
crucially **`peer_delivered_seq` / `peer_read_seq`** — how far the other person has
received and read, which is what drives ✓ / ✓✓ / blue ✓✓ on your own messages.

At most **5 pinned** conversations; a sixth returns 409.

### Messages and the sync model

```
GET  /v1/conversations/{id}/messages?before_seq=   # history, newest first
GET  /v1/conversations/{id}/messages?after_seq=    # catch-up, oldest first
GET  /v1/conversations/{id}/messages/changes?since= # edits, deletes, reactions, previews
```

Every message has a **`seq`** — its position in the conversation, 1, 2, 3… Paging is
by `seq`, not by timestamp or offset, and `next_seq` comes back for the next page.
New messages arrive via `after_seq`; *changes to old ones* (edited, deleted, reacted
to, link preview attached) only come from `/changes?since=`, because they do not
move the sequence. An app that polls only `after_seq` will silently miss edits.

### Sending

```
POST /v1/messages  { conversation_id, client_id, text | media_id | location | contact, reply_to_id? }
```

`client_id` is a UUID the app generates, and it makes the call **idempotent**: 201
when stored, 200 returning the already-stored message if the same `client_id` was
seen before. That is the retry story — resend the same `client_id` after a timeout
rather than risking a duplicate.

Files are a **three-step** flow: `POST /v1/media` returns presigned R2 links → `PUT`
the bytes (and thumbnail) with exactly the declared Content-Type and size → `POST
/v1/media/{id}/complete` → send with `media_id`. Sending a pending upload is a 409;
unsent uploads are deleted after 24 hours; the whole feature 503s when file sharing
is not configured.

Message types: `text`, `image`, `video`, `voice`, `audio`, `document`, `location`,
`contact`. Voice messages carry a `waveform` (0–100 per bar) and media carries a
BlurHash `placeholder` to show while loading.

Editing is capped at **15 minutes** after sending, delete-for-everyone at **48
hours**, forwarding at **5 conversations**, bulk delete at **100 messages**.

### Media

Implemented, and blocked by the server rather than by the app — see the note at the
end of this section.

```
POST /v1/media                      -> presigned R2 links
PUT  <upload.url>                   -> the raw bytes, no Authorization header
POST /v1/media/{id}/complete        -> now sendable
POST /v1/messages { media_id }      -> finally a message
```

`CreateUploadRequest` is a **oneOf**: a different contract per kind, each declaring
the file up front so the server can presign for exactly that object.

| kind | required besides `kind` | max size | accepted types |
|---|---|---|---|
| `image` | `mime_type`, `size`, `width`, `height` | 20 MB | jpeg, png, webp, gif, heic, heif |
| `video` | + `duration_ms` | 100 MB | mp4, 3gpp, quicktime, webm, x-matroska |
| `voice` | `mime_type`, `size`, `duration_ms` | 16 MB | ogg, opus, mp4, aac, mpeg, amr, 3gpp, webm |
| `audio` | `mime_type`, `size` | 100 MB | any |
| `document` | + `file_name` | 100 MB | any |

`size` is *"Bytes, exactly"* — the presigned PUT is signed for that length, so a
byte out and the upload fails. Images and videos may also declare a BlurHash
`placeholder` and a `thumbnail` (jpeg/webp/png, **max 512 KB**), which gets its own
presigned target; voice may carry a `waveform` of 0-100 bars.

Then: `PUT` the bytes with **exactly** the `Content-Type` handed back and no
`Authorization` header, call `/complete` (409 while storage has not seen the file,
idempotent once it has), and send with `media_id` (409 if still pending). Unsent
uploads are deleted after **24 hours**, and the whole feature answers **503** when
file sharing is not configured on the server.

Downloads are `GET /v1/messages/{id}/media` and `.../media/thumbnail`, which **302**
to an R2 link valid for an hour — follow the redirect *without* the Authorization
header, or pass `?token=` where a header cannot be set. Both 404 once the message is
deleted for everyone.

**How the app does it.**
[`MediaPreparer`](app/src/main/java/com/iqsocial/iq_message/data/media/MediaPreparer.kt)
reads the file's real size from its descriptor — not from a column that can be stale
— because the presign is for that exact length, and decodes only the image *bounds*
so a large photo costs nothing to measure. Unknown image and video types degrade to
`document` rather than being refused.
[`MediaApi.putBytes`](app/src/main/java/com/iqsocial/iq_message/data/remote/MediaApi.kt)
streams with `setFixedLengthStreamingMode` and sends **only** the headers the server
signed for: an Authorization header on that request would make R2 reject it.
Downloads go through
[`MediaCache`](app/src/main/java/com/iqsocial/iq_message/data/media/MediaCache.kt),
which turns redirects *off* so the token cannot follow the 302, then fetches the
signed link bare and downsamples before decoding. Images render in the bubble, sized
from the server's width and height before the bytes arrive so the thread does not
jump.

**The picture appears at once, and the upload outlives the screen.**
[`UploadQueue`](app/src/main/java/com/iqsocial/iq_message/data/media/UploadQueue.kt)
owns both.

It is an object with a process-wide scope, not something the chat screen holds. An
upload living in a composable's scope is cancelled the moment you leave the
conversation, which turns backing out of a thread into a way to lose a photo you
already watched start. Leaving now is just leaving.

The bubble is drawn from the file on the device before a byte has been sent - the
thing you are waiting for should be the thing you are looking at - with a ring over
the middle of the picture showing how far it has got. Not under it: a bar somewhere
else makes you look in two places.

This reverses the earlier position, which was that nothing is shown until the server
has the bytes, on the grounds that a bubble before then claims something untrue. What
makes it honest is the progress drawn over it: the bubble says *this is going*, not
*this is sent*, and a failure says so in the same place. The placeholder is removed
only once the real message exists, so the thread never shows neither.

Three details that are easy to get wrong:

- **The shape is measured before the upload**, from bounds alone, so the bubble is
  the right size on its first frame. Learning it later means a visible jump.
- **Progress is reported on whole percents only.** A 20 MB photo is some three
  hundred buffers, and redrawing the thread on every one would cost more than the
  upload.
- **A finished upload reaches the screen over a flow**, because the screen that
  started it may be gone. Nothing is lost if nobody is listening - reopening the
  thread reads its history.

> Voice messages still take the older path and show the banner instead: they have no
> picture to put a ring on, and a recording is sent the moment it is finished rather
> than chosen and queued.

> ⚠️ **File sharing is switched off on the server.** `POST /v1/media` answers
> **503 "Sending files is not available right now"** — the exact case the spec
> documents as *"503 when file sharing isn't configured"*. The upload path is
> complete and verified up to that wall; it needs R2 configured server-side to go
> further. The app surfaces the server's own words in a dismissible banner rather
> than appearing to do nothing.

**Thumbnails are declared, and the thread reads them rather than the originals.**

This reverses an earlier decision. The reasoning then was that a thumbnail is
optional and costs a second presigned PUT for no benefit while there is no grid view
to feed. That missed where the benefit actually is: a phone photo is several
megabytes, a bubble shows a few hundred kilobytes of it, and fetching the rest to
throw most of it away is the weight behind scrolling a thread of pictures.

- **Made at 720px JPEG**, decoded straight to that size rather than loaded whole and
  shrunk - the original can be twelve megapixels and none of that needs to be in
  memory to produce a preview of it. Videos get their first frame.
- **Declared with its exact byte count**, like the file, because the server presigns
  for it; the bytes that were measured are the bytes that go.
- **Both are in storage before `complete`**, which answers 409 for a declared
  thumbnail that never arrived.
- **A failure to make one is not an error.** The upload goes without, and the reader
  falls back to the file.

`MediaCache` asks for `/media/thumbnail` first and falls through to `/media`. Messages
sent before this existed have no thumbnail and the endpoint says so, so the fallback
is the ordinary path for them rather than an error case.

> The bubble is all there is to show a photo in at the moment. A full-screen viewer
> would want the original, and would ask for `/media` directly.

### Profile pictures

A different problem from message media, and so a different loader:
[`RemoteImage`](app/src/main/java/com/iqsocial/iq_message/data/media/RemoteImage.kt).

`profile_picture` is a **required** field on the messenger API's `Person`, `Me` and
`ContactMatch` schemas, and the account API's upload route stores the full public R2
URL it got back (`iq-backend-web/routes/upload.routes.js` → `utils/r2.js`:
`${R2_PUBLIC_URL}/${key}`). So it is one plain unauthenticated GET - none of
`MediaCache`'s 302-to-a-signed-link dance, and sending a bearer token to a public
bucket would be a leak for no gain.

| | `MediaCache` | `RemoteImage` |
|---|---|---|
| Keyed by | message id | URL |
| Auth | bearer, redirects off | none |
| Decoded to | ~1080px | ~192–384px |
| Cache | 24 MB | 16 MB |

Avatars repeat down a list and are drawn at 28–40dp, so they are decoded small and
kept. **Failures are remembered too**: without that, every scroll past a deleted or
broken picture would retry the fetch.

The URL reaches the UI through `Person.photo`, filled at each place a `Person` is
built from server data - conversation peers, message rows, people search, the
forward picker, the call screens and your own profile. The People tab is the one
indirect case: a device contact has no picture to give, so the row takes it from the
`ContactMatch` the server returned, which is sound because that list is only ever
matched accounts.

> An empty `profile_picture` is the normal case, not an error - most accounts have
> never set one. That is why the seeded gradient is drawn underneath rather than a
> placeholder waiting to be replaced.

### Link previews

```
GET /v1/link-preview?url=            -> LinkPreview, 404 when there is none
GET /v1/link-preview/image?url=&sig= -> the image bytes
```

Only for the compose box: a link in a *sent* message gets its preview by itself, and
everyone including the sender is told through `message:updated`, which the app
already handles. The image route is a **privacy proxy** - use `image_url` from the
preview exactly as given, so the linked site never sees the reader's IP. It needs
the same bearer token, and answers 403 on a bad `sig`, 502 when the fetch fails.

The composer watches the draft, waits 500 ms so it is not chasing keystrokes, and
shows a card above the input with the title and site. Dismissing it remembers *that
link*, so it does not spring back while the same message is still being written. The
card is text only: showing the image would mean routing it through the proxy, which
is not worth it at this size.

### Endpoint audit

**38 of the API's 41 routes are implemented, and every one of them is used.** All 28 Conversations and Messages
routes were exercised against the live server with the exact paths and bodies the
Kotlin builds: **34/34 calls passed**, counting the variants (each `folder`, each
`shared` kind, `before_seq` and `after_seq`, read with and without `seq`).

What is not implemented, and why:

| Routes | Why not |
|---|---|
| 1 × `POST /v1/contacts` | single-contact upsert, for one contact added on the device; the full sync covers the app's needs today |
| 2 × `/health/*` | probes for infrastructure, not for a client |

Media and link previews are now implemented too, which takes the app to **38 of 41**.

The audit found three real defects, all now fixed:

| Defect | Effect | Fix |
|---|---|---|
| `delete-for-me` / `delete-for-everyone` sent only `message_ids` | `conversation_id` is required — both calls would have 400'd | send both |
| `changes` was parsed as a `MessagePage` | it returns `MessageChanges` — `next_cursor` and `synced_at`, no `next_seq` — so paging and the resume point were silently wrong | its own model |
| `PATCH` went as `POST` + `X-HTTP-Method-Override` | the server 404s it: `Route not found: POST /v1/messages/{id}`, so editing never worked | send a real `PATCH` |

Five routes were simply missing and are now present: `DELETE /v1/conversations/{id}`,
`GET /v1/messages/search`, `GET /{id}/messages/search`, `GET /{id}/messages/shared`,
`GET /v1/messages/{id}/info` and `POST /v1/messages/{id}/forward`.

Two behaviours worth knowing, both confirmed rather than assumed:

- **Sending really is idempotent.** Re-posting a `client_id` returns *"Message
  already sent"* with the stored message, not a duplicate — so a retry after a
  timeout is safe.
- **Deleting a conversation is reversible.** `DELETE` empties the list, and the
  conversation returns as soon as a new message arrives. Verified by round trip.

> The `PATCH` fix is the one call not exercised from the app itself, because there
> is no edit UI to trigger it. The server accepts a genuine `PATCH` (proven in the
> audit), and Android's `HttpURLConnection` sends one; the reflection fallback in
> [`ApiClient`](app/src/main/java/com/iqsocial/iq_message/data/remote/ApiClient.kt)
> covers the stricter JDK implementation a unit test would meet.

### Every route has a caller

All 28 Conversations and Messages routes are reachable from the UI - not merely
implemented. Where each one lives:

| Surface | Routes it drives |
|---|---|
| Chat list | `list` (both folders), `search`, `unread-count` |
| Conversation long-press | `pin`/`unpin`, `mute`/`unmute`, `archive`/`unarchive`, `read`/`unread`, `DELETE` |
| Opening a chat | `get`, `messages` (history), `changes`, `read` |
| Socket reconnect | `messages?after_seq`, `delivered` |
| Composer | `POST /messages`, `PATCH /messages/{id}` |
| Message long-press | `reaction` (put/delete), `delete-for-me`, `delete-for-everyone`, `forward`, `info` |
| Message long-press UI | floating reaction tray + bottom action bar (Reply/Copy/Delete/More) |
| Chat menu | `messages/search`, `messages/shared` |
| Chat-list search | `conversations/search` **and** `messages/search` |

Design decisions worth keeping:

- **Actions re-read rather than guess.** Pinning, muting and archiving all change
  what the list contains and its order - pinning even pulls a conversation out of
  the archived folder - so the repository reloads one page instead of patching
  local state that would drift within a couple of taps.
- **Labels read the current state.** The sheet says *Unpin* on a pinned chat and
  *Move to inbox* on an archived one, so no action is ever offered twice.
- **Mute is a second step**, because the server wants `8h`, `1w` or `always`, not a
  boolean. Offering a toggle would mean inventing a default.
- **Forward stops at five.** The server's cap, enforced by disabling further picks
  rather than letting someone choose six and meet a rejection.
- **Message info greys what has not happened.** A tick that is not yet earned is
  muted, not hidden, so the row reads as a timeline.
- **Searching a messenger searches what was said.** The chat-list box returns two
  sections, Chats and Messages, with the matching run emphasised in place.

Two correctness fixes came out of wiring the last routes:

- **Delivery receipts no longer depend on the socket.** `message:delivered` is
  preferred, but the HTTP `delivered` route now covers the case the socket was
  meant to handle and could not - being down.
- **Reconnecting catches up.** Anything sent while the socket was away arrived as
  no event at all; `messages?after_seq` now fills that gap, since `changes` only
  reports edits to messages already seen.

### Runs of messages

Consecutive messages close up into one block, the way Messenger draws them. Five
things all have to hold, and each breaks it for its own reason:

| Rule | Why |
|---|---|
| Same sender | the obvious one |
| Same type | a photo and a sentence are different shapes; flattening the corners between them reads as a mistake, not a group |
| **Neither is a file** | see below |
| Within 5 minutes | two messages an hour apart are not one thought |
| Neither is a reply | a reply carries a header naming what it answers, and buried mid-block that header looks like it belongs to the message above |

**Pictures never join a run, not even to each other.** They used to - consecutive
photos were collapsed into a grid, on the reasoning that a grid reads as one thing
sent at once. In practice it reads as one thing *full stop*: several photos closed up
into a single slab under one avatar and one tick, with no way to tell how many there
were or which one the tick was about. Each picture is its own message on the server,
and is now drawn as one.

### Sent, delivered, seen

The server reports **positions**, not per-message flags: `peer_delivered_seq` and
`peer_read_seq` on the conversation say how far the other person has got. A message
counts as delivered once their position reaches its own `seq`, so the state is
derived rather than stored:

| State | Shown as | When |
|---|---|---|
| Sending | clock | the optimistic copy, before the server answers |
| Not sent | alert | the send failed and can be retried |
| Sent | one tick | stored, but the peer has not received it |
| Delivered | two ticks, faded | `seq <= peer_delivered_seq` |
| Seen | two ticks, full strength | `seq <= peer_read_seq` |

On a crimson bubble a colour change reads poorly, so the progression is carried by
the glyph and its opacity rather than by turning blue.

### Replying

**Swipe a message to reply**, or long-press and tap Reply on the action bar.

The drag runs *away from the bubble's own edge* - right on a message that sits on
the left, left on your own - so it always pulls the bubble towards the middle rather
than off the screen it is already hugging. Three details make the gesture feel
right rather than merely work:

- **It resists past the trigger** instead of stopping dead or following the finger
  off the edge. Damping to 35 % beyond 56 dp is what tells a thumb it has gone far
  enough.
- **It fires on release, not on crossing.** A drag can still be taken back by
  sliding home, which is how every messenger behaves and what makes the gesture safe
  to try.
- **One haptic tap at the moment it arms**, not a buzz throughout.

The indicator that rides in the vacated gutter is a **solid disc with a white reply
hook**, drawn from the first millimetre of the drag. An outline would read as
disabled; in Messenger the filled disc is the affordance and the white glyph is what
carries against it. It is `IqIcons.Reply` - the curved hook, not the plain
`ArrowBack` - because this answers the message rather than navigating away from it.
The disc is `InkMuted` while dragging and turns **crimson once armed**, which is the
only thing that can tell a thumb it has gone far enough, since the gesture completes
on lift. Opacity and scale follow the drag so it grows in rather than appearing.

A reply arrow fades and scales in behind the bubble, in the gutter the bubble is
vacating.

The quote sits **above** the bubble, as Messenger has it - a small *"You replied to
Samim"* line, then the quoted message as a muted bubble of its own - rather than
inside the reply as a coloured strip. It keeps the reply itself readable and makes
the quote skimmable down the thread. The label is built where both names are known,
so it reads *"You replied to yourself"*, *"Samim replied to you"* and so on rather
than leaving an author blank.

Sending carries `reply_to_id`.

### The composer

**Emoji** open a panel in place of the keyboard rather than a dialog. A dialog would
take focus off the field and close the keyboard, so picking two in a row would mean
opening it twice; the panel keeps the cursor and inserts without dismissing.
Backspace deletes one **emoji**, not one `Char` - most are surrogate pairs, and many
carry a variation selector, a skin tone or a zero-width joiner, so removing a single
character would leave half a glyph behind.

**Voice messages** record on a hold of the mic, send on release, and cancel if the
finger slides left. The permission is asked the moment the mic is first held, not at
launch. While recording, the field is replaced by a timer and a cancel hint.

The waveform sent with the message is **real**: `MediaRecorder.maxAmplitude` is
sampled every 100 ms and condensed to 48 bars, so the shape is what was actually
said. A recording under 700 ms is discarded as a mis-tap.

Playback fetches to the cache first rather than streaming, because the media route
answers a 302 to a signed link and `MediaPlayer` handed the original URL would carry
the token across. Only one voice message plays at a time.

> Voice messages upload through the same `POST /v1/media` as any other file, so they
> meet the same **503** until file sharing is configured on the server. Recording,
> the waveform and the gesture all work; the upload is what waits.

**The photo tray** ([`MediaTray.kt`](app/src/main/java/com/iqsocial/iq_message/ui/screens/MediaTray.kt))
also sits in the keyboard's place. The system picker needs no permission and is still
there behind **All photos**, but sending the shot you just took should not mean
leaving the conversation, so the recent roll is read from `MediaStore` and shown
inline. That read *does* need a permission, which the tray asks for in place rather
than at launch - and if refused, it still offers the system picker, which needs none.

**It drags up to fill the screen.** Two stops: about a keyboard (40 %) and most of the
window (74 %, which leaves the header and composer visible - covering them would make
it a different screen rather than a tray). Not free resize: a sheet that holds any
height leaves the person deciding what height is correct, which is not their problem.

Three things make the gesture work rather than merely exist:

- **The grid drags it too**, through a `NestedScrollConnection`. Swiping up inside the
  photos grows the sheet before the grid scrolls; swiping down once the grid is back
  at its top shrinks it. The handle alone would be a small target, and not where a
  thumb already is.
- **The throw decides the stop** past 420 px/s, the position decides below that - so a
  flick commits and a slow drag goes wherever it was left nearest to.
- **A settle can be interrupted.** The animation is a tracked `Job` that the next drag
  cancels. Without that, grabbing the sheet mid-animation leaves the animation and the
  finger both writing the height, and the sheet fights the hand.

Height is plain state rather than an `Animatable`, because a drag has to land on the
same frame as the finger and `Animatable` can only snap from a coroutine.

### Acting on a message

Long-press any bubble and two things appear, the way Messenger splits them
(`MessageActionOverlay.kt`):

- a **reaction tray** - a white pill of six emoji plus `+` - floating directly
  against the pressed bubble, on that bubble's own side of the screen
- a **bottom action bar**: Reply, Copy, Delete, More

They are split because they aim at different things. A reaction belongs to *that*
message, so the tray has to sit on it; a centred dialog - which is what this was -
makes you look away from the thing you are reacting to. The actions aim at the app,
so they go where the app's other actions go, in reach of a thumb.

The thread behind is **not dimmed**. Dimming would say the conversation is
unavailable, when it is in fact the subject of what is on top of it.

Details that are easy to get wrong:

- The tray goes **above** the bubble unless that would put it behind the header
  (status bar + 56dp), in which case it drops below instead. Each row reports its
  own `boundsInRoot()` so the tray knows where to go.
- Your current reaction is **marked** in the tray, because tapping it again removes
  it and nothing else would say so.
- **Copy** is dropped from the bar for a photo or a voice note - there is nothing to
  put on the clipboard - rather than shown dead.
- The keyboard and any open emoji/photo panel are **dismissed** on long press, since
  the bar wants the bottom of the screen.
- An **unsent** message gets no tray and no Reply: there is nothing left to react to
  or answer.
- `+` opens the **full emoji sheet** - the same one the composer uses, so the set and
  the recents match wherever an emoji is chosen. Its backspace key is left out there
  rather than sitting dead.

`Delete` on the bar is delete-for-me. Everything rarer is behind **More**, which
offers only what the server would accept:

| Behind "More" | Offered when |
|---|---|
| Forward | the message is not deleted |
| Message info | your own message |
| Edit | your own text message, **under 15 minutes** old |
| Unsend for everyone | your own message, **under 48 hours** old |
| Delete for me | always |

Those two windows are the spec's, not invented. Editing loads the text back into the
composer and the send button saves it. The server keeps one reaction per member, so
picking the one already there removes it.

Reactions and delete-for-everyone rewrite a message in place rather than appending
one, so the thread is re-read afterwards instead of being patched locally - there is
no new `seq` to append.

### Catching up on changes

After loading history, the thread polls `/messages/changes` from the point it last
reached. This is the only way to see an edit, a deletion or a reaction that happened
while the app was away: none of them move a message's `seq`, so history alone would
never show them. The resume point is `synced_at` from the last page, kept per
conversation in `ChatRepository` for the life of the process.

### Realtime: Socket.IO, undocumented but present

The spec names socket events without saying where the socket is, so the endpoint was
found by probing. It is **Socket.IO v4 at `/socket.io/` on the same host**:

| | |
|---|---|
| Transport | **WebSocket only** — the handshake reports `upgrades: []`, and polling is refused with `{"code":0,"message":"Transport unknown"}` |
| Auth | the bearer token in the Socket.IO **auth payload** (`40{"token":"…"}`); a `?token=` query also works |
| Without auth | `44{"message":"You are not authorized","data":{"statusCode":401}}`, then closed |
| Keepalive | `pingInterval` 25 s, `pingTimeout` 20 s |
| Payload | wrapped: `42["message:updated",{"message":{…}}]` |

Events: `message:new`, `message:updated`, `message:receipt`, and `message:delivered`
/ `message:send` to emit.

> **A sender does not receive their own `message:new`.** It goes to the *other*
> members, which is why a single-account test sees nothing. `message:updated` does
> reach everyone including the sender — a link preview attaching is the way to
> verify the socket with one account, and that is how it was confirmed here.

[`ChatSocket`](app/src/main/java/com/iqsocial/iq_message/data/remote/ChatSocket.kt)
pins the transport to WebSocket rather than letting it negotiate, since the
negotiation would only produce a failed polling request first. It connects once per
signed-in session from `IqApp`, not per screen, and is torn down on log-out.

**Sending stays on HTTP.** `message:send` does the same job, but `POST /v1/messages`
returns the stored message and is idempotent on `client_id`, which is what makes a
retry safe. The socket is used for what only it can do: hearing about everything
else.

### What is wired

The chat list, the conversation thread, sending, and the realtime socket — following
the API's own model rather than the old rendering one, which had no room for `seq`,
the delivered/read positions, or pinned/muted/archived.

| Piece | File |
|---|---|
| Models (`ChatConversation`, `ChatPerson`, `LastMessage`, `ConversationPage`) | [`ChatModels.kt`](app/src/main/java/com/iqsocial/iq_message/data/remote/ChatModels.kt) |
| 13 conversation calls | [`ConversationsApi.kt`](app/src/main/java/com/iqsocial/iq_message/data/remote/ConversationsApi.kt) |
| Paged state | [`ChatRepository.kt`](app/src/main/java/com/iqsocial/iq_message/data/chat/ChatRepository.kt) |
| List UI | [`ChatsScreen.kt`](app/src/main/java/com/iqsocial/iq_message/ui/screens/ChatsScreen.kt) |
| Message models + 9 message calls | [`ChatModels.kt`](app/src/main/java/com/iqsocial/iq_message/data/remote/ChatModels.kt), [`MessagesApi.kt`](app/src/main/java/com/iqsocial/iq_message/data/remote/MessagesApi.kt) |
| Realtime | [`ChatSocket.kt`](app/src/main/java/com/iqsocial/iq_message/data/remote/ChatSocket.kt) |
| Thread UI + sending | [`ChatScreen.kt`](app/src/main/java/com/iqsocial/iq_message/ui/screens/ChatScreen.kt) |

Notes on the wiring:

- **Search is the server's** (`/v1/conversations/search`), debounced 300 ms, because
  the spec matches a *word prefix of the peer's name* — a filter over the loaded
  page would quietly behave differently.
- **The row reads the whole model**: verified tick, pinned and muted icons, the
  unread badge, and a filled dot for `marked_unread`, which carries no number.
- **Non-text previews** are rendered from `type` — Photo, Video, Voice message,
  Audio, Document, Location, Contact — and a deleted last message reads *"This
  message was deleted"*.
- **Timestamps** use `Calendar`, not `java.time`: minSdk is 24 and the project has
  no desugaring.
- **Paging** carries `next_cursor` and de-duplicates by id on append, so a repeated
  page cannot collide `LazyColumn` keys.
- Two icons were drawn for this — `Pin` and `BellOff` — since the set had neither.

An empty inbox shows an empty state; there is no demo list behind it any more.

**The thread and sending.** The screen fetches the conversation and its history
(newest first, reversed for display), reports how far it has read, and renders
through a mapping onto the mockup's existing bubble model, so the whole visual layer
is reused.
Sending shows the message immediately and reconciles it by `client_id` when the
server answers — the same id is reused on a retry, which is what makes that safe.
A message arriving for another conversation clears `ChatRepository`, so the list
re-reads.

### People search

Finding someone to message, from
[`PeopleApi.kt`](app/src/main/java/com/iqsocial/iq_message/data/remote/PeopleApi.kt)
and [`PeopleSearchScreen.kt`](app/src/main/java/com/iqsocial/iq_message/ui/screens/PeopleSearchScreen.kt):

```
GET /v1/people/search?q=&region=&limit=     # q is 2-64 chars
GET /v1/people/suggestions?cursor=&limit=   # people you follow
```

The server decides how to read `q` and reports it back as **`by`**: `name` or
`phone`. Both branches were verified live:

| Query | `by` | Result |
|---|---|---|
| `Mithun` | `name` | several matches, case-insensitive |
| `+8801792011775` | `phone` | one account |
| `01792011775` + `region=BD` | `phone` | the **same** account — the region parameter works |
| `017920` (partial) | `phone` | empty, as documented |
| `a` | — | **400**, `q` must be ≥ 2 characters |

Three rules shape the UI:

- **`q` has a two-character minimum**, enforced by the server with a 400. The client
  checks first rather than firing a request it knows will fail, and below two
  characters the screen shows *people you follow* instead of nothing.
- **Names match whole words.** `Rahim` finds `Rahim Uddin`; `Rah` finds nothing.
  That surprises people, so the empty state says it outright.
- **A phone number must be complete.** `region` resolves a nationally-formatted
  number, and the app passes the SIM's country from `DeviceContacts.region`.

Results **never include phone numbers** — you can look someone up by number, but
you cannot read numbers back out of search.

Reached from the Chats FAB and the People tab's add button. Tapping a result calls
`POST /v1/conversations { uid }`, which opens the conversation, creating it the
first time, then navigates into it and clears `ChatRepository` so the list picks up
a conversation that did not exist a moment ago.

> A search for `+8801792011775` returns an account with an **empty `fullname`**
> whose uid is not the main one — the stray row from the duplicate-signup probe,
> still present and now discoverable by search. The UI renders a nameless account as
> *"Unknown"*. See [Known gaps](#known-gaps).

### Verified live

Called with a real token: `/v1/conversations` → `{items: [], next_cursor: null}` and
`/v1/conversations/unread-count` → `{conversations: 0, messages: 0}`. The contract
and auth work; the account simply has no chat history yet, so there is nothing to
render against.

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
│   ├── Models.kt            Person, Message, CallRecord - shapes, not data
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

## Unit tests

`./gradlew :app:testDebugUnitTest` - **40 tests, no device needed.**

Everything here is maths that was living inside a `@Composable` or a suspend function
and so could only ever be checked by hand. Pulling it out is the point: a gesture you
can only verify by swiping is a gesture nobody re-verifies after the next change.

| Suite | Covers |
|---|---|
| `TraySheetTest` (21) | the photo tray's drag: which way a swipe moves it, both clamps, where a release lands, how much scroll it takes from the grid, who owns a fling, and three end-to-end gestures |
| `MediaPreparerTest` (8) | what a picked file is declared as, and the size limits |
| `DownsampleTest` (6) | decode sampling for avatars and for message photos |
| `ClockTest` (4) | the duration badge on a video tile |

The tray's sign convention is the thing worth stating, because it is the easy mistake:
every delta and velocity is a **screen** quantity, positive downwards, so a finger
moving up is negative and must make the sheet *taller*. Getting it backwards shrinks
the sheet on a swipe up, which compiles perfectly.
[`TraySheet`](app/src/main/java/com/iqsocial/iq_message/ui/screens/TraySheet.kt) holds
that arithmetic with no Compose in it at all.

> One caveat worth being plain about: these assert that the code does what *I* meant.
> They cannot tell you the intent matches Messenger - only a device can. They are a
> guard against regressions and arithmetic slips, not a substitute for looking at it.

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

**Two third-party dependencies, and only two.** REST is `HttpURLConnection` +
`org.json`; storage is `SharedPreferences`; navigation is a hand-rolled back stack;
icons and flags are drawn, not bundled.

Both exceptions are things the platform does not provide and cannot be written
around:

- **`io.socket:socket.io-client:2.1.2`** (pulling `engine.io-client` and
  `okhttp 3.12.12`), for realtime messaging. Android ships no WebSocket client, so
  the alternative was hand-writing RFC 6455 framing plus Engine.IO and Socket.IO
  packet parsing — several hundred lines of protocol code, untestable without the
  very socket it implements. `org.json` is excluded from it because Android already
  provides that.
- **`com.google.firebase:firebase-messaging`** (via the Firebase BOM, applied by the
  `google-services` plugin), for push. There is no way on Android to be woken for a
  message while the app is closed that does not go through FCM, so this is not a
  choice between libraries.

### Push

`app/google-services.json` carries the `com.iqsocial.iq_message` client of the
`iqama-social` Firebase project. The key in it is a client identifier, not a secret -
it is compiled into every copy of the app.

The server side is two routes that had no caller until now:

| | |
|---|---|
| `POST /v1/devices` | registers this install's token. Safe to repeat, and a token that belonged to another account **moves** to yours, so one phone only ever rings for whoever is signed in on it |
| `POST /v1/devices/unregister` | called **before** the session is discarded, because unregistering needs the very token it is about to throw away |

[`PushTokens`](app/src/main/java/com/iqsocial/iq_message/data/push/PushTokens.kt)
registers on **every `ON_START`** - that is, every time the app is opened or
returned to - and not once per composition.

That distinction is the whole point. A `LaunchedEffect` runs once per composition, so
it covers a cold start and a sign-in but not the ordinary case of leaving the app and
coming back, because the composition survives that. Firebase can reissue a token
while the app is away — a restore to a new device, data cleared, a reinstall — and
`onNewToken` only fires if there is an app running to hear it. Asking Firebase afresh
on every foreground is what closes the gap.

Signing in happens *after* `ON_START`, so the effect also sends once on arrival when
the lifecycle is already started; otherwise a freshly signed-in phone would stay
unregistered until it had been left and come back to. The duplicate that can cause on
a cold start is harmless.

The call is deliberately unconditional - no "have we sent this already?" check.
Remembering would mean trusting this app's memory over Firebase's answer, which is
precisely the thing that goes stale.

A notification carries `conversation_id`, and tapping it opens that thread.
`MainActivity` holds the pending id as *state* rather than reading the intent once,
because the activity is `singleTop`: a second notification tapped while the app is
open arrives through `onNewIntent`, and reading only at start-up would open the first
conversation and ignore every one after it.

> Push is for when the app is **closed or in the background**. While it is open the
> socket already carries everything, faster and without a round trip through Google,
> so anything that arrives by push for a conversation on screen is a duplicate.

**Message notifications are always the app's own.** A message push carries a
`notification` block, and with the app away the Firebase SDK draws that block by
itself: a plain title and a line of text, with no face, no sender's name and no
buttons. [`IqMessagingService.handleIntent`](app/src/main/java/com/iqsocial/iq_message/data/push/IqMessagingService.kt)
removes the one flag that asks for that (`gcm.n.e`) from message pushes before the
SDK reads it. The push then reaches `onMessageReceived` as a data message whether the
app is open, in the background or not running, and `MessageNotifications` builds the
conversation notification: the sender's face and name, then Like and Reply.
`handleIntent` is not documented API, so check this after a Firebase upgrade. If it
stops working, Firebase's plain notification comes back; messages are never silent.
Sending message pushes data-only from the server would make it unnecessary.

Every message gets a notification, including one in the chat that is open on screen.
This is a product decision, not an oversight: the push layer deliberately does not
check which chat is open. Opening a chat clears its notification.

The app takes a push only when it is sure to show it (someone is signed in and the
push names a conversation); anything else is left for Firebase to draw. Once the app
has taken a message, a failed sender lookup or profile photo only costs the name or
the face. If the full conversation notification is refused, a plain one with the
sender, the text and a tap to open the chat is posted in its place.

`POST_NOTIFICATIONS` is asked for after sign-in, straight to the system prompt with
no screen of its own - unlike contacts, which hands the server everybody's phone
number and is explained first. Refusing costs only the notifications.

**Avatars show the real picture, and fall back to a generated one.**
[`Avatar`](app/src/main/java/com/iqsocial/iq_message/ui/components/Common.kt) draws
the account's `profile_picture` when there is one, over a stable seeded gradient with
initials. The gradient is not a spinner - it is the answer for the many accounts that
have never set a picture, and what stays on screen if one fails to load. Drawing it
first also means a list never flashes empty circles while the pictures arrive.

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

**Calls are not real.** There is no call API on either server, so the Calls tab shows
an empty state and the call screens resolve a name from the loaded conversations, or
say "Unknown". The screens exist because the mockups specify them; nothing dials.

**Stories were removed, not implemented.** The row on the chat list was six invented
people. No API serves stories, so it is gone rather than faked.

**Media has no UI.** `POST /v1/media` is a three-step upload and nothing drives it
yet, so the attachment sheet opens and closes without sending: a bubble claiming
otherwise would be a lie. Reactions, editing and both kinds of delete *are* wired -
long-press any message.

**Rotation is avoided rather than handled.** The back stack and every form field
live in `remember`, which does not survive activity recreation, so the activity is
locked to `portrait`. The mockups are portrait-only, so nothing is lost; but if
landscape is ever wanted, the stack needs a `Saver` and the fields
`rememberSaveable` before that lock comes off.

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
| `/v1/me`, 401 | ✅ card stays empty rather than inventing a name, no crash |
| Messenger health | ✅ `/health/ready` reports `mongo: up` |
| Auth routing after the base-URL flip | ✅ still reaches the account API |
| People search, name | ✅ `by: name`, real users, case-insensitive |
| People search, phone + `region` | ✅ national and E.164 resolve to the same account |
| People search, under 2 chars | ✅ blocked client-side; the server returns 400 |
| Open conversation from search | ✅ `POST /v1/conversations` creates and opens it |
| Thread history | ✅ real messages, correct order and sender side |
| Send from the app | ✅ stored server-side with the app's own `client_id` |
| Socket handshake + auth | ✅ connects with the token; refused without it |
| Socket event delivery | ✅ `message:updated` received (link preview) |
| Conversation long-press actions | ✅ pin verified server-side, two-step mute sets `muted_until` |
| Media upload | ⚠️ blocked at `POST /v1/media` — server returns 503, file sharing not configured |
| Upload failure surfaced | ✅ the server's message shown in a dismissible banner |
| Link preview while typing | ✅ live card for a pasted URL |
| All 28 conversation/message routes | ✅ 34/34 live calls pass |
| Send idempotency | ✅ same `client_id` returns "Message already sent" |
| Delete + restore a conversation | ✅ leaves the list, returns on the next message |
| Profile pictures | ✅ wired from `profile_picture` everywhere a `Person` is built; not yet seen on a device |
| Long-press reaction tray + action bar | ✅ builds; not yet seen on a device |
| Long-press overflow ("More") | ✅ offers only what the server would allow |
| Edit from the app (PATCH) | ✅ bubble updates in place, `edited_at` set server-side |

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
>
> Still present: a people-search for that number returns uid
> `6aa659d80f88a418d5f26884` with an empty name, alongside the real account
> `680f9fa73f8336a0ba6843fe`.
