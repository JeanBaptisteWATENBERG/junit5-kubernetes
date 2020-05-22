package com.github.jeanbaptistewatenberg.wait.impl.pod;

import com.github.jeanbaptistewatenberg.wait.WaitStrategy;
import com.google.gson.reflect.TypeToken;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.util.Watch;

import java.io.IOException;
import java.time.LocalDateTime;

public class PodWaitRunningStatusStrategy extends WaitStrategy<V1Pod> {

    @Override
    public void apply(Watch<V1Pod> podWatch, V1Pod createdPod) {
        boolean podSuccessfullyStarted = false;
        LocalDateTime startTime = LocalDateTime.now();
            for (Watch.Response<V1Pod> item : podWatch) {
                String name = item.object.getMetadata().getName();
                if (name.equals(createdPod.getMetadata().getName())) {
                    V1PodStatus podStatus = item.object.getStatus();
                    if (LocalDateTime.now().isBefore(startTime.plus(this.getTimeout()))) {
                        if (podStatus == null) {
                            continue;
                        }
                        if (podStatus.getPhase().equalsIgnoreCase("Running")) {
                            podSuccessfullyStarted = true;
                            break;
                        }
                    } else {
                        break;
                    }
                }
            }

        if (!podSuccessfullyStarted) {
            throw new RuntimeException("Failed to run pod " + this + " before timeout " + this.getTimeout());
        }
    }
}
