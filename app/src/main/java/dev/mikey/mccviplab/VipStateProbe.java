package dev.mikey.mccviplab;

import java.util.Map;

/** Sanitized observations of RevenueCat CustomerInfo maps. Never reads account IDs or tokens. */
public final class VipStateProbe {
    private VipStateProbe() {}

    public static String describe(Object value) {
        if (!(value instanceof Map)) return "shape=non_map";
        Map<?, ?> info = (Map<?, ?>) value;
        Object entitlements = info.get("entitlements");
        if (!(entitlements instanceof Map)) return "shape=no_entitlements";
        Map<?, ?> ents = (Map<?, ?>) entitlements;
        Object activeObj = ents.get("active");
        Object allObj = ents.get("all");
        if (!(activeObj instanceof Map) || !(allObj instanceof Map)) return "shape=incomplete_entitlements";
        Map<?, ?> active = (Map<?, ?>) activeObj;
        Map<?, ?> all = (Map<?, ?>) allObj;
        boolean inActive = active.containsKey(VipPayload.ENTITLEMENT);
        boolean inAll = all.containsKey(VipPayload.ENTITLEMENT);
        Object details = active.get(VipPayload.ENTITLEMENT);
        Object isActive = details instanceof Map ? ((Map<?, ?>) details).get("isActive") : null;
        String activeFlag = Boolean.TRUE.equals(isActive) ? "true" : Boolean.FALSE.equals(isActive) ? "false" : "unknown";
        Object verification = details instanceof Map ? ((Map<?, ?>) details).get("verification") : null;
        String marker = safeVerification(verification);
        return "listed_active=" + inActive + " listed_all=" + inAll +
                " isActive=" + activeFlag + " verification=" + marker;
    }

    private static String safeVerification(Object v) {
        if (!(v instanceof String)) return "not_present";
        String s = (String) v;
        if ("VERIFIED".equals(s) || "NOT_REQUESTED".equals(s) || "FAILED".equals(s)) return s;
        return "other";
    }
}
