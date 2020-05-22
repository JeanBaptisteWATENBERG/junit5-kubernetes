package com.github.jeanbaptistewatenberg.junit5kubernetes.core;

import com.github.jeanbaptistewatenberg.junit5kubernetes.core.junit.JunitKubernetesExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(JunitKubernetesExtension.class)
@Inherited
public @interface JunitKubernetes {
}
