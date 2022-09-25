package com.github.jeanbaptistewatenberg.junit5kubernetes.core;

import com.github.jeanbaptistewatenberg.junit5kubernetes.core.wait.WaitStrategy;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Watch;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class Service extends KubernetesGenericObject<Service> {
    protected static final String DEBUG = System.getProperty("junitKubernetesDebug");
    protected static final String DISABLE_HTTP2 = System.getProperty("junitKubernetesDisableHttp2");
    protected static final String SYSTEM_NAMESPACE = System.getProperty("kubernetesNamespace");
    protected static final String NAMESPACE = SYSTEM_NAMESPACE != null && !SYSTEM_NAMESPACE.trim().equals("") ? SYSTEM_NAMESPACE : "default";
    private static final Logger LOGGER = Logger.getLogger(Service.class.getName());

    protected final V1Service serviceToCreate;
    protected final CoreV1Api coreV1Api;
    protected WaitStrategy<V1Service> waitStrategy;

    protected final ThreadLocal<V1Service> createdService = new ThreadLocal<>();

    public Service(V1Service serviceToCreate) {
        this.serviceToCreate = serviceToCreate;
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

            client.setVerifyingSsl(false);

            OkHttpClient httpClient = builder.build();
            client.setHttpClient(httpClient);
            Configuration.setDefaultApiClient(client);

            coreV1Api = new CoreV1Api(client);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return coreV1Api;
    }

    @Override
    public String getObjectName() {
        V1Service v1Service = this.createdService.get();
        if (v1Service != null) {
            return v1Service.getMetadata().getName();
        } else {
            throw new RuntimeException("Can't get name of a non running object.");
        }
    }

    @Override
    public String getObjectHostIp() {
        V1Service v1Service = this.createdService.get();
        if (v1Service != null) {
            try {
                V1Service retrievedService = coreV1Api.readNamespacedService(v1Service.getMetadata().getName(), NAMESPACE, null, null, null);
                if (retrievedService.getStatus() == null) {
                    throw new RuntimeException("Can't get ip of a non running object.");
                }
                //Anhand der Umgebungsvariable entscheiden, ob der Test im Cluster oder lokal ausgeführt wird
                if(System.getenv("KUBERNETES_PORT") != null) {
                    return retrievedService.getSpec().getClusterIP();
                }else{
                    return retrievedService.getSpec().getExternalIPs().get(0);
                }
            } catch (ApiException e) {
                throw logAndThrowApiException(e);
            }
        } else {
            throw new RuntimeException("Can't get ip of a non running object.");
        }
    }

    @Override
    public void create() {
        try{
            createdService.set(coreV1Api.createNamespacedService(NAMESPACE, serviceToCreate, null, null, null));
            final String serviceName = createdService.get().getMetadata().getName();

            if (this.waitStrategy != null) {
                try (Watch<V1Service> watch = Watch.createWatch(
                        coreV1Api.getApiClient(),
                        coreV1Api.listNamespacedServiceCall(NAMESPACE, null, null, null,
                                null, null, null, null, null, true, null),
                        new TypeToken<Watch.Response<V1Service>>() {
                        }.getType())) {

                    this.waitStrategy.apply(watch, createdService.get());
                } catch (IOException | ApiException e) {
                    if (e instanceof ApiException) {
                        throw logAndThrowApiException((ApiException) e);
                    }
                    throw new RuntimeException(e);
                }
            }

            Runtime.getRuntime().addShutdownHook(new Thread(() -> removeService(serviceName, coreV1Api)));
            onKubernetesObjectReady();
        } catch (ApiException e) {
            throw logAndThrowApiException(e);
        }
    }

    @Override
    public void remove() {
        V1Service v1Service = this.createdService.get();
        if (v1Service != null) {
            String serviceName = v1Service.getMetadata().getName();
            removeService(serviceName, coreV1Api);
        }
    }

    private static void removeService(String serviceName, CoreV1Api coreV1Api) {
        try {
            coreV1Api.deleteNamespacedService(serviceName, NAMESPACE, null, null, null, null, null, null);
        } catch (ApiException e) {
            throw logAndThrowApiException(e);
        } catch (JsonSyntaxException e) {
            if (e.getCause() instanceof IllegalStateException) {
                IllegalStateException ise = (IllegalStateException) e.getCause();
                if (ise.getMessage() != null && ise.getMessage().contains("Expected a string but was BEGIN_OBJECT")) {
                    // Catching exception because of issue https://github.com/kubernetes-client/java/issues/86
                } else throw e;
            } else throw e;
        }
    }

    @Override
    public Service withWaitStrategy(WaitStrategy waitStrategy) {
        this.waitStrategy = waitStrategy;
        return this;
    }

    protected static RuntimeException logAndThrowApiException(ApiException e) {
        LOGGER.severe("Kubernetes API replied with " + e.getCode() + " status code and body " + e.getResponseBody());
        System.out.println("Kubernetes API replied with " + e.getCode() + " status code and body " + e.getResponseBody());
        return new RuntimeException(e.getResponseBody(), e);
    }
}
