package dev.mikey.mccviplab;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public final class DiamondPolicyTest {
    @Test public void ignoresUnrelatedKeys() {
        Map<String,Long> memory = new HashMap<>();
        assertEquals(3, DiamondPolicy.transform("subscription_ttl",3,"high",999999,memory).intValue());
        assertEquals(12, DiamondPolicy.transform("gemstoneColor",12,"high",999999,memory).intValue());
    }
    @Test public void highIsLocalAndExact() {
        Map<String,Long> memory = new HashMap<>();
        assertEquals(999999, DiamondPolicy.transform("flutter.diamondBalance",2,"high",999999,memory).intValue());
        assertEquals(2, DiamondPolicy.transform("flutter.diamondBalance",2,"observe",999999,memory).intValue());
        assertEquals(2000000000L, DiamondPolicy.transform("diamonds",2000000000L,"high",2000000001L,memory).longValue());
    }
    @Test public void freezePreservesFirstObservedValue() {
        Map<String,Long> memory = new HashMap<>();
        assertEquals(7, DiamondPolicy.transform("diamond_balance",7,"freeze",999999,memory).intValue());
        assertEquals(7, DiamondPolicy.transform("diamond_balance",3,"freeze",999999,memory).intValue());
    }
    @Test public void wrongTypeOrNameCannotChange() {
        Map<String,Long> memory = new HashMap<>();
        assertFalse(DiamondPolicy.exactKey("diamond_cache_timeout"));
        assertTrue(DiamondPolicy.candidate("diamond_cache_timeout"));
        assertEquals(0, DiamondPolicy.transform("diamond_cache_timeout",0,"high",999999,memory).intValue());
    }
}
