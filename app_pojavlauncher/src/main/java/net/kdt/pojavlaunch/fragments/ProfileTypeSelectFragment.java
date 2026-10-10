package net.kdt.pojavlaunch.fragments;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.utils.AerixThemeManager;
import net.kdt.pojavlaunch.utils.PrismGlass;

import java.io.IOException;

public class ProfileTypeSelectFragment extends Fragment {
    public static final String TAG = "ProfileTypeSelectFragment";
    public ProfileTypeSelectFragment() {
        super(R.layout.fragment_profile_type);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setBackgroundColor(Color.TRANSPARENT);
        styleCreatePage(view);
        PrismGlass.apply(view.findViewById(R.id.create_vanilla_panel));
        PrismGlass.apply(view.findViewById(R.id.create_modded_panel));
        view.findViewById(R.id.vanilla_profile).setOnClickListener(v -> {
            try {
                Instance instance = Instances.createDefaultInstance();
                Instances.setSelectedInstance(instance);
                Tools.swapFragment(requireActivity(), InstanceEditorFragment.class,
                        InstanceEditorFragment.TAG, new Bundle(1));
            }catch (IOException e) {
                Tools.showError(view.getContext(), e);
            }
        });

        // NOTE: Special care needed! If you wll decide to add these to the back stack, please read
        // the comment in FabricInstallFragment.onDownloadFinished() and amend the code
        // in FabricInstallFragment.onDownloadFinished() and ModVersionListFragment.onDownloadFinished()
        view.findViewById(R.id.optifine_profile).setOnClickListener(v -> Tools.swapFragment(requireActivity(), OptiFineInstallFragment.class,
                OptiFineInstallFragment.TAG, null));
        view.findViewById(R.id.modded_profile_fabric).setOnClickListener((v)->
                Tools.swapFragment(requireActivity(), FabricInstallFragment.class, FabricInstallFragment.TAG, null));
        view.findViewById(R.id.modded_profile_forge).setOnClickListener((v)->
                Tools.swapFragment(requireActivity(), ForgeInstallFragment.class, ForgeInstallFragment.TAG, null));
        view.findViewById(R.id.modded_profile_modpack).setOnClickListener((v)->
                Tools.swapFragment(requireActivity(), SearchModFragment.class, SearchModFragment.TAG, null));
        view.findViewById(R.id.modded_profile_quilt).setOnClickListener((v)->
                Tools.swapFragment(requireActivity(), QuiltInstallFragment.class, QuiltInstallFragment.TAG, null));
        view.findViewById(R.id.modded_profile_bta).setOnClickListener((v)->
                Tools.swapFragment(requireActivity(), BTAInstallFragment.class, BTAInstallFragment.TAG, null));
        view.findViewById(R.id.modded_profile_neoforge).setOnClickListener((v)->
                Tools.swapFragment(requireActivity(), NeoforgeInstallFragment.class, NeoforgeInstallFragment.TAG, null));
        view.findViewById(R.id.modded_profile_legacy_fabric).setOnClickListener((v) ->
                Tools.swapFragment(requireActivity(), LegacyFabricInstallFragment.class, LegacyFabricInstallFragment.TAG, null));
    }

    private void styleCreatePage(View root) {
        int[] headingIds = {R.id.title_textview, R.id.title_modded_textview};
        for (int id : headingIds) {
            TextView heading = root.findViewById(id);
            if (heading != null) {
                heading.setTextColor(Color.WHITE);
                heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            }
        }
        int[] actionIds = {
                R.id.vanilla_profile, R.id.optifine_profile, R.id.modded_profile_fabric,
                R.id.modded_profile_quilt, R.id.modded_profile_legacy_fabric,
                R.id.modded_profile_forge, R.id.modded_profile_neoforge,
                R.id.modded_profile_modpack, R.id.modded_profile_bta
        };
        for (int id : actionIds) {
            Button action = root.findViewById(id);
            if (action == null) continue;
            action.setAllCaps(false);
            action.setTextColor(Color.WHITE);
            action.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            action.setMinHeight(dp(48));
            action.setBackgroundResource(R.drawable.aerix_nav_button);
            AerixThemeManager.tintButton(action, requireContext(), AerixThemeManager.SECTION_HOME);
        }
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
