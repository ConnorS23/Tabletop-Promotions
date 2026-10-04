# Tabletop Promotions

Restaurant table-display platform:

- `android/` — Android 11+ (minSdk 30) kiosk app (Kotlin + Jetpack Compose). Ad carousel → Home (Ads / Menu / Games / PIN-locked Settings), PDF menu with scroll + zoom, Tic-Tac-Toe, QR pairing.
- `web/` — Next.js manager website + super-admin console.
- `supabase/` — Postgres schema, RLS, storage bucket and RPCs (`supabase/migrations`).

## How pairing works

1. On first launch the tablet generates a random device ID + secret (stored locally), calls `register_device`, and shows a QR code + 6-character pair code.
2. A manager scans the QR (opens `/link?code=XXXXXX`) or types the code on the website and picks a restaurant (`link_device`).
3. The tablet polls `device_sync` (every 3s while unlinked, 30s when linked) and starts showing that restaurant's ads immediately. Ads and the menu are cached for offline use.

## Roles

- **manager** — manages restaurants they are a member of (ads, menu, displays, settings, other managers).
- **super_admin** — manages every restaurant, device and user, plus the platform master PIN that unlocks settings on any tablet.

Promote the first super admin in the Supabase SQL editor:

```sql
update public.profiles set role = 'super_admin' where email = 'you@example.com';
```

## Local development

Requirements: Docker, Node 20+, JDK 17, Android SDK (platform 34).

```bash
# Supabase (prints API URL + anon key)
npx supabase start          # applies supabase/migrations

# Website
cd web
cp .env.example .env.local  # fill NEXT_PUBLIC_SUPABASE_URL / NEXT_PUBLIC_SUPABASE_ANON_KEY
npm install
npm run dev                 # http://localhost:3000

# Android
cd android
cat > local.properties <<PROPS
sdk.dir=/path/to/android-sdk
SUPABASE_URL=http://10.0.2.2:54321
SUPABASE_ANON_KEY=<anon key>
LINK_BASE_URL=http://10.0.2.2:3000
PROPS
./gradlew assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
```

## Production

1. Create a Supabase project and run the migration (`npx supabase link --project-ref <ref> && npx supabase db push`).
2. Deploy `web/` (e.g. Vercel) with `NEXT_PUBLIC_SUPABASE_URL` and `NEXT_PUBLIC_SUPABASE_ANON_KEY`. Set the Supabase Auth site URL to the deployed URL.
3. Build the APK with production `SUPABASE_URL`, `SUPABASE_ANON_KEY` and `LINK_BASE_URL` (website URL) in `local.properties` or env vars.
4. **Change the default PINs**: restaurant settings PIN defaults to `1234`, platform master PIN to `246810` (Admin → Platform).

## Kiosk mode

On a factory-reset tablet (no accounts added), install the APK then:

```bash
adb shell dpm set-device-owner com.tableads.app/.kiosk.AdminReceiver
```

The app then pins itself (no status bar / home / recents) and acts as the launcher. Without device-owner it still runs fullscreen but users can leave via system gestures. Exit kiosk from Settings (PIN).

## Adding a game

Add an entry to `GAMES` and a branch in `GameHost` in `android/app/src/main/java/com/tableads/app/ui/games/GamesScreen.kt`.
