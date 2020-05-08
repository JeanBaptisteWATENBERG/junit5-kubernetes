package com.github.jeanbaptistewatenberg.traits;

import com.github.jeanbaptistewatenberg.wait.WaitStrategy;

public interface Waitable<T> {
    T withWaitStrategy(WaitStrategy waitStrategy);
}
