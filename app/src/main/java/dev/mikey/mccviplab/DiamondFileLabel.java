package dev.mikey.mccviplab;

import java.io.File;

/**
 * Pure Java filename classifier for local P06 diagnostics.
 * Separate from Xposed hooks so host-side JVM tests never load Xposed classes.
 * Only fixed labels are returned; account-specific filenames stay private.
 */
final class DiamondFileLabel {
    private DiamondFileLabel() {}

    static String label(Object source) {
        if (!(source instanceof File) && !(source instanceof String)) return null;
        String path = source instanceof File ? ((File) source).getPath() : (String) source;
        String normal = path.replace('\\', '/');
        // Ignore arbitrary account- or network-derived strings; log a fixed label only.
        if (!normal.contains("/databases/")) return null;
        int slash = normal.lastIndexOf('/');
        String basename = normal.substring(slash + 1);
        if (basename.endsWith("-DiamondBalance")) return "DiamondBalance";
        if (basename.endsWith("-LastSyncAzure")) return "LastSyncAzure";
        if (basename.endsWith("-LastSyncHasura")) return "LastSyncHasura";
        return null;
    }

}
