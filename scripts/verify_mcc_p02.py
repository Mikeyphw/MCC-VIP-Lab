#!/usr/bin/env python3
"""P02 invariants: scoped, read-only probes, bundled synthetic tests."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
p = root / 'app/src/main/java/dev/mikey/mccviplab'
hook = (p / 'HookEntry.java').read_text(encoding='utf-8')
assert hook.count('LocalStateProbe.install();') == 1
assert hook.count('AdEventProbe.install(loader);') == 1
assert hook.count('PurchaseCallProbe.install(loader);') == 1
for tag in ('MAPPER_BEFORE', 'MAPPER_AFTER', 'CUSTOMER_INFO_BEFORE', 'CUSTOMER_INFO_AFTER'):
    assert tag in hook
for cls in ('VipStateProbe.java', 'LocalStateProbe.java', 'AdEventProbe.java', 'PurchaseCallProbe.java'):
    assert (p / cls).is_file(), cls
local = (p / 'LocalStateProbe.java').read_text(encoding='utf-8')
ad = (p / 'AdEventProbe.java').read_text(encoding='utf-8')
assert 'param.setResult(' not in local + ad
assert 'hookAllMethods' in local and 'hookAllMethods' in ad
assert 'BRIDGE_GROUP' in ad and 'coalesce' in ad
assert 'SHARED_PREF_PROBE_INSTALLED_READ_ONLY' in local
manifest = (root/'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
assert 'android:name="xposedscope" android:value="br.art.code.meucronogramacapilar"' in manifest
assert (root/'scripts/install_latest_successful.py').is_file()
print('MCC_P02_SCOPE_READ_ONLY_TRACING_CONTRACT_PASS')
