package dev.mikey.mccviplab;

/**
 * The exact P09 mechanism: stop the target, select its sole one-byte balance,
 * write ASCII '9', verify, and launch the target so Dart reloads persistent state.
 * Nothing in this command calls a network API, edits sync markers or mints ad callbacks.
 */
final class RootRefillCommand {
    private RootRefillCommand() {}
    static final String TARGET = "br.art.code.meucronogramacapilar";
    static final String PROVIDER_AUTHORITY = "dev.mikey.mccviplab.refill";
    static String script(boolean onlyWhenZero) {
        return "set -eu\n"
             + "pkg='" + TARGET + "'\n"
             + "base='/data/user/0/" + TARGET + "/databases'\n"
             + "am force-stop \"$pkg\"\n"
             + "restart_target() { am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p \"$pkg\" >/dev/null 2>&1 || :; }\n"
             + "trap 'restart_target' EXIT\n"
             + "[ -d \"$base\" ] || { echo MCC_REFILL_NO_DATABASE; exit 51; }\n"
             + "count=0\n"
             + "f=''\n"
             + "for candidate in \"$base\"/*-DiamondBalance; do\n"
             + "  [ -f \"$candidate\" ] || continue\n"
             + "  [ ! -L \"$candidate\" ] || { echo MCC_REFILL_SYMLINK_REFUSED; exit 52; }\n"
             + "  count=$((count + 1))\n"
             + "  f=$candidate\n"
             + "done\n"
             + "[ \"$count\" -eq 1 ] || { echo MCC_REFILL_ACCOUNT_COUNT_REFUSED; exit 53; }\n"
             + "bytes=$(wc -c < \"$f\")\n"
             + "[ \"$bytes\" -eq 1 ] || { echo MCC_REFILL_BAD_SIZE; exit 54; }\n"
             + "v=$(cat \"$f\")\n"
             + "case \"$v\" in [0-9]) : ;; *) echo MCC_REFILL_BAD_CONTENT; exit 55 ;; esac\n"
             + (onlyWhenZero
                 ? "[ \"$v\" = '0' ] || { echo MCC_REFILL_AUTO_SKIP_NONZERO; exit 63; }\n"
                 : "")
             + "printf '%s' '9' > \"$f\"\n"
             + "[ \"$(wc -c < \"$f\")\" -eq 1 ] && [ \"$(cat \"$f\")\" = '9' ] || { echo MCC_REFILL_VERIFY_FAILED; exit 59; }\n"
             + "am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p \"$pkg\" >/dev/null 2>&1 || { echo MCC_REFILL_LAUNCH_FAILED; exit 62; }\n"
             + "trap - EXIT\n"
             + "printf 'MCC_REFILL_P09_SEQUENCE_PASS\\n'\n";
    }
}
