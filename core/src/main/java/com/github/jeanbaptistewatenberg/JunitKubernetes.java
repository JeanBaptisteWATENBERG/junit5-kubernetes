package com.github.jeanbaptistewatenberg;

import com.github.jeanbaptistewatenberg.junit.JunitKubernetesExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(JunitKubernetesExtension.class)
@Inherited
public @interface JunitKubernetes {
}
