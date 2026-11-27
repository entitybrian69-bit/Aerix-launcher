package org.spongepowered.asm.mixin.injection;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface Inject {
    String[] method();
    At[] at();
    boolean cancellable() default false;
    boolean remap() default true;
    int require() default -1;
}
