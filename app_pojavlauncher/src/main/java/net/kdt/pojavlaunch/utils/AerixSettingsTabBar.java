package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.fragments.WallpaperGalleryFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceControlFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceJavaFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceMiscellaneousFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceVideoFragment;

/** Shared liquid-glass category strip for the launcher settings and wallpaper screens. */
public final class AerixSettingsTabBar {
    private static final String[] LABELS = {"Renderer", "Game", "Java", "Wallpapers", "Launcher", "Controls"};
    @SuppressWarnings("unchecked")
    private static final Class<? extends androidx.fragment.app.Fragment>[] DESTINATIONS = new Class[]{
            LauncherPreferenceVideoFragment.class,
            LauncherPreferenceMiscellaneousFragment.class,
            LauncherPreferenceJavaFragment.class,
            WallpaperGalleryFragment.class,
            LauncherPreferenceFragment.class,
            LauncherPreferenceControlFragment.class
    };

    private AerixSettingsTabBar() {
    }

    public static View create(Context context, FragmentActivity activity, Class<?> current) {
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 3), dp(context, 3), dp(context, 3), dp(context, 3));
        for (int i = 0; i < LABELS.length; i++) {
            final Class<? extends androidx.fragment.app.Fragment> destination = DESTINATIONS[i];
            boolean selected = current == destination;
            TextView tab = new TextView(context);
            tab.setText(LABELS[i]);
            tab.setTextColor(Color.WHITE);
            tab.setTextSize(12);
            tab.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(context, 15), 0, dp(context, 15), 0);
            tab.setMinWidth(dp(context, 82));
            tab.setBackground(tabBackground(context, selected));
            tab.setContentDescription(LABELS[i] + (selected ? ", selected" : ""));
            tab.setOnClickListener(v -> {
                if (activity.isFinishing() || activity.isDestroyed() || current == destination) return;
                FragmentManager manager = activity.getSupportFragmentManager();
                if (manager.isStateSaved()) return;
                manager.beginTransaction()
                        .setReorderingAllowed(true)
                        .addToBackStack(destination.getName())
                        .replace(R.id.container_fragment, destination, null, destination.getSimpleName())
                        .commit();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(context, 34));
            params.leftMargin = dp(context, 3);
            params.rightMargin = dp(context, 3);
            row.addView(tab, params);
        }
        scroll.addView(row, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT, HorizontalScrollView.LayoutParams.MATCH_PARENT));
        return scroll;
    }

    private static GradientDrawable tabBackground(Context context, boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        int accent = AerixThemeManager.accentColor(context, AerixThemeManager.SECTION_SETTINGS);
        drawable.setColor(selected ? Color.argb(62, Color.red(accent), Color.green(accent), Color.blue(accent))
                : Color.argb(50, 22, 41, 60));
        drawable.setCornerRadius(dp(context, 16));
        drawable.setStroke(dp(context, 1), selected ? Color.argb(220, 228, 250, 255)
                : Color.argb(135, 206, 236, 255));
        return drawable;
    }

    private static int dp(Context context, float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
