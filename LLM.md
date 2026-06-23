# LLM.md — lux Android wallet (Kotlin / Gradle)

Guidance for AI agents. Fork lineage: Unstoppable Wallet (Horizontal Systems).

## Build

- Gradle (Kotlin DSL). Wrapper Gradle **9.3.1**, AGP **9.1.1**, Kotlin
  **2.3.20**, JVM **17**, compileSdk **36**, minSdk **28**. Version catalog
  `gradle/libs.versions.toml`.
- App module `app/`, `applicationId = network.lux.wallet`, versionName 0.49.0.
- Flavors (dim `distribution`): `base` (Play), `fdroid`, `fdroidCi`, `ci`
  (AppCenter). Build types: `debug` (`.dev`, test-signed), `release`
  (**no signingConfig → unsigned**).
- `./gradlew :app:bundleBaseRelease` → unsigned `.aab` in
  `app/build/outputs/bundle/baseRelease/`. `:app:assembleBaseRelease` → `.apk`.

## Native release + signing (`.github/workflows/release.yml`)

Runs on the **arcd self-hosted fleet** (`luxfi-linux-amd64`;
`.github/actionlint.yaml` registers the pools). **No GitHub-hosted runners.**
Triggered by `workflow_dispatch` (brand = all|lux|hanzo|zoo, `upload-play`
toggle) or a `v*` tag.

Flow: `build` (per brand: `:app:bundleBaseRelease`, unsigned `.aab`, brand
passed as `-P` props) → `sign-android`
(`hanzoai/ci-signing/.github/workflows/sign-android.yml@v1`, `secrets:
inherit`) → `publish-release` (signed `.aab` to a GitHub Release on a tag).

`sign-android.yml` inputs: `artifact-name`, `file-glob: '*.aab'`,
`upload-play` (internal track), `package-name` (`network.<brand>.wallet`),
`track: internal`, `runs-on: '["luxfi-linux-amd64"]'`. It jarsigns the `.aab`
with the org **upload** keystore (Play App Signing re-signs with the app key
Google holds) and optionally uploads to Play. The release buildType is
deliberately unsigned so ci-signing owns the upload key — never re-add a
`signingConfig` to `release`.

## Per-brand baking (lux | hanzo | zoo)

The build runs `@luxwallet/brand`'s `emit-brand <brand>` → `BRAND_*` env
(default EVM chain resolved via `@luxwallet/chains`). `app/build.gradle.kts`
reads Gradle props (unset = the Lux default, so local
`./gradlew assembleRelease` is unchanged):
- `-PluxwalletGatewayRpcBaseUrl` → `BuildConfig.GATEWAY_RPC_BASE_URL`
  (existing brand-agnostic gateway hook; routes `<base>/v1/rpc/<chainId>`).
- `-PluxwalletDefaultEvmChainId` → `BuildConfig.DEFAULT_EVM_CHAIN_ID`.
- `-PluxwalletBrandName` → the WalletConnect app-metadata name resValue.

`@luxwallet/brand` is the ONE source of brand truth.

Logo / app label: per-brand `mipmap` launcher icons + `App_Name` string for
hanzo/zoo are the remaining asset wiring (lux ships today). Non-lux
`applicationId`s need their own Play app records before `upload-play`.

## Legacy fork-CI purge (arcd-only)

The Unstoppable-fork CI used forbidden GitHub-hosted runners and is fully
superseded by `release.yml`. DELETED: `build_apk.yml` (duplicated the tag
build+GitHub-Release publish), `deploy_dev.yml` + `deploy_release.yml`
(Firebase distribution to the upstream's `horsysteam` group + hardcoded
upstream Firebase appIds), `notify_telegram.yml` + `translate.yml` (telegram /
crowdin fork noise). None retargeted — all were upstream-only plumbing.

## Rules

1. Update THIS file; never create scratch summary files.
2. arcd pools only — never `ubuntu-*` (the legacy fork workflows that used
   `ubuntu-*` were purged; `release.yml` is the one build+sign path).
3. Signing via `hanzoai/ci-signing`; never put a keystore/secret in the repo
   (the `test.keystore` is for CI test flavors only — never for `release`).
4. Brand selection via `@luxwallet/brand` only.
