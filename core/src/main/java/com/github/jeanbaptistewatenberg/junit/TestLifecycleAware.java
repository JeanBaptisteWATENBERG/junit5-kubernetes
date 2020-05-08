package com.github.jeanbaptistewatenberg.junit;

import java.util.Optional;

public interface TestLifecycleAware {

    default void beforeTest(TestDescription description) {

    }

    default void afterTest(TestDescription description, Optional<Throwable> throwable) {

    }
}
