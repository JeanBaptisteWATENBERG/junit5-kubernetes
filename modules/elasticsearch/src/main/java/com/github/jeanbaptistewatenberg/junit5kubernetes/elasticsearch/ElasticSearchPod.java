package com.github.jeanbaptistewatenberg.junit5kubernetes.elasticsearch;

import com.github.jeanbaptistewatenberg.junit5kubernetes.core.Pod;
import com.github.jeanbaptistewatenberg.junit5kubernetes.core.PortMapper;
import com.github.jeanbaptistewatenberg.junit5kubernetes.core.wait.impl.pod.PodWaitLogStrategy;
import io.kubernetes.client.openapi.models.*;

import java.net.InetSocketAddress;
import java.time.Duration;

public class ElasticSearchPod extends Pod {

    private static final int ELASTICSEARCH_DEFAULT_PORT = 9200;
    private static final int ELASTICSEARCH_DEFAULT_TCP_PORT = 9300;

    private static final String NAMED_HTTP_PORT = "DEFAULT_HTTP_PORT_9200";
    private static final String NAMED_TCP_PORT = "DEFAULT_TCP_PORT_9300";

    private final PortMapper portMapper = new PortMapper();

    private static final String DEFAULT_IMAGE = "docker.elastic.co/elasticsearch/elasticsearch";
    private static final String DEFAULT_TAG = "7.9.2";
    private static final String INIT_IMAGE = "busybox";
    public static final String JUNIT_5_KUBERNETES_ELASTIC_SEARCH_CONTAINER = "junit5kuberneteselasticsearchcontainer";

    public ElasticSearchPod() {
        this(DEFAULT_IMAGE + ":" + DEFAULT_TAG, INIT_IMAGE);
    }

    public ElasticSearchPod(final String dockerImageName) {
        this(dockerImageName, INIT_IMAGE);
    }

    public ElasticSearchPod(final String dockerImageName, final String initContainerImage) {
        super(new V1PodBuilder()
                .withNewSpec()
                .addNewInitContainer()
                .withImage(initContainerImage)
                .withName("set-vm-max-map-count")
                .withSecurityContext(new V1SecurityContext().privileged(true))
                .withCommand("sysctl", "-w", "vm.max_map_count=262144")
                .endInitContainer()
                .addNewContainer()
                .withName(JUNIT_5_KUBERNETES_ELASTIC_SEARCH_CONTAINER)
                .withEnv(new V1EnvVar().name("discovery.type").value("single-node"))
                .withImage(dockerImageName)
                .withSecurityContext(new V1SecurityContext().runAsNonRoot(true).runAsUser(1000L))
                .endContainer()
                .endSpec()
                .build());
        this.waitStrategy = new PodWaitLogStrategy(".*started.*", Duration.ofSeconds(60));
    }

    public String getHttpHostAddress() {
        return getObjectHostIp() + ":" + getHttpPort();
    }

    public InetSocketAddress getTcpHost() {
        return new InetSocketAddress(getObjectHostIp(), getTcpPort());
    }

    public Integer getHttpPort() {
        return portMapper.getComputedPort(NAMED_HTTP_PORT);
    }

    public Integer getTcpPort() {
        return portMapper.getComputedPort(NAMED_TCP_PORT);
    }


    @Override
    protected void onBeforeCreateKubernetesObject() {
        super.onBeforeCreateKubernetesObject();
        V1Container elasticContainer = this.podToCreate.getSpec().getContainers().get(0);
        elasticContainer
                .addPortsItem(new V1ContainerPort()
                        .hostPort(portMapper.computeAvailablePort(NAMED_HTTP_PORT))
                        .containerPort(ELASTICSEARCH_DEFAULT_PORT)
                ).addPortsItem(new V1ContainerPort()
                .hostPort(portMapper.computeAvailablePort(NAMED_TCP_PORT))
                .containerPort(ELASTICSEARCH_DEFAULT_TCP_PORT)
        );

    }
}
