package com.api.backend.global.lock;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DistributedLock {
    String key();
    long waitTime() default 5;
    long leaseTime() default 3;
    TimeUnit timeUnit() default TimeUnit.SECONDS;
}
