package com.github.jeanbaptistewatenberg.wait.impl;

import com.github.jeanbaptistewatenberg.wait.WaitStrategy;
import com.google.gson.reflect.TypeToken;
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


    @Override
    public void apply(Watch<V1Pod> podWatch, final V1Pod createdPod) throws ApiException {
        LocalDateTime startTime = LocalDateTime.now();
        PodLogs logs = new PodLogs();
        //Wait pod to start

        for (Watch.Response<V1Pod> item : podWatch) {
            String name = item.object.getMetadata().getName();
            if (name.equals(createdPod.getMetadata().getName())) {
                V1PodStatus podStatus = item.object.getStatus();
                if (LocalDateTime.now().isAfter(startTime.plus(this.getTimeout()))) {
                    throw new RuntimeException("Failed to start pod " + createdPod + " before timeout " + this.getTimeout());
                }
                if (podStatus == null || podStatus.getPhase().equalsIgnoreCase("Pending") || podStatus.getPhase().equalsIgnoreCase("Unknown")) {
                    continue;
                }
                break;
            }
        }

        //Read pods logs
        try (InputStream is = logs.streamNamespacedPodLog(createdPod)) {
            Scanner sc = new Scanner(is);
            int conditionMetTimes = 0;
            String textOrRegex = this.getText();
            int howManyTimesShouldConditionMet = this.getTimes();
            while (sc.hasNextLine() && LocalDateTime.now().isBefore(startTime.plus(this.getTimeout()))) {
                String input = sc.nextLine();
                //Check if log line matches the expected text or regex
                if (input.matches(textOrRegex) || input.contains(textOrRegex)) {
                    conditionMetTimes++;
                    if (conditionMetTimes == howManyTimesShouldConditionMet) {
                        break;
                    }
                }
            }

            if (conditionMetTimes == 0 || conditionMetTimes != howManyTimesShouldConditionMet) {
                throw new RuntimeException("Failed to find (x" + howManyTimesShouldConditionMet + ") " + textOrRegex + " in log of pod " + createdPod + " before timeout " + this.getTimeout());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
