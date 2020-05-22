package com.github.jeanbaptistewatenberg.junit5kubernetes.core;

import com.github.jeanbaptistewatenberg.junit5kubernetes.core.wait.WaitStrategy;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Watch;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Pod extends KubernetesGenericObject<Pod> {
    public static final String JUNIT_5_KUBERNETES_POD_PREFIX = "junit5-kubernetes-pod-";
    protected WaitStrategy<V1Pod> waitStrategy;
    protected final V1Pod podToCreate;
    protected final ThreadLocal<V1Pod> createdPod = new ThreadLocal<>();
    protected static final String SYSTEM_NAMESPACE = System.getProperty("kubernetesNamespace");
    protected static final String SYSTEM_PULL_SECRETS = System.getProperty("kubernetesPullSecrets");
    protected static final String DEBUG = System.getProperty("junitKubernetesDebug");
    protected static final String DISABLE_HTTP2 = System.getProperty("junitKubernetesDisableHttp2");
    protected static final String NAMESPACE = SYSTEM_NAMESPACE != null && !SYSTEM_NAMESPACE.trim().equals("") ? SYSTEM_NAMESPACE : "default";
    private static final Logger LOGGER = Logger.getLogger(Pod.class.getName());
    private final CoreV1Api coreV1Api;

    public Pod(V1Pod podToCreate) {
        this.podToCreate = podToCreate;
        this.coreV1Api = initiateCoreV1Api();
    }

    private static CoreV1Api initiateCoreV1Api() {
        CoreV1Api coreV1Api;
        try {
            ApiClient client = Config.defaultClient();
            if (DEBUG != null && DEBUG.equalsIgnoreCase("true")) {
                client.setDebugging(true);
            }
            // infinite timeout
            OkHttpClient.Builder builder = client.getHttpClient().newBuilder()
                    .readTimeout(0, TimeUnit.SECONDS);

            if (DISABLE_HTTP2 != null && DISABLE_HTTP2.equalsIgnoreCase("true")) {
                builder.protocols(Collections.singletonList(Protocol.HTTP_1_1));
            }

            OkHttpClient httpClient = builder.build();
            client.setHttpClient(httpClient);
            Configuration.setDefaultApiClient(client);

            coreV1Api = new CoreV1Api(client);

        } catch (IOException e) {
            throw new  RuntimeException(e);
        }
        return coreV1Api;
    }

    // TODO study cleanup mechanism if JVM is interrupted before test completion
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
                return logAnThrowApiException(e);
            }
        } else {
            throw new RuntimeException("Can't get ip of a non running object.");
        }
    }

    private static String logAnThrowApiException(ApiException e) {
        LOGGER.severe("Kubernetes API replied with " + e.getCode() + " status code and body " + e.getResponseBody());
        System.out.println("Kubernetes API replied with " + e.getCode() + " status code and body " + e.getResponseBody());
        throw new RuntimeException(e.getResponseBody(), e);
    }

    @Override
    public void create() {
        try {
            if (podToCreate.getMetadata() == null) {
                podToCreate.setMetadata(new V1ObjectMeta());
            }
            String podName = JUNIT_5_KUBERNETES_POD_PREFIX + UUID.randomUUID().toString().split("-")[0];
            podToCreate.getMetadata().setName(podName);
            if (SYSTEM_PULL_SECRETS != null && !SYSTEM_PULL_SECRETS.isEmpty() && podToCreate.getSpec() != null) {
                for (String secret : SYSTEM_PULL_SECRETS.split(",")) {
                    podToCreate.getSpec().addImagePullSecretsItem(new V1LocalObjectReferenceBuilder().withName(secret).build());
                }
            }
            onBeforeCreateKubernetesObject();
            V1Pod createdPod = coreV1Api.createNamespacedPod(NAMESPACE, podToCreate, null, null, null);
            this.createdPod.set(createdPod);
            if (this.waitStrategy != null) {
                try(Watch<V1Pod> watch = Watch.createWatch(
                        coreV1Api.getApiClient(),
                        coreV1Api.listNamespacedPodCall(
                                NAMESPACE, null, null, null, null, null,
                                null, null, null, true,
                                null),
                        new TypeToken<Watch.Response<V1Pod>>() {}.getType())) {
                this.waitStrategy.apply(watch, createdPod);
                } catch (IOException | ApiException e) {
                    if (e instanceof ApiException) {
                        logAnThrowApiException((ApiException) e);
                    }
                    throw new RuntimeException(e);
                }
            }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> removePod(podName, coreV1Api)));
            onKubernetesObjectReady();
        } catch (ApiException e) {
            logAnThrowApiException(e);
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
            logAnThrowApiException(e);
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
