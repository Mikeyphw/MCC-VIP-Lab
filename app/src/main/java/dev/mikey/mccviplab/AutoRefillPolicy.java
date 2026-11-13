package dev.mikey.mccviplab;

import java.io.File;
import java.nio.file.Files;

/** Strictly account-local filename and one-byte state selection; no Android dependencies. */
final class AutoRefillPolicy {
    private static final String SUFFIX = "-DiamondBalance";
    private AutoRefillPolicy() {}

    static boolean allowedName(String name) {
        if (name == null || !name.endsWith(SUFFIX)) return false;
        int n = name.length() - SUFFIX.length();
        if (n < 8 || n > 128) return false;
        for (int i = 0; i < n; i++) {
            char c = name.charAt(i);
            if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') ||
                  (c >= '0' && c <= '9') || c == '-' || c == '_')) return false;
        }
        return true;
    }

    /** Refuse multiple accounts; never choose an arbitrary balance file. */
    static File uniqueBalanceFile(File databaseDirectory) {
        if (databaseDirectory == null || !databaseDirectory.isDirectory() ||
                Files.isSymbolicLink(databaseDirectory.toPath())) return null;
        File[] files = databaseDirectory.listFiles((directory, name) -> allowedName(name));
        if (files == null || files.length != 1) return null;
        File target = files[0];
        if (!target.isFile() || Files.isSymbolicLink(target.toPath())) return null;
        return target;
    }

    static boolean exactZero(byte[] contents) {
        return contents != null && contents.length == 1 && contents[0] == (byte) '0';
    }

    static byte[] nineOnlyWhenZero(byte[] contents) {
        return exactZero(contents) ? new byte[] { (byte) '9' } : null;
    }
}
