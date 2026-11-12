# MCC VIP Lab P06 — rewarded ad / diamond file timeline (read only)

Target version: `br.art.code.meucronogramacapilar` **20.2609.3009**. Pin: `Mikeyphw/MCC-VIP-Lab` `main`, `f956f83ca02c5e2345746a0fd837171f53d789a3`.

## Evidence motivating P06

The two user-supplied P05 snapshots, taken around 18 hours apart, showed the account-prefixed `databases/*-DiamondBalance` file change from a one-byte `0` to a one-byte `1` by SHA-256. The user reports watching a real rewarded ad and earning one diamond between snapshots. `*-LastSyncAzure` and `*-LastSyncHasura` changed too. This strongly supports a persisted balance that follows the rewarded-ad flow, but does **not** prove which function credited it or whether the backend verified or accepted it. Static P03 high/freeze intercepts on SharedPreferences did not touch this file.

## P06 layers

1. `DiamondFileIOTrace.java`: hooks `java.io.FileInputStream`, `FileOutputStream`, `RandomAccessFile` constructors and `File.renameTo` *in the target app process*. Logs only three fixed labels, never an account prefix, file content or token. Constructor events are I/O attempts, not confirmed writes. Dart AOT `dart:io` may bypass Java APIs entirely. The logger cannot reliably monitor all file reads.
2. `mcc_p06_trace.py capture`: from Termux, uses rooted **read-only** `sha256sum` and `wc -c` on *only* `databases/*-DiamondBalance`, `*-LastSyncAzure`, `*-LastSyncHasura`. Fails closed on missing, duplicate or oversized `DiamondBalance` candidates. Samples the hash on a bounded interval without reading actual balance bytes or touching unrelated auth files. The output JSON contains only fixed labels, timestamp, byte count and file hash (not user/account IDs). No network calls are initiated by this tool.
3. Pulls only filtered `MCCADID` rewarded-ad and `MCCP06IO` log messages from logcat in the chosen time window. The P03 `AdInstanceTrace` observer must already be present for `MCCADID` events, and may miss mediation networks other than AdMob. Does not fake ad callbacks or impressions.
4. Produces a single chronological log of reward callbacks, file hash transitions and sync marker updates. The 30-second reward-to-file correlation is **timing only**, not proof of app/server authorization or verified AdMob reward.

## Apply P06 overlay (existing repository only)

```zsh
cd ~/Code/MCC_VIP_LAB
git pull --ff-only
python3 /storage/emulated/0/Download/MCC_VIP_LAB_P06_REWARD_STORAGE_TIMELINE.zip
```

Wait for the successful hosted GitHub Actions Android build, then install and enable the new LSPosed module:

```zsh
cd ~/Code/MCC_VIP_LAB
python3 scripts/install_latest_successful.py
```

No local Gradle is needed. No new GitHub repo is created.

## Device test

Keep ad blocking configured as usual. To test a **legitimate** rewarded ad, the network/ad unit obviously must be available. Turn off P03's local diamond override to reduce confounding observations:

```zsh
python3 scripts/mcc_p03_device.py diamond observe
python3 scripts/mcc_p06_trace.py capture --serial localhost:5555 --seconds 120 --interval 0.5 --out ~/mcc-p06-trace.json
```

After the capture starts, switch to the app, earn **one diamond through the normal flow**, and return to Termux when the capture ends. If no rewarded ad loads due to the active DNS blocker, do **not** assume a reward callback occurred; the resulting trace remains useful as a failure baseline. Do not disable the blocker just to create test traffic; use a legitimate test ad configuration if you have one.

Analyze the saved sanitized capture:

```zsh
python3 scripts/mcc_p06_trace.py report --file ~/mcc-p06-trace.json
```

Interpretation:

- `reward_callbacks=1` means an AdMob plugin callback was observed. It is **not** an SSV receipt.
- `diamond_hash_changes=1` means the persisted local file hash changed during the sampling window, **not** that its numerical value necessarily increased.
- `sync_marker_hash_changes=2` means both local synchronization marker files changed, not that the backend accepted credit.
- `java_file_api_events=0` does **not** mean the file was not touched: Dart may use native I/O.
- A diamond change after a callback within 30 seconds is suggestive of temporal association only.
- An unchanged file does not rule out an in-memory, transient or remote balance change or a change-and-revert between samples.

**Safety:** Do not edit `*-DiamondBalance` or any sync marker as part of P06. The tracer is for diagnosis, not balance manipulation. Keep the saved JSON private if desired; hashes of one-byte files can reveal tiny values. The tracer does not export account IDs, files, ad-unit IDs, or authentication tokens.
