package dev.mikey.mccviplab;

import static org.junit.Assert.*;
import org.junit.Test;

public class DiamondButtonPlacementTest {
    @Test public void placesButtonLeftOfDiamondCounter() {
        assertArrayEquals(new int[]{636, 18},
                DiamondButtonPlacement.position(1080, 2000, 120, 40, 8, 74, new int[]{764, 20, 860, 56}));
    }
    @Test public void placesButtonRightWhenNoRoomOnLeft() {
        assertArrayEquals(new int[]{90, 20},
                DiamondButtonPlacement.position(1080, 2000, 120, 40, 8, 74, new int[]{12, 24, 82, 56}));
    }
    @Test public void fallbackIsNearTopRightAndClamped() {
        assertArrayEquals(new int[]{952, 74},
                DiamondButtonPlacement.position(1080, 2000, 120, 40, 8, 74, null));
        assertArrayEquals(new int[]{8, 8},
                DiamondButtonPlacement.position(100, 100, 120, 100, 8, 74, null));
    }
}
