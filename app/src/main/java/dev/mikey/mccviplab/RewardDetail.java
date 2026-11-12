package dev.mikey.mccviplab;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;

/** Platform-neutral, strictly bounded reward metadata reader. No callback mutation. */
public final class RewardDetail {
    public final Integer amount;
    public final String unit;
    public final String status;

    private RewardDetail(Integer amount, String unit, String status) {
        this.amount = amount;
        this.unit = unit;
        this.status = status;
    }

    private static Object read(Object value, String key, String getter) {
        if (value instanceof Map<?, ?>) return ((Map<?, ?>) value).get(key);
        if (value == null) return null;
        // Restrict reflection to explicit rewarded-ad item classes, never unrelated payloads.
        String simple = value.getClass().getSimpleName();
        if (!("RewardItem".equals(simple) || "FlutterRewardItem".equals(simple))) return null;
        try {
            Method method = value.getClass().getMethod(getter);
            return method.invoke(value);
        } catch (ReflectiveOperationException | SecurityException ignored) {
            try {
                Field field = value.getClass().getDeclaredField(key);
                field.setAccessible(true);
                return field.get(value);
            } catch (ReflectiveOperationException | RuntimeException ignoredAgain) {
                return null;
            }
        }
    }

    public static RewardDetail inspect(Object value) {
        Object amountRaw = read(value, "amount", "getAmount");
        Object typeRaw = read(value, "type", "getType");
        Integer amount = null;
        if (amountRaw instanceof Number) {
            Number n = (Number) amountRaw;
            double raw = n.doubleValue();
            if (Double.isFinite(raw) && raw == Math.rint(raw) && raw >= 0 && raw <= 100000) {
                amount = Integer.valueOf(n.intValue());
            }
        }
        String unit = "unknown";
        if (typeRaw instanceof String) {
            String type = ((String) typeRaw).trim().toLowerCase(Locale.ROOT);
            if ("diamond".equals(type) || "diamonds".equals(type)) unit = "diamond";
            else if ("gem".equals(type) || "gems".equals(type)) unit = "gem";
            else if ("coin".equals(type) || "coins".equals(type)) unit = "coin";
            else unit = "other"; // Never log raw free-form reward type strings.
        }
        return new RewardDetail(amount, unit, amount == null ? "unavailable" : "observed");
    }

    public String logFields() {
        return "amount=" + (amount == null ? "unknown" : amount) +
                " unit=" + unit + " status=" + status;
    }
}
