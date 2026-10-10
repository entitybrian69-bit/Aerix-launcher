package net.kdt.pojavlaunch.fragments;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import net.kdt.pojavlaunch.utils.AerixThemeManager;
import net.kdt.pojavlaunch.utils.PrismGlass;

import java.io.IOException;
import java.io.OutputStream;
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
    private LinearLayout mDetailHost;
    private EditText mSearch;
    private boolean mHasDetailPane;
    private String mSelectedId;
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
    private Instance mPendingBackup;
    private final ActivityResultLauncher<String> mBackupLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/zip"), this::onBackupDestination);

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
        // The header floats on the wallpaper; only the controls have a glass footprint.

        LinearLayout titleStack = new LinearLayout(requireContext());
        titleStack.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(getString(R.string.aerix_library_title), 22, "#183148", true);
        TextView subtitle = text(getString(R.string.aerix_library_subtitle), 12, "#39546B", false);
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

        mSearch = new EditText(requireContext());
        mSearch.setSingleLine(true);
        mSearch.setTextSize(14);
        mSearch.setHint(R.string.prism_library_search);
        mSearch.setTextColor(Color.rgb(22, 46, 65));
        mSearch.setHintTextColor(Color.rgb(86, 112, 130));
        mSearch.setBackgroundResource(R.drawable.prism_create_side_button);
        mSearch.setPadding(dp(16), 0, dp(16), 0);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(43));
        searchParams.setMargins(dp(5), dp(8), dp(5), dp(2));
        mRoot.addView(mSearch, searchParams);
        mSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderInstances();
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        mProgress = new ProgressBar(requireContext());
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        progressParams.gravity = Gravity.CENTER;
        mRoot.addView(mProgress, progressParams);
        mEmptyState = text(getString(R.string.aerix_library_empty), 15, "#254358", false);
        mEmptyState.setGravity(Gravity.CENTER);
        mEmptyState.setPadding(dp(24), dp(20), dp(24), dp(20));
        mEmptyState.setVisibility(View.GONE);
        mRoot.addView(mEmptyState, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.HORIZONTAL);
        mRoot.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        ScrollView scroll = new ScrollView(requireContext());
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, dp(8), 0, dp(80));
        body.addView(scroll, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        mGrid = new GridLayout(requireContext());
        mGrid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        mGrid.setUseDefaultMargins(false);
        int widthDp = getResources().getConfiguration().screenWidthDp;
        mHasDetailPane = widthDp >= 700;
        mCardColumns = widthDp >= 1150 ? 3 : (widthDp >= 700 ? 2 : 1);
        mGrid.setColumnCount(mCardColumns);
        scroll.addView(mGrid, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (mHasDetailPane) {
            ScrollView detailScroll = new ScrollView(requireContext());
            detailScroll.setFillViewport(true);
            LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(dp(238),
                    ViewGroup.LayoutParams.MATCH_PARENT);
            detailParams.setMargins(dp(7), dp(7), dp(5), dp(75));
            body.addView(detailScroll, detailParams);
            mDetailHost = new LinearLayout(requireContext());
            mDetailHost.setOrientation(LinearLayout.VERTICAL);
            PrismGlass.apply(mDetailHost);
            detailScroll.addView(mDetailHost);
        } else {
            mDetailHost = null;
        }
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
        if (mDetailHost != null) mDetailHost.removeAllViews();
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
            String query = mSearch == null ? "" : mSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !displayName(instance).toLowerCase(Locale.ROOT).contains(query)
                    && (instance.versionId == null || !instance.versionId.toLowerCase(Locale.ROOT).contains(query))) continue;
            visible.add(instance);
        }
        Collections.sort(visible, this::compareInstances);
        if (visible.isEmpty()) {
            mEmptyState.setText(R.string.aerix_library_no_filter_matches);
            mEmptyState.setVisibility(View.VISIBLE);
            return;
        }

        mEmptyState.setVisibility(View.GONE);
        Instance selected = visible.get(0);
        for (Instance item : visible) {
            if (stableId(item).equals(mSelectedId)) { selected = item; break; }
        }
        mSelectedId = stableId(selected);
        if (mDetailHost != null) mDetailHost.addView(createDetailContent(selected));
        for (int i = 0; i < visible.size(); i++) {
            Instance instance = visible.get(i);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(i / mCardColumns),
                    GridLayout.spec(i % mCardColumns, 1, 1f));
            params.width = 0;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
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

    private int coverFor(Instance instance) {
        int index = (stableId(instance).hashCode() & Integer.MAX_VALUE) % 3;
        return index == 0 ? R.drawable.prism_cover_river
                : index == 1 ? R.drawable.prism_cover_cherry : R.drawable.prism_cover_sunset;
    }

    private View createInstanceCard(Instance instance) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(9));
        PrismGlass.apply(card);
        ImageView cover = new ImageView(requireContext());
        cover.setImageResource(coverFor(instance));
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setContentDescription(getString(R.string.prism_cover_illustration));
        card.addView(cover, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(95)));
        TextView name = text(displayName(instance), 16, "#152D42", true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-1, -2);
        nameParams.topMargin = dp(7);
        card.addView(name, nameParams);
        String version = Tools.isValidString(instance.versionId) ? instance.versionId : getString(R.string.error_no_version);
        TextView versionText = text(version, 12, "#36566B", false);
        versionText.setSingleLine(true);
        versionText.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(versionText);
        LinearLayout actions = new LinearLayout(requireContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button details = lightButton(getString(R.string.prism_library_details));
        details.setOnClickListener(v -> showDetails(instance));
        addActionButton(actions, details);
        Button play = lightButton(getString(R.string.main_play));
        play.setContentDescription(getString(R.string.aerix_play_instance, displayName(instance)));
        play.setOnClickListener(v -> launchInstance(instance));
        addActionButton(actions, play);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, dp(39));
        actionsParams.topMargin = dp(7);
        card.addView(actions, actionsParams);
        card.setOnClickListener(v -> showDetails(instance));
        return card;
    }

    private Button lightButton(String label) {
        Button button = button(label);
        button.setBackgroundResource(R.drawable.prism_create_side_button);
        button.setTextColor(Color.rgb(20, 47, 65));
        button.setTextSize(12);
        return button;
    }

    private void showDetails(Instance instance) {
        mSelectedId = stableId(instance);
        if (mDetailHost != null) {
            mDetailHost.removeAllViews();
            mDetailHost.addView(createDetailContent(instance));
        } else {
            ScrollView detailScroll = new ScrollView(requireContext());
            detailScroll.addView(createDetailContent(instance));
            new AlertDialog.Builder(requireContext())
                    .setView(detailScroll)
                    .setNegativeButton(android.R.string.cancel, null).show();
        }
    }

    private void launchInstance(Instance instance) {
        Instances.setSelectedInstance(instance);
        ExtraCore.setValue(ExtraConstants.REFRESH_VERSION_SPINNER, null);
        ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
    }

    private View createDetailContent(Instance instance) {
        LinearLayout detail = new LinearLayout(requireContext());
        detail.setOrientation(LinearLayout.VERTICAL);
        detail.setPadding(dp(12), dp(12), dp(12), dp(14));
        if (!mHasDetailPane) PrismGlass.apply(detail);
        ImageView cover = new ImageView(requireContext());
        cover.setImageResource(coverFor(instance));
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setContentDescription(getString(R.string.prism_cover_illustration));
        detail.addView(cover, new LinearLayout.LayoutParams(-1, dp(93)));
        TextView name = text(displayName(instance), 17, "#152D42", true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(8);
        detail.addView(name, titleParams);
        String version = Tools.isValidString(instance.versionId) ? instance.versionId : getString(R.string.error_no_version);
        detail.addView(text(version, 12, "#36566B", false));
        String normalized = version.toLowerCase(Locale.ROOT);
        boolean modded = instance.installer != null || normalized.contains("fabric")
                || normalized.contains("forge") || normalized.contains("quilt")
                || normalized.contains("optifine");
        detail.addView(text(getString(modded ? R.string.aerix_modded_instance
                : R.string.aerix_vanilla_instance), 12, "#36566B", false));
        Button play = lightButton(getString(R.string.main_play));
        play.setOnClickListener(v -> launchInstance(instance));
        addDetailAction(detail, play);
        Button favorite = lightButton(getString(isFavorite(instance) ? R.string.aerix_favorite_on : R.string.aerix_favorite_off));
        favorite.setOnClickListener(v -> { setFavorite(instance, !isFavorite(instance)); renderInstances(); });
        addDetailAction(detail, favorite);
        Button pin = lightButton(getString(isPinned(instance) ? R.string.aerix_pin_on : R.string.aerix_pin_off));
        pin.setOnClickListener(v -> { setPinned(instance, !isPinned(instance)); renderInstances(); });
        addDetailAction(detail, pin);
        Button group = lightButton(groupButtonLabel(instance));
        group.setOnClickListener(v -> editGroup(instance));
        addDetailAction(detail, group);
        Button edit = lightButton(getString(R.string.global_edit));
        edit.setOnClickListener(v -> {
            Instances.setSelectedInstance(instance);
            Tools.swapFragment(requireActivity(), InstanceEditorFragment.class, InstanceEditorFragment.TAG, null);
        });
        addDetailAction(detail, edit);
        Button clone = lightButton(getString(R.string.aerix_clone_action));
        clone.setOnClickListener(v -> confirmClone(instance));
        addDetailAction(detail, clone);
        Button backup = lightButton(getString(R.string.aerix_backup_action));
        backup.setOnClickListener(v -> startBackup(instance));
        addDetailAction(detail, backup);
        Button delete = lightButton(getString(R.string.global_delete));
        delete.setOnClickListener(v -> confirmDelete(instance));
        addDetailAction(detail, delete);
        return detail;
    }

    private void addDetailAction(LinearLayout detail, Button action) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(38));
        params.topMargin = dp(5);
        detail.addView(action, params);
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

    private void confirmClone(Instance instance) {
        int message = instance.sharedData ? R.string.aerix_clone_shared_message : R.string.aerix_clone_message;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.aerix_clone_title)
                .setMessage(getString(message, displayName(instance)))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.aerix_clone_action, (dialog, which) ->
                        PojavApplication.sExecutorService.execute(() -> {
                            try {
                                Instances.cloneInstance(instance, displayName(instance) + " Copy");
                                FragmentActivity activity = getActivity();
                                if (isAdded() && activity != null) activity.runOnUiThread(() -> {
                                    if (!isAdded()) return;
                                    Toast.makeText(activity, R.string.aerix_clone_complete, Toast.LENGTH_SHORT).show();
                                    refreshInstances();
                                });
                            } catch (IOException e) {
                                postError(e);
                            }
                        }))
                .show();
    }

    private void startBackup(Instance instance) {
        mPendingBackup = instance;
        String safeName = displayName(instance).replaceAll("[^A-Za-z0-9._-]", "_");
        mBackupLauncher.launch(safeName + "-backup.zip");
    }

    private void onBackupDestination(Uri uri) {
        Instance instance = mPendingBackup;
        mPendingBackup = null;
        if (uri == null || instance == null || !isAdded()) return;
        ContentResolver resolver = requireContext().getContentResolver();
        PojavApplication.sExecutorService.execute(() -> {
            Exception failure = null;
            try {
                try (OutputStream output = resolver.openOutputStream(uri, "w")) {
                    if (output == null) throw new IOException("The selected destination could not be opened");
                    Instances.writeBackup(instance, output);
                }
            } catch (Exception e) {
                failure = e;
            }
            final Exception backupFailure = failure;
            FragmentActivity activity = getActivity();
            if (activity == null) return;
            activity.runOnUiThread(() -> {
                if (!isAdded()) return;
                if (backupFailure != null) Tools.showError(activity, backupFailure);
                else Toast.makeText(activity, R.string.aerix_backup_complete, Toast.LENGTH_LONG).show();
            });
        });
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
        AerixThemeManager.tintButton(button, requireContext(), AerixThemeManager.SECTION_LIBRARY);
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
        drawable.setColor(Color.argb(136, 31, 58, 82));
        drawable.setCornerRadius(dp(20));
        drawable.setStroke(dp(1), Color.argb(170, 222, 246, 255));
        return drawable;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
