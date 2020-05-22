package com.github.jeanbaptistewatenberg.junit5kubernetes.core;

import com.github.jeanbaptistewatenberg.junit5kubernetes.core.traits.Waitable;

public abstract class KubernetesGenericObject<T> implements Waitable<T> {

    public abstract String getObjectName();
    public abstract String getObjectHostIp();
    public abstract void create();
    public abstract void remove();

    // Listeners to hook into kubernetes object lifecycle

    protected void onBeforeCreateKubernetesObject() {
        // Default NOOP onBeforeCreate listener
    }

    protected void onKubernetesObjectReady() {
        // Default NOOP onReady listener
    }

}
