package dev.mikey.mccviplab;

/**
 * Exact locally-proven P09 operation. No launch command runs from the root shell;
 * launcher dispatch is a separately reported operation in the module process.
 */
final class RootRefillCommand {
    private RootRefillCommand() {}
    static final String TARGET = "br.art.code.meucronogramacapilar";
    static final String ACTION_FROM_TARGET = "dev.mikey.mccviplab.action.REFILL_FROM_TARGET";
    static final String EXTRA_AUTO = "dev.mikey.mccviplab.extra.AUTO";
    static String script(boolean onlyWhenZero) {
        return "set -eu\n"
             + "[ \"$(id -u)\" = '0' ] || { echo MCC_REFILL_NOT_ROOT; exit 70; }\n"
             + "pkg='" + TARGET + "'\n"
             + "base='/data/user/0/" + TARGET + "/databases'\n"
             + "am force-stop \"$pkg\"\n"
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
             + (onlyWhenZero ? "[ \"$v\" = '0' ] || { echo MCC_REFILL_AUTO_SKIP_NONZERO; exit 63; }\n" : "")
             + "printf '%s' '9' > \"$f\"\n"
             + "[ \"$(wc -c < \"$f\")\" -eq 1 ] && [ \"$(cat \"$f\")\" = '9' ] || { echo MCC_REFILL_VERIFY_FAILED; exit 59; }\n"
             + "printf 'MCC_REFILL_WRITE_VERIFIED\\n'\n";
    }
}
