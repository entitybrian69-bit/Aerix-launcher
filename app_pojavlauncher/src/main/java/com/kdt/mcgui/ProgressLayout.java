package com.kdt.mcgui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.constraintlayout.widget.ConstraintLayout;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.downloader.DownloadControl;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.ProgressListener;
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener;
import java.util.ArrayList;

/** Release launcher's shared task observer, presented as the full-width Aerix download dock. */
public class ProgressLayout extends ConstraintLayout implements View.OnClickListener, TaskCountListener {
    public static final String UNPACK_RUNTIME = "unpack_runtime";
    public static final String DOWNLOAD_GAME = "download_minecraft";
    public static final String DOWNLOAD_VERSION_LIST = "download_verlist";
    public static final String AUTHENTICATE = "authenticate";
    public static final String INSTALL_MODPACK = "install_modpack";
    public static final String EXTRACT_COMPONENTS = "extract_components";
    public static final String EXTRACT_SINGLE_FILES = "extract_single_files";
    public static final String INSTANCE_INSTALL = "instance_install";
    public static final String DATA_MIGRATION = "data_migration";
    private final ArrayList<LayoutProgressListener> observers = new ArrayList<>();
    private LinearLayout tasks;
    private View taskScroll;
    private TextView title, percent, status, speed, eta;
    private ProgressBar progress;
    private ImageButton pause, cancel, expand;
    private DownloadControl selectedControl;
    private boolean closed;

    public ProgressLayout(@NonNull Context c) { super(c); init(); }
    public ProgressLayout(@NonNull Context c, @Nullable AttributeSet a) { super(c, a); init(); }
    public ProgressLayout(@NonNull Context c, @Nullable AttributeSet a, int s) { super(c, a, s); init(); }
    public ProgressLayout(@NonNull Context c, @Nullable AttributeSet a, int s, int r) { super(c, a, s, r); init(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void init() {
        inflate(getContext(), R.layout.view_progress, this);
        tasks = findViewById(R.id.progress_linear_layout);
        taskScroll = findViewById(R.id.dock_task_scroll);
        title = findViewById(R.id.dock_title);
        percent = findViewById(R.id.dock_percent);
        status = findViewById(R.id.dock_status);
        speed = findViewById(R.id.dock_speed);
        eta = findViewById(R.id.dock_eta);
        progress = findViewById(R.id.dock_progress);
        pause = findViewById(R.id.dock_pause);
        cancel = findViewById(R.id.dock_cancel);
        expand = findViewById(R.id.progress_flip_arrow);
        expand.setOnClickListener(this);
        pause.setOnClickListener(v -> { if (selectedControl != null) selectedControl.togglePause(); render(); });
        cancel.setOnClickListener(v -> {
            final DownloadControl control = selectedControl;
            if (control == null) return;
            new AlertDialog.Builder(getContext()).setTitle(R.string.dock_cancel)
                .setMessage(R.string.dock_cancel_message)
                .setNegativeButton(android.R.string.no, null)
                .setPositiveButton(android.R.string.yes, (dialog, which) -> control.cancel()).show();
        });
        android.view.ViewGroup.LayoutParams params = taskScroll.getLayoutParams();
        params.height = Math.min(dp(160), getResources().getDisplayMetrics().heightPixels / 3);
        taskScroll.setLayoutParams(params);
    }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w == oldw) return;
        boolean narrow = w < dp(500);
        findViewById(R.id.dock_icon).setVisibility(w < dp(600) ? GONE : VISIBLE);
        LinearLayout metadata = findViewById(R.id.dock_metadata);
        metadata.setOrientation(narrow ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        status.setLayoutParams(new LinearLayout.LayoutParams(narrow ? -1 : 0, -2, narrow ? 0 : 1));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.setMarginStart(narrow ? 0 : dp(12));
        speed.setLayoutParams(p);
        render();
    }
    public void observe(String key) {
        for (LayoutProgressListener observer : observers) if (observer.key.equals(key)) return;
        LayoutProgressListener observer = new LayoutProgressListener(key);
        observers.add(observer);
        ProgressKeeper.addListener(key, observer);
    }
    public void cleanUpObservers() {
        closed = true;
        for (LayoutProgressListener observer : observers) ProgressKeeper.removeListener(observer.key, observer);
    }
    public boolean hasProcesses() { return ProgressKeeper.getTaskCount() > 0; }
    public static void setProgress(String key, int value) { ProgressKeeper.submitProgress(key, value, -1, (Object) null); }
    public static void setProgress(String key, int value, @StringRes int resource, Object... args) { ProgressKeeper.submitProgress(key, value, resource, args); }
    public static void setProgress(String key, int value, String message) { setProgress(key, value, -1, message); }
    public static void clearProgress(String key) { setProgress(key, -1, -1); }
    @Override public void onClick(View v) {
        boolean show = taskScroll.getVisibility() != VISIBLE;
        taskScroll.setVisibility(show ? VISIBLE : GONE);
        expand.setRotation(show ? 180 : 0);
        expand.setContentDescription(getContext().getString(show ? R.string.dock_hide_tasks : R.string.dock_show_tasks));
    }
    @Override public boolean onUpdateTaskCount(int count) {
        post(() -> {
            if (closed) return;
            setVisibility(count > 0 ? VISIBLE : GONE);
            if (count == 0) { taskScroll.setVisibility(GONE); expand.setRotation(0); }
            render();
        });
        return false;
    }
    private int taskTitle(String key) {
        switch (key) {
            case DOWNLOAD_GAME: return R.string.dock_download_minecraft;
            case UNPACK_RUNTIME: return R.string.dock_runtime;
            case INSTALL_MODPACK: case INSTANCE_INSTALL: return R.string.dock_modpack;
            case AUTHENTICATE: return R.string.dock_auth;
            case DOWNLOAD_VERSION_LIST: return R.string.dock_versions;
            case DATA_MIGRATION: return R.string.dock_migration;
            case EXTRACT_COMPONENTS: case EXTRACT_SINGLE_FILES: return R.string.dock_extract;
            default: return R.string.dock_working;
        }
    }
    private void render() {
        LayoutProgressListener primary = null;
        int count = 0;
        for (LayoutProgressListener observer : observers) if (observer.active) {
            count++;
            if (primary == null || observer.key.equals(DOWNLOAD_GAME)) primary = observer;
        }
        selectedControl = primary == null ? null : DownloadControl.active(primary.key);
        boolean paused = selectedControl != null && selectedControl.isPaused();
        boolean stopping = selectedControl != null && selectedControl.isCancelled();
        pause.setEnabled(!stopping); cancel.setEnabled(!stopping);
        pause.setVisibility(selectedControl == null ? GONE : VISIBLE);
        cancel.setVisibility(selectedControl == null ? GONE : VISIBLE);
        pause.setImageResource(paused ? android.R.drawable.ic_media_play : android.R.drawable.ic_media_pause);
        pause.setContentDescription(getContext().getString(paused ? R.string.dock_resume : R.string.dock_pause));
        title.setText(stopping ? R.string.dock_stopping : paused ? R.string.dock_paused : primary == null ? R.string.dock_working : taskTitle(primary.key));
        speed.setVisibility(GONE);
        eta.setVisibility(GONE);
        if (primary == null) {
            progress.setIndeterminate(true); percent.setText("");
            status.setText(getResources().getQuantityString(R.plurals.dock_tasks, Math.max(1, count), Math.max(1, count)));
            return;
        }
        boolean sized = primary.resource == R.string.newerdl_downloading_files_size && primary.args.length >= 3;
        boolean counted = primary.resource == R.string.newerdl_downloading_files_count || primary.resource == R.string.newerdl_inserting_metadata_count;
        boolean determinate = sized || counted || primary.value > 0;
        progress.setIndeterminate(!determinate && !paused);
        progress.setProgress(Math.max(0, Math.min(100, primary.value)));
        percent.setText(determinate ? getContext().getString(R.string.dock_percent, Math.max(0, Math.min(100, primary.value))) : "");
        status.setText(primary.message());
        if (sized && primary.args[0] instanceof Number && primary.args[1] instanceof Number && primary.args[2] instanceof Number) {
            double done = ((Number) primary.args[0]).doubleValue();
            double total = ((Number) primary.args[1]).doubleValue();
            double rate = paused ? 0 : ((Number) primary.args[2]).doubleValue();
            status.setText(getContext().getString(R.string.dock_size, done, total));
            speed.setText(getContext().getString(R.string.dock_speed, rate));
            speed.setVisibility(VISIBLE);
            if (rate > 0 && total > done && !Double.isInfinite(rate) && !Double.isNaN(rate) && !Double.isInfinite(total) && !Double.isNaN(total)) {
                long seconds = (long) Math.ceil((total - done) / rate);
                eta.setText(getContext().getString(R.string.dock_eta, seconds / 60, seconds % 60));
                eta.setVisibility(getWidth() >= dp(600) ? VISIBLE : GONE);
            }
        }
        expand.setContentDescription(getContext().getString(taskScroll.getVisibility() == VISIBLE ? R.string.dock_hide_tasks : R.string.dock_show_tasks)
            + ", " + getResources().getQuantityString(R.plurals.dock_tasks, count, count));
    }
    private final class LayoutProgressListener implements ProgressListener {
        final String key;
        final LinearLayout row;
        final TextView label;
        final ProgressBar bar;
        boolean active;
        int value, resource = -1;
        Object[] args = new Object[0];
        LayoutProgressListener(String key) {
            this.key = key;
            row = new LinearLayout(getContext()); row.setOrientation(LinearLayout.VERTICAL); row.setPadding(dp(8), dp(8), dp(8), dp(12));
            label = new TextView(getContext()); label.setTextSize(13); label.setTextColor(Color.parseColor("#D6E4ED"));
            bar = new ProgressBar(getContext(), null, android.R.attr.progressBarStyleHorizontal);
            bar.setProgressDrawable(getContext().getDrawable(R.drawable.aerix_download_progress));
            row.addView(label, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(6)); p.topMargin = dp(8); row.addView(bar, p);
        }
        String message() {
            if (resource != -1) return getContext().getString(resource, args);
            if (args.length > 0 && args[0] != null) return String.valueOf(args[0]);
            return getContext().getString(taskTitle(key));
        }
        @Override public void onProgressStarted() {
            post(() -> { if (closed) return; active = true; if (row.getParent() == null) tasks.addView(row); render(); });
        }
        @Override public void onProgressUpdated(int value, int resource, Object... args) {
            final Object[] copy = args == null ? new Object[0] : args.clone();
            post(() -> {
                if (closed) return;
                this.value = value; this.resource = resource; this.args = copy;
                label.setText(getContext().getString(taskTitle(key)) + " · " + message());
                bar.setIndeterminate(value <= 0 && resource != R.string.newerdl_downloading_files_size && resource != R.string.newerdl_downloading_files_count);
                bar.setProgress(Math.max(0, Math.min(100, value))); render();
            });
        }
        @Override public void onProgressEnded() {
            post(() -> { if (closed) return; active = false; tasks.removeView(row); render(); });
        }
    }
}
