package net.fabricmc.loader.api;

import java.nio.file.Path;

public interface FabricLoader {
    static FabricLoader getInstance() { return null; }
    Path getGameDir();
}
