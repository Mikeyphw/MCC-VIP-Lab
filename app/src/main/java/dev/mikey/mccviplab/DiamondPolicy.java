package dev.mikey.mccviplab;

import java.util.Map;
import java.util.Locale;

/** Local test-only SharedPreferences override policy; never writes balances. */
public final class DiamondPolicy {
    private DiamondPolicy() {}
    public static boolean exactKey(String key) {
        if (key == null) return false;
        String k = key.toLowerCase(Locale.ROOT);
        if (k.startsWith("flutter.")) k = k.substring(8);
        return k.equals("diamondbalance") || k.equals("diamond_balance") ||
            k.equals("diamonds") || k.equals("diamond_count") ||
            k.equals("user_diamonds") || k.equals("diamond");
    }
    public static boolean candidate(String key) {
        if (key == null) return false;
        String k = key.toLowerCase(Locale.ROOT);
        return k.contains("diamond") || k.contains("gem") || k.contains("coin");
    }
    public static Number transform(String key, Number original, String mode,
                                   long highValue, Map<String, Long> frozen) {
        if (!exactKey(key) || original == null) return original;
        long chosen;
        if ("high".equals(mode)) {
            if (highValue < 0 || highValue > 1000000000L) return original;
            chosen = highValue;
        } else if ("freeze".equals(mode)) {
            synchronized (frozen) {
                if (!frozen.containsKey(key)) frozen.put(key, original.longValue());
                chosen = frozen.get(key);
            }
        } else return original;
        if (original instanceof Integer) return Integer.valueOf((int) chosen);
        return Long.valueOf(chosen);
    }
}
