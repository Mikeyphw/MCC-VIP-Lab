package dev.mikey.mccviplab;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** RevenueCat Flutter CustomerInfo-compatible fixture, isolated to the test process. */
public final class VipPayload {
    public static final String ENTITLEMENT = "premium_noads";
    public static final String TEST_PRODUCT = "premium_noads";
    private VipPayload() {}

    private static String iso(long millis) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        return df.format(new Date(millis));
    }

    @SuppressWarnings("unchecked")
    private static Map<String,Object> asMap(Object value) {
        if (!(value instanceof Map)) return new LinkedHashMap<>();
        Map<String,Object> out = new LinkedHashMap<>();
        Map<?,?> raw=(Map<?,?>)value;
        for (Map.Entry<?,?> item:raw.entrySet())
            if(item.getKey() instanceof String) out.put((String)item.getKey(),item.getValue());
        return out;
    }

    private static List<String> plusProduct(Object obj, String product) {
        List<String> out = new ArrayList<>();
        if (obj instanceof List) for (Object entry: (List<?>)obj)
            if (entry instanceof String && !out.contains(entry)) out.add((String)entry);
        if (!out.contains(product)) out.add(product);
        return out;
    }

    private static String nonEmpty(Object obj, String fallback) {
        if (obj instanceof String && !((String)obj).isEmpty()) return (String)obj;
        return fallback;
    }

    /**
     * Never edit the SDK's original map: it may be immutable or reused elsewhere.
     * Returns null unless the input has a CustomerInfo-style structure.
     */
    public static Map<String,Object> patch(Object customerInfo) {
        if (!(customerInfo instanceof Map)) return null;
        Map<String,Object> info=asMap(customerInfo);
        if (!(info.get("entitlements") instanceof Map)) return null;
        long now=System.currentTimeMillis();
        String purchaseDate=iso(now - 60_000L);
        String expires=iso(now + 365L*24*60*60*1000);

        Map<String,Object> ents=asMap(info.get("entitlements"));
        Map<String,Object> all=asMap(ents.get("all"));
        Map<String,Object> active=asMap(ents.get("active"));
        Map<String,Object> existing=asMap(all.get(ENTITLEMENT));
        String product=nonEmpty(existing.get("productIdentifier"),TEST_PRODUCT);

        Map<String,Object> vip=new LinkedHashMap<>(existing);
        vip.put("identifier",ENTITLEMENT);
        vip.put("isActive",Boolean.TRUE);
        vip.put("willRenew",Boolean.TRUE);
        vip.put("latestPurchaseDate",purchaseDate);
        vip.put("originalPurchaseDate",nonEmpty(existing.get("originalPurchaseDate"), purchaseDate));
        vip.put("productIdentifier",product);
        vip.put("isSandbox",Boolean.TRUE);
        vip.put("ownershipType","PURCHASED");
        vip.put("store","TEST_STORE");
        vip.put("periodType","NORMAL");
        vip.put("expirationDate",expires);
        vip.put("unsubscribeDetectedAt",null);
        vip.put("billingIssueDetectedAt",null);
        vip.put("verification","VERIFIED"); // synthetic, NOT a genuine store verification
        all.put(ENTITLEMENT,vip);
        active.put(ENTITLEMENT,new LinkedHashMap<>(vip));
        ents.put("all",all);
        ents.put("active",active);
        ents.put("verification","VERIFIED"); // synthetic QA marker, not a genuine receipt
        info.put("entitlements",ents);
        info.put("activeSubscriptions",plusProduct(info.get("activeSubscriptions"),product));
        info.put("allPurchasedProductIdentifiers",plusProduct(info.get("allPurchasedProductIdentifiers"),product));
        Map<String,Object> purchases=asMap(info.get("allPurchaseDates"));
        purchases.put(product,purchaseDate);
        info.put("allPurchaseDates",purchases);
        Map<String,Object> expiration=asMap(info.get("allExpirationDates"));
        expiration.put(product,expires);
        info.put("allExpirationDates",expiration);
        info.put("latestExpirationDate",expires);
        info.put("requestDate",iso(now));
        // Preserve originalAppUserId, firstSeen and all other server-provided fields.
        // SDKs that do not return subscriptionsByProductIdentifier are valid.
        return info;
    }

    /** Only used if the SDK had no previous CustomerInfo, e.g. an offline QA session. */
    public static Map<String,Object> fixture() {
        long now=System.currentTimeMillis();
        Map<String,Object> info = new LinkedHashMap<>();
        Map<String,Object> ents=new LinkedHashMap<>();
        ents.put("all",new LinkedHashMap<String,Object>());
        ents.put("active",new LinkedHashMap<String,Object>());
        ents.put("verification","NOT_REQUESTED");
        info.put("entitlements",ents);
        info.put("allPurchaseDates",new LinkedHashMap<String,Object>());
        info.put("activeSubscriptions",new ArrayList<String>());
        info.put("allPurchasedProductIdentifiers",new ArrayList<String>());
        info.put("nonSubscriptionTransactions",new ArrayList<Object>());
        info.put("firstSeen",iso(now));
        info.put("originalAppUserId","mcc-lab-offline");
        info.put("allExpirationDates",new LinkedHashMap<String,Object>());
        info.put("requestDate",iso(now));
        info.put("subscriptionsByProductIdentifier",new LinkedHashMap<String,Object>());
        return patch(info);
    }
}
