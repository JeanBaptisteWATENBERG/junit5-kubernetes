package com.github.jeanbaptistewatenberg.junit5kubernetes.core.traits;

import com.github.jeanbaptistewatenberg.junit5kubernetes.core.wait.WaitStrategy;

public interface Waitable<T> {
    T withWaitStrategy(WaitStrategy<?> waitStrategy);
}
