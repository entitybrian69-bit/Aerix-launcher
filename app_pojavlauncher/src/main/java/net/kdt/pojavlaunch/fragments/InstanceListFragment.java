package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.instances.DisplayInstance;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.InstanceIconProvider;
import net.kdt.pojavlaunch.instances.Instances;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Mirai's Library: every instance in one list, with select, edit and delete.
 *
 * The instances themselves, their storage and their editor are untouched, this
 * is only another way in to them.
 */
public class InstanceListFragment extends Fragment {

    public static final String TAG = "InstanceListFragment";

    private final List<DisplayInstance> mInstances = new ArrayList<>();
    private ListView mListView;
    private TextView mEmptyView;
    private final BaseAdapter mAdapter = new BaseAdapter() {
        @Override
        public int getCount() {
            return mInstances.size();
        }

        @Override
        public Object getItem(int position) {
            return mInstances.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView != null ? convertView
                    : LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_mirai_instance, parent, false);
            DisplayInstance instance = mInstances.get(position);

            ImageView icon = row.findViewById(R.id.instance_row_icon);
            icon.setImageDrawable(InstanceIconProvider.fetchIcon(getResources(), instance));

            TextView name = row.findViewById(R.id.instance_row_name);
            name.setText(instance.name != null ? instance.name : instance.versionId);

            TextView detail = row.findViewById(R.id.instance_row_detail);
            detail.setText(instance.versionId);

            ImageButton edit = row.findViewById(R.id.instance_row_edit);
            edit.setOnClickListener(v -> openEditor(instance));

            ImageButton delete = row.findViewById(R.id.instance_row_delete);
            delete.setOnClickListener(v -> confirmDelete(instance));

            return row;
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_instance_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mListView = view.findViewById(R.id.mirai_instance_list);
        mEmptyView = view.findViewById(R.id.mirai_library_empty);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener((parent, row, position, id) -> select(mInstances.get(position)));

        view.findViewById(R.id.mirai_new_instance).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), ProfileTypeSelectFragment.class,
                        ProfileTypeSelectFragment.TAG, null));

        refresh();
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        PojavApplication.sExecutorService.execute(() -> {
            try {
                Instances instances = Instances.loadDisplay();
                List<DisplayInstance> loaded = new ArrayList<>(instances.list);
                Tools.runOnUiThread(() -> {
                    mInstances.clear();
                    mInstances.addAll(loaded);
                    mAdapter.notifyDataSetChanged();
                    mEmptyView.setVisibility(mInstances.isEmpty() ? View.VISIBLE : View.GONE);
                });
            } catch (IOException e) {
                // A broken instance file should not take the screen down.
            }
        });
    }

    private void select(DisplayInstance instance) {
        PojavApplication.sExecutorService.execute(() -> {
            Instances.setSelectedInstance(instance);
            Tools.runOnUiThread(() -> {
                ExtraCore.setValue(ExtraConstants.REFRESH_VERSION_SPINNER, true);
                refresh();
            });
        });
    }

    private void openEditor(DisplayInstance instance) {
        select(instance);
        Tools.swapFragment(requireActivity(), InstanceEditorFragment.class,
                InstanceEditorFragment.TAG, null);
    }

    private void confirmDelete(DisplayInstance instance) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.global_delete)
                .setMessage(getString(R.string.mirai_library_delete_confirm,
                        instance.name != null ? instance.name : instance.versionId))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.global_delete, (dialog, which) -> delete(instance))
                .show();
    }

    private void delete(DisplayInstance instance) {
        PojavApplication.sExecutorService.execute(() -> {
            try {
                for (Instance full : Instances.loadAllInstances()) {
                    if (full.name != null && full.name.equals(instance.name)) {
                        Instances.removeInstance(full);
                        break;
                    }
                }
            } catch (IOException e) {
                // Nothing else to do, the list is refreshed below either way.
            }
            Tools.runOnUiThread(() -> {
                ExtraCore.setValue(ExtraConstants.REFRESH_VERSION_SPINNER, true);
                refresh();
            });
        });
    }
}
