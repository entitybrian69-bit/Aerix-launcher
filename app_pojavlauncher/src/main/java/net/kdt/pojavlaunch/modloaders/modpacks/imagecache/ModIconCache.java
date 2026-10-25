package net.kdt.pojavlaunch.modloaders.modpacks.imagecache;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.kdt.mcgui.ProgressLayout;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.progresskeeper.DownloaderProgressWrapper;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.utils.FileUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ModIconCache {
    /** Instance icons are drawn small; publishing art is not kept bigger than this for them. */
    private static final int ICON_MIN_EDGE_PX = 384;
    ThreadPoolExecutor cacheLoaderPool = new ThreadPoolExecutor(10,
            10,
            1000,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>());
    File cachePath;
    private final List<WeakReference<ImageReceiver>> mCancelledReceivers = new ArrayList<>();
    public ModIconCache() {
        cachePath = getImageCachePath();
        if(!FileUtils.ensureDirectorySilently(cachePath)) {
                throw new RuntimeException("Failed to create icon cache directory");
        }

    }
    static File getImageCachePath() {
        return new File(Tools.DIR_CACHE, "mod_icons");
    }

    /**
     * Get an image for a mod with the associated tag and URL to download it in case if its not cached
     * @param imageReceiver the receiver interface that would get called when the image loads
     * @param imageTag the tag of the image to keep track of it
     * @param imageUrl the URL of the image in case if it's not cached
     */
    public void getImage(ImageReceiver imageReceiver, String imageTag, String imageUrl) {
        cacheLoaderPool.execute(new ReadFromDiskTask(this, imageReceiver, imageTag, imageUrl));
    }

    /**
     * Mark the image obtainment task requested with this receiver as "cancelled". This means that
     * this receiver will not be called back and that some tasks related to this image may be
     * prevented from happening or interrupted.
     * @param imageReceiver the receiver to cancel
     */
    public void cancelImage(ImageReceiver imageReceiver) {
        synchronized (mCancelledReceivers) {
            mCancelledReceivers.add(new WeakReference<>(imageReceiver));
        }
    }

    boolean checkCancelled(ImageReceiver imageReceiver) {
        boolean isCanceled = false;
        synchronized (mCancelledReceivers) {
            Iterator<WeakReference<ImageReceiver>> iterator = mCancelledReceivers.iterator();
            while (iterator.hasNext()) {
                WeakReference<ImageReceiver> reference = iterator.next();
                if (reference.get() == null) {
                    iterator.remove();
                    continue;
                }
                if(reference.get() == imageReceiver) {
                    isCanceled = true;
                }
            }
        }
        if(isCanceled) Log.i("IconCache", "checkCancelled("+imageReceiver.hashCode()+") == true");
        return isCanceled;
    }

    /**
     * Get the base64-encoded version of a cached icon by its tag.
     * Note: this functions performs I/O operations, and should not be called on the UI
     * thread.
     * @param imageTag the icon tag
     * @return the base64 encoded image or null if not cached
     */

    /**
     * Writes a modpack's own image onto its instance, so the launcher shows the pack's logo instead
     * of falling back to a stock icon.
     *
     * The image normally sits in the browse cache already - the player looked at it a moment before
     * installing. When it does not (Discovery draws its cards from its own memory cache, a cache wipe,
     * the app having restarted), the pack's own URL is fetched here rather than silently leaving the
     * instance with the stock icon.
     *
     * @param imageUrl the pack's image, used only when the cache has nothing
     */
    public static void writeInstanceImage(Instance instance, String imageTag, String imageUrl) {
        File imagePath = new File(Tools.DIR_CACHE, "mod_icons/" + imageTag + ".ca");
        if((!imagePath.isFile() || !imagePath.canRead()) && imageUrl != null && !imageUrl.isEmpty()) {
            try {
                FileUtils.ensureDirectory(imagePath.getParentFile());
                DownloadUtils.downloadFileMonitored(imageUrl, imagePath, new byte[8192],
                        new DownloaderProgressWrapper(R.string.modpack_download_downloading_metadata,
                                ProgressLayout.INSTALL_MODPACK));
            } catch (Exception e) {
                Log.i("ModIconCache", "Could not fetch the modpack image", e);
            }
        }
        writeInstanceImage(instance, imageTag);
    }

    /**
     * Store the image the publisher gave a pack as the icon of the instance built from it.
     *
     * @param imageBytes the pack's image, as downloaded
     * @return whether the instance now carries that image
     */
    public static boolean writeInstanceIcon(Instance instance, byte[] imageBytes) {
        if(imageBytes == null || imageBytes.length == 0) return false;
        Bitmap bitmap = decodeDownsampled(imageBytes);
        if(bitmap == null) return false;
        try {
            instance.encodeNewIcon(bitmap);
            return true;
        } catch (IOException e) {
            Log.i("ModIconCache", "Failed to reencode icon for instance", e);
            return false;
        } finally {
            bitmap.recycle();
        }
    }

    public static void writeInstanceImage(Instance instance, String imageTag) {
        File imagePath = new File(Tools.DIR_CACHE, "mod_icons/"+imageTag+".ca");
        Log.i("IconCache", "Creating base64 version of icon "+imageTag);
        if(!imagePath.canRead() || !imagePath.isFile()) {
            Log.i("IconCache", "Icon does not exist");
            return;
        }
        try {
            writeInstanceIcon(instance, readFileBytes(imagePath));
        }catch (IOException e) {
            Log.i("ModIconCache", "Failed to read the cached modpack icon", e);
        }
    }

    private static byte[] readFileBytes(File file) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(file);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) outputStream.write(buffer, 0, read);
            return outputStream.toByteArray();
        }
    }

    /**
     * Decode an icon at roughly the size it is shown at. Publisher artwork can be a couple of
     * thousand pixels of flat colour; keeping that would bloat every instance with a needless file.
     */
    private static Bitmap decodeDownsampled(byte[] imageBytes) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, bounds);
        if(bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= ICON_MIN_EDGE_PX &&
                bounds.outHeight / (sample * 2) >= ICON_MIN_EDGE_PX) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, options);
    }
}
