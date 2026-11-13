# MCC VIP Lab — Personal release / P10 promotion

This is a **personal-use** APK, not a public release. The previous `main` history
is preserved under `archive/main-before-personal-release-20261113`; the P10
feature is promoted to `main` only after that archived ref is pushed.

## Runtime behavior

- Exact Android target package: `br.art.code.meucronogramacapilar`.
- **Last manually tested target app version: `20.2609.3009`**. This is **only a
  reference record**, not a version gate. Newer versions are not rejected, but
  compatibility is best-effort, and changing Flutter semantics or app data
  storage can require changes to this module.
- The diamond button uses Flutter's accessibility semantics to anchor next to
  a diamond indicator. If those semantics are not exposed, the compact `♦ 9`
  button appears near the **top-right** of the target Flutter Activity. Exact
  placement cannot be guaranteed without a screenshot or live UI acceptance.
- A tap opens the MCC module Activity with Android 14+ launch identity sharing.
  Only a verified launch from the target UID automatically performs a refill;
  unverified launches show the normal manual module UI instead.
- The module Activity executes the previously successful P09 operation: stop
  the target app, write the sole `*-DiamondBalance` file to ASCII `9`, verify,
  and separately request Android to launch the app again via `IntentSender`.
  A verified write and a failed launch are reported **separately**.
- Automatic refill at zero uses the same module Activity handoff and requires
  a currently resumed target Flutter Activity. Reopening the app is intentional.
- No fabricated ad callback, network reward claim, or API request is introduced.
  On-device and server behavior remain unverified for this release.

## Personal stable signing key

This repository contains **no keystore or password**. In Termux install
`openjdk-17` (for `keytool`) and use:

```zsh
pkg install -y openjdk-17 git gh python
python3 scripts/configure_personal_signing.py --publish-secrets
```

That command creates or reuses a private signing key locally at
`~/.local/share/mcc-vip-lab/signing/` and uploads four encrypted GitHub Actions
secrets to this repository. **Back up that directory privately.** Losing the
signing key may prevent updating an existing personally signed installation.
Do not paste a password or keystore into chats, commits, bug reports or logs.

## CI-only signed build

No Gradle build in Termux. On `main`:

```zsh
gh workflow run personal-release.yml -R Mikeyphw/MCC-VIP-Lab --ref main
gh run list -R Mikeyphw/MCC-VIP-Lab -w personal-release.yml -L 3
```

Once that specific run succeeds:

```zsh
python3 scripts/install_personal_release.py --serial localhost:5555
```

The installer validates the run's `head_sha` against local `main` and refuses
an artifact built from another commit. A prior debug-signed APK can have a
different signing certificate; Android may reject the update. **Nothing will
uninstall the previous app automatically.** Preserve module settings before
any voluntary uninstall.

## Known limitations

The custom virtual-currency data is locally modified. The app may synchronize
with Azure/Hasura; backend authorization is not established and must not be
inferred from local UI changes. Automatic reloading by process restart may be
visibly disruptive. A new target-app release can change these assumptions.
