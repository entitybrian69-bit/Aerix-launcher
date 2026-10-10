package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.runOnUiThread;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.math.MathUtils;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.ModItemAdapter;
import net.kdt.pojavlaunch.modloaders.modpacks.api.CommonApi;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModpackApi;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.profiles.VersionSelectorDialog;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener;
import net.kdt.pojavlaunch.utils.AerixThemeManager;
import net.kdt.pojavlaunch.utils.PrismGlass;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class SearchModFragment extends Fragment implements ModItemAdapter.SearchResultCallback {

    public static final String TAG = "SearchModFragment";
    static final String PREF_DISCOVER_TYPE = "aerix_discover_project_type";
    static final String PREF_DISCOVER_SOURCE = "aerix_discover_source";
    private static final String[] DISCOVER_TYPES = {"modpack", "mod", "resourcepack", "shader", "world"};
    private View mOverlay;
    private float mOverlayTopCache; // Padding cache reduce resource lookup

    private final RecyclerView.OnScrollListener mOverlayPositionListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            mOverlay.setY(MathUtils.clamp(mOverlay.getY() - dy, -mOverlay.getHeight(), mOverlayTopCache));
        }
    };

    private EditText mSearchEditText;
    private ImageButton mFilterButton;
    private RecyclerView mRecyclerview;
    private ModItemAdapter mModItemAdapter;
    private ProgressBar mSearchProgressBar;
    private TextView mStatusTextView;
    private TextView mDetailTitle, mDetailSource, mDetailDescription;
    private ColorStateList mDefaultTextColor;
    private ModpackApi modpackApi;
    private String mConfiguredCurseforgeKey = "";

    private final SearchFilters mSearchFilters;

    private Button mImportButton;
    private TaskCountListener mTaskCountListener;

    ActivityResultLauncher<String> mImportLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri == null) return;
                Context context = getContext();
                ContentResolver contentResolver = getContext().getContentResolver();
                PojavApplication.sExecutorService.execute(() -> {
                    performLocalInstall(uri, context, contentResolver);
                });
            });

    public void performLocalInstall(Uri uri, Context context, ContentResolver contentResolver) {
            String fileName = Tools.getFileName(context, uri);
            if (fileName == null) return;
            File outFile = new File(Tools.DIR_CACHE, fileName + ".cf");
            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, R.string.multirt_progress_caching);
            try (InputStream inputStream = contentResolver.openInputStream(uri);
                 OutputStream outputStream = new FileOutputStream(outFile)) {
                if (inputStream == null) return;
                IOUtils.copy(inputStream, outputStream);
                outputStream.flush();
            } catch (IOException e) {
                Tools.showErrorRemote("Error", e);
                ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
                return;
            }
            try {
                modpackApi.installLocalModpack(fileName, outFile, null);
            } catch (IOException e) {
                Tools.showErrorRemote("Error", e);
            } finally {
                outFile.delete();
                ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
            }
    }

    public SearchModFragment(){
        super(R.layout.fragment_mod_search);
        mSearchFilters = new SearchFilters();
        mSearchFilters.setProjectType("modpack");
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        reloadModpackApi();
    }

    private void reloadModpackApi() {
        mConfiguredCurseforgeKey = getCurseforgeApiKey();
        modpackApi = new CommonApi(mConfiguredCurseforgeKey);
        if (mModItemAdapter != null) mModItemAdapter.setModpackApi(modpackApi);
    }

    private String getCurseforgeApiKey() {
        String key = LauncherPreferences.DEFAULT_PREF.getString(
                LauncherPreferences.PREF_KEY_CURSEFORGE_API_KEY, "");
        return key == null ? "" : key.trim();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // You can only access resources after attaching to current context
        mModItemAdapter = new ModItemAdapter(getResources(), modpackApi, this);
        ProgressKeeper.addTaskCountListener(mModItemAdapter);
        mModItemAdapter.setProjectSelectionListener(this::showProjectDetail);
        mOverlayTopCache = getResources().getDimension(R.dimen.fragment_padding_medium);

        mOverlay = view.findViewById(R.id.search_mod_overlay);
        PrismGlass.apply(mOverlay);
        mSearchEditText = view.findViewById(R.id.search_mod_edittext);
        mSearchProgressBar = view.findViewById(R.id.search_mod_progressbar);
        mRecyclerview = view.findViewById(R.id.search_mod_list);
        mStatusTextView = view.findViewById(R.id.search_mod_status_text);
        mDetailTitle = view.findViewById(R.id.prism_mod_detail_title);
        mDetailSource = view.findViewById(R.id.prism_mod_detail_source);
        mDetailDescription = view.findViewById(R.id.prism_mod_detail_description);
        mFilterButton = view.findViewById(R.id.search_mod_filter);
        mFilterButton.setBackgroundResource(R.drawable.prism_create_side_button);
        mFilterButton.setColorFilter(Color.rgb(20, 45, 66));

        mStatusTextView.setTextColor(Color.rgb(22, 46, 65));
        mDefaultTextColor = mStatusTextView.getTextColors();
        mSearchEditText.setTextColor(Color.rgb(22, 46, 65));
        mSearchEditText.setHintTextColor(Color.rgb(86, 112, 130));
        String savedType = LauncherPreferences.DEFAULT_PREF.getString(PREF_DISCOVER_TYPE, "modpack");
        try {
            mSearchFilters.setProjectType(savedType);
        } catch (IllegalArgumentException ignored) {
            mSearchFilters.setProjectType("modpack");
        }
        mSearchFilters.apiSource = LauncherPreferences.DEFAULT_PREF.getInt(PREF_DISCOVER_SOURCE, -1);
        updateDiscoverChips(view);

        int columns = getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE ? 2 : 1;
        GridLayoutManager manager = new GridLayoutManager(requireContext(), columns);
        manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) {
                return mModItemAdapter.getItemViewType(position) == 1 ? columns : 1;
            }
        });
        mRecyclerview.setLayoutManager(manager);
        mRecyclerview.setAdapter(mModItemAdapter);

        mRecyclerview.addOnScrollListener(mOverlayPositionListener);

        mSearchEditText.setOnEditorActionListener((v, actionId, event) -> {
            searchMods(mSearchEditText.getText().toString());
            mSearchEditText.clearFocus();
            return false;
        });

        mOverlay.post(()->{
           int overlayHeight = mOverlay.getHeight();
           mRecyclerview.setPadding(mRecyclerview.getPaddingLeft(),
                   mRecyclerview.getPaddingTop() + overlayHeight,
                   mRecyclerview.getPaddingRight(),
                   mRecyclerview.getPaddingBottom());
        });
        mFilterButton.setOnClickListener(v -> displayFilterDialog());
        mImportButton = view.findViewById(R.id.mineButton_import_local_modpack);
        AerixThemeManager.tintButton(mImportButton, requireContext(), AerixThemeManager.SECTION_DISCOVER);
        mImportButton.setOnClickListener(v -> {
            mImportLauncher.launch("*/*");
        });
        mTaskCountListener = taskCount -> {
            runOnUiThread(() -> mImportButton.setEnabled(taskCount == 0));
            return false;
        };
        ProgressKeeper.addTaskCountListener(mTaskCountListener);

        searchMods(null);
    }

    @Override
    public void onResume() {
        super.onResume();
        String configuredKey = getCurseforgeApiKey();
        if (!configuredKey.equals(mConfiguredCurseforgeKey)) {
            reloadModpackApi();
            if (mSearchEditText != null) searchMods(mSearchEditText.getText().toString());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ProgressKeeper.removeTaskCountListener(mModItemAdapter);
        mRecyclerview.removeOnScrollListener(mOverlayPositionListener);
        if (mTaskCountListener != null) { ProgressKeeper.removeTaskCountListener(mTaskCountListener); }
    }

    @Override
    public void onSearchFinished() {
        mSearchProgressBar.setVisibility(View.GONE);
        mStatusTextView.setVisibility(View.GONE);
    }

    @Override
    public void onSearchError(int error) {
        mSearchProgressBar.setVisibility(View.GONE);
        mStatusTextView.setVisibility(View.VISIBLE);
        switch (error) {
            case ERROR_INTERNAL:
                mStatusTextView.setTextColor(Color.RED);
                mStatusTextView.setText(R.string.search_modpack_error);
                break;
            case ERROR_NO_RESULTS:
                mStatusTextView.setTextColor(mDefaultTextColor);
                mStatusTextView.setText(R.string.search_modpack_no_result);
                break;
        }
    }

    private void showProjectDetail(ModItem item) {
        if (mDetailTitle == null) return;
        mDetailTitle.setText(item.title);
        mDetailSource.setText(item.apiSource == Constants.SOURCE_MODRINTH ? "Modrinth" : "CurseForge");
        mDetailDescription.setText(item.description);
    }

    private void updateDiscoverChips(View root) {
        LinearLayout row = root.findViewById(R.id.prism_discover_chips);
        if (row == null) return;
        row.removeAllViews();
        addDiscoverChip(row, getString(R.string.prism_all_sources), mSearchFilters.apiSource == -1, () -> {
            mSearchFilters.apiSource = -1;
            saveSourceAndRefresh(root);
        });
        addDiscoverChip(row, "Modrinth", mSearchFilters.apiSource == Constants.SOURCE_MODRINTH, () -> {
            mSearchFilters.apiSource = Constants.SOURCE_MODRINTH;
            saveSourceAndRefresh(root);
        });
        addDiscoverChip(row, "CurseForge", mSearchFilters.apiSource == Constants.SOURCE_CURSEFORGE, () -> {
            mSearchFilters.apiSource = Constants.SOURCE_CURSEFORGE;
            saveSourceAndRefresh(root);
        });
        String[] labels = {"Modpacks", "Mods", "Resource packs", "Shaders", "World saves"};
        for (int i = 0; i < DISCOVER_TYPES.length; i++) {
            final String type = DISCOVER_TYPES[i];
            addDiscoverChip(row, labels[i], type.equals(mSearchFilters.resolvedProjectType()), () -> {
                mSearchFilters.setProjectType(type);
                if ("world".equals(type) && mSearchFilters.apiSource == Constants.SOURCE_MODRINTH) {
                    mSearchFilters.apiSource = Constants.SOURCE_CURSEFORGE;
                }
                LauncherPreferences.DEFAULT_PREF.edit().putString(PREF_DISCOVER_TYPE, type).apply();
                saveSourceAndRefresh(root);
            });
        }
        addDiscoverChip(row, "Filters", false, this::displayFilterDialog);
    }

    private void addDiscoverChip(LinearLayout row, String label, boolean selected, Runnable action) {
        TextView chip = new TextView(requireContext());
        chip.setText(label);
        chip.setTextSize(12);
        chip.setGravity(android.view.Gravity.CENTER);
        chip.setPadding(dp(14), 0, dp(14), 0);
        chip.setMinHeight(dp(38));
        chip.setBackgroundResource(R.drawable.prism_dock_item);
        chip.setActivated(selected);
        chip.setTextColor(Color.rgb(20, 53, 78));
        chip.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(38));
        params.rightMargin = dp(7);
        row.addView(chip, params);
    }

    private void saveSourceAndRefresh(View root) {
        LauncherPreferences.DEFAULT_PREF.edit().putInt(PREF_DISCOVER_SOURCE, mSearchFilters.apiSource).apply();
        updateDiscoverChips(root);
        searchMods(mSearchEditText.getText().toString());
    }

    private void searchMods(String name) {
        mSearchFilters.name = name == null ? "" : name;
        if (mDetailTitle != null) {
            mDetailTitle.setText(R.string.prism_mod_detail_prompt);
            mDetailSource.setText("");
            mDetailDescription.setText(R.string.prism_mod_detail_hint);
        }
        String projectType = mSearchFilters.resolvedProjectType();
        if ("world".equals(projectType) && mSearchFilters.apiSource == Constants.SOURCE_MODRINTH) {
            showSearchUnavailable(R.string.aerix_discover_worlds_key_note);
            return;
        }
        boolean needsCurseforge = mSearchFilters.apiSource == Constants.SOURCE_CURSEFORGE
                || ("world".equals(projectType) && mSearchFilters.apiSource != Constants.SOURCE_MODRINTH);
        if (needsCurseforge && getCurseforgeApiKey().isEmpty()) {
            showSearchUnavailable("world".equals(projectType)
                    ? R.string.aerix_discover_worlds_key_note
                    : R.string.aerix_discover_source_key_note);
            return;
        }
        mStatusTextView.setVisibility(View.GONE);
        mSearchProgressBar.setVisibility(View.VISIBLE);
        mModItemAdapter.performSearchQuery(mSearchFilters);
    }

    private void showSearchUnavailable(int messageResource) {
        mSearchProgressBar.setVisibility(View.GONE);
        mModItemAdapter.clearResults();
        mStatusTextView.setTextColor(mDefaultTextColor);
        mStatusTextView.setText(messageResource);
        mStatusTextView.setVisibility(View.VISIBLE);
    }

    private void displayFilterDialog() {
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(R.layout.dialog_mod_filters)
                .create();

        // setup the view behavior
        dialog.setOnShowListener(dialogInterface -> {
            TextView mSelectedVersion = dialog.findViewById(R.id.search_mod_selected_mc_version_textview);
            Spinner projectTypeSpinner = dialog.findViewById(R.id.search_mod_project_type_spinner);
            Spinner sourceSpinner = dialog.findViewById(R.id.search_mod_source_spinner);
            Button connectCurseforgeButton = dialog.findViewById(R.id.search_mod_connect_curseforge);
            Button mSelectVersionButton = dialog.findViewById(R.id.search_mod_mc_version_button);
            Button mApplyButton = dialog.findViewById(R.id.search_mod_apply_filters);

            assert mSelectVersionButton != null;
            assert mSelectedVersion != null;
            assert projectTypeSpinner != null;
            assert sourceSpinner != null;
            assert connectCurseforgeButton != null;
            assert mApplyButton != null;
            AerixThemeManager.tintButton(mSelectVersionButton, requireContext(), AerixThemeManager.SECTION_DISCOVER);
            AerixThemeManager.tintButton(connectCurseforgeButton, requireContext(), AerixThemeManager.SECTION_DISCOVER);
            AerixThemeManager.tintButton(mApplyButton, requireContext(), AerixThemeManager.SECTION_DISCOVER);
            projectTypeSpinner.setSelection(projectTypeIndex(mSearchFilters.resolvedProjectType()));
            sourceSpinner.setSelection(sourceIndex(mSearchFilters.apiSource));
            connectCurseforgeButton.setOnClickListener(v -> showCurseforgeKeyDialog(() -> {
                reloadModpackApi();
                searchMods(mSearchEditText.getText().toString());
            }));

            // Setup the expendable list behavior
            mSelectVersionButton.setOnClickListener(v -> VersionSelectorDialog.open(v.getContext(), true, (id, snapshot)-> mSelectedVersion.setText(id)));

            // Apply visually all the current settings
            mSelectedVersion.setText(mSearchFilters.mcVersion);

            // Apply the new settings
            mApplyButton.setOnClickListener(v -> {
                mSearchFilters.mcVersion = mSelectedVersion.getText().toString();
                int selectedType = Math.max(0, Math.min(DISCOVER_TYPES.length - 1,
                        projectTypeSpinner.getSelectedItemPosition()));
                String projectType = DISCOVER_TYPES[selectedType];
                int selectedSourceIndex = Math.max(0, Math.min(2, sourceSpinner.getSelectedItemPosition()));
                int selectedSource = sourceFromIndex(selectedSourceIndex);
                // Modrinth does not publish world-save projects; guide that selection to its supported provider.
                if ("world".equals(projectType) && selectedSource == Constants.SOURCE_MODRINTH) {
                    selectedSource = Constants.SOURCE_CURSEFORGE;
                    sourceSpinner.setSelection(2);
                }
                mSearchFilters.setProjectType(projectType);
                mSearchFilters.apiSource = selectedSource;
                LauncherPreferences.DEFAULT_PREF.edit()
                        .putString(PREF_DISCOVER_TYPE, projectType)
                        .putInt(PREF_DISCOVER_SOURCE, selectedSource)
                        .apply();
                updateDiscoverChips(requireView());
                searchMods(mSearchEditText.getText().toString());
                dialogInterface.dismiss();
                if ((selectedSource == Constants.SOURCE_CURSEFORGE || "world".equals(projectType))
                        && getCurseforgeApiKey().isEmpty()) {
                    showCurseforgeKeyDialog(() -> {
                        reloadModpackApi();
                        searchMods(mSearchEditText.getText().toString());
                    });
                }
            });
        });

        dialog.show();
    }

    private void showCurseforgeKeyDialog(@Nullable Runnable onChanged) {
        if (!isAdded()) return;
        String existingKey = getCurseforgeApiKey();
        EditText keyInput = new EditText(requireContext());
        keyInput.setSingleLine(true);
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput.setHint(R.string.aerix_discover_curseforge_key_hint);
        keyInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(6), dp(24), dp(4));
        content.addView(keyInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.aerix_discover_curseforge_key_title)
                .setMessage(R.string.aerix_discover_curseforge_key_required)
                .setView(content)
                .setPositiveButton(R.string.aerix_discover_curseforge_key_save, null)
                .setNegativeButton(android.R.string.cancel, null);
        if (!existingKey.isEmpty()) {
            builder.setNeutralButton(R.string.aerix_discover_curseforge_key_remove, null);
        }
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String key = keyInput.getText().toString().trim();
                if (key.isEmpty() || key.length() > 512) {
                    keyInput.setError(getString(R.string.aerix_discover_curseforge_key_empty));
                    return;
                }
                LauncherPreferences.DEFAULT_PREF.edit()
                        .putString(LauncherPreferences.PREF_KEY_CURSEFORGE_API_KEY, key)
                        .apply();
                dialog.dismiss();
                if (onChanged != null) onChanged.run();
            });
            if (!existingKey.isEmpty()) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                    LauncherPreferences.DEFAULT_PREF.edit()
                            .remove(LauncherPreferences.PREF_KEY_CURSEFORGE_API_KEY)
                            .apply();
                    dialog.dismiss();
                    if (onChanged != null) onChanged.run();
                });
            }
        });
        dialog.show();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static int projectTypeIndex(String projectType) {
        for (int i = 0; i < DISCOVER_TYPES.length; i++) {
            if (DISCOVER_TYPES[i].equals(projectType)) return i;
        }
        return 0;
    }

    private static int sourceIndex(int apiSource) {
        if (apiSource == Constants.SOURCE_MODRINTH) return 1;
        if (apiSource == Constants.SOURCE_CURSEFORGE) return 2;
        return 0;
    }

    private static int sourceFromIndex(int selectedIndex) {
        if (selectedIndex == 1) return Constants.SOURCE_MODRINTH;
        if (selectedIndex == 2) return Constants.SOURCE_CURSEFORGE;
        return -1;
    }
}
