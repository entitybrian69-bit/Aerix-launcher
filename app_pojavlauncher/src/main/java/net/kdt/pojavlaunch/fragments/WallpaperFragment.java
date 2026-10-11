package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.ui.MiraiBackdropView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Mirai's Wallpapers screen.
 *
 * Either a picture from the device, or the backdrop the launcher draws itself.
 * The picture is copied into the launcher's own files so it stays available
 * even if the original is moved or deleted.
 */
public class WallpaperFragment extends Fragment {

    public static final String TAG = "WallpaperFragment";
    private static final String WALLPAPER_FILE = "wallpaper.png";
    private static final String PREF_KEY = "mirai_wallpaper";

    private ImageView mPreview;

    private final ActivityResultLauncher<String> mPickImage = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) importWallpaper(uri);
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_wallpaper, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mPreview = view.findViewById(R.id.wallpaper_preview);

        view.findViewById(R.id.wallpaper_pick).setOnClickListener(v -> mPickImage.launch("image/*"));
        view.findViewById(R.id.wallpaper_clear).setOnClickListener(v -> apply(null));

        showCurrentWallpaper();
    }

    private void showCurrentWallpaper() {
        String path = LauncherPreferences.PREF_MIRAI_WALLPAPER;
        if (path == null || path.isEmpty()) {
            mPreview.setImageDrawable(null);
            mPreview.setBackgroundColor(0x00000000);
            return;
        }
        PojavApplication.sExecutorService.execute(() -> {
            Bitmap bitmap = decodeSampled(path, 480);
            Tools.runOnUiThread(() -> mPreview.setImageBitmap(bitmap));
        });
    }

    private void importWallpaper(@NonNull Uri uri) {
        // Hold the application context: the screen may be gone by the time the
        // copy finishes.
        Context context = requireContext().getApplicationContext();
        PojavApplication.sExecutorService.execute(() -> {
            File target = new File(context.getFilesDir(), WALLPAPER_FILE);
            boolean copied = false;
            try (InputStream in = context.getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(target)) {
                if (in != null) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
                    copied = true;
                }
            } catch (IOException e) {
                copied = false;
            }
            final boolean ok = copied;
            Tools.runOnUiThread(() -> {
                if (!isAdded()) return;
                if (ok) {
                    apply(target.getAbsolutePath());
                } else {
                    Toast.makeText(requireContext(), R.string.mirai_wallpaper_failed, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    /** Stores the choice, repaints the backdrop straight away. */
    private void apply(@Nullable String path) {
        LauncherPreferences.DEFAULT_PREF.edit()
                .putString(PREF_KEY, path == null ? "" : path).apply();
        LauncherPreferences.PREF_MIRAI_WALLPAPER = path == null ? "" : path;

        View backdrop = requireActivity().findViewById(R.id.mirai_backdrop);
        if (backdrop instanceof MiraiBackdropView) {
            ((MiraiBackdropView) backdrop).setWallpaperPath(path);
        }
        showCurrentWallpaper();
    }

    private static Bitmap decodeSampled(String path, int maxSide) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2;
        BitmapFactory.Options decode = new BitmapFactory.Options();
        decode.inSampleSize = sample;
        return BitmapFactory.decodeFile(path, decode);
    }
}
