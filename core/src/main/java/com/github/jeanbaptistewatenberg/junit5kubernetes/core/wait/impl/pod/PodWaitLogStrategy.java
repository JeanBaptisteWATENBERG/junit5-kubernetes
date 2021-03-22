package com.github.jeanbaptistewatenberg.junit5kubernetes.core.wait.impl.pod;

import com.github.jeanbaptistewatenberg.junit5kubernetes.core.wait.WaitStrategy;
import io.kubernetes.client.PodLogs;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.util.Watch;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class PodWaitLogStrategy extends WaitStrategy<V1Pod> {
    private String text;
    private int times = 1;

    public PodWaitLogStrategy(String text) {
        super();
        this.text = text;
    }

    public PodWaitLogStrategy(String text, int times) {
        super();
        this.text = text;
        this.times = times;
    }

    public PodWaitLogStrategy(String text, Duration timeout) {
        super(timeout);
        this.text = text;
    }

    public PodWaitLogStrategy(String text, int times, Duration timeout) {
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


    @Override
    public void apply(Watch<V1Pod> resourceWatch, V1Pod createdResource) throws ApiException {
        LocalDateTime startTime = LocalDateTime.now();
        PodLogs logs = new PodLogs();
        //Wait pod to start

        for (Watch.Response<V1Pod> item : resourceWatch) {
            String name = item.object.getMetadata().getName();
            if (name.equals(createdResource.getMetadata().getName())) {
                V1PodStatus podStatus = item.object.getStatus();
                if (LocalDateTime.now().isAfter(startTime.plus(this.getTimeout()))) {
                    throw new RuntimeException("Failed to start resource " + createdResource + " before timeout " + this.getTimeout());
                }
                if (podStatus == null || podStatus.getPhase().equalsIgnoreCase("Pending") || podStatus.getPhase().equalsIgnoreCase("Unknown")) {
                    continue;
                }
                break;
            }
        }

        readPodsLogs(logs, createdResource);
    }

    private void readPodsLogs(PodLogs logs, V1Pod createdResource) {

        try (InputStream is = logs.streamNamespacedPodLog(createdResource)) {
            String textOrRegex = this.getText();
            AtomicInteger conditionMetTimes = new AtomicInteger();
            int howManyTimesShouldConditionMet = this.getTimes();

            CompletableFuture.runAsync(() -> {
                Scanner sc = new Scanner(is);
                while (sc.hasNextLine()) {
                    String input = sc.nextLine();
                    //Check if log line matches the expected text or regex
                    if (input.matches(textOrRegex) || input.contains(textOrRegex)) {
                        conditionMetTimes.getAndIncrement();
                        if (conditionMetTimes.get() == howManyTimesShouldConditionMet) {
                            break;
                        }
                    }
                }
            }).get(this.getTimeout().toMillis(), TimeUnit.MILLISECONDS);

            if (conditionMetTimes.get() != howManyTimesShouldConditionMet) {
                throw new RuntimeException(
                        "Failed to find (x" + howManyTimesShouldConditionMet + ") " + textOrRegex + " in log of resource " + createdResource + " before timeout "
                                + this.getTimeout());
            }

        } catch (InterruptedException | ExecutionException | TimeoutException | ApiException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}
