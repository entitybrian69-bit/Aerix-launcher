package aerix.flashback.compat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

@Mixin(targets = "com.moulberry.flashback.utils.AsyncFileDialogs", remap = false)
public class MixinAsyncFileDialogsModern {

    @Inject(method = "saveFileDialog", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void aerix$saveFileDialog(String defaultPath, String defaultName, String filterDescription, String[] filters, CallbackInfoReturnable<CompletableFuture<String>> cir) {
        String ext = (filters != null && filters.length == 1 && filters[0] != null && !filters[0].isEmpty()) ? filters[0] : "mp4";
        String dir = (defaultPath != null && !defaultPath.trim().isEmpty()) ? defaultPath : "flashback/videos";
        String name = (defaultName != null && !defaultName.trim().isEmpty()) ? defaultName : "replay." + ext;
        if (!name.toLowerCase().endsWith("." + ext.toLowerCase())) {
            name = name + "." + ext;
        }
        Path fullPath = Paths.get(dir, name);
        try {
            if (fullPath.getParent() != null) Files.createDirectories(fullPath.getParent());
        } catch (Throwable ignored) {}
        cir.setReturnValue(CompletableFuture.completedFuture(fullPath.toAbsolutePath().toString()));
    }

    @Inject(method = "openFolderDialog", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void aerix$openFolderDialog(String defaultPath, CallbackInfoReturnable<CompletableFuture<String>> cir) {
        String dir = (defaultPath != null && !defaultPath.trim().isEmpty()) ? defaultPath : "flashback/exports";
        Path fullPath = Paths.get(dir);
        try {
            Files.createDirectories(fullPath);
        } catch (Throwable ignored) {}
        cir.setReturnValue(CompletableFuture.completedFuture(fullPath.toAbsolutePath().toString()));
    }

    @Inject(method = "openFileDialog", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void aerix$openFileDialog(String defaultPath, String filterDescription, String[] filters, CallbackInfoReturnable<CompletableFuture<String>> cir) {
        String resolved = null;
        if (defaultPath != null && !defaultPath.trim().isEmpty()) {
            Path p = Paths.get(defaultPath);
            if (Files.isRegularFile(p)) {
                resolved = p.toAbsolutePath().toString();
            }
        }
        cir.setReturnValue(CompletableFuture.completedFuture(resolved));
    }
}
