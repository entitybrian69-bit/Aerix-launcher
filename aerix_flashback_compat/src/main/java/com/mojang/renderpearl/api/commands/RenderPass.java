package com.mojang.renderpearl.api.commands;

public interface RenderPass {
    void enableScissor(int x, int y, int width, int height);
    void disableScissor();
    void drawIndexed(int indexCount, int instanceCount, int firstIndex, int firstVertex, int firstInstance);
}
