package dev.horizon.android;

/** Aerix preparation UI around the native Rust game. */
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

    private android.widget.FrameLayout preparation;
    private android.widget.TextView status;
    private android.widget.ProgressBar progress;
    private android.widget.Button cancel;

    @Override protected void onCreate(android.os.Bundle state) {
        // Only the public username is shared. Aerix's Microsoft tokens are not
        // copied to the separate native process or stored in Horizon's config.
        saveOfflineName(getIntent().getStringExtra("aerixOfflineName"));
        super.onCreate(state);
        showPreparation();
    }

    private void saveOfflineName(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_]{3,16}")) return;
        java.io.File config = new java.io.File(getFilesDir(), "android-launcher.json");
        try {
            org.json.JSONObject object;
            if (config.isFile()) {
                java.io.ByteArrayOutputStream contents = new java.io.ByteArrayOutputStream();
                try (java.io.FileInputStream input = new java.io.FileInputStream(config)) {
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        if (contents.size() + read > 1024 * 1024) throw new java.io.IOException("Config too large");
                        contents.write(buffer, 0, read);
                    }
                }
                object = new org.json.JSONObject(contents.toString("UTF-8"));
            } else object = new org.json.JSONObject();
            object.put("name", name);
            java.io.File temporary = new java.io.File(getFilesDir(), "android-launcher.aerix.tmp");
            try (java.io.FileOutputStream output = new java.io.FileOutputStream(temporary)) {
                output.write(object.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                output.getFD().sync();
            }
            if (!temporary.renameTo(config)) throw new java.io.IOException("Could not save name");
        } catch (Exception error) {
            // Preserve existing data if it is unreadable or cannot be saved.
            android.util.Log.w("AerixRust", "Could not update offline username", error);
        }
    }

    private void showPreparation() {
        preparation = new android.widget.FrameLayout(this);
        preparation.setBackgroundColor(android.graphics.Color.rgb(18, 20, 26));
        preparation.setClickable(true);
        android.widget.LinearLayout column = new android.widget.LinearLayout(this);
        column.setOrientation(android.widget.LinearLayout.VERTICAL);
        column.setGravity(android.view.Gravity.CENTER);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        column.setPadding(padding, padding, padding, padding);
        android.widget.TextView title = new android.widget.TextView(this);
        title.setText("AERIX  ·  MINECRAFT RUST");
        title.setTextColor(android.graphics.Color.WHITE);
        title.setTextSize(22);
        title.setGravity(android.view.Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        status = new android.widget.TextView(this);
        status.setText("Preparing game…\nThe first launch downloads Minecraft game resources.");
        status.setTextColor(0xffd4d3dd);
        status.setTextSize(16);
        status.setGravity(android.view.Gravity.CENTER);
        status.setPadding(0, padding, 0, padding);
        progress = new android.widget.ProgressBar(this);
        progress.setIndeterminate(true);
        cancel = new android.widget.Button(this);
        cancel.setText("Cancel and return to Aerix");
        cancel.setOnClickListener(view -> finish());
        column.addView(title);
        column.addView(status);
        column.addView(progress);
        column.addView(cancel);
        preparation.addView(column, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.CENTER));
        addContentView(preparation, new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** Called by the patched Rust host on its event thread, not the UI thread. */
    @androidx.annotation.Keep
    public void aerixStatus(String message) {
        runOnUiThread(() -> {
            if (isFinishing()) return;
            if (preparation == null && message != null && message.startsWith("error:")) showPreparation();
            if (preparation == null) return;
            if (message == null || message.isEmpty()) {
                android.view.ViewParent parent = preparation.getParent();
                if (parent instanceof android.view.ViewGroup) ((android.view.ViewGroup) parent).removeView(preparation);
                preparation = null;
            } else if (message.startsWith("error:")) {
                status.setText("Could not prepare Minecraft Rust:\n" + message.substring(6)
                        + "\nReturn to Aerix and tap Play to retry.");
                progress.setVisibility(android.view.View.GONE);
                cancel.setText("Return to Aerix");
            } else {
                status.setText(message);
            }
        });
    }

    private native void nativeDocumentResult(int request, int result, String uri);

    @Override protected void onActivityResult(int request, int result, android.content.Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 4101) return;
        nativeDocumentResult(request, result,
                data == null || data.getData() == null ? null : data.getData().toString());
    }
}
