package net.kdt.pojavlaunch.fragments;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** A responsive instance library with persistent favorites, groups, pins, and sorting. */
public class InstanceLibraryFragment extends Fragment {
    public static final String TAG = "InstanceLibraryFragment";

    private static final String PREF_FAVORITES_ONLY = "aerix_library_favorites_only";
    private static final String PREF_GROUP_FILTER = "aerix_library_group_filter";
    private static final String PREF_SORT_MODE = "aerix_library_sort_mode";
    private static final String META_FAVORITE = "aerix_instance_favorite_";
    private static final String META_PINNED = "aerix_instance_pinned_";
    private static final String META_GROUP = "aerix_instance_group_";
    private static final String META_LAST_PLAYED = "aerix_instance_last_played_";
    private static final String FILTER_UNGROUPED = "__AERIX_UNGROUPED_FILTER__";

    private LinearLayout mRoot;
    private GridLayout mGrid;
    private ProgressBar mProgress;
    private TextView mEmptyState;
    private Button mFavoritesFilterButton;
    private Button mGroupFilterButton;
    private Button mSortButton;
    private List<Instance> mAllInstances = Collections.emptyList();
    private boolean mFavoritesOnly;
    private String mGroupFilter;
    private int mSortMode;
    private int mCardColumns = 1;

    public InstanceLibraryFragment() {
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
    public void onResume() {
        super.onResume();
        refreshInstances();
    }

    private View buildView() {
        android.content.SharedPreferences prefs = LauncherPreferences.DEFAULT_PREF;
        mFavoritesOnly = prefs.getBoolean(PREF_FAVORITES_ONLY, false);
        mGroupFilter = prefs.getString(PREF_GROUP_FILTER, null);
        mSortMode = Math.max(0, Math.min(2, prefs.getInt(PREF_SORT_MODE, 0)));

        int pad = dp(18);
        mRoot = new LinearLayout(requireContext());
        mRoot.setOrientation(LinearLayout.VERTICAL);
        mRoot.setPadding(dp(4), dp(4), dp(4), dp(4));
        mRoot.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout header = new LinearLayout(requireContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(pad, dp(12), pad, dp(12));
        header.setBackground(panelBackground());

        LinearLayout titleStack = new LinearLayout(requireContext());
        titleStack.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(getString(R.string.aerix_library_title), 22, "#F1F6FC", true);
        TextView subtitle = text(getString(R.string.aerix_library_subtitle), 12, "#AABCD0", false);
        titleStack.addView(title);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(3);
        titleStack.addView(subtitle, subtitleParams);
        header.addView(titleStack, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button create = button(getString(R.string.create_instance));
        create.setOnClickListener(v -> openCreateFlow());
        header.addView(create, new LinearLayout.LayoutParams(dp(174), dp(46)));
        mRoot.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout filters = new LinearLayout(requireContext());
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.setPadding(0, dp(8), 0, 0);
        mFavoritesFilterButton = button("");
        mGroupFilterButton = button("");
        mSortButton = button("");
        addFilterButton(filters, mFavoritesFilterButton);
        addFilterButton(filters, mGroupFilterButton);
        addFilterButton(filters, mSortButton);
        mFavoritesFilterButton.setOnClickListener(v -> {
            mFavoritesOnly = !mFavoritesOnly;
            LauncherPreferences.DEFAULT_PREF.edit().putBoolean(PREF_FAVORITES_ONLY, mFavoritesOnly).apply();
            updateFilterLabels();
            renderInstances();
        });
        mGroupFilterButton.setOnClickListener(v -> showGroupFilterDialog());
        mSortButton.setOnClickListener(v -> showSortDialog());
        mRoot.addView(filters, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        updateFilterLabels();

        mProgress = new ProgressBar(requireContext());
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        progressParams.gravity = Gravity.CENTER;
        progressParams.topMargin = dp(18);
        mRoot.addView(mProgress, progressParams);

        mEmptyState = text(getString(R.string.aerix_library_empty), 15, "#B8C8D9", false);
        mEmptyState.setGravity(Gravity.CENTER);
        mEmptyState.setPadding(dp(24), dp(20), dp(24), dp(20));
        mEmptyState.setVisibility(View.GONE);
        LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        emptyParams.topMargin = dp(14);
        mRoot.addView(mEmptyState, emptyParams);

        ScrollView scroll = new ScrollView(requireContext());
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, dp(10), 0, dp(8));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollParams.topMargin = dp(8);
        mRoot.addView(scroll, scrollParams);

        mGrid = new GridLayout(requireContext());
        mGrid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        mGrid.setUseDefaultMargins(false);
        int widthDp = getResources().getConfiguration().screenWidthDp;
        mCardColumns = widthDp >= 1150 ? 3 : (widthDp >= 700 ? 2 : 1);
        mGrid.setColumnCount(mCardColumns);
        scroll.addView(mGrid, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return mRoot;
    }

    private void addFilterButton(LinearLayout parent, Button button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1f);
        params.rightMargin = dp(5);
        parent.addView(button, params);
    }

    private void refreshInstances() {
        if (mProgress != null) mProgress.setVisibility(View.VISIBLE);
        if (mEmptyState != null) mEmptyState.setVisibility(View.GONE);
        if (mGrid != null) mGrid.removeAllViews();

        PojavApplication.sExecutorService.execute(() -> {
            List<Instance> instances;
            try {
                instances = Instances.loadAllInstances();
            } catch (IOException e) {
                postError(e);
                return;
            }
            FragmentActivity activity = getActivity();
            if (!isAdded() || activity == null) return;
            activity.runOnUiThread(() -> {
                if (!isAdded() || mGrid == null) return;
                mAllInstances = instances;
                mProgress.setVisibility(View.GONE);
                renderInstances();
            });
        });
    }

    private void renderInstances() {
        if (mGrid == null || mEmptyState == null) return;
        mGrid.removeAllViews();
        if (mAllInstances.isEmpty()) {
            mEmptyState.setText(R.string.aerix_library_empty);
            mEmptyState.setVisibility(View.VISIBLE);
            return;
        }

        List<Instance> visible = new ArrayList<>();
        for (Instance instance : mAllInstances) {
            if (mFavoritesOnly && !isFavorite(instance)) continue;
            String group = getGroup(instance);
            if (mGroupFilter != null) {
                if (FILTER_UNGROUPED.equals(mGroupFilter) && !group.isEmpty()) continue;
                if (!FILTER_UNGROUPED.equals(mGroupFilter) && !mGroupFilter.equals(group)) continue;
            }
            visible.add(instance);
        }
        Collections.sort(visible, this::compareInstances);
        if (visible.isEmpty()) {
            mEmptyState.setText(R.string.aerix_library_no_filter_matches);
            mEmptyState.setVisibility(View.VISIBLE);
            return;
        }

        mEmptyState.setVisibility(View.GONE);
        for (int i = 0; i < visible.size(); i++) {
            Instance instance = visible.get(i);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(i / mCardColumns),
                    GridLayout.spec(i % mCardColumns, 1, 1f));
            params.width = 0;
            params.height = dp(320);
            params.setMargins(dp(5), dp(5), dp(5), dp(5));
            mGrid.addView(createInstanceCard(instance), params);
        }
    }

    private int compareInstances(Instance left, Instance right) {
        boolean leftPinned = isPinned(left);
        boolean rightPinned = isPinned(right);
        if (leftPinned != rightPinned) return leftPinned ? -1 : 1;

        int result;
        if (mSortMode == 1) {
            result = compareText(left.versionId, right.versionId);
        } else if (mSortMode == 2) {
            long leftTime = LauncherPreferences.DEFAULT_PREF.getLong(lastPlayedKey(left), 0L);
            long rightTime = LauncherPreferences.DEFAULT_PREF.getLong(lastPlayedKey(right), 0L);
            result = Long.compare(rightTime, leftTime);
        } else {
            result = compareText(displayName(left), displayName(right));
        }
        return result != 0 ? result : compareText(displayName(left), displayName(right));
    }

    private static int compareText(String left, String right) {
        return String.CASE_INSENSITIVE_ORDER.compare(left == null ? "" : left, right == null ? "" : right);
    }

    private View createInstanceCard(Instance instance) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(12), dp(15), dp(11));
        card.setBackground(panelBackground());

        TextView name = text(displayName(instance), 16, "#F1F6FC", true);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        String version = Tools.isValidString(instance.versionId) ? instance.versionId : getString(R.string.error_no_version);
        TextView versionText = text(version, 12, "#93B8D4", false);
        LinearLayout.LayoutParams versionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        versionParams.topMargin = dp(3);
        card.addView(versionText, versionParams);

        String normalizedVersion = instance.versionId == null ? "" : instance.versionId.toLowerCase(Locale.ROOT);
        boolean modded = instance.installer != null || normalizedVersion.contains("fabric")
                || normalizedVersion.contains("forge") || normalizedVersion.contains("quilt")
                || normalizedVersion.contains("neoforge") || normalizedVersion.contains("optifine");
        TextView loader = text(modded ? getString(R.string.aerix_modded_instance)
                : getString(R.string.aerix_vanilla_instance), 11, "#B7C9D8", false);
        loader.setPadding(dp(9), dp(4), dp(9), dp(4));
        GradientDrawable badge = new GradientDrawable();
        badge.setColor(Color.argb(42, 105, 180, 235));
        badge.setCornerRadius(dp(10));
        loader.setBackground(badge);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeParams.topMargin = dp(7);
        card.addView(loader, badgeParams);

        LinearLayout metadataActions = new LinearLayout(requireContext());
        metadataActions.setOrientation(LinearLayout.HORIZONTAL);
        Button favorite = button(isFavorite(instance) ? getString(R.string.aerix_favorite_on)
                : getString(R.string.aerix_favorite_off));
        favorite.setOnClickListener(v -> {
            setFavorite(instance, !isFavorite(instance));
            renderInstances();
        });
        Button pin = button(isPinned(instance) ? getString(R.string.aerix_pin_on)
                : getString(R.string.aerix_pin_off));
        pin.setOnClickListener(v -> {
            setPinned(instance, !isPinned(instance));
            renderInstances();
        });
        addActionButton(metadataActions, favorite);
        addActionButton(metadataActions, pin);
        LinearLayout.LayoutParams metadataParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        metadataParams.topMargin = dp(6);
        card.addView(metadataActions, metadataParams);

        Button group = button(groupButtonLabel(instance));
        group.setMaxLines(1);
        group.setEllipsize(TextUtils.TruncateAt.END);
        group.setOnClickListener(v -> editGroup(instance));
        LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        groupParams.topMargin = dp(5);
        card.addView(group, groupParams);

        View spacer = new View(requireContext());
        card.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        Button launch = button(getString(R.string.main_play));
        launch.setContentDescription(getString(R.string.aerix_play_instance, displayName(instance)));
        launch.setOnClickListener(v -> {
            Instances.setSelectedInstance(instance);
            ExtraCore.setValue(ExtraConstants.REFRESH_VERSION_SPINNER, null);
            ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
        });
        card.addView(launch, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        LinearLayout actions = new LinearLayout(requireContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button edit = button(getString(R.string.global_edit));
        edit.setOnClickListener(v -> {
            Instances.setSelectedInstance(instance);
            Tools.swapFragment(requireActivity(), InstanceEditorFragment.class,
                    InstanceEditorFragment.TAG, null);
        });
        Button delete = button(getString(R.string.global_delete));
        delete.setOnClickListener(v -> confirmDelete(instance));
        addActionButton(actions, edit);
        addActionButton(actions, delete);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        actionParams.topMargin = dp(4);
        card.addView(actions, actionParams);
        return card;
    }

    private void addActionButton(LinearLayout parent, Button button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        params.rightMargin = dp(4);
        parent.addView(button, params);
    }

    private void showGroupFilterDialog() {
        Set<String> groups = new LinkedHashSet<>();
        for (Instance instance : mAllInstances) {
            String group = getGroup(instance);
            if (!group.isEmpty()) groups.add(group);
        }
        List<String> sortedGroups = new ArrayList<>(groups);
        Collections.sort(sortedGroups, String.CASE_INSENSITIVE_ORDER);
        String[] labels = new String[sortedGroups.size() + 2];
        labels[0] = getString(R.string.aerix_group_filter_all);
        labels[1] = getString(R.string.aerix_group_filter_ungrouped);
        for (int i = 0; i < sortedGroups.size(); i++) labels[i + 2] = sortedGroups.get(i);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.aerix_group_filter_title)
                .setItems(labels, (dialog, which) -> {
                    if (which == 0) mGroupFilter = null;
                    else if (which == 1) mGroupFilter = FILTER_UNGROUPED;
                    else mGroupFilter = sortedGroups.get(which - 2);
                    android.content.SharedPreferences.Editor editor = LauncherPreferences.DEFAULT_PREF.edit();
                    if (mGroupFilter == null) editor.remove(PREF_GROUP_FILTER);
                    else editor.putString(PREF_GROUP_FILTER, mGroupFilter);
                    editor.apply();
                    updateFilterLabels();
                    renderInstances();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showSortDialog() {
        String[] labels = {
                getString(R.string.aerix_sort_name),
                getString(R.string.aerix_sort_version),
                getString(R.string.aerix_sort_recent)
        };
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.aerix_sort_title)
                .setSingleChoiceItems(labels, mSortMode, (dialog, which) -> {
                    mSortMode = which;
                    LauncherPreferences.DEFAULT_PREF.edit().putInt(PREF_SORT_MODE, mSortMode).apply();
                    dialog.dismiss();
                    updateFilterLabels();
                    renderInstances();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void editGroup(Instance instance) {
        EditText editor = new EditText(requireContext());
        editor.setSingleLine(true);
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editor.setHint(R.string.aerix_group_hint);
        editor.setText(getGroup(instance));
        editor.setSelection(editor.getText().length());
        int pad = dp(20);
        editor.setPadding(pad, dp(8), pad, 0);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.aerix_group_title)
                .setView(editor)
                .setPositiveButton(R.string.global_save, (dialog, which) -> {
                    String group = editor.getText().toString().trim();
                    if (group.isEmpty()) clearGroup(instance);
                    else LauncherPreferences.DEFAULT_PREF.edit().putString(groupKey(instance), group).apply();
                    updateFilterLabels();
                    renderInstances();
                })
                .setNeutralButton(R.string.aerix_group_remove, (dialog, which) -> {
                    clearGroup(instance);
                    updateFilterLabels();
                    renderInstances();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmDelete(Instance instance) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.instance_delete)
                .setMessage(R.string.instance_delete_confirmation)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.global_delete, (dialog, which) ->
                        PojavApplication.sExecutorService.execute(() -> {
                            try {
                                Instances.removeInstance(instance);
                                LauncherPreferences.DEFAULT_PREF.edit()
                                        .remove(favoriteKey(instance)).remove(pinKey(instance))
                                        .remove(groupKey(instance)).remove(lastPlayedKey(instance)).apply();
                                List<Instance> remaining = Instances.loadAllInstances();
                                if (Instances.loadSelectedInstance() == null && !remaining.isEmpty()) {
                                    Instances.setSelectedInstance(remaining.get(0));
                                }
                                ExtraCore.setValue(ExtraConstants.REFRESH_VERSION_SPINNER, null);
                                FragmentActivity activity = getActivity();
                                if (isAdded() && activity != null) activity.runOnUiThread(this::refreshInstances);
                            } catch (IOException e) {
                                postError(e);
                            }
                        }))
                .show();
    }

    private void openCreateFlow() {
        Tools.swapFragment(requireActivity(), ProfileTypeSelectFragment.class,
                ProfileTypeSelectFragment.TAG, null);
    }

    private void postError(Exception e) {
        if (!isAdded()) return;
        FragmentActivity activity = getActivity();
        if (activity != null) activity.runOnUiThread(() -> {
            if (!isAdded()) return;
            if (mProgress != null) mProgress.setVisibility(View.GONE);
            Tools.showError(activity, e);
        });
    }

    private String displayName(Instance instance) {
        if (Tools.isValidString(instance.name)) return instance.name;
        return Tools.isValidString(instance.versionId) ? instance.versionId : getString(R.string.app_short_name);
    }

    private boolean isFavorite(Instance instance) {
        return LauncherPreferences.DEFAULT_PREF.getBoolean(favoriteKey(instance), false);
    }

    private boolean isPinned(Instance instance) {
        return LauncherPreferences.DEFAULT_PREF.getBoolean(pinKey(instance), false);
    }

    private String getGroup(Instance instance) {
        return LauncherPreferences.DEFAULT_PREF.getString(groupKey(instance), "").trim();
    }

    private void setFavorite(Instance instance, boolean favorite) {
        LauncherPreferences.DEFAULT_PREF.edit().putBoolean(favoriteKey(instance), favorite).apply();
    }

    private void setPinned(Instance instance, boolean pinned) {
        LauncherPreferences.DEFAULT_PREF.edit().putBoolean(pinKey(instance), pinned).apply();
    }

    private void clearGroup(Instance instance) {
        LauncherPreferences.DEFAULT_PREF.edit().remove(groupKey(instance)).apply();
    }

    private String groupButtonLabel(Instance instance) {
        String group = getGroup(instance);
        return group.isEmpty() ? getString(R.string.aerix_group_set) : getString(R.string.aerix_group_card_label, group);
    }

    private void updateFilterLabels() {
        if (mFavoritesFilterButton == null) return;
        mFavoritesFilterButton.setText(mFavoritesOnly
                ? getString(R.string.aerix_favorites_only)
                : getString(R.string.aerix_favorites_all));
        String groupLabel;
        if (mGroupFilter == null) groupLabel = getString(R.string.aerix_group_filter_all);
        else if (FILTER_UNGROUPED.equals(mGroupFilter)) groupLabel = getString(R.string.aerix_group_filter_ungrouped);
        else groupLabel = getString(R.string.aerix_group_filter_chip, mGroupFilter);
        mGroupFilterButton.setText(groupLabel);
        int sortString = mSortMode == 1 ? R.string.aerix_sort_version
                : mSortMode == 2 ? R.string.aerix_sort_recent : R.string.aerix_sort_name;
        mSortButton.setText(getString(R.string.aerix_sort_chip, getString(sortString)));
    }

    private String stableId(Instance instance) {
        return Instances.getStableId(instance);
    }

    private String favoriteKey(Instance instance) { return META_FAVORITE + stableId(instance); }
    private String pinKey(Instance instance) { return META_PINNED + stableId(instance); }
    private String groupKey(Instance instance) { return META_GROUP + stableId(instance); }
    private String lastPlayedKey(Instance instance) { return META_LAST_PLAYED + stableId(instance); }

    private Button button(String label) {
        Button button = new Button(requireContext());
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(11);
        button.setAllCaps(false);
        button.setMinHeight(dp(0));
        button.setMinimumHeight(dp(0));
        button.setMinWidth(dp(0));
        button.setMinimumWidth(dp(0));
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackgroundResource(R.drawable.aerix_nav_button);
        button.setPadding(dp(6), 0, dp(6), 0);
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
