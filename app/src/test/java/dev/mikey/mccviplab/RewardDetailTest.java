package dev.mikey.mccviplab;

import static org.junit.Assert.*;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public final class RewardDetailTest {
    public static class RewardItem {
        public int getAmount() { return 1; }
        public String getType() { return "gems"; }
    }
    @Test public void readsMapAndWhitelistsOnlyUnit() {
        Map<String,Object> item = new HashMap<>();
        item.put("amount", 4);
        item.put("type", "a-user-identifier-with-private-data");
        RewardDetail detail = RewardDetail.inspect(item);
        assertEquals(Integer.valueOf(4), detail.amount);
        assertEquals("other", detail.unit);
        assertFalse(detail.logFields().contains("private"));
    }
    @Test public void readsGetterWithoutAndroidOrXposed() {
        RewardDetail detail = RewardDetail.inspect(new RewardItem());
        assertEquals(Integer.valueOf(1), detail.amount);
        assertEquals("gem", detail.unit);
    }
    @Test public void neverUsesUnrelatedObjectsOrUnboundedAmounts() {
        assertNull(RewardDetail.inspect(new Object()).amount);
        Map<String,Object> item = new HashMap<>();
        item.put("amount", 999999999);
        assertEquals("unavailable", RewardDetail.inspect(item).status);
        item.put("amount", -1);
        assertEquals("unavailable", RewardDetail.inspect(item).status);
        item.put("amount", 1.5d);
        assertEquals("unavailable", RewardDetail.inspect(item).status);
        item.put("amount", 0);
        assertEquals(Integer.valueOf(0), RewardDetail.inspect(item).amount);
    }
}
