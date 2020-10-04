package com.github.jeanbaptistewatenberg.junit5kubernetes.elasticsearch;

import com.github.jeanbaptistewatenberg.junit5kubernetes.core.Pod;
import com.github.jeanbaptistewatenberg.junit5kubernetes.core.PortMapper;
import com.github.jeanbaptistewatenberg.junit5kubernetes.core.wait.impl.pod.PodWaitLogStrategy;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.*;

import java.time.Duration;

public class ElasticSearchPod extends Pod {

    private static final int DEFAULT_HTTP_PORT = 9200;
    private static final int DEFAULT_TCP_PORT = 9300;

    private static final String NAMED_HTTP_PORT = "DEFAULT_HTTP_PORT_9200";
    private static final String NAMED_TCP_PORT = "DEFAULT_TCP_PORT_9300";

    private final PortMapper portMapper = new PortMapper();

    private static final String DEFAULT_IMAGE = "docker.elastic.co/elasticsearch/elasticsearch";
    private static final String DEFAULT_TAG = "7.9.2";
    public static final String JUNIT_5_KUBERNETES_ELASTIC_SEARCH_CONTAINER = "junit5kuberneteselasticsearchcontainer";
    public ElasticSearchPod() {
        this(DEFAULT_IMAGE + ":" + DEFAULT_TAG);
    }

    /**
     * @return URL of the HTTP management endpoint.
     */
    public String getHttpUrl() {
        return "http://" + getObjectHostIp() + ":" + getHttpPort();
    }

    public Integer getHttpPort() {
        return portMapper.getComputedPort(NAMED_HTTP_PORT);
    }

    public ElasticSearchPod(final String dockerImageName) {
        super(new V1PodBuilder()
                .withNewSpec()
                .addNewInitContainer()
                .withImage("busybox")
                .withName("set-vm-max-map-count")
                .withSecurityContext(new V1SecurityContext().privileged(true))
                .withCommand("sysctl","-w","vm.max_map_count=262144")
                .endInitContainer()
                .addNewContainer()
                .withName(JUNIT_5_KUBERNETES_ELASTIC_SEARCH_CONTAINER)
                .withEnv(new V1EnvVar().name("discovery.type").value("single-node"))
                .withImage(dockerImageName)
                .withSecurityContext(new V1SecurityContext().runAsNonRoot(true).runAsUser(1000L))
                .endContainer()
                .endSpec()
                .build());
        this.waitStrategy = new PodWaitLogStrategy(".*started.*",Duration.ofSeconds(60));
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
                throw logAndThrowApiException(e);
            }
        } else {
            throw new RuntimeException("Can't get ip of a non running object.");
        }
    }

    @Override
    protected void onBeforeCreateKubernetesObject() {
        super.onBeforeCreateKubernetesObject();
            V1Container elasticContainer = this.podToCreate.getSpec().getContainers().get(0);
            elasticContainer
                    .addPortsItem(new V1ContainerPort()
                            .hostPort(portMapper.computeAvailablePort(NAMED_HTTP_PORT))
                            .containerPort(DEFAULT_HTTP_PORT)
                    ).addPortsItem(new V1ContainerPort()
                    .hostPort(portMapper.computeAvailablePort(NAMED_TCP_PORT))
                    .containerPort(DEFAULT_TCP_PORT)
            );

    }
}
