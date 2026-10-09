package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.WallpaperUtils;

import java.io.IOException;

/** Lets the user apply a wallpaper from device storage while the bundled catalog is unavailable. */
public class WallpaperGalleryFragment extends Fragment {
    public static final String TAG = "WallpaperGalleryFragment";

    private ImageView mPreview;
    private TextView mPlaceholder;
    private final ActivityResultLauncher<String[]> mWallpaperPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::onWallpaperPicked);

    public WallpaperGalleryFragment() {
        super();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return buildView();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        String saved = LauncherPreferences.DEFAULT_PREF.getString(WallpaperUtils.PREFERENCE_KEY, null);
        if (Tools.isValidString(saved)) loadWallpaper(Uri.parse(saved));
    }

    private View buildView() {
        int side = dp(20);
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(4), dp(4), dp(4), dp(4));

        LinearLayout header = new LinearLayout(requireContext());
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(side, dp(14), side, dp(14));
        header.setBackground(panelBackground());
        TextView title = text(getString(R.string.aerix_wallpaper_title), 22, "#F1F6FC", true);
        TextView subtitle = text(getString(R.string.aerix_wallpaper_subtitle), 12, "#AABCD0", false);
        header.addView(title);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(3);
        header.addView(subtitle, subtitleParams);
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout previewFrame = new FrameLayout(requireContext());
        previewFrame.setBackground(panelBackground());
        previewFrame.setClipToOutline(true);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        previewParams.topMargin = dp(12);
        previewParams.bottomMargin = dp(10);
        root.addView(previewFrame, previewParams);

        mPreview = new ImageView(requireContext());
        mPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        previewFrame.addView(mPreview, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mPlaceholder = text(getString(R.string.aerix_wallpaper_empty), 14, "#AABCD0", false);
        mPlaceholder.setGravity(Gravity.CENTER);
        mPlaceholder.setPadding(dp(30), dp(20), dp(30), dp(20));
        previewFrame.addView(mPlaceholder, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView note = text(getString(R.string.aerix_wallpaper_note), 12, "#AABCD0", false);
        note.setPadding(dp(4), dp(2), dp(4), dp(8));
        root.addView(note, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(requireContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button choose = button(getString(R.string.aerix_wallpaper_choose));
        choose.setOnClickListener(v -> mWallpaperPicker.launch(new String[]{"image/*"}));
        Button clear = button(getString(R.string.aerix_wallpaper_clear));
        clear.setOnClickListener(v -> clearWallpaper());
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        actionParams.rightMargin = dp(6);
        actions.addView(choose, actionParams);
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        clearParams.leftMargin = dp(6);
        actions.addView(clear, clearParams);
        root.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    private void onWallpaperPicked(Uri uri) {
        if (uri == null || !isAdded()) return;
        try {
            requireContext().getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            LauncherPreferences.DEFAULT_PREF.edit()
                    .putString(WallpaperUtils.PREFERENCE_KEY, uri.toString())
                    .apply();
            loadWallpaper(uri);
        } catch (SecurityException e) {
            Tools.showError(requireContext(), e);
        }
    }

    private void loadWallpaper(Uri uri) {
        Context context = requireContext().getApplicationContext();
        int width = Math.max(640, getResources().getDisplayMetrics().widthPixels);
        int height = Math.max(360, getResources().getDisplayMetrics().heightPixels);
        PojavApplication.sExecutorService.execute(() -> {
            try {
                Bitmap wallpaper = WallpaperUtils.decode(context, uri, width, height);
                if (!isAdded()) return;
                android.app.Activity activity = getActivity();
                if (activity == null) return;
                activity.runOnUiThread(() -> {
                    if (!isAdded() || mPreview == null) return;
                    mPreview.setImageBitmap(wallpaper);
                    mPlaceholder.setVisibility(View.GONE);
                    ImageView backdrop = activity.findViewById(R.id.launcher_wallpaper_backdrop);
                    if (backdrop != null) {
                        backdrop.setImageBitmap(wallpaper);
                        backdrop.setVisibility(View.VISIBLE);
                    }
                });
            } catch (IOException e) {
                if (!isAdded()) return;
                android.app.Activity activity = getActivity();
                if (activity != null) activity.runOnUiThread(() -> {
                    if (isAdded()) Tools.showError(activity, e);
                });
            }
        });
    }

    private void clearWallpaper() {
        String saved = LauncherPreferences.DEFAULT_PREF.getString(WallpaperUtils.PREFERENCE_KEY, null);
        if (Tools.isValidString(saved)) {
            try {
                Uri uri = Uri.parse(saved);
                requireContext().getContentResolver().releasePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
                // The persisted permission may already have been revoked by the document provider.
            }
        }
        LauncherPreferences.DEFAULT_PREF.edit().remove(WallpaperUtils.PREFERENCE_KEY).apply();
        if (mPreview != null) mPreview.setImageDrawable(null);
        if (mPlaceholder != null) mPlaceholder.setVisibility(View.VISIBLE);
        ImageView backdrop = requireActivity().findViewById(R.id.launcher_wallpaper_backdrop);
        if (backdrop != null) {
            backdrop.setImageDrawable(null);
            backdrop.setVisibility(View.GONE);
        }
    }

    private Button button(String label) {
        Button button = new Button(requireContext());
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackgroundResource(R.drawable.aerix_nav_button);
        return button;
    }

    private TextView text(String value, int sizeSp, String color, boolean bold) {
        TextView text = new TextView(requireContext());
        text.setText(value);
        text.setTextSize(sizeSp);
        text.setTextColor(Color.parseColor(color));
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private GradientDrawable panelBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.argb(218, 22, 36, 56));
        drawable.setCornerRadius(dp(20));
        drawable.setStroke(dp(1), Color.argb(42, 121, 156, 191));
        return drawable;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
