package dev.mikey.mccviplab;

import static org.junit.Assert.*;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AutoRefillPolicyTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void refillsExactlyAsciiZeroOnly() {
        assertArrayEquals(new byte[]{'9'}, AutoRefillPolicy.nineOnlyWhenZero(new byte[]{'0'}));
        assertNull(AutoRefillPolicy.nineOnlyWhenZero(new byte[]{'1'}));
        assertNull(AutoRefillPolicy.nineOnlyWhenZero(new byte[]{'9'}));
        assertNull(AutoRefillPolicy.nineOnlyWhenZero(new byte[]{'0', '\n'}));
        assertNull(AutoRefillPolicy.nineOnlyWhenZero(new byte[]{}));
        assertNull(AutoRefillPolicy.nineOnlyWhenZero(null));
    }

    @Test public void permitsOnlyAccountScopedDiamondFile() {
        assertTrue(AutoRefillPolicy.allowedName("hrjQBBEy2tUN7V0rV36IzDTDL1F3-DiamondBalance"));
        assertFalse(AutoRefillPolicy.allowedName("DiamondBalance"));
        assertFalse(AutoRefillPolicy.allowedName("a-DiamondBalance"));
        assertFalse(AutoRefillPolicy.allowedName("../my-DiamondBalance"));
        assertFalse(AutoRefillPolicy.allowedName("lastPremiumLevel"));
    }

    @Test public void refusesMissingAndAmbiguousAccounts() throws Exception {
        File directory = tmp.newFolder("databases");
        assertNull(AutoRefillPolicy.uniqueBalanceFile(directory));
        File first = new File(directory, "account123-DiamondBalance");
        try (FileOutputStream f = new FileOutputStream(first)) { f.write('0'); }
        assertEquals(first, AutoRefillPolicy.uniqueBalanceFile(directory));
        File second = new File(directory, "account456-DiamondBalance");
        try (FileOutputStream f = new FileOutputStream(second)) { f.write('0'); }
        assertNull(AutoRefillPolicy.uniqueBalanceFile(directory));
        assertNull(AutoRefillPolicy.uniqueBalanceFile(new File(directory, "missing")));
    }

    @Test public void rejectsSymlinkBalance() throws Exception {
        File directory = tmp.newFolder("symlink-database");
        File actual = tmp.newFile("elsewhere");
        File link = new File(directory, "account123-DiamondBalance");
        Files.createSymbolicLink(link.toPath(), actual.toPath());
        assertNull(AutoRefillPolicy.uniqueBalanceFile(directory));
    }
}
