package com.github.jeanbaptistewatenberg.junit5kubernetes.core.wait.impl.service;

import com.github.jeanbaptistewatenberg.junit5kubernetes.core.wait.WaitStrategy;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.util.Watch;

import java.time.LocalDateTime;

public class ServiceWaitReadyStrategy extends WaitStrategy<V1Service> {

    private final CoreV1Api coreClient;

    public ServiceWaitReadyStrategy() {
        this.coreClient = new CoreV1Api(Configuration.getDefaultApiClient());
    }

    @Override
    public void apply(Watch<V1Service> resourceWatch, V1Service createdResource) throws ApiException {

        boolean serviceSuccessfullyStarted = false;
        LocalDateTime startTime = LocalDateTime.now();
        for (Watch.Response<V1Service> item : resourceWatch) {
            String name = item.object.getMetadata().getName();
            if (name.equals(createdResource.getMetadata().getName())) {

                while (LocalDateTime.now().isBefore(startTime.plus(this.getTimeout()))) {

                    V1Service retrievedService = coreClient.readNamespacedService(createdResource.getMetadata().getName(),
                            createdResource.getMetadata().getNamespace(), null, null, null);
                    if (retrievedService.getSpec().getExternalIPs() != null && retrievedService.getSpec().getExternalIPs().size() > 0) {
                        serviceSuccessfullyStarted = true;
                        break;
                    }
                }
                break;
            }
        }

        if (!serviceSuccessfullyStarted) {
            throw new RuntimeException("Failed to run pod " + this + " before timeout " + this.getTimeout());
        }
    }
}
