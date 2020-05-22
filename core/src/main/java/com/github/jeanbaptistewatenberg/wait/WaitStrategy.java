package com.github.jeanbaptistewatenberg.wait;

import java.time.Duration;

public abstract class WaitStrategy<T> implements IWaitStrategy<T> {
    private Duration timeout = Duration.ofSeconds(30);

    public WaitStrategy() {
    }

    public WaitStrategy(Duration timeout) {
        this.timeout = timeout;
    }

    public Duration getTimeout() {
        return timeout;
    }
}
