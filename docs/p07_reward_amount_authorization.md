# P07 — Reward amount provenance and authorization boundary

Base: `Mikeyphw/MCC-VIP-Lab` main source-pinned before application.

## Why P07

P06 HF02 observed one rewarded ad callback (`ad_id=1`) followed by a stored
`DiamondBalance` update from ASCII `0` to `4` about 0.556 s later. Both
`LastSyncAzure` and `LastSyncHasura` markers changed afterward. This proves
neither that AdMob asked for four diamonds nor that server-side reward validation
occurred. This update answers the observable **reward amount** question.

## New read-only observations

- `MCCP07: SDK_REWARD` — emitted after `AdInstanceManager`'s rewarded callback
  (where that callback signature exposes a reward item).
- `MCCP07: BRIDGE_REWARD` — emitted before `MethodChannel.invokeMethod` sends
  a recognized rewarded-ad event to Flutter.
- Data: local numerical ad ID; bounded `amount` (0..100000) and sanitized unit
  (`diamond`, `gem`, `coin`, `other`, `unknown`); no raw reward-type string,
  token, URL, account name, or ad payload recorded.
- Missing amount means the Android object shape was not exposed through this
  version's expected getter or field; report it as missing, never invent it.
- `mcc_p07_capture.py` streams sanitized callback logs during the existing P06
  SHA-256 hash sampler and correlates a callback, observed local balance delta,
  and later sync-marker changes.
- Only known ASCII integers 0..999 are recovered from hashes; file contents
  remain unread by the observer. Larger/other formats become unknown.

## Explicit limits

- P07 does **not** hook the Dart AOT `incrementDiamondBalance` method directly.
  It observes the input and output boundaries (bridge + persisted file).
- A callback followed by a file delta is temporal correlation, not proof of
  causation, a verified AdMob SSV transaction, or backend trust.
- Sync marker changes indicate local timestamps/metadata updates only; no
  verified server ACK or actual backend ledger is observed.
- No fake ad callbacks, HTTP interception, token capture, balance modification,
  or network disablement is involved.

## Apply from Termux Zsh

```zsh
cd ~/Code/MCC_VIP_LAB
git pull --ff-only
python3 /storage/emulated/0/Download/MCC_VIP_LAB_P07_REWARD_AMOUNT_AUDIT.zip --check-only
python3 /storage/emulated/0/Download/MCC_VIP_LAB_P07_REWARD_AMOUNT_AUDIT.zip
```

Wait for the hosted Android debug APK build to pass, then install
with `python3 scripts/install_latest_successful.py`, enable Vector/LSPosed
scope for `br.art.code.meucronogramacapilar`, and force stop/reopen the target.

Capture:

```zsh
python3 scripts/mcc_p03_device.py diamond observe
python3 scripts/mcc_p07_capture.py capture --serial localhost:5555 --seconds 300 --interval 0.5 --out ~/mcc-p07-trace.json
python3 scripts/mcc_p07_capture.py report --file ~/mcc-p07-trace.json
```

While capturing, spend diamonds if necessary and complete **one real rewarded
ad**. Keep the app open for the remainder of the capture. Upload the sanitized
`mcc-p07-trace.json` for analysis. Expected classifications include
`MATCHES_SDK_AMOUNT`, `DIFFERS_FROM_SDK_AMOUNT`, and
`FILE_DELTA_WITHOUT_REWARD_AMOUNT`; none establishes server authorization.
