package net.kdt.pojavlaunch.fragments;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.AuthType;
import net.kdt.pojavlaunch.authenticator.accounts.Account;
import net.kdt.pojavlaunch.authenticator.accounts.Accounts;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.utils.AerixThemeManager;

import java.io.IOException;
import java.util.List;

/** Clear account picker/manager using the launcher's existing Microsoft, Ely.by, and local flows. */
public class AccountManagerFragment extends Fragment {
    public static final String TAG = "AccountManagerFragment";

    private LinearLayout mAccountList;
    private TextView mStatus;

    public AccountManagerFragment() {
        super();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(4), dp(4), dp(4), dp(4));
        root.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout header = new LinearLayout(requireContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(12), dp(16), dp(12));
        header.setBackground(panelBackground());
        LinearLayout titleStack = new LinearLayout(requireContext());
        titleStack.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(getString(R.string.aerix_account_title), 22, true);
        TextView subtitle = text(getString(R.string.aerix_account_subtitle), 12, false);
        titleStack.addView(title);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(3);
        titleStack.addView(subtitle, subtitleParams);
        header.addView(titleStack, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button addButton = button(getString(R.string.aerix_account_add));
        addButton.setOnClickListener(v -> openAuthMethods());
        header.addView(addButton, new LinearLayout.LayoutParams(dp(150), dp(44)));
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mStatus = text(getString(R.string.aerix_account_empty), 14, false);
        mStatus.setGravity(Gravity.CENTER);
        mStatus.setPadding(dp(22), dp(20), dp(22), dp(20));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(10);
        root.addView(mStatus, statusParams);

        ScrollView scroll = new ScrollView(requireContext());
        scroll.setClipToPadding(false);
        scroll.setFillViewport(false);
        scroll.setPadding(0, dp(8), 0, dp(8));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollParams.topMargin = dp(4);
        root.addView(scroll, scrollParams);
        mAccountList = new LinearLayout(requireContext());
        mAccountList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(mAccountList, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshAccounts();
    }

    private void openAuthMethods() {
        Tools.swapFragment(requireActivity(), SelectAuthFragment.class, SelectAuthFragment.TAG, null);
    }

    private void refreshAccounts() {
        if (mStatus != null) mStatus.setText(R.string.aerix_account_empty);
        PojavApplication.sExecutorService.execute(() -> {
            try {
                List<Account> accounts = Accounts.load().accounts;
                Account current = Accounts.getCurrent();
                if (current == null && !accounts.isEmpty()) {
                    Accounts.setCurrent(accounts.get(0));
                    current = accounts.get(0);
                }
                Account selected = current;
                FragmentActivity activity = getActivity();
                if (!isAdded() || activity == null) return;
                activity.runOnUiThread(() -> {
                    if (!isAdded() || mAccountList == null || mStatus == null) return;
                    renderAccounts(accounts, selected);
                    ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
                });
            } catch (IOException e) {
                FragmentActivity activity = getActivity();
                if (!isAdded() || activity == null) return;
                activity.runOnUiThread(() -> {
                    if (isAdded() && mStatus != null) {
                        mStatus.setText(getString(R.string.aerix_account_load_error, e.getLocalizedMessage()));
                    }
                });
            }
        });
    }

    private void renderAccounts(List<Account> accounts, Account current) {
        mAccountList.removeAllViews();
        mStatus.setVisibility(accounts.isEmpty() ? View.VISIBLE : View.GONE);
        if (accounts.isEmpty()) return;
        for (Account account : accounts) {
            boolean selected = current != null && current.mSaveLocation != null
                    && account.mSaveLocation != null
                    && current.mSaveLocation.getName().equals(account.mSaveLocation.getName());
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardParams.bottomMargin = dp(10);
            mAccountList.addView(accountCard(account, selected), cardParams);
        }
    }

    private View accountCard(Account account, boolean selected) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(panelBackground());
        LinearLayout summary = new LinearLayout(requireContext());
        summary.setGravity(Gravity.CENTER_VERTICAL);
        ImageView avatar = new ImageView(requireContext());
        avatar.setBackground(navBackground());
        avatar.setPadding(dp(8), dp(8), dp(8), dp(8));
        Bitmap skin = account.getSkinFace();
        if (skin == null) avatar.setImageResource(R.drawable.ic_aerix_account);
        else avatar.setImageBitmap(skin);
        summary.addView(avatar, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout info = new LinearLayout(requireContext());
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(12), 0, 0, 0);
        TextView username = text(account.username, 16, true);
        username.setMaxLines(1);
        username.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView provider = text(accountProvider(account), 11, false);
        info.addView(username);
        LinearLayout.LayoutParams providerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        providerParams.topMargin = dp(3);
        info.addView(provider, providerParams);
        summary.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView badge = text(selected ? getString(R.string.aerix_account_current) : "", 11, true);
        badge.setTextColor(selected ? AerixThemeManager.accentColor(requireContext(), AerixThemeManager.SECTION_ACCOUNT)
                : Color.TRANSPARENT);
        summary.addView(badge, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(summary);

        LinearLayout actions = new LinearLayout(requireContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        actionsParams.topMargin = dp(10);
        card.addView(actions, actionsParams);
        Button useButton = button(selected ? getString(R.string.aerix_account_current)
                : getString(R.string.aerix_account_use));
        useButton.setEnabled(!selected);
        useButton.setOnClickListener(v -> selectAccount(account));
        actions.addView(useButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        Button removeButton = button(getString(R.string.aerix_account_remove));
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(dp(112), ViewGroup.LayoutParams.MATCH_PARENT);
        removeParams.leftMargin = dp(8);
        removeButton.setOnClickListener(v -> confirmRemove(account));
        actions.addView(removeButton, removeParams);
        return card;
    }

    private void selectAccount(Account account) {
        Accounts.setCurrent(account);
        ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
        Toast.makeText(requireContext(), getString(R.string.aerix_account_current), Toast.LENGTH_SHORT).show();
        refreshAccounts();
    }

    private void confirmRemove(Account account) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.aerix_account_remove_title)
                .setMessage(getString(R.string.aerix_account_remove_message, account.username))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.aerix_account_remove, (dialog, which) -> {
                    Accounts.delete(account);
                    ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
                    refreshAccounts();
                })
                .show();
    }

    private String accountProvider(Account account) {
        if (account.authType == AuthType.MICROSOFT) return getString(R.string.auth_select_microsoft);
        if (account.authType == AuthType.ELY_BY) return getString(R.string.auth_select_elyby);
        return getString(R.string.auth_select_local);
    }

    private Button button(String label) {
        Button button = new Button(requireContext());
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(12);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setMinHeight(dp(40));
        button.setBackground(navBackground());
        return button;
    }

    private TextView text(String label, int size, boolean bold) {
        TextView view = new TextView(requireContext());
        view.setText(label);
        view.setTextColor(Color.WHITE);
        view.setTextSize(size);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private GradientDrawable panelBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.argb(88, 22, 46, 70));
        drawable.setCornerRadius(dp(22));
        drawable.setStroke(dp(1), Color.argb(150, 225, 245, 255));
        return drawable;
    }

    private GradientDrawable navBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.argb(55, 220, 245, 255));
        drawable.setCornerRadius(dp(14));
        drawable.setStroke(dp(1), Color.argb(145, 230, 248, 255));
        return drawable;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
