package com.github.jeanbaptistewatenberg.wait.impl;

import com.github.jeanbaptistewatenberg.wait.WaitStrategy;

import java.time.Duration;

public class WaitLogStrategy extends WaitStrategy {
    private String text;
    private int times = 1;

    public WaitLogStrategy(String text) {
        super();
        this.text = text;
    }

    public WaitLogStrategy(String text, int times) {
        super();
        this.text = text;
        this.times = times;
    }

    public WaitLogStrategy(String text, Duration timeout) {
        super(timeout);
        this.text = text;
    }

    public WaitLogStrategy(String text, int times, Duration timeout) {
        super(timeout);
        this.text = text;
        this.times = times;
    }

    public String getText() {
        return text;
    }

    public int getTimes() {
        return times;
    }
}
