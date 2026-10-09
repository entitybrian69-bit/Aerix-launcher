package net.kdt.pojavlaunch.fragments;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** A responsive, native instance library backed by the launcher's existing instance store. */
public class InstanceLibraryFragment extends Fragment {
    public static final String TAG = "InstanceLibraryFragment";

    private LinearLayout mRoot;
    private GridLayout mGrid;
    private ProgressBar mProgress;
    private TextView mEmptyState;
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
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        refreshInstances();
    }

    private View buildView() {
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

    private void refreshInstances() {
        if (mProgress != null) mProgress.setVisibility(View.VISIBLE);
        if (mEmptyState != null) mEmptyState.setVisibility(View.GONE);
        if (mGrid != null) mGrid.removeAllViews();

        PojavApplication.sExecutorService.execute(() -> {
            List<Instance> instances;
            try {
                instances = Instances.loadAllInstances();
                Collections.sort(instances, (Instance left, Instance right) -> String.CASE_INSENSITIVE_ORDER.compare(
                        displayName(left), displayName(right)));
            } catch (IOException e) {
                postError(e);
                return;
            }
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded() || mGrid == null) return;
                mProgress.setVisibility(View.GONE);
                mGrid.removeAllViews();
                if (instances.isEmpty()) {
                    mEmptyState.setVisibility(View.VISIBLE);
                    return;
                }
                mEmptyState.setVisibility(View.GONE);
                for (int i = 0; i < instances.size(); i++) {
                    Instance instance = instances.get(i);
                    GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                            GridLayout.spec(i / mCardColumns),
                            GridLayout.spec(i % mCardColumns, 1, 1f));
                    params.width = 0;
                    params.height = dp(188);
                    params.setMargins(dp(5), dp(5), dp(5), dp(5));
                    mGrid.addView(createInstanceCard(instance), params);
                }
            });
        });
    }

    private View createInstanceCard(Instance instance) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(12));
        card.setBackground(panelBackground());

        TextView name = text(displayName(instance), 17, "#F1F6FC", true);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        String version = Tools.isValidString(instance.versionId) ? instance.versionId : getString(R.string.error_no_version);
        TextView versionText = text(version, 12, "#93B8D4", false);
        LinearLayout.LayoutParams versionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        versionParams.topMargin = dp(4);
        card.addView(versionText, versionParams);

        String normalizedVersion = instance.versionId == null ? "" : instance.versionId.toLowerCase(Locale.ROOT);
        boolean modded = instance.installer != null || normalizedVersion.contains("fabric")
                || normalizedVersion.contains("forge") || normalizedVersion.contains("quilt")
                || normalizedVersion.contains("neoforge") || normalizedVersion.contains("optifine");
        TextView loader = text(modded ? getString(R.string.aerix_modded_instance)
                : getString(R.string.aerix_vanilla_instance), 11, "#B7C9D8", false);
        loader.setPadding(dp(9), dp(5), dp(9), dp(5));
        GradientDrawable badge = new GradientDrawable();
        badge.setColor(Color.argb(42, 105, 180, 235));
        badge.setCornerRadius(dp(10));
        loader.setBackground(badge);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeParams.topMargin = dp(10);
        card.addView(loader, badgeParams);

        View spacer = new View(requireContext());
        card.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        Button launch = button(getString(R.string.main_play));
        launch.setOnClickListener(v -> {
            Instances.setSelectedInstance(instance);
            ExtraCore.setValue(ExtraConstants.REFRESH_VERSION_SPINNER, null);
            ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
        });
        card.addView(launch, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

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
        LinearLayout.LayoutParams actionButtonParams = new LinearLayout.LayoutParams(
                0, dp(36), 1f);
        actionButtonParams.topMargin = dp(6);
        actionButtonParams.rightMargin = dp(4);
        actions.addView(edit, actionButtonParams);
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                0, dp(36), 1f);
        deleteParams.topMargin = dp(6);
        deleteParams.leftMargin = dp(4);
        actions.addView(delete, deleteParams);
        card.addView(actions);

        return card;
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
                                List<Instance> remaining = Instances.loadAllInstances();
                                if (Instances.loadSelectedInstance() == null && !remaining.isEmpty()) {
                                    Instances.setSelectedInstance(remaining.get(0));
                                }
                                ExtraCore.setValue(ExtraConstants.REFRESH_VERSION_SPINNER, null);
                                if (isAdded()) requireActivity().runOnUiThread(this::refreshInstances);
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
            if (isAdded()) Tools.showError(activity, e);
        });
    }

    private String displayName(Instance instance) {
        if (Tools.isValidString(instance.name)) return instance.name;
        return Tools.isValidString(instance.versionId) ? instance.versionId : getString(R.string.app_short_name);
    }

    private Button button(String label) {
        Button button = new Button(requireContext());
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackgroundResource(R.drawable.aerix_nav_button);
        button.setPadding(dp(8), 0, dp(8), 0);
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
