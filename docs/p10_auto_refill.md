# P10 — Personal diamond refill

The old in-process file watcher was replaced with the P09-proven stop → write →
verify sequence, performed by MCC VIP Lab in its **own KernelSU-authorized UID**.
The Android restart is **separate** from the verified write and uses the
package manager's launch `IntentSender` on Android 13+.

`ROOT_REFUSED_exit_62` in the older build referred to an app relaunch failure,
not an actual root denial. The personal build reports `WRITE_9_VERIFIED_*` and
`LAUNCH_FAILED_*` independently.

The target UI button is compact and positions itself next to diamond-related
Flutter semantics when available, falling back to the upper-right of the screen.
It launches the module Activity with verified launch identity. Older Android
versions may require a second tap in that Activity.

The previously tested version `20.2609.3009` is an **informational record**.
Newer target app versions are allowed; individual Flutter or Xposed hooks may
still become unavailable as the target implementation evolves.

This is personal-use local-state modification. Backend validation and later
application versions are not claimed to be tested. See `docs/personal_release.md`.
