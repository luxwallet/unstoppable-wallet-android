# Brownfield React Native host (additive @hanzo/gui screens)

This package embeds the shared `@luxwallet/mobile-rn` bundle (the `@hanzo/gui`
login + future chrome) into the native Android wallet **additively** — existing
Kotlin screens are untouched.

## Kotlin pieces (in this dir)

| File | Role |
|------|------|
| `LuxReactHost.kt` | Holds the `ReactHost` (composition — App extends CoreApp, can't extend ReactApplication). `App.onCreate()` calls `LuxReactHost.init(this)`. |
| `LoginRNActivity.kt` | Hosts the registered `"LuxLogin"` RN component via `ReactDelegate`. |
| `LuxSessionModule.kt` / `LuxSessionPackage.kt` | Native↔RN bridge — JS `NativeModules.LuxSession.setSession(...)` persists into `App.oidcAuthManager` (existing encrypted store). |

`App.kt` (one line in onCreate) + `AndroidManifest.xml` (`LoginRNActivity` entry)
+ `core/managers/OidcAuthManager.kt` (one public `setSession`) are the only
edits outside this package.

## Launch point (add where you want the button)

From any native screen — e.g. the login or settings flow:

```kotlin
startActivity(Intent(this, io.horizontalsystems.bankwallet.rn.LoginRNActivity::class.java))
```

## CI / build wiring (NOT applied here — needs the RN Gradle plugin)

The Kotlin + manifest are complete, but the app module must opt into React
Native at build time. CI (never a dev machine) must:

1. **Install the JS bundle deps** in the shared bundle:
   ```bash
   cd ../../luxwallet/mobile-rn && npm install   # or pnpm, resolving the local @luxwallet/* + @hanzo/gui
   ```
2. **settings.gradle.kts** — add the RN plugin to `pluginManagement.repositories`
   (google(), mavenCentral(), and `maven { url = uri("$rootDir/../node_modules/react-native/android") }`),
   and `includeBuild("../../luxwallet/mobile-rn/node_modules/@react-native/gradle-plugin")`.
3. **app/build.gradle.kts** — apply `id("com.facebook.react")`, add a `react { ... }`
   block (`root = file("../../luxwallet/mobile-rn")`, `entryFile = file("index.js")`,
   `hermesEnabled = true`, `bundleAssetName = "index.android.bundle"`), and add deps:
   `implementation("com.facebook.react:react-android")`,
   `implementation("com.facebook.react:hermes-android")`.
   Autolinking pulls reanimated / screens / svg / safe-area-context.
4. Build as usual; the `:app:bundleReleaseJsAndAssets` task bakes the JS bundle
   into the APK. New-arch + Hermes are enabled in `LuxReactHost`.

See `~/work/luxwallet/mobile-rn/LLM.md` for the JS side.
TODO: switch `@luxwallet/*` from local paths to published npm versions, then the
`react { root }` can point at a normal node_modules install.
