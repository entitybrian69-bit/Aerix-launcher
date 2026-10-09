package net.kdt.pojavlaunch.fragments;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.utils.MinecraftServerListStore;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Manages the selected profile's vanilla-compatible multiplayer servers.dat file. */
public class ServerManagerFragment extends Fragment {
    public static final String TAG = "ServerManagerFragment";

    private LinearLayout mRows;
    private TextView mProfile;
    private TextView mStatus;
    private ProgressBar mProgress;
    private Button mAddButton;
    private File mServerFile;
    private List<MinecraftServerListStore.ServerEntry> mEntries = new ArrayList<>();
    private boolean mCanEdit;

    public ServerManagerFragment() {
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
        reloadServers();
    }

    private View buildView() {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(4), dp(4), dp(4), dp(4));

        LinearLayout header = new LinearLayout(requireContext());
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(14), dp(20), dp(14));
        header.setBackground(panelBackground());
        header.addView(text(getString(R.string.aerix_servers_title), 22, "#F1F6FC", true));
        TextView subtitle = text(getString(R.string.aerix_servers_subtitle), 12, "#AABCD0", false);
        LinearLayout.LayoutParams subtitleParams = wrapParams();
        subtitleParams.topMargin = dp(3);
        header.addView(subtitle, subtitleParams);
        mProfile = text(getString(R.string.aerix_servers_no_instance), 12, "#D3E3F1", true);
        LinearLayout.LayoutParams profileParams = wrapParams();
        profileParams.topMargin = dp(8);
        header.addView(mProfile, profileParams);
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(requireContext());
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        mStatus = text(getString(R.string.aerix_servers_loading), 12, "#AABCD0", false);
        actions.addView(mStatus, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        mProgress = new ProgressBar(requireContext());
        mProgress.setIndeterminate(true);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(24), dp(24));
        progressParams.rightMargin = dp(10);
        actions.addView(mProgress, progressParams);
        mAddButton = button(getString(R.string.aerix_servers_add));
        mAddButton.setEnabled(false);
        mAddButton.setOnClickListener(v -> showServerEditor(-1));
        actions.addView(mAddButton, new LinearLayout.LayoutParams(dp(142), dp(44)));
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = dp(10);
        root.addView(actions, actionsParams);

        ScrollView scroll = new ScrollView(requireContext());
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollParams.topMargin = dp(8);
        root.addView(scroll, scrollParams);

        mRows = new LinearLayout(requireContext());
        mRows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(mRows, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView note = text(getString(R.string.aerix_servers_note), 12, "#AABCD0", false);
        note.setPadding(dp(4), dp(9), dp(4), dp(2));
        root.addView(note, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    private void reloadServers() {
        if (mRows == null || mProgress == null) return;
        mCanEdit = false;
        mServerFile = null;
        mAddButton.setEnabled(false);
        mProgress.setVisibility(View.VISIBLE);
        mStatus.setText(R.string.aerix_servers_loading);
        mRows.removeAllViews();
        PojavApplication.sExecutorService.execute(() -> {
            Instance selected = Instances.loadSelectedInstance();
            File file = selected == null ? null : new File(selected.getGameDirectory(), "servers.dat");
            List<MinecraftServerListStore.ServerEntry> entries = new ArrayList<>();
            Exception failure = null;
            if (file != null) {
                try {
                    entries = MinecraftServerListStore.load(file);
                } catch (Exception e) {
                    failure = e;
                }
            }
            final List<MinecraftServerListStore.ServerEntry> loadedEntries = entries;
            final Exception loadFailure = failure;
            postToActivity(activity -> {
                if (mRows == null) return;
                if (selected == null) {
                    mProfile.setText(R.string.aerix_servers_no_instance);
                    mStatus.setText(R.string.aerix_servers_select_instance);
                    mProgress.setVisibility(View.GONE);
                    return;
                }
                String profileName = selected.name == null ? "" : selected.name;
                String version = selected.versionId == null ? "" : selected.versionId;
                mProfile.setText(getString(R.string.aerix_servers_profile, profileName, version));
                if (loadFailure != null) {
                    mStatus.setText(getString(R.string.aerix_servers_load_error, loadFailure.getLocalizedMessage()));
                    mProgress.setVisibility(View.GONE);
                    return;
                }
                mServerFile = file;
                mEntries = loadedEntries;
                mCanEdit = true;
                mAddButton.setEnabled(true);
                mProgress.setVisibility(View.GONE);
                if (mEntries.isEmpty()) mStatus.setText(R.string.aerix_servers_empty);
                else mStatus.setText(getResources().getQuantityString(
                        R.plurals.aerix_servers_count, mEntries.size(), mEntries.size()));
                renderEntries();
            });
        });
    }

    private void renderEntries() {
        if (mRows == null) return;
        mRows.removeAllViews();
        if (mEntries.isEmpty()) return;
        for (int i = 0; i < mEntries.size(); i++) {
            MinecraftServerListStore.ServerEntry entry = mEntries.get(i);
            final int position = i;
            LinearLayout card = new LinearLayout(requireContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(15), dp(12), dp(15), dp(12));
            card.setBackground(panelBackground());

            TextView name = text(entry.name, 16, "#F1F6FC", true);
            name.setMaxLines(1);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.addView(name, wrapParams());
            TextView address = text(entry.address, 13, "#AAC5DD", false);
            address.setTextIsSelectable(true);
            LinearLayout.LayoutParams addressParams = wrapParams();
            addressParams.topMargin = dp(4);
            card.addView(address, addressParams);

            LinearLayout actions = new LinearLayout(requireContext());
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.topMargin = dp(8);
            card.addView(actions, rowParams);
            addAction(actions, getString(R.string.aerix_servers_copy), v -> copyAddress(entry.address));
            addAction(actions, getString(R.string.aerix_servers_edit), v -> showServerEditor(position));
            addAction(actions, getString(R.string.aerix_servers_delete), v -> confirmDelete(position));

            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardParams.bottomMargin = dp(8);
            mRows.addView(card, cardParams);
        }
    }

    private void addAction(LinearLayout row, String label, View.OnClickListener listener) {
        Button action = button(label);
        action.setTextSize(12);
        action.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(40), 1f);
        params.rightMargin = dp(4);
        params.leftMargin = dp(4);
        row.addView(action, params);
    }

    private void showServerEditor(int position) {
        if (!mCanEdit || mServerFile == null) return;
        boolean editing = position >= 0 && position < mEntries.size();
        MinecraftServerListStore.ServerEntry selected = editing ? mEntries.get(position) : null;
        LinearLayout fields = new LinearLayout(requireContext());
        fields.setPadding(dp(20), dp(8), dp(20), dp(2));
        fields.setOrientation(LinearLayout.VERTICAL);

        EditText nameInput = new EditText(requireContext());
        nameInput.setSingleLine(true);
        nameInput.setHint(R.string.aerix_servers_name_hint);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        if (selected != null) nameInput.setText(selected.name);
        fields.addView(nameInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        EditText addressInput = new EditText(requireContext());
        addressInput.setSingleLine(true);
        addressInput.setHint(R.string.aerix_servers_address_hint);
        addressInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        if (selected != null) addressInput.setText(selected.address);
        fields.addView(addressInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(editing ? R.string.aerix_servers_edit_title : R.string.aerix_servers_add_title)
                .setView(fields)
                .setPositiveButton(R.string.aerix_servers_save, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            String address = addressInput.getText().toString().trim();
            if (name.isEmpty() || name.length() > 80 || containsLineBreak(name)) {
                nameInput.setError(getString(R.string.aerix_servers_invalid_name));
                return;
            }
            if (!isValidAddress(address)) {
                addressInput.setError(getString(R.string.aerix_servers_invalid_address));
                return;
            }
            ArrayList<MinecraftServerListStore.ServerEntry> updated = copyEntries(mEntries);
            if (editing) {
                updated.get(position).name = name;
                updated.get(position).address = address;
            } else {
                updated.add(new MinecraftServerListStore.ServerEntry(name, address));
            }
            dialog.dismiss();
            persistEntries(updated);
        }));
        dialog.show();
    }

    private void confirmDelete(int position) {
        if (!mCanEdit || position < 0 || position >= mEntries.size()) return;
        MinecraftServerListStore.ServerEntry entry = mEntries.get(position);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.aerix_servers_delete_title)
                .setMessage(getString(R.string.aerix_servers_delete_message, entry.name))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.aerix_servers_delete, (dialog, which) -> {
                    ArrayList<MinecraftServerListStore.ServerEntry> updated = copyEntries(mEntries);
                    updated.remove(position);
                    persistEntries(updated);
                })
                .show();
    }

    private void persistEntries(List<MinecraftServerListStore.ServerEntry> updated) {
        if (!mCanEdit || mServerFile == null) return;
        File target = mServerFile;
        mCanEdit = false;
        mAddButton.setEnabled(false);
        mProgress.setVisibility(View.VISIBLE);
        mStatus.setText(R.string.aerix_servers_saving);
        PojavApplication.sExecutorService.execute(() -> {
            Exception failure = null;
            try {
                MinecraftServerListStore.save(target, updated);
            } catch (Exception e) {
                failure = e;
            }
            final Exception saveFailure = failure;
            postToActivity(activity -> {
                if (mRows == null) return;
                if (saveFailure != null) {
                    mCanEdit = true;
                    mAddButton.setEnabled(true);
                    mProgress.setVisibility(View.GONE);
                    mStatus.setText(getString(R.string.aerix_servers_save_error, saveFailure.getLocalizedMessage()));
                    return;
                }
                mEntries = updated;
                mCanEdit = true;
                mAddButton.setEnabled(true);
                mProgress.setVisibility(View.GONE);
                mStatus.setText(mEntries.isEmpty() ? getString(R.string.aerix_servers_empty)
                        : getResources().getQuantityString(R.plurals.aerix_servers_count,
                        mEntries.size(), mEntries.size()));
                renderEntries();
                Toast.makeText(activity, R.string.aerix_servers_saved, Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void copyAddress(String address) {
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.aerix_servers_address_label), address));
        Toast.makeText(requireContext(), R.string.aerix_servers_address_copied, Toast.LENGTH_SHORT).show();
    }

    private void postToActivity(ActivityAction action) {
        FragmentActivity activity = getActivity();
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            if (isAdded() && getActivity() == activity) action.run(activity);
        });
    }

    private static ArrayList<MinecraftServerListStore.ServerEntry> copyEntries(
            List<MinecraftServerListStore.ServerEntry> source) {
        ArrayList<MinecraftServerListStore.ServerEntry> result = new ArrayList<>(source.size());
        for (MinecraftServerListStore.ServerEntry entry : source) result.add(entry.copy());
        return result;
    }

    private static boolean isValidAddress(String address) {
        if (address == null || address.isEmpty() || address.length() > 255) return false;
        for (int i = 0; i < address.length(); i++) {
            if (Character.isWhitespace(address.charAt(i)) || Character.isISOControl(address.charAt(i))) return false;
        }
        return true;
    }

    private static boolean containsLineBreak(String text) {
        return text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0;
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
        drawable.setCornerRadius(dp(18));
        drawable.setStroke(dp(1), Color.argb(42, 121, 156, 191));
        return drawable;
    }

    private LinearLayout.LayoutParams wrapParams() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private interface ActivityAction {
        void run(FragmentActivity activity);
    }
}
