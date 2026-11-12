#!/usr/bin/env python3
"""Recompile compileOnly Xposed API signatures for the CI JDK (Java 17)."""
from pathlib import Path
from tempfile import TemporaryDirectory
from zipfile import ZipFile, ZIP_DEFLATED, ZipInfo
import hashlib
import struct
import subprocess

ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / 'app/libs/xposed-api-82-stubs.jar'
SOURCES = {
    'de/robv/android/xposed/IXposedHookLoadPackage.java': '''package de.robv.android.xposed;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
public interface IXposedHookLoadPackage {
 void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) throws Throwable;
}
''',
    'de/robv/android/xposed/callbacks/XC_LoadPackage.java': '''package de.robv.android.xposed.callbacks;
public final class XC_LoadPackage {
 public static final class LoadPackageParam { public String packageName; public ClassLoader classLoader; }
}
''',
    'de/robv/android/xposed/XC_MethodHook.java': '''package de.robv.android.xposed;
public abstract class XC_MethodHook {
 protected void beforeHookedMethod(MethodHookParam p) throws Throwable {}
 protected void afterHookedMethod(MethodHookParam p) throws Throwable {}
 public final class Unhook {}
 public static final class MethodHookParam {
  public Object[] args; public Object thisObject;
  public Object getResult() { return null; }
  public void setResult(Object r) {}
 }
}
''',
    'de/robv/android/xposed/XposedBridge.java': '''package de.robv.android.xposed;
import java.util.Set;
public final class XposedBridge {
 public static void log(String message) {}
 public static Set<XC_MethodHook.Unhook> hookAllMethods(Class<?> c, String name, XC_MethodHook callback) { return null; }
 public static Set<XC_MethodHook.Unhook> hookAllConstructors(Class<?> c, XC_MethodHook callback) { return null; }
}
''',
    'de/robv/android/xposed/XposedHelpers.java': '''package de.robv.android.xposed;
public final class XposedHelpers {
 public static Class<?> findClass(String name, ClassLoader cl) { return null; }
 public static XC_MethodHook.Unhook findAndHookMethod(Class<?> c, String name, Object... args) { return null; }
}
''',
}

with TemporaryDirectory(prefix='mcc-xposed-stubs-') as tmp:
    root = Path(tmp)
    inputs = []
    for relative, source in sorted(SOURCES.items()):
        path = root / 'src' / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source, encoding='utf-8')
        inputs.append(str(path))
    classes = root / 'classes'
    classes.mkdir()
    subprocess.run(['javac', '--release', '17', '-d', str(classes), *inputs], check=True)
    JAR.parent.mkdir(parents=True, exist_ok=True)
    with ZipFile(JAR, 'w') as z:
        for path in sorted(classes.rglob('*.class')):
            entry = ZipInfo(str(path.relative_to(classes)))
            entry.date_time = (2020, 1, 1, 0, 0, 0)
            entry.compress_type = ZIP_DEFLATED
            z.writestr(entry, path.read_bytes())

with ZipFile(JAR) as z:
    entries = [name for name in z.namelist() if name.endswith('.class')]
    assert len(entries) >= 7, 'incomplete Xposed API stub jar'
    for name in entries:
        blob = z.read(name)
        assert blob[:4] == b'\xca\xfe\xba\xbe', ('bad class header', name)
        assert struct.unpack('>H', blob[6:8])[0] == 61, ('requires Java 17 classfile', name)

methods = subprocess.run(
    ['javap', '-classpath', str(JAR), '-s',
     'de.robv.android.xposed.XposedBridge', 'de.robv.android.xposed.XposedHelpers'],
    check=True, capture_output=True, text=True).stdout
assert '(Ljava/lang/Class;Ljava/lang/String;Lde/robv/android/xposed/XC_MethodHook;)Ljava/util/Set;' in methods, 'hookAllMethods return signature mismatch'
assert '(Ljava/lang/Class;Lde/robv/android/xposed/XC_MethodHook;)Ljava/util/Set;' in methods, 'hookAllConstructors return signature mismatch'
assert '(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Object;)Lde/robv/android/xposed/XC_MethodHook$Unhook;' in methods, 'findAndHookMethod return signature mismatch'
print('MCC_XPOSED_STUBS_JAVA17_ABI_PASS', len(entries), hashlib.sha256(JAR.read_bytes()).hexdigest())
