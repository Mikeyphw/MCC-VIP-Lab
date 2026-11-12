package dev.mikey.mccviplab;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.concurrent.atomic.AtomicInteger;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Read-only Java I/O diagnostics for three known account-scoped files.
 * Never reads content, dumps paths, hooks native Dart I/O, or changes arguments.
 * JVM stream construction is an attempt, not proof of a persisted write.
 */
public final class DiamondFileIOTrace {
    private static final int MAX_EVENTS = 90;
    private static final AtomicInteger EVENTS = new AtomicInteger();
    private DiamondFileIOTrace() {}

    private static void note(String event, String label) {
        if (label == null) return;
        if (EVENTS.incrementAndGet() > MAX_EVENTS) return;
        XposedBridge.log("MCCP06IO: " + event + " label=" + label +
                " status=attempt_observed");
    }

    private static void register(Class<?> type, final String event) {
        XposedBridge.hookAllConstructors(type, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.args == null || p.args.length == 0) return;
                String target = DiamondFileLabel.label(p.args[0]);
                note(event, target);
            }
        });
    }

    public static void install() {
        int installed = 0;
        try { register(FileInputStream.class, "JAVA_OPEN_READ"); installed++; }
        catch (Throwable t) { XposedBridge.log("MCCP06IO: READ_HOOK_UNAVAILABLE " + t.getClass().getSimpleName()); }
        try { register(FileOutputStream.class, "JAVA_OPEN_WRITE"); installed++; }
        catch (Throwable t) { XposedBridge.log("MCCP06IO: WRITE_HOOK_UNAVAILABLE " + t.getClass().getSimpleName()); }
        try { register(RandomAccessFile.class, "JAVA_OPEN_RANDOM_ACCESS"); installed++; }
        catch (Throwable t) { XposedBridge.log("MCCP06IO: RANDOM_HOOK_UNAVAILABLE " + t.getClass().getSimpleName()); }
        try {
            XposedBridge.hookAllMethods(File.class, "renameTo", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.args == null || p.args.length == 0) return;
                    String target = DiamondFileLabel.label(p.args[0]);
                    if (target == null && p.thisObject instanceof File) target = DiamondFileLabel.label(p.thisObject);
                    if (target != null) note("JAVA_RENAME", target);
                }
            });
            installed++;
        } catch (Throwable t) { XposedBridge.log("MCCP06IO: RENAME_HOOK_UNAVAILABLE " + t.getClass().getSimpleName()); }
        XposedBridge.log("MCCP06IO: JAVA_FILE_TRACE_INSTALLED_READ_ONLY hooks=" + installed +
                " dart_native_io_not_intercepted");
    }
}
