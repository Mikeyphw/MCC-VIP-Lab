package dev.mikey.mccviplab;

/** Pure positioning model, independent of Android runtime for JVM testing. */
final class DiamondButtonPlacement {
    private DiamondButtonPlacement() {}
    static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }
    /** Returns top-left coordinates within the content view. */
    static int[] position(int width, int height, int buttonWidth, int buttonHeight,
                          int margin, int fallbackTop, int[] diamondBounds) {
        int x = width - buttonWidth - margin;
        int y = fallbackTop;
        if (diamondBounds != null && diamondBounds.length == 4) {
            int left = diamondBounds[0], top = diamondBounds[1];
            int right = diamondBounds[2], bottom = diamondBounds[3];
            if (right > left && bottom > top) {
                int before = left - buttonWidth - margin;
                int after = right + margin;
                x = before >= margin ? before : after;
                y = (top + bottom - buttonHeight) / 2;
            }
        }
        return new int[]{clamp(x, margin, Math.max(margin, width - buttonWidth - margin)),
                         clamp(y, margin, Math.max(margin, height - buttonHeight - margin))};
    }
}
