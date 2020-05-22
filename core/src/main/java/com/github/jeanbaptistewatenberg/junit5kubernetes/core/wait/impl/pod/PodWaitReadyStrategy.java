package com.github.jeanbaptistewatenberg.junit5kubernetes.core.wait.impl.pod;

import com.github.jeanbaptistewatenberg.junit5kubernetes.core.wait.WaitStrategy;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.util.Watch;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class PodWaitReadyStrategy extends WaitStrategy<V1Pod> {

    private final Set<String> containers;

    public PodWaitReadyStrategy(String... containers) {
        super();
        this.containers = Arrays.stream(containers).collect(Collectors.toSet());
    }

    public PodWaitReadyStrategy(Duration timeout, String... containers) {
        super(timeout);
        this.containers = Arrays.stream(containers).collect(Collectors.toSet());
    }

    @Override
    public void apply(Watch<V1Pod> resourceWatch, V1Pod createdResource) throws ApiException {
        boolean podSuccessfullyStarted = false;
        LocalDateTime startTime = LocalDateTime.now();
        for (Watch.Response<V1Pod> item : resourceWatch) {
            String name = item.object.getMetadata().getName();
            if (name.equals(createdResource.getMetadata().getName())) {
                V1PodStatus podStatus = item.object.getStatus();
                Map<String, Boolean> containerStatuses = new HashMap<>();
                if (LocalDateTime.now().isBefore(startTime.plus(this.getTimeout()))) {
                    if (podStatus == null || podStatus.getContainerStatuses() == null) {
                        continue;
                    }
                    for (V1ContainerStatus containerStatus : podStatus.getContainerStatuses()) {
                        if (containers.size() == 0 || containers.contains(containerStatus.getName())) {
                            containerStatuses.put(containerStatus.getName(), containerStatus.getReady());
                        }
                    }
                    Optional<Boolean> anyNotReadyContainer = containerStatuses.values().stream().filter(isReady -> !isReady).findAny();
                    if (!anyNotReadyContainer.isPresent()) {
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
