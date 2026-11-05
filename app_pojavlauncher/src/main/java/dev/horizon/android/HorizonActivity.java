package dev.horizon.android;

/** SAF result callback only. The native activity, UI and file operations stay Rust. */
public final class HorizonActivity extends android.app.NativeActivity {
    static { System.loadLibrary("horizon_android"); }
    // NativeActivity passes getFilesDir() to Rust as internalDataPath. Keep Horizon's
    // options, profiles, saves, account data and generated tables separate from Aerix.
    private java.io.File isolated(java.io.File parent) {
        if (parent == null) return null;
        java.io.File dir = new java.io.File(parent, "horizon-rust");
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory())
            throw new IllegalStateException("Unable to create Horizon data directory");
        return dir;
    }
    @Override public java.io.File getFilesDir() { return isolated(super.getFilesDir()); }
    @Override public java.io.File getCacheDir() { return isolated(super.getCacheDir()); }
    @Override public java.io.File getExternalFilesDir(String type) { return isolated(super.getExternalFilesDir(type)); }

    private native void nativeDocumentResult(int request, int result, String uri);

    @Override protected void onActivityResult(int request, int result, android.content.Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 4101) return;
        nativeDocumentResult(request, result,
                data == null || data.getData() == null ? null : data.getData().toString());
    }
}
