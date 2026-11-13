package dev.mikey.mccviplab;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

/**
 * Single fixed local operation. Authenticates the calling Android UID as
 * the exact scoped package; never accepts a path, value, account or shell text.
 */
public final class ModuleRefillProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        int caller = Binder.getCallingUid();
        String[] packages = getContext().getPackageManager().getPackagesForUid(caller);
        boolean allowed = false;
        if (packages != null) {
            for (String name : packages) {
                if (RootRefillCommand.TARGET.equals(name)) allowed = true;
            }
        }
        if (!allowed) throw new SecurityException("Only the scoped target app may call the refill bridge");
        boolean auto;
        if ("AUTO_ZERO".equals(method)) auto = true;
        else if ("MANUAL_SET_9".equals(method)) auto = false;
        else throw new IllegalArgumentException("Unsupported method");
        String result = AutoRefillWorker.execute(getContext(), auto);
        Bundle response = new Bundle();
        response.putString("result", result);
        return response;
    }

    @Override public Cursor query(Uri u, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues values) { return null; }
    @Override public int delete(Uri u, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri u, ContentValues values, String selection,
                                String[] selectionArgs) { return 0; }
}
