# MCC VIP Lab — personal LSPosed module

Personal-use modification for the Android app **Meu Cronograma Capilar**
(package `br.art.code.meucronogramacapilar`). It contains local RevenueCat
entitlement instrumentation, ad/reward diagnostics and a configurable
nine-diamond refill. It is **not** a genuine premium purchase or a server-side
currency authorization mechanism.

**Last manually tested target app version: `20.2609.3009`.** This number is
**informational only**. Newer app versions are allowed to launch and install
hooks; compatibility of individual Android/Flutter APIs or local balance
formats is not guaranteed. The module logs both the recorded and installed
version, never refuses solely because the numbers differ.

## Personal diamond control

- In Meu Cronograma Capilar, the compact **`♦ 9`** button is positioned beside
  the diamond indicator when Flutter exposes its accessibility semantics.
  Otherwise it uses a **top-right fallback**; exact placement must be checked
  on the device.
- The in-app button opens the separate **MCC VIP Lab Personal** Activity.
  On Android 14+, a verified request from the target app starts the operation.
  The root command executes under the **module's UID**, not the target's UID.
- The standalone module screen also has a manual button and an auto-refill
  toggle. Auto-refill triggers at a persisted balance of exactly zero.
- Uses the successful P09 mechanism: force-stop the target, write the sole
  account-local `*-DiamondBalance` ASCII value `9`, verify the file and request
  Android to reopen the app. **Write verification and launch success are
  reported separately**; it can succeed at writing while failing to relaunch.
- No fabricated rewarded-ad callback or direct backend write is added.
  This local change can still interact with existing app synchronization.

## Build and sign for personal use

Builds run **only on GitHub Actions**; never run Gradle in Termux. The repository
contains a manually triggered **Personal Release** workflow that builds a
signed APK using your own stable signing key stored in encrypted GitHub Secrets.
No signing material is committed to Git.

From Termux, after the main branch has been promoted:

```zsh
pkg install -y openjdk-17 gh python
python3 scripts/configure_personal_signing.py --publish-secrets
gh workflow run personal-release.yml -R Mikeyphw/MCC-VIP-Lab --ref main
```

After the workflow succeeds, install the APK matching the exact local commit:

```zsh
python3 scripts/install_personal_release.py --serial localhost:5555
```

An existing debug-signed module may have a different Android signing
certificate. The installer does not uninstall it automatically.

See [personal release and safety notes](docs/personal_release.md) for signing
key backups, source-history archive, root/UID behavior and limitations.

## Scoped diagnostics and legacy tools

Earlier diagnostic workflows and Python scripts (P02–P07) remain in the
repository. LSPosed/Vector scope stays limited to
`br.art.code.meucronogramacapilar`. RevenueCat hooks simulate local entitlement
state; they do not constitute purchases, verified receipts, or server proof.
Those hooks can cease working when the target app changes its implementation.

Use your own authorized test environment when evaluating backend behavior.
You can disable the module in LSPosed/Vector and reopen the target app. Existing
local balance data may remain changed after disabling the module.
