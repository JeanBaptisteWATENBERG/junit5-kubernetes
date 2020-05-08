package com.github.jeanbaptistewatenberg;

import com.github.jeanbaptistewatenberg.wait.WaitStrategy;
import com.github.jeanbaptistewatenberg.wait.impl.WaitLogStrategy;
import com.github.jeanbaptistewatenberg.wait.impl.WaitRunningStatusStrategy;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import io.kubernetes.client.PodLogs;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Watch;
import okhttp3.OkHttpClient;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Pod implements KubernetesGenericObject<Pod> {
    public static final String JUNIT_5_KUBERNETES_POD_PREFIX = "junit5-kubernetes-pod-";
    private final CoreV1Api coreV1Api;
    private WaitStrategy waitStrategy;
    private final V1Pod podToCreate;
    private ThreadLocal<V1Pod> createdPod = new ThreadLocal<>();
    private static final String SYSTEM_NAMESPACE = System.getProperty("kubernetesNamespace");
    private static final String NAMESPACE = SYSTEM_NAMESPACE != null && !SYSTEM_NAMESPACE.trim().equals("") ? SYSTEM_NAMESPACE : "default";

    public Pod(V1Pod podToCreate) {
        this.podToCreate = podToCreate;
        this.coreV1Api = initiateCoreV1Api();
    }

    private static CoreV1Api initiateCoreV1Api() {
        CoreV1Api coreV1Api;
        try {
            ApiClient client = Config.defaultClient();
            // infinite timeout
            OkHttpClient httpClient = client.getHttpClient().newBuilder().readTimeout(0, TimeUnit.SECONDS).build();
            client.setHttpClient(httpClient);
            Configuration.setDefaultApiClient(client);

            coreV1Api = new CoreV1Api(client);

        } catch (IOException e) {
            throw new  RuntimeException(e);
        }
        return coreV1Api;
    }

    public static void cleanup() {
        CoreV1Api coreV1Api = initiateCoreV1Api();
        try {
            V1PodList v1PodList = coreV1Api.listNamespacedPod(NAMESPACE, null, null, null, null, null, null, null, null, null);
            List<String> podNamesToRemove = v1PodList.getItems().stream()
                    .map(v1Pod -> v1Pod.getMetadata().getName())
                    .filter(v1PodName -> v1PodName.startsWith(JUNIT_5_KUBERNETES_POD_PREFIX))
                    .collect(Collectors.toList());
            podNamesToRemove.forEach(podName -> removePod(podName, coreV1Api));
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Pod withWaitStrategy(WaitStrategy waitStrategy) {
        this.waitStrategy = waitStrategy;
        return this;
    }

    @Override
    public String getObjectName() {
        V1Pod v1Pod = this.createdPod.get();
        if (v1Pod != null) {
            return v1Pod.getMetadata().getName();
        } else {
            throw new RuntimeException("Can't get name of a non running object.");
        }
    }

    @Override
    public String getObjectHostIp() {
        V1Pod v1Pod = this.createdPod.get();
        if (v1Pod != null) {
            try {
                V1Pod retrievedPod = coreV1Api.readNamespacedPod(v1Pod.getMetadata().getName(), NAMESPACE, null, null, null);
                if (retrievedPod.getStatus() == null) {
                    throw new RuntimeException("Can't get ip of a non running object.");
                }
                return retrievedPod.getStatus().getHostIP();
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new RuntimeException("Can't get ip of a non running object.");
        }
    }

    @Override
    public void apply() {
        try {
            if (podToCreate.getMetadata() == null) {
                podToCreate.setMetadata(new V1ObjectMeta());
            }
            String podName = JUNIT_5_KUBERNETES_POD_PREFIX + UUID.randomUUID().toString().split("-")[0];
            podToCreate.getMetadata().setName(podName);
            V1Pod createdPod = coreV1Api.createNamespacedPod(NAMESPACE, podToCreate, null, null, null);
            this.createdPod.set(createdPod);
            if (this.waitStrategy != null) {
                LocalDateTime startTime = LocalDateTime.now();
                if (this.waitStrategy instanceof WaitRunningStatusStrategy) {
                    boolean podSuccessfullyStarted = false;

                    try(Watch<V1Pod> watch = Watch.createWatch(
                            coreV1Api.getApiClient(),
                            coreV1Api.listNamespacedPodCall(
                                    NAMESPACE, null, null, null, null, null,
                                    null, null, null, true,
                                    null),
                            new TypeToken<Watch.Response<V1Pod>>() {}.getType())) {

                        for (Watch.Response<V1Pod> item : watch) {
                            String name = item.object.getMetadata().getName();
                            if (name.equals(createdPod.getMetadata().getName())) {
                                V1PodStatus podStatus = item.object.getStatus();
                                if (LocalDateTime.now().isBefore(startTime.plus(waitStrategy.getTimeout()))) {
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
                    }

                    if (!podSuccessfullyStarted) {
                        throw new RuntimeException("Failed to run pod " + this + " before timeout " + waitStrategy.getTimeout());
                    }
                }
                else if (this.waitStrategy instanceof WaitLogStrategy) {
                    PodLogs logs = new PodLogs();

                    //Wait pod to start
                    try(Watch<V1Pod> watch = Watch.createWatch(
                            coreV1Api.getApiClient(),
                            coreV1Api.listNamespacedPodCall(
                                    NAMESPACE, null, null, null, null, null,
                                    null, null, null, true,
                                    null),
                            new TypeToken<Watch.Response<V1Pod>>() {}.getType())) {

                        for (Watch.Response<V1Pod> item : watch) {
                            String name = item.object.getMetadata().getName();
                            if (name.equals(createdPod.getMetadata().getName())) {
                                V1PodStatus podStatus = item.object.getStatus();
                                if (LocalDateTime.now().isAfter(startTime.plus(waitStrategy.getTimeout()))) {
                                    throw new RuntimeException("Failed to start pod " + createdPod + " before timeout " + waitStrategy.getTimeout());
                                }
                                if (podStatus == null || podStatus.getPhase().equalsIgnoreCase("Pending") || podStatus.getPhase().equalsIgnoreCase("Unknown")) {
                                    continue;
                                }
                                break;
                            }
                        }
                    }
                    //Read pods logs
                    try (InputStream is = logs.streamNamespacedPodLog(createdPod)) {
                        Scanner sc = new Scanner(is);
                        int conditionMetTimes = 0;
                        String textOrRegex = ((WaitLogStrategy) this.waitStrategy).getText();
                        int howManyTimesShouldConditionMet = ((WaitLogStrategy) this.waitStrategy).getTimes();
                        while (sc.hasNextLine() && LocalDateTime.now().isBefore(startTime.plus(waitStrategy.getTimeout()))) {
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
                            throw new RuntimeException("Failed to find (x" + howManyTimesShouldConditionMet + ") " + textOrRegex + " in log of pod " + createdPod + " before timeout " + waitStrategy.getTimeout());
                        }
                    }
                }
            }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> removePod(podName, coreV1Api)));
        } catch (IOException | ApiException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void remove() {
        V1Pod v1Pod = this.createdPod.get();
        if (v1Pod != null) {
            String podName = v1Pod.getMetadata().getName();
            removePod(podName, coreV1Api);

        }
    }

    private static void removePod(String podName, CoreV1Api coreV1Api) {
        try {
            coreV1Api.deleteNamespacedPod(podName, NAMESPACE, null, null, null, null, null, null);
        } catch (ApiException e) {
            throw new RuntimeException(e);
        } catch (JsonSyntaxException e) {
            if (e.getCause() instanceof IllegalStateException) {
                IllegalStateException ise = (IllegalStateException) e.getCause();
                if (ise.getMessage() != null && ise.getMessage().contains("Expected a string but was BEGIN_OBJECT")) {
                    // Catching exception because of issue https://github.com/kubernetes-client/java/issues/86
                }
                else throw e;
            }
            else throw e;
        }
    }

    @Override
    public String toString() {
        return "Pod{" +
                "waitStrategy=" + waitStrategy +
                "V1Pod=" + podToCreate +
                '}';
    }
}
