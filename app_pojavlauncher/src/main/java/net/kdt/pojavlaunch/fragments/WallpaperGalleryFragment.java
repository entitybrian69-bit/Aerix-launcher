package net.kdt.pojavlaunch.fragments;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.AerixSettingsTabBar;
import net.kdt.pojavlaunch.utils.AerixThemeManager;
import net.kdt.pojavlaunch.utils.PrismGlass;
import net.kdt.pojavlaunch.utils.WallpaperUtils;

import java.io.IOException;

/** Landscape-first wallpaper gallery with bundled presets, custom images, and wallpaper accents. */
public class WallpaperGalleryFragment extends Fragment {
    public static final String TAG = "WallpaperGalleryFragment";

    private ImageView mPreview;
    private TextView mSelectedLabel;
    private TextView mThemeLabel;
    private RecyclerView mWallpaperGrid;
    private WallpaperAdapter mAdapter;
    private volatile int mLoadToken;

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
        if (Tools.isValidString(saved)) {
            loadWallpaper(Uri.parse(saved), false, null);
        } else {
            String selectedId = WallpaperUtils.selectedId(requireContext());
            loadWallpaper(null, false, selectedId);
        }
        updateThemeLabel();
    }

    private View buildView() {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(4), dp(4), dp(4), dp(4));
        root.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout header = new LinearLayout(requireContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(10), dp(20), dp(10));
        PrismGlass.apply(header);
        LinearLayout titleStack = new LinearLayout(requireContext());
        titleStack.setOrientation(LinearLayout.VERTICAL);
        titleStack.addView(text(getString(R.string.aerix_wallpaper_title), 22, "#F1F6FC", true));
        TextView subtitle = text("25 scenic presets · wallpaper-derived launcher colors", 12, "#C6D8E8", false);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(2);
        titleStack.addView(subtitle, subtitleParams);
        header.addView(titleStack, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView themeBadge = text("AERIX  /  APPEARANCE", 11, "#EAF7FF", true);
        themeBadge.setLetterSpacing(0.08f);
        header.addView(themeBadge, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        tabParams.topMargin = dp(7);
        root.addView(AerixSettingsTabBar.create(requireContext(), requireActivity(), getClass()), tabParams);

        LinearLayout body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        bodyParams.topMargin = dp(10);
        root.addView(body, bodyParams);

        LinearLayout galleryPanel = new LinearLayout(requireContext());
        galleryPanel.setOrientation(LinearLayout.VERTICAL);
        galleryPanel.setPadding(dp(12), dp(10), dp(12), dp(8));
        PrismGlass.apply(galleryPanel);
        body.addView(galleryPanel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        LinearLayout galleryHeading = new LinearLayout(requireContext());
        galleryHeading.setOrientation(LinearLayout.HORIZONTAL);
        galleryHeading.setGravity(Gravity.CENTER_VERTICAL);
        TextView galleryTitle = text("SCENIC WALLPAPERS", 11, "#BBD0E1", true);
        galleryTitle.setLetterSpacing(0.08f);
        galleryHeading.addView(galleryTitle, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView count = text("25", 11, "#DFFBFF", true);
        count.setGravity(Gravity.CENTER);
        GradientDrawable countBg = new GradientDrawable();
        countBg.setColor(Color.argb(100, 25, 202, 214));
        countBg.setCornerRadius(dp(20));
        countBg.setStroke(dp(1), Color.argb(160, 188, 250, 255));
        count.setBackground(countBg);
        count.setMinWidth(dp(32));
        count.setPadding(dp(8), dp(4), dp(8), dp(4));
        galleryHeading.addView(count);
        galleryPanel.addView(galleryHeading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));

        mWallpaperGrid = new RecyclerView(requireContext());
        mWallpaperGrid.setClipToPadding(false);
        mWallpaperGrid.setPadding(dp(2), dp(4), dp(2), dp(4));
        mWallpaperGrid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        int widthDp = getResources().getConfiguration().screenWidthDp;
        int columns = widthDp >= 1300 ? 4 : (widthDp >= 900 ? 3 : 2);
        mWallpaperGrid.setLayoutManager(new GridLayoutManager(requireContext(), columns));
        mAdapter = new WallpaperAdapter(WallpaperUtils.catalog());
        mWallpaperGrid.setAdapter(mAdapter);
        galleryPanel.addView(mWallpaperGrid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout sidePanel = new LinearLayout(requireContext());
        sidePanel.setOrientation(LinearLayout.VERTICAL);
        sidePanel.setPadding(dp(12), dp(10), dp(12), dp(10));
        PrismGlass.apply(sidePanel);
        LinearLayout.LayoutParams sideParams = new LinearLayout.LayoutParams(
                Math.max(dp(248), Math.min(dp(312), (int) (getResources().getConfiguration().screenWidthDp * 0.28f))),
                ViewGroup.LayoutParams.MATCH_PARENT);
        sideParams.leftMargin = dp(10);
        body.addView(sidePanel, sideParams);

        TextView previewTitle = text("PREVIEW", 11, "#BBD0E1", true);
        previewTitle.setLetterSpacing(0.08f);
        sidePanel.addView(previewTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(24)));
        FrameLayout previewFrame = new FrameLayout(requireContext());
        PrismGlass.apply(previewFrame);
        previewFrame.setClipToOutline(true);
        sidePanel.addView(previewFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.78f));
        mPreview = new ImageView(requireContext());
        mPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        previewFrame.addView(mPreview, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View previewShade = new View(requireContext());
        previewShade.setBackgroundColor(Color.argb(78, 6, 16, 29));
        previewFrame.addView(previewShade, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout previewCaption = new LinearLayout(requireContext());
        previewCaption.setOrientation(LinearLayout.VERTICAL);
        previewCaption.setGravity(Gravity.BOTTOM);
        previewCaption.setPadding(dp(12), dp(8), dp(12), dp(9));
        FrameLayout.LayoutParams captionParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        previewFrame.addView(previewCaption, captionParams);
        mSelectedLabel = text("Loading wallpaper…", 15, "#FFFFFF", true);
        previewCaption.addView(mSelectedLabel);
        TextView previewHint = text("Applied behind every launcher screen", 10, "#E0EAF4", false);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = dp(2);
        previewCaption.addView(previewHint, hintParams);

        LinearLayout imageActions = new LinearLayout(requireContext());
        imageActions.setOrientation(LinearLayout.HORIZONTAL);
        Button chooseButton = button("Choose image");
        chooseButton.setOnClickListener(v -> mWallpaperPicker.launch(new String[]{"image/*"}));
        Button resetButton = button("Use preset");
        resetButton.setOnClickListener(v -> clearCustomWallpaper());
        imageActions.addView(chooseButton, new LinearLayout.LayoutParams(0, dp(42), 1f));
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
        resetParams.leftMargin = dp(7);
        imageActions.addView(resetButton, resetParams);
        LinearLayout.LayoutParams imageActionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        imageActionsParams.topMargin = dp(8);
        sidePanel.addView(imageActions, imageActionsParams);

        View separator = new View(requireContext());
        separator.setBackgroundColor(Color.argb(90, 215, 238, 255));
        LinearLayout.LayoutParams separatorParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        separatorParams.topMargin = dp(10);
        separatorParams.bottomMargin = dp(8);
        sidePanel.addView(separator, separatorParams);

        LinearLayout themeHeader = new LinearLayout(requireContext());
        themeHeader.setOrientation(LinearLayout.HORIZONTAL);
        themeHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView themeHeading = text("LAUNCHER COLORS", 11, "#BBD0E1", true);
        themeHeading.setLetterSpacing(0.08f);
        themeHeader.addView(themeHeading, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        mThemeLabel = text(AerixThemeManager.currentThemeLabel(), 10, "#E9F7FF", true);
        themeHeader.addView(mThemeLabel);
        LinearLayout.LayoutParams themeHeaderParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(24));
        sidePanel.addView(themeHeader, themeHeaderParams);

        LinearLayout themeActions = new LinearLayout(requireContext());
        themeActions.setOrientation(LinearLayout.HORIZONTAL);
        Button presets = button("Presets");
        presets.setOnClickListener(v -> showPresetPicker());
        Button custom = button("Custom");
        custom.setOnClickListener(v -> showCustomColorDialog());
        Button sections = button("Sections");
        sections.setOnClickListener(v -> showSectionAccentPicker());
        addWeighted(themeActions, presets);
        addWeighted(themeActions, custom);
        addWeighted(themeActions, sections);
        LinearLayout.LayoutParams themeActionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        themeActionsParams.topMargin = dp(4);
        sidePanel.addView(themeActions, themeActionsParams);

        Switch dynamicSwitch = new Switch(requireContext());
        dynamicSwitch.setText(Build.VERSION.SDK_INT >= 31
                ? "Material You colors" : "Material You (Android 12+)");
        dynamicSwitch.setTextColor(Color.WHITE);
        dynamicSwitch.setTextSize(11);
        dynamicSwitch.setChecked(Build.VERSION.SDK_INT >= 31 && AerixThemeManager.isMaterialYouEnabled());
        dynamicSwitch.setEnabled(Build.VERSION.SDK_INT >= 31);
        dynamicSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            AerixThemeManager.setMaterialYouEnabled(checked);
            requireActivity().recreate();
        });
        LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        switchParams.topMargin = dp(3);
        sidePanel.addView(dynamicSwitch, switchParams);
        TextView accentHint = text("New wallpaper selections automatically recolor the launcher.",
                10, "#BBD0E1", false);
        accentHint.setPadding(0, dp(2), 0, 0);
        sidePanel.addView(accentHint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    private void addWeighted(LinearLayout parent, Button button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        params.leftMargin = dp(2);
        params.rightMargin = dp(2);
        parent.addView(button, params);
    }

    private void selectCatalogWallpaper(WallpaperUtils.Wallpaper wallpaper) {
        if (wallpaper == null) return;
        releaseSavedWallpaperPermission();
        LauncherPreferences.DEFAULT_PREF.edit()
                .remove(WallpaperUtils.PREFERENCE_KEY)
                .putString(WallpaperUtils.SELECTED_CATALOG_KEY, wallpaper.id)
                .apply();
        if (mAdapter != null) mAdapter.setSelectedId(wallpaper.id);
        loadWallpaper(null, true, wallpaper.id);
    }

    private void clearCustomWallpaper() {
        releaseSavedWallpaperPermission();
        String selectedId = WallpaperUtils.selectedId(requireContext());
        LauncherPreferences.DEFAULT_PREF.edit().remove(WallpaperUtils.PREFERENCE_KEY).apply();
        loadWallpaper(null, true, selectedId);
    }

    private void onWallpaperPicked(Uri uri) {
        if (uri == null || !isAdded()) return;
        try {
            requireContext().getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            LauncherPreferences.DEFAULT_PREF.edit()
                    .putString(WallpaperUtils.PREFERENCE_KEY, uri.toString())
                    .apply();
            if (mAdapter != null) mAdapter.setSelectedId(null);
            loadWallpaper(uri, true, null);
        } catch (SecurityException e) {
            Tools.showError(requireContext(), e);
        }
    }

    private void releaseSavedWallpaperPermission() {
        String saved = LauncherPreferences.DEFAULT_PREF.getString(WallpaperUtils.PREFERENCE_KEY, null);
        if (!Tools.isValidString(saved)) return;
        try {
            requireContext().getContentResolver().releasePersistableUriPermission(
                    Uri.parse(saved), Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // A document provider may already have revoked this persisted grant.
        }
    }

    private void loadWallpaper(Uri customUri, boolean applyAccent, @Nullable String catalogId) {
        final int token = ++mLoadToken;
        Context appContext = requireContext().getApplicationContext();
        int width = Math.max(960, getResources().getDisplayMetrics().widthPixels);
        int height = Math.max(540, getResources().getDisplayMetrics().heightPixels);
        PojavApplication.sExecutorService.execute(() -> {
            Bitmap bitmap = null;
            try {
                bitmap = customUri == null
                        ? WallpaperUtils.decodeBundled(appContext, catalogId, width, height)
                        : WallpaperUtils.decode(appContext, customUri, width, height);
                int accent = WallpaperUtils.sampleAccentColor(bitmap);
                if (applyAccent) AerixThemeManager.setWallpaperAccent(accent);
                else AerixThemeManager.setWallpaperAccentIfMissing(accent);
                Bitmap result = bitmap;
                android.app.Activity activity = getActivity();
                if (activity == null) {
                    result.recycle();
                    return;
                }
                activity.runOnUiThread(() -> {
                    if (!isAdded() || token != mLoadToken || mPreview == null) {
                        result.recycle();
                        return;
                    }
                    mPreview.setImageBitmap(result);
                    String label = customUri == null
                            ? wallpaperName(catalogId) : "Custom wallpaper";
                    mSelectedLabel.setText(label);
                    ImageView backdrop = activity.findViewById(R.id.launcher_wallpaper_backdrop);
                    if (backdrop != null) {
                        backdrop.setImageBitmap(result);
                        backdrop.setVisibility(View.VISIBLE);
                    }
                    updateThemeLabel();
                    if (applyAccent && !activity.isFinishing()) activity.recreate();
                });
            } catch (IOException | RuntimeException e) {
                if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                android.app.Activity activity = getActivity();
                if (activity != null) activity.runOnUiThread(() -> {
                    if (isAdded() && token == mLoadToken) {
                        Toast.makeText(activity, "Unable to load wallpaper: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private String wallpaperName(@Nullable String id) {
        WallpaperUtils.Wallpaper wallpaper = WallpaperUtils.find(id);
        return wallpaper == null ? "Crystal River" : wallpaper.name;
    }

    private void updateThemeLabel() {
        if (mThemeLabel != null) mThemeLabel.setText(AerixThemeManager.currentThemeLabel());
    }

    private void showPresetPicker() {
        String[] presets = AerixThemeManager.presetNames();
        new AlertDialog.Builder(requireContext())
                .setTitle("Choose launcher theme")
                .setSingleChoiceItems(presets, AerixThemeManager.selectedPreset(), (dialog, which) -> {
                    AerixThemeManager.setPreset(which);
                    dialog.dismiss();
                    requireActivity().recreate();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showCustomColorDialog() {
        EditText input = new EditText(requireContext());
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setHint("#RRGGBB");
        input.setText(AerixThemeManager.colorHex(
                AerixThemeManager.accentColor(requireContext(), AerixThemeManager.SECTION_APPEARANCE)));
        input.setSelection(input.length());
        LinearLayout wrapper = new LinearLayout(requireContext());
        wrapper.setPadding(dp(20), dp(4), dp(20), 0);
        wrapper.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(requireContext())
                .setTitle("Custom accent color")
                .setMessage("Enter a six-digit hex color such as #7C5CFF.")
                .setView(wrapper)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Apply", (dialog, which) -> {
                    if (!AerixThemeManager.setCustomHex(input.getText().toString())) {
                        Toast.makeText(requireContext(), "Use a six-digit #RRGGBB color.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    requireActivity().recreate();
                })
                .show();
    }

    private void showSectionAccentPicker() {
        final String[] names = {"Home", "Library", "Discover", "Servers", "Skins", "Renderer", "Controls", "Java", "Appearance", "Settings"};
        final String[] ids = {
                AerixThemeManager.SECTION_HOME, AerixThemeManager.SECTION_LIBRARY,
                AerixThemeManager.SECTION_DISCOVER, AerixThemeManager.SECTION_SERVERS,
                AerixThemeManager.SECTION_SKINS, AerixThemeManager.SECTION_RENDERER,
                AerixThemeManager.SECTION_CONTROLS, AerixThemeManager.SECTION_JAVA,
                AerixThemeManager.SECTION_APPEARANCE, AerixThemeManager.SECTION_SETTINGS
        };
        new AlertDialog.Builder(requireContext())
                .setTitle("Choose a section")
                .setItems(names, (dialog, which) -> showSectionColorPicker(names[which], ids[which]))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showSectionColorPicker(String name, String id) {
        String[] presetNames = AerixThemeManager.presetNames();
        String[] options = new String[presetNames.length + 2];
        options[0] = "Use global theme";
        options[1] = "Match current theme (" + AerixThemeManager.colorHex(
                AerixThemeManager.accentColor(requireContext(), "global")) + ")";
        System.arraycopy(presetNames, 0, options, 2, presetNames.length);
        int[] presetColors = AerixThemeManager.presetColors();
        new AlertDialog.Builder(requireContext())
                .setTitle(name + " accent")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        AerixThemeManager.setSectionAccent(id, null);
                    } else if (which == 1) {
                        AerixThemeManager.setSectionAccent(id, AerixThemeManager.colorHex(
                                AerixThemeManager.accentColor(requireContext(), "global")));
                    } else {
                        AerixThemeManager.setSectionAccent(id,
                                AerixThemeManager.colorHex(presetColors[which - 2]));
                    }
                    requireActivity().recreate();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private Button button(String label) {
        Button button = new Button(requireContext());
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(11);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackgroundResource(R.drawable.aerix_nav_button);
        AerixThemeManager.tintButton(button, requireContext(), AerixThemeManager.SECTION_APPEARANCE);
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
        drawable.setColor(Color.argb(133, 31, 58, 82));
        drawable.setCornerRadius(dp(22));
        drawable.setStroke(dp(1), Color.argb(164, 222, 246, 255));
        return drawable;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private final class WallpaperAdapter extends RecyclerView.Adapter<WallpaperAdapter.WallpaperHolder> {
        private final WallpaperUtils.Wallpaper[] items;
        private String selectedId = WallpaperUtils.selectedId(requireContext());

        WallpaperAdapter(WallpaperUtils.Wallpaper[] items) {
            this.items = items;
            setHasStableIds(true);
        }

        void setSelectedId(@Nullable String id) {
            selectedId = id;
            notifyDataSetChanged();
        }

        @Override
        public long getItemId(int position) {
            return items[position].id.hashCode();
        }

        @NonNull
        @Override
        public WallpaperHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout card = new FrameLayout(parent.getContext());
            card.setClipToOutline(true);
            RecyclerView.LayoutParams outer = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(126));
            outer.setMargins(dp(4), dp(4), dp(4), dp(4));
            card.setLayoutParams(outer);

            ImageView image = new ImageView(parent.getContext());
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(Color.rgb(31, 52, 72));
            card.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            View scrim = new View(parent.getContext());
            scrim.setBackgroundColor(Color.argb(105, 8, 17, 30));
            FrameLayout.LayoutParams scrimParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(38), Gravity.BOTTOM);
            card.addView(scrim, scrimParams);
            TextView label = text("", 12, "#FFFFFF", true);
            label.setGravity(Gravity.CENTER_VERTICAL);
            label.setPadding(dp(9), dp(4), dp(9), dp(4));
            FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(38), Gravity.BOTTOM);
            card.addView(label, labelParams);
            return new WallpaperHolder(card, image, label);
        }

        @Override
        public void onBindViewHolder(@NonNull WallpaperHolder holder, int position) {
            WallpaperUtils.Wallpaper wallpaper = items[position];
            boolean selected = wallpaper.id.equals(selectedId);
            holder.label.setText(wallpaper.name);
            holder.image.setTag(wallpaper.id);
            holder.image.setImageDrawable(null);
            holder.PrismGlass.apply(card);
            holder.card.setForeground(cardBackground(selected));
            holder.card.setContentDescription(wallpaper.name + (selected ? ", selected" : ""));
            holder.card.setOnClickListener(v -> selectCatalogWallpaper(wallpaper));
            Context context = holder.image.getContext().getApplicationContext();
            PojavApplication.sExecutorService.execute(() -> {
                Bitmap bitmap;
                try {
                    bitmap = WallpaperUtils.decodeBundled(context, wallpaper.id, 420, 236);
                } catch (IOException e) {
                    return;
                }
                ImageView image = holder.image;
                image.post(() -> {
                    Object tag = image.getTag();
                    if (tag != null && wallpaper.id.equals(tag)) image.setImageBitmap(bitmap);
                    else bitmap.recycle();
                });
            });
        }

        @Override
        public void onViewRecycled(@NonNull WallpaperHolder holder) {
            holder.image.setTag(null);
            holder.image.setImageDrawable(null);
            super.onViewRecycled(holder);
        }

        @Override
        public int getItemCount() {
            return items.length;
        }

        private GradientDrawable cardBackground(boolean selected) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(Color.TRANSPARENT);
            drawable.setCornerRadius(dp(13));
            int accent = AerixThemeManager.accentColor(requireContext(), AerixThemeManager.SECTION_APPEARANCE);
            drawable.setStroke(dp(selected ? 2 : 1), selected ? accent : Color.argb(150, 215, 239, 255));
            return drawable;
        }

        final class WallpaperHolder extends RecyclerView.ViewHolder {
            final FrameLayout card;
            final ImageView image;
            final TextView label;

            WallpaperHolder(FrameLayout card, ImageView image, TextView label) {
                super(card);
                this.card = card;
                this.image = image;
                this.label = label;
            }
        }
    }
}
