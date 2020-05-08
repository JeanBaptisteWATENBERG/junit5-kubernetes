package com.github.jeanbaptistewatenberg;

import com.github.jeanbaptistewatenberg.traits.Waitable;

public interface KubernetesGenericObject<T> extends Waitable<T> {

    String getObjectName();
    String getObjectHostIp();
    void apply();
    void remove();

}
