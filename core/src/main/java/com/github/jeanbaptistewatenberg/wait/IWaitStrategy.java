package com.github.jeanbaptistewatenberg.wait;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Watch;

import java.io.IOException;

public interface IWaitStrategy {
    void apply(Watch<V1Pod> podWatch, final V1Pod createdPod) throws ApiException;
}
