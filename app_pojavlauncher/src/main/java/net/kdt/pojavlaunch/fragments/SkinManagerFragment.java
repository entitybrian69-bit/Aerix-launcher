package net.kdt.pojavlaunch.fragments;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.AuthType;
import net.kdt.pojavlaunch.authenticator.accounts.Account;
import net.kdt.pojavlaunch.authenticator.accounts.Accounts;
import net.kdt.pojavlaunch.utils.AerixThemeManager;
import net.kdt.pojavlaunch.utils.PrismGlass;
import net.kdt.pojavlaunch.utils.MinecraftSkinLookup;
import net.kdt.pojavlaunch.utils.MinecraftSkinUploader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Safe, user-initiated skin preview and upload for signed-in Microsoft Minecraft accounts. */
public class SkinManagerFragment extends Fragment {
    public static final String TAG = "SkinManagerFragment";
    private static final int MAX_SKIN_BYTES = 1024 * 1024;

    private ImageView mPreview;
    private TextView mAccountLabel;
    private TextView mStatus;
    private EditText mSearchField;
    private Button mSearchButton;
    private Spinner mVariant;
    private Button mUploadButton;
    private byte[] mSkinBytes;
    private String mSearchResultName;
    private boolean mSearchInProgress;
    private volatile int mSearchGeneration;
    private Bitmap mPreviewBitmap;

    private final ActivityResultLauncher<String[]> mSkinPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::onSkinPicked);

    public SkinManagerFragment() {
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
        refreshAccountStatus();
    }

    @Override
    public void onDestroyView() {
        if (mPreview != null) mPreview.setImageDrawable(null);
        if (mPreviewBitmap != null && !mPreviewBitmap.isRecycled()) mPreviewBitmap.recycle();
        mPreviewBitmap = null;
        mPreview = null;
        mAccountLabel = null;
        mStatus = null;
        mSearchField = null;
        mSearchButton = null;
        mVariant = null;
        mUploadButton = null;
        mSearchInProgress = false;
        mSearchGeneration++;
        super.onDestroyView();
    }

    private View buildView() {
        ScrollView scroll = new ScrollView(requireContext());
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(12), dp(18), dp(18));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout header = panel();
        TextView title = text(getString(R.string.aerix_skin_title), 22, "#F1F6FC", true);
        TextView subtitle = text(getString(R.string.aerix_skin_subtitle), 13, "#AABCD0", false);
        header.addView(title);
        header.addView(subtitle, withTopMargin(dp(4)));
        root.addView(header, matchWrap());

        LinearLayout searchPanel = panel();
        searchPanel.setOrientation(LinearLayout.HORIZONTAL);
        searchPanel.setGravity(Gravity.CENTER_VERTICAL);
        searchPanel.setPadding(dp(10), dp(7), dp(10), dp(7));
        mSearchField = new EditText(requireContext());
        mSearchField.setSingleLine(true);
        mSearchField.setTextColor(Color.WHITE);
        mSearchField.setHintTextColor(Color.LTGRAY);
        mSearchField.setTextSize(14);
        mSearchField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        mSearchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        mSearchField.setHint(R.string.aerix_skin_search_hint);
        mSearchField.setBackgroundResource(R.drawable.aerix_nav_button);
        mSearchField.setPadding(dp(12), 0, dp(12), 0);
        searchPanel.addView(mSearchField, new LinearLayout.LayoutParams(0, dp(44), 1f));
        mSearchButton = button(getString(R.string.aerix_skin_search_action));
        LinearLayout.LayoutParams searchButtonParams = new LinearLayout.LayoutParams(dp(94), dp(44));
        searchButtonParams.leftMargin = dp(8);
        searchPanel.addView(mSearchButton, searchButtonParams);
        mSearchButton.setOnClickListener(v -> searchPlayerSkin());
        mSearchField.setOnEditorActionListener((v, actionId, event) -> {
            searchPlayerSkin();
            return true;
        });
        LinearLayout.LayoutParams searchParams = matchWrap();
        searchParams.topMargin = dp(8);
        root.addView(searchPanel, searchParams);

        LinearLayout accountPanel = panel();
        accountPanel.setOrientation(LinearLayout.HORIZONTAL);
        accountPanel.setGravity(Gravity.CENTER_VERTICAL);
        mPreview = new ImageView(requireContext());
        mPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        PrismGlass.apply(mPreview);
        accountPanel.addView(mPreview, new LinearLayout.LayoutParams(dp(120), dp(120)));
        LinearLayout accountText = new LinearLayout(requireContext());
        accountText.setOrientation(LinearLayout.VERTICAL);
        accountText.setPadding(dp(14), 0, 0, 0);
        mAccountLabel = text("", 15, "#F1F6FC", true);
        mStatus = text("", 12, "#AABCD0", false);
        accountText.addView(mAccountLabel, matchWrap());
        accountText.addView(mStatus, withTopMargin(dp(6)));
        accountPanel.addView(accountText, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams accountParams = matchWrap();
        accountParams.topMargin = dp(10);
        root.addView(accountPanel, accountParams);

        TextView variantLabel = text(getString(R.string.aerix_skin_model), 13, "#D8E9FA", true);
        LinearLayout.LayoutParams variantLabelParams = matchWrap();
        variantLabelParams.topMargin = dp(12);
        root.addView(variantLabel, variantLabelParams);
        mVariant = new Spinner(requireContext());
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(requireContext(),
                R.array.aerix_skin_variants, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mVariant.setAdapter(adapter);
        root.addView(mVariant, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        TextView note = text(getString(R.string.aerix_skin_note), 12, "#AABCD0", false);
        LinearLayout.LayoutParams noteParams = matchWrap();
        noteParams.topMargin = dp(8);
        root.addView(note, noteParams);

        LinearLayout actions = new LinearLayout(requireContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button choose = button(getString(R.string.aerix_skin_choose));
        choose.setOnClickListener(v -> mSkinPicker.launch(new String[]{"image/png"}));
        mUploadButton = button(getString(R.string.aerix_skin_upload));
        mUploadButton.setEnabled(false);
        mUploadButton.setOnClickListener(v -> confirmUpload());
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        actionParams.rightMargin = dp(6);
        actions.addView(choose, actionParams);
        LinearLayout.LayoutParams uploadParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        uploadParams.leftMargin = dp(6);
        actions.addView(mUploadButton, uploadParams);
        LinearLayout.LayoutParams actionsParams = matchWrap();
        actionsParams.topMargin = dp(12);
        root.addView(actions, actionsParams);
        refreshAccountStatus();
        return scroll;
    }

    private void searchPlayerSkin() {
        if (mSearchField == null || mSearchButton == null || mStatus == null) return;
        String username = mSearchField.getText().toString().trim();
        if (username.isEmpty()) {
            mSearchField.setError(getString(R.string.aerix_skin_search_hint));
            return;
        }
        final int generation = ++mSearchGeneration;
        mSearchInProgress = true;
        mSearchResultName = null;
        mSkinBytes = null;
        if (mPreviewBitmap != null && !mPreviewBitmap.isRecycled()) mPreviewBitmap.recycle();
        mPreviewBitmap = null;
        if (mPreview != null) mPreview.setImageDrawable(null);
        mSearchButton.setEnabled(false);
        mSearchButton.setText(R.string.aerix_skin_searching);
        mStatus.setText(R.string.aerix_skin_searching);
        if (mUploadButton != null) mUploadButton.setEnabled(false);
        PojavApplication.sExecutorService.execute(() -> {
            Bitmap bitmap = null;
            try {
                MinecraftSkinLookup.Result result = MinecraftSkinLookup.lookup(username);
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(result.png, 0, result.png.length, bounds);
                if (bounds.outWidth != 64 || (bounds.outHeight != 64 && bounds.outHeight != 32)) {
                    throw new IOException("Mojang returned a skin with unsupported dimensions.");
                }
                bitmap = BitmapFactory.decodeByteArray(result.png, 0, result.png.length);
                if (bitmap == null) throw new IOException("Could not decode the public skin PNG.");
                Bitmap resultBitmap = bitmap;
                android.app.Activity activity = getActivity();
                if (activity == null) {
                    resultBitmap.recycle();
                    return;
                }
                activity.runOnUiThread(() -> {
                    if (!isAdded() || generation != mSearchGeneration || mPreview == null) {
                        resultBitmap.recycle();
                        return;
                    }
                    mSearchInProgress = false;
                    mSearchResultName = result.username;
                    if (mPreviewBitmap != null && !mPreviewBitmap.isRecycled()) mPreviewBitmap.recycle();
                    mPreviewBitmap = resultBitmap;
                    mPreview.setImageBitmap(resultBitmap);
                    mStatus.setText(getString(R.string.aerix_skin_search_found, result.username));
                    mSearchButton.setEnabled(true);
                    mSearchButton.setText(R.string.aerix_skin_search_action);
                });
            } catch (IOException | RuntimeException e) {
                if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                android.app.Activity activity = getActivity();
                if (activity != null) activity.runOnUiThread(() -> {
                    if (!isAdded() || generation != mSearchGeneration) return;
                    mSearchInProgress = false;
                    mSearchResultName = null;
                    mSearchButton.setEnabled(true);
                    mSearchButton.setText(R.string.aerix_skin_search_action);
                    mStatus.setText(e.getMessage() == null
                            ? getString(R.string.aerix_skin_upload_failed) : e.getMessage());
                });
            }
        });
    }

    private void refreshAccountStatus() {
        if (mAccountLabel == null || mUploadButton == null || mStatus == null) return;
        Account account = Accounts.getCurrent();
        boolean canUpload = isUploadAccountReady(account);
        if (account == null) {
            mAccountLabel.setText(getString(R.string.aerix_skin_no_account));
        } else {
            mAccountLabel.setText(account.username == null ? "Minecraft account" : account.username);
        }
        if (mSearchInProgress) {
            mStatus.setText(getString(R.string.aerix_skin_searching));
        } else if (mSearchResultName != null) {
            mStatus.setText(getString(R.string.aerix_skin_search_found, mSearchResultName));
        } else if (account == null) {
            mStatus.setText(getString(R.string.aerix_skin_sign_in));
        } else if (account.authType != AuthType.MICROSOFT) {
            mStatus.setText(getString(R.string.aerix_skin_microsoft_only));
        } else if (!canUpload) {
            mStatus.setText(getString(R.string.aerix_skin_reauthenticate));
        } else if (mSkinBytes != null) {
            mStatus.setText(getString(R.string.aerix_skin_preview_ready));
        } else {
            mStatus.setText(getString(R.string.aerix_skin_account_ready));
        }
        mUploadButton.setEnabled(canUpload && mSkinBytes != null);
    }

    private boolean isUploadAccountReady(@Nullable Account account) {
        return account != null
                && account.authType == AuthType.MICROSOFT
                && account.accessToken != null
                && !account.accessToken.trim().isEmpty()
                && !"0".equals(account.accessToken)
                && !account.isLocal()
                && (account.expiresAt <= 0 || account.expiresAt > System.currentTimeMillis());
    }

    private void onSkinPicked(Uri uri) {
        if (uri == null || !isAdded()) return;
        mSearchGeneration++;
        mSearchInProgress = false;
        mSearchResultName = null;
        if (mSearchButton != null) {
            mSearchButton.setEnabled(true);
            mSearchButton.setText(R.string.aerix_skin_search_action);
        }
        final android.content.Context appContext = requireContext().getApplicationContext();
        ContextLoader loader = new ContextLoader(appContext.getContentResolver(), uri);
        PojavApplication.sExecutorService.execute(() -> {
            try {
                byte[] bytes = loader.read();
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                if (bounds.outWidth != 64 || bounds.outHeight != 64) {
                    throw new IOException(appContext.getString(R.string.aerix_skin_invalid_dimensions));
                }
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bitmap == null) throw new IOException(appContext.getString(R.string.aerix_skin_invalid_image));
                android.app.Activity activity = getActivity();
                if (activity == null) {
                    bitmap.recycle();
                    return;
                }
                activity.runOnUiThread(() -> {
                    if (!isAdded() || mPreview == null) {
                        bitmap.recycle();
                        return;
                    }
                    if (mPreviewBitmap != null && !mPreviewBitmap.isRecycled()) mPreviewBitmap.recycle();
                    mSkinBytes = bytes;
                    mPreviewBitmap = bitmap;
                    mPreview.setImageBitmap(bitmap);
                    mStatus.setText(getString(R.string.aerix_skin_preview_ready));
                    refreshAccountStatus();
                });
            } catch (IOException e) {
                android.app.Activity activity = getActivity();
                if (activity != null) activity.runOnUiThread(() -> {
                    if (isAdded()) Toast.makeText(activity, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void confirmUpload() {
        Account account = Accounts.getCurrent();
        if (account == null || mSkinBytes == null) {
            Toast.makeText(requireContext(), R.string.aerix_skin_sign_in, Toast.LENGTH_LONG).show();
            refreshAccountStatus();
            return;
        }
        String variant = mVariant.getSelectedItemPosition() == 1 ? "slim" : "classic";
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.aerix_skin_confirm_title)
                .setMessage(R.string.aerix_skin_confirm_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.aerix_skin_upload, (dialog, which) -> upload(account, variant))
                .show();
    }

    private void upload(Account account, String variant) {
        if (account.authType != AuthType.MICROSOFT || account.isLocal()
                || account.accessToken == null || "0".equals(account.accessToken)) {
            refreshAccountStatus();
            return;
        }
        byte[] skinSnapshot = mSkinBytes.clone();
        String tokenSnapshot = account.accessToken;
        mUploadButton.setEnabled(false);
        mStatus.setText(getString(R.string.aerix_skin_uploading));
        final android.content.Context appContext = requireContext().getApplicationContext();
        PojavApplication.sExecutorService.execute(() -> {
            String result;
            try {
                MinecraftSkinUploader.upload(tokenSnapshot, skinSnapshot, variant);
                result = appContext.getString(R.string.aerix_skin_upload_success);
            } catch (IOException e) {
                result = e.getMessage() == null ? appContext.getString(R.string.aerix_skin_upload_failed) : e.getMessage();
            }
            String finalResult = result;
            Tools.runOnUiThread(() -> {
                if (!isAdded() || mStatus == null) return;
                mStatus.setText(finalResult);
                if (mUploadButton != null) {
                    mUploadButton.setEnabled(isUploadAccountReady(Accounts.getCurrent()) && mSkinBytes != null);
                }
            });
        });
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(requireContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(14), dp(16), dp(14));
        PrismGlass.apply(panel);
        return panel;
    }

    private GradientDrawable panelBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.argb(136, 31, 58, 82));
        drawable.setCornerRadius(dp(20));
        drawable.setStroke(dp(1), Color.argb(170, 222, 246, 255));
        return drawable;
    }

    private Button button(String label) {
        Button button = new Button(requireContext());
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackgroundResource(R.drawable.aerix_nav_button);
        AerixThemeManager.tintButton(button, requireContext(), AerixThemeManager.SECTION_SKINS);
        return button;
    }

    private TextView text(String value, int size, String color, boolean bold) {
        TextView text = new TextView(requireContext());
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(Color.parseColor(color));
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams withTopMargin(int margin) {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = margin;
        return params;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private final class ContextLoader {
        private final android.content.ContentResolver resolver;
        private final Uri uri;

        ContextLoader(android.content.ContentResolver resolver, Uri uri) {
            this.resolver = resolver;
            this.uri = uri;
        }

        byte[] read() throws IOException {
            try (InputStream input = resolver.openInputStream(uri);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                if (input == null) throw new IOException("Could not open the selected skin image");
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > MAX_SKIN_BYTES) {
                        throw new IOException("Skin image exceeds the 1 MiB upload limit");
                    }
                    output.write(buffer, 0, count);
                }
                if (output.size() == 0) throw new IOException("The selected skin image is empty");
                return output.toByteArray();
            }
        }
    }
}
