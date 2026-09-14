# LinkUp API — Security & Feature Changes

No Maven/network access in my sandbox, so I couldn't run an actual build —
I reviewed every changed file by hand for compile-correctness, but **run
a build yourself before deploying** (`./mvnw clean compile`).

## Critical fixes (things that were actually exploitable)

| Issue | Fix |
|---|---|
| Anyone could send chat messages as any username | `ChatController.sendMessage` now takes the sender from the authenticated STOMP `Principal`, never the client-supplied `sender` field |
| `/chat/**` and `/api/v1/rooms/**` were `permitAll` — no auth needed to connect to the socket, create/join rooms, or read any room's full message history | Room endpoints now require a JWT. The WebSocket *handshake* stays `permitAll` (SockJS can't attach a Bearer header to it), but a new `StompAuthChannelInterceptor` authenticates the actual STOMP `CONNECT` frame and rejects it if the JWT is missing/invalid/stale |
| IDOR: any logged-in user could overwrite anyone's GPS location or query anyone's nearby list by editing the URL | `LocationController` now checks the path variable against the authenticated principal and returns 403 on mismatch |
| JWT secret hardcoded in source, committed to git | Moved to `application.properties` → `jwt.secret`, overridable via `JWT_SECRET` env var. **Generate a real one**: `openssl rand -base64 64` |
| A banned/deleted user's token stayed valid until natural expiry | Added a `tokenVersion` claim to the JWT; bumped on delete-account. Checked on every request (`JWTService.validateToken`) and on every WebSocket connect |
| Malformed/expired token → raw 500 | `JWTService.extractUsername` now returns `null` instead of throwing; filter treats that as "not authenticated" → clean 401 |
| Ban/lock/active flags existed on the entity but did nothing | `UserPrinciples.isEnabled()`/`isAccountNonLocked()` now actually check them, so Spring Security itself rejects banned/deleted/locked accounts at login |
| `GET /takeid/{id}` — any authenticated user could fetch anyone's full profile by walking IDs | Removed from meaningful use; added `GET /api/user/me` (and `/details` as an alias for your current frontend) which always returns the caller's own profile |
| `NearbyPersonResponse` leaked raw sequential DB IDs and hardcoded `age`/`profilePhoto`/`online`/`verified` to `null` even though the data now exists | Switched to `publicId` (UUID), and it's now populated with real data |
| Wildcard `@CrossOrigin(origins = "*")` on `LocationController` | Removed — it now goes through the same configurable CORS origin list as everything else |
| Unhandled exceptions could leak stack traces; validation errors didn't match the `{status, message, data}` shape used everywhere else | `GlobalExceptionHandler` now has a generic fallback and formats `@Valid` failures consistently |

## New capabilities

- **Login accepts username OR email** (same request field, resolved server-side)
- **Brute-force lockout**: 5 failed attempts → 15-minute lock, tracked per-account
- **Real presence**: `WebSocketPresenceListener` sets `online`/`lastSeenAt` from actual socket connect/disconnect events and broadcasts to `/topic/presence` — matches what the frontend's `useRealtimeConnection` hook already expects
- **Soft-delete account** (`DELETE /api/user/me`): anonymizes username/email (frees them for reuse), strips PII, invalidates all outstanding tokens — keeps the row so existing chat history isn't orphaned
- **`PATCH /api/user/me`**: profile-completion endpoint your onboarding wizard can call once the user is logged in
- **`PATCH /api/user/status`** and **`POST /api/user/logout`**
- **`geohash`** column, computed on every location update — a real path off the current full-table-scan "nearby" query, whenever you're ready to switch to a prefix-range query

## User entity — every new field

Profile: `dob`, `gender`, `photos[]`, `interests[]`, `onboardingCompleted`
Dating prefs: `lookingFor`, `genderPreference[]`, `minAgePreference`, `maxAgePreference`, `maxDistanceKm`
Presence: `online` (driven by sockets now, not trusted REST calls), `lastSeenAt`
Trust & safety: `isBanned`, `bannedUntil`, `isBlocked`, `reportCount`
Lifecycle: `isActive`, `isDeleted`, `deletedAt`
Security: `lastLoginAt`, `failedLoginAttempts`, `lockedUntil`, `tokenVersion`
Device: `fcmToken`, `platform`
Identity: `publicId` (UUID — use this in URLs/DTOs, never the raw `id`)

## What YOU need to do

1. **Set `JWT_SECRET`** as an env var before running (there's a dev-only fallback in `application.properties`, don't ship it).
2. **`spring.jpa.hibernate.ddl-auto=update`** will add all the new columns/tables automatically on next boot against your Postgres DB — back it up first if it has real data, and consider a proper migration tool (Flyway/Liquibase) going forward instead of `update`.
3. **Frontend `api.config.ts`** — I added `/me` but kept `/details` as an alias so your current frontend keeps working. Whenever convenient, add `logout`, `deleteAccount`, and `updateStatus` to point at `/api/user/logout`, `/api/user/me` (DELETE), `/api/user/status` — they already exist server-side, the frontend's `userService.ts` from earlier in this project already expected exactly these.
4. **CORS**: if/when you test on a real device or emulator, add its origin to `cors.allowed-origins` (comma-separated) — same property drives both REST CORS and the WebSocket handshake now.
5. Run `./mvnw clean compile` yourself — I hand-reviewed every file for correctness but couldn't execute a real build in this sandbox (no network to pull dependencies).

---

## Round 2 — register-token, email OTP, photo upload, username check

### Register now returns a token
`POST /register`'s response shape changed:
```json
// before: { status, message, data: UserDTO }
// now:    { status, message, data: { user: UserDTO, token: "<jwt>" } }
```
`/login` is untouched — still returns a bare token string. **Frontend `AuthContext.register()` needs updating** to read `data.token`/`data.user` instead of just `data`, and to store the token the same way `login()` does.

### `verified` split into `emailVerified` / `phoneVerified`
Was one boolean, now two — `phoneVerified` isn't wired to anything yet (phone/OTP is deferred), but the shape is settled now so it doesn't need touching twice. **Frontend `User` model and anywhere reading `user.verified` needs updating** to `user.emailVerified`.

### Email OTP verification — new endpoints (both authenticated)
- `POST /api/auth/email/send-code` — no body, sends to whichever email the token belongs to
- `POST /api/auth/email/verify-code` — body: `{ "code": "123456" }`
- 60s resend cooldown, 30min expiry, 5 wrong-attempt cap — same pattern as the login lockout
- **Needs real SMTP credentials** to actually send anything — `application.properties` has `SMTP_HOST`/`SMTP_USERNAME`/`SMTP_PASSWORD` env vars wired up but pointed at a placeholder. Pick a provider (AWS SES is cheapest at scale, Resend/SendGrid are faster to set up) and plug in real credentials before testing this end-to-end.

### Photo upload — new endpoints (both authenticated)
- `POST /api/user/me/photos` — multipart `file` field, max 5MB, jpeg/png/webp only. Appends to the user's `photos[]` server-side and returns the updated profile — no separate `PATCH /me` needed after each upload.
- `DELETE /api/user/me/photos?url=...` — removes one photo, re-picks `profilePhoto` if the deleted one was it
- Files land in `uploads/photos/` on disk (configurable via `UPLOAD_DIR`) and are served publicly at `/uploads/photos/**` (has to be public — `<img>` tags can't send an Authorization header). **This is local disk storage, not cloud** — fine for dev, but files won't survive a redeploy; swap `PhotoStorageService`'s internals for S3/Cloudinary when you're ready, nothing else needs to change.
- Cap is 6 photos per user, matching `UpdateProfileDTO`'s validation.

### Username availability — new public endpoint
`GET /api/user/check-username?username=ganesh1234` → `{ available: false, suggestions: ["ganesh_1234", ...] }`. Suggestions are generated and verified against the DB server-side, not guessed client-side. Rate-limited to 20 requests/minute per IP (in-memory — if this ever runs across multiple backend instances, that limiter needs to move to Redis, since each instance would otherwise allow its own 20/min).

### What's still NOT done from what we discussed
- `onboardingCompleted` route-guard redirect — that's frontend-only, nothing to add here
- Location capture during onboarding's permissions step — `POST /users/{username}/location` already existed and needs no backend change, just needs to be called from the new onboarding flow
- Phone/OTP — deferred, `phoneNumber`/`phoneVerified` columns exist but nothing uses them yet
- The likes/connections/posts/calling plan — parked, not started

