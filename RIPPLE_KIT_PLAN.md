# Ripple (XRP) Integration Plan

Status: PLAN ONLY. No crypto kit authored. This document is the precise integration
map for adding XRP/XRPL support to Lux Wallet (forked Unstoppable Wallet, Kotlin).

Line numbers are accurate at time of writing (after the inert XRP markers were
added) and will drift as the tree changes — treat them as approximate-at-time-of-writing
and re-grep before editing. Anchors below cite stable code, not just numbers.

---

## 1. Gating dependency — MarketKit `BlockchainType.ripple`

The whole integration is gated on `io.horizontalsystems.marketkit.models.BlockchainType`
exposing a `Ripple` case (with the matching coin/token/blockchain records served by
the MarketKit backend).

- `BlockchainType` is a **binary dependency** (`com.github.horizontalsystems:market-kit-android`,
  pinned in `gradle/libs.versions.toml` -> `marketKit = "3bfaaae"`, library alias
  `kit-market` at `gradle/libs.versions.toml:235`). Its case set cannot be grepped from
  this repo, so the existence of `BlockchainType.Ripple` / token-type whitelisting for XRP
  **MUST be confirmed at build time**. This is the single gating item.
- Until MarketKit ships `Ripple`, **no `BlockchainType.Ripple ->` arm will compile**. That is
  why every plug-point below is described as an arm to ADD, and why the in-tree markers
  (section 5) are COMMENTS, not code.
- Required fork: **`luxwallet/market-kit-android`** — add/whitelist the `Ripple` case in
  `BlockchainType`, add XRP to the blockchain/coin/token-type tables, publish, and bump
  `marketKit` in `gradle/libs.versions.toml`. (Mirror the existing `Tron`/`Stellar`
  treatment in that fork.)

## 2. New library to author — `luxwallet/ripple-kit-android`

A new Kotlin library wrapping an XRPL client (e.g. an XRPL4J-style JVM library, or a Kotlin
XRP lib) and exposing a Horizontal-Systems-shaped surface so it drops into the existing
manager/adapter machinery. It must provide:

- **`RippleKit`** — account state, balance, send/sign, transaction history, sync lifecycle
  (mirrors `TronKit` / `StellarKit`). Address model + validation, XRP drops<->XRP scaling,
  destination tag support.
- **`RippleKitWrapper`** — the per-account handle the app caches (mirror `TronKitWrapper`,
  returned by `TronKitManager.getTronKitWrapper(account)`).
- **`RippleKitManager`** (lives in THIS app, `core/managers/`, not in the kit) — caches the
  active `RippleKitWrapper` keyed by account, started/stopped from `App`, `unlink(account)`
  to tear down (mirror `TronKitManager` at
  `app/src/main/java/io/horizontalsystems/bankwallet/core/managers/TronKitManager.kt`).
- **`RippleAccountManager`** (THIS app, `core/managers/`) — watches `accountManager` +
  `walletManager`, auto-enables the native XRP token on accounts, `.start()`ed from `App`
  (mirror `TronAccountManager`, constructed at `App.kt:377`).
- **`RippleAdapter`** (THIS app, `core/adapters/`) — implements the app's `IAdapter` /
  balance / send / receive / `ITransactionsAdapter` contracts over `RippleKitWrapper`
  (mirror `TronAdapter` + `TronTransactionsAdapter`).
- **RestoreSettings**: XRP is account-based with no "birthday"/checkpoint, so it needs **no**
  `BirthdayHeight` restore setting (see section 4). No `RestoreSettingsManager` change is
  expected.

## 3. Exact plug points in this repo

### 3a. `app/src/main/java/io/horizontalsystems/bankwallet/core/factories/AdapterFactory.kt`
- `getAdapter(wallet)` — `TokenType.Native -> when (wallet.token.blockchainType)` branch
  opens at **`AdapterFactory.kt:145`**. Add a new arm:
  ```kotlin
  BlockchainType.Ripple -> RippleAdapter(rippleKitManager.getRippleKitWrapper(wallet.account))
  ```
  placed before the Native branch `else -> null` (the inert marker is at **`AdapterFactory.kt:199`**).
- Constructor: add `private val rippleKitManager: RippleKitManager` to `AdapterFactory(...)`
  (ctor ends at `AdapterFactory.kt:72`) and pass it from `App.kt` where `AdapterFactory(...)`
  is built (`App.kt` ~lines 401-418).
- `unlinkAdapter(wallet: Wallet)` — **`AdapterFactory.kt:286`** — add
  `BlockchainType.Ripple -> rippleKitManager.unlink(wallet.account)`.
- `unlinkAdapter(transactionSource: TransactionSource)` — **`AdapterFactory.kt:317`** — same arm
  for the `TransactionSource` overload.
- If XRP carries issued (non-native) assets, also add a `ripple*TransactionsAdapter(source)`
  factory method mirroring `tronTransactionsAdapter` (`AdapterFactory.kt:238`); native-only XRP
  does not require it.

### 3b. `app/src/main/java/io/horizontalsystems/bankwallet/core/App.kt`
- Construct the kit manager next to the other non-EVM kit managers
  (`tronKitManager`/`tonKitManager`/`stellarKitManager` at **`App.kt:329-331`**; inert marker at
  **`App.kt:332`**):
  ```kotlin
  rippleKitManager = RippleKitManager(backgroundManager)
  ```
  Add the matching `lateinit var rippleKitManager: RippleKitManager` to the companion (near
  `tronKitManager` at `App.kt:186`).
- Construct + start `RippleAccountManager` next to `TronAccountManager`
  (`val tronAccountManager = TronAccountManager(...)` at **`App.kt:377`**, `.start()` right after):
  ```kotlin
  val rippleAccountManager = RippleAccountManager(accountManager, walletManager, marketKit, rippleKitManager, tokenAutoEnableManager)
  rippleAccountManager.start()
  ```
- Pass `rippleKitManager` into `AdapterFactory(...)` (`App.kt` ~401-418) and, if XRP needs
  start/stop on wallet sync, thread it through `AdapterManager(...)` (`App.kt` ~419-427) and
  `walletManager.start(...)` (`App.kt:635`) the same way `tronKitManager` is threaded.

### 3c. `app/src/main/java/io/horizontalsystems/bankwallet/core/managers/RestoreSettingsManager.kt`
- `getSettingValueForCreatedAccount(...)` — **`RestoreSettingsManager.kt:62`**, `BirthdayHeight`
  switch at **`RestoreSettingsManager.kt:64`**. XRP has **no birthday height**, so it falls through
  the existing `else -> null` — **no change required**. Documented here so the reviewer does not
  add a spurious arm. (Same for `getSettingsTitle` at `RestoreSettingsManager.kt:84`.)

### 3d. `app/build.gradle.kts`
- Add to the "Wallet Kits" block (`// Wallet Kits` at **`app/build.gradle.kts:333`**, alongside
  `implementation(libs.kit.tron)` at **`app/build.gradle.kts:343`**):
  ```kotlin
  implementation(libs.kit.ripple)
  ```

### 3e. `gradle/libs.versions.toml`
- Version pin: under `# Wallet Kits` (**`gradle/libs.versions.toml:71`**), next to
  `tronKit = "2f07e86"` (**line 81**), add e.g.:
  ```toml
  rippleKit = "<commit-or-tag>"
  ```
- Library alias: under the second `# Wallet Kits` (**`gradle/libs.versions.toml:227`**), next to
  `kit-tron` (**line 237**), add:
  ```toml
  kit-ripple = { module = "com.github.luxwallet:ripple-kit-android", version.ref = "rippleKit" }
  ```
  (Use the `luxwallet` Maven/JitPack group for the new fork; the upstream `horizontalsystems`
  group has no ripple-kit.)

## 4. RestoreSettings note (explicit)

XRP accounts are derived from the seed/mnemonic and have no scan checkpoint, so there is **no**
`RestoreSettingType` to add and **no** `RestoreSettingsManager` arm. Confirm at integration time
that XRP wallets create cleanly with an empty `RestoreSettings` (the same path Tron/Ton/Stellar use).

## 5. In-tree inert markers already added

Single-line, clearly labeled `XRP: pending ripple-kit`, compile-safe (comments only):
- `app/src/main/java/io/horizontalsystems/bankwallet/core/factories/AdapterFactory.kt:199`
- `app/src/main/java/io/horizontalsystems/bankwallet/core/App.kt:332`

## 6. Load-bearing per-chain `when` switches needing a `Ripple` arm

`BlockchainType.Tron` is referenced across ~50 files in `app/src/main/java`; that grep
(`grep -rln "BlockchainType.Tron" app/src/main/java`) is the proxy for the full switch set.
Not all 50 are required for first-class send/receive/restore. The load-bearing ones:

| Concern | File | Anchor |
|---|---|---|
| Adapter dispatch (balance/send/tx) | `core/factories/AdapterFactory.kt` | Native branch `:145`, unlink `:286` / `:317` |
| Kit + account lifecycle | `core/App.kt` | kit mgr `:329`, account mgr `:377` |
| Address validation | `core/factories/AddressValidatorFactory.kt` | per-chain `when` (`BlockchainType.*` arms) |
| URI scheme / address parsing | `core/factories/AddressParserFactory.kt` | `BlockchainType.uriScheme` (`:6`) + parser arms |
| Send flow service | `modules/multiswap/sendtransaction/SendTransactionServiceFactory.kt` | per-chain `when` (`:14`+) |
| Receive screen | `modules/receive/ReceiveFragment.kt` | per-chain `when` (`:39`+) |
| Coin/blockchain metadata (icons, token-type label, descriptions) | `core/MarketKitExtensions.kt` | `BlockchainType.*` arms (`:77`, `:88`, `:132`, `:169`, `:203`) |

EVM-only managers do **not** need an arm: `EvmBlockchainManager`, `EvmSyncSourceManager`,
`EvmAccountManagerFactory` are EVM-scoped and XRP is not EVM — N/A.

Restore/coin-settings screens (e.g. `modules/blockchainsettings/BlockchainSettingsService.kt`,
`modules/syncerror/SyncErrorService.kt`) branch per chain for display; add a `Ripple` arm only
if XRP exposes a configurable node/sync source (XRPL public clusters likely make this optional).

## 7. Recommended sequence

1. Land `luxwallet/market-kit-android` with `BlockchainType.Ripple` + XRP tables; bump
   `marketKit` in `gradle/libs.versions.toml`. (Gating — nothing compiles before this.)
2. Author + publish `luxwallet/ripple-kit-android` (`RippleKit` + `RippleKitWrapper`).
3. Add `kit-ripple` to `gradle/libs.versions.toml` + `app/build.gradle.kts`.
4. Add `RippleKitManager`, `RippleAccountManager`, `RippleAdapter` (+ tx adapter if needed) in-app.
5. Replace the two inert markers (section 5) with real arms; add the `AdapterFactory` ctor param
   and the `App.kt` wiring; sweep the section-6 switches.
6. Build + on-device verify: create XRP wallet, receive, send (with destination tag), restore.
