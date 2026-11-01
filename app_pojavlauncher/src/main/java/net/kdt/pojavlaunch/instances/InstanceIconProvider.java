package net.kdt.pojavlaunch.instances;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;

import net.kdt.pojavlaunch.R;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class InstanceIconProvider {
    public static final String FALLBACK_ICON_NAME = "default";
    private static final Map<String, CachedIcon> sIconCache = new HashMap<>();
    private static final Map<String, Drawable> sStaticIconCache = new HashMap<>();
    private static final Map<String, Integer> sStaticIcons = new HashMap<>();

    /** A resolved icon plus the icon file stamp it was read from; 0 means a built-in loader mark. */
    private static final class CachedIcon {
        final Drawable drawable;
        final long stamp;

        CachedIcon(Drawable drawable, long stamp) {
            this.drawable = drawable;
            this.stamp = stamp;
        }
    }

    static {
        // A plain Minecraft instance is a grass block, not the launcher's own logo: the icon is
        // there to say what the instance *is*, and every instance used to say "PojavLauncher".
        sStaticIcons.put(FALLBACK_ICON_NAME, R.drawable.aerix_loader_vanilla);
        sStaticIcons.put("vanilla", R.drawable.aerix_loader_vanilla);
        sStaticIcons.put("fabric", R.drawable.ic_fabric);
        sStaticIcons.put("quilt", R.drawable.ic_quilt);
        sStaticIcons.put("forge", R.drawable.ic_forge);
        sStaticIcons.put("neoforge", R.drawable.ic_neoforge);
        sStaticIcons.put("optifine", R.drawable.aerix_loader_optifine);
        sStaticIcons.put("legacy_fabric", R.drawable.aerix_loader_legacy_fabric);
        sStaticIcons.put("bta", R.drawable.aerix_loader_bta);
    }

    /**
     * Which loader an instance runs, read from the version id it is built on.
     *
     * Instances created before the launcher recorded an icon - and any instance whose metadata names
     * the stock icon - carry the old "default" value, which is why a Fabric or OptiFine instance
     * could sit there showing the launcher's logo. The version id always says what it is:
     * "fabric-loader-0.15.11-1.20.1", "1.20.1-forge-47.2.0", "1.20.1-OptiFine_HD_U_I6".
     *
     * @return the static icon name for that loader, or null when nothing matches
     */
    public static String loaderIconFor(String versionId) {
        if (versionId == null) return null;
        String id = versionId.toLowerCase(java.util.Locale.ROOT);
        if (id.contains("neoforge")) return "neoforge";
        if (id.contains("legacyfabric") || id.contains("legacy-fabric") || id.contains("legacy_fabric")) {
            return "legacy_fabric";
        }
        if (id.contains("quilt")) return "quilt";
        if (id.contains("fabric")) return "fabric";
        if (id.contains("optifine")) return "optifine";
        if (id.contains("forge")) return "forge";
        if (id.startsWith("bta-") || id.contains("-bta")) return "bta";
        return null;
    }

    /**
     * Fetch an icon from the cache, or load it if it's not cached.
     *
     * The cache is keyed by the instance directory and the moment the icon file was last written,
     * not by the object handed in: the instance list is rebuilt every time it refreshes, and an
     * instance that has just been given a modpack's logo has to show it rather than the loader mark
     * that was cached for it a moment earlier.
     *
     * @param resources the Resources object, used for creating drawables
     * @param instance the instance
     * @return an icon drawable
     */
    public static @NonNull Drawable fetchIcon(Resources resources, @NonNull DisplayInstance instance) {
        String cacheKey = instance.getIconCacheKey();
        File iconLocation = instance.getOwnIconLocation();
        boolean hasIconFile = iconLocation != null && iconLocation.isFile();
        long stamp = hasIconFile ? iconLocation.lastModified() : 0L;

        CachedIcon cached = sIconCache.get(cacheKey);
        if(cached != null && cached.stamp == stamp) return cached.drawable;

        Drawable iconDrawable = hasIconFile ? decodeIconFile(resources, iconLocation) : null;
        if(iconDrawable == null) iconDrawable = fetchStaticIcon(resources, instance.icon);
        sIconCache.put(cacheKey, new CachedIcon(iconDrawable, stamp));
        return iconDrawable;
    }

    /**
     * Drop an icon from the icon cache. When dropped, it's Drawable will be re-read from the
     * instance icon file (or re-fetched from the static cache)
     * @param key the instance
     */
    public static void dropIcon(@NonNull DisplayInstance key) {
        sIconCache.remove(key.getIconCacheKey());
    }

    private static Drawable decodeIconFile(Resources resources, File iconLocation) {
        if(!iconLocation.isFile() || !iconLocation.canRead()) return null;
        Bitmap iconBitmap = BitmapFactory.decodeFile(iconLocation.getAbsolutePath());
        if(iconBitmap == null) return null;
        return new BitmapDrawable(resources, iconBitmap);
    }

    private static Drawable fetchStaticIcon(Resources resources, String icon) {
        String iconName = icon != null ? icon : FALLBACK_ICON_NAME;
        Drawable staticIcon = sStaticIconCache.get(iconName);
        if(staticIcon == null) {
            staticIcon = getStaticIcon(resources, iconName);
            if(staticIcon == null) staticIcon = fetchFallbackIcon(resources);
            sStaticIconCache.put(iconName, staticIcon);
        }
        return staticIcon;
    }

    private static @NonNull Drawable fetchFallbackIcon(Resources resources) {
        Drawable fallbackIcon = sStaticIconCache.get(FALLBACK_ICON_NAME);
        if(fallbackIcon == null) {
            fallbackIcon = Objects.requireNonNull(getStaticIcon(resources, FALLBACK_ICON_NAME));
            sStaticIconCache.put(FALLBACK_ICON_NAME, fallbackIcon);
        }
        return fallbackIcon;
    }

    private static Drawable getStaticIcon(Resources resources, @NonNull String icon) {
        int staticIconResource = getStaticIconResource(icon);
        if(staticIconResource == -1) return null;
        return ResourcesCompat.getDrawable(resources, staticIconResource, null);
    }

    private static int getStaticIconResource(String icon) {
        Integer iconResource = sStaticIcons.get(icon);
        if(iconResource == null) return -1;
        return iconResource;
    }

    /**
     * Check whether the icon under the specified name is a static icon available in the provider.
     * @param name static icon name to check
     * @return whether the icon is available or not
     */
    public static boolean hasStaticIcon(String name) {
        return sStaticIcons.containsKey(name);
    }
}
