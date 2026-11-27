package org.spongepowered.asm.mixin.injection.callback;

public class CallbackInfoReturnable<R> extends CallbackInfo {
    public void setReturnValue(R returnValue) {}
    public R getReturnValue() { return null; }
}
