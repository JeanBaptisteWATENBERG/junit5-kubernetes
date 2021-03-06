package com.github.jeanbaptistewatenberg.junit5kubernetes.core;

import com.github.jeanbaptistewatenberg.junit5kubernetes.core.impl.GenericPodBuilder;
import com.github.jeanbaptistewatenberg.junit5kubernetes.core.wait.impl.pod.PodWaitRunningStatusStrategy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@JunitKubernetes
public class TestReusePod {

    private static final PortMapper portMapper = new PortMapper();
    private static final Set<String> startedPods = new HashSet<>();

    @KubernetesObject
    private static KubernetesGenericObject pod = new GenericPodBuilder()
            .withNewSpec()
            .addNewContainer()
            .withName("testnginx")
            .withImage("nginx")
            .addNewPort()
            .withHostPort(portMapper.computeAvailablePort("nginx-80"))
            .withContainerPort(80)
            .endPort()
            .endContainer()
            .endSpec()
            .withWaitStrategy(new PodWaitRunningStatusStrategy())
            .build();

    @ParameterizedTest
    @ValueSource(strings = { "one", "two" })
    void should_reuse_pod_for_each(String candidate) throws IOException {
        if (startedPods.size() != 0) {
            startedPods.add(pod.getObjectName());
            System.out.println(startedPods);
            assertThat(startedPods).hasSize(1);
        } else {
            startedPods.add(pod.getObjectName());
        }
        assertThat(pod.getObjectHostIp()).isNotBlank();
        assertThat(portMapper.getComputedPort("nginx-80")).isNotNull();

        URL url = new URL("http://" + pod.getObjectHostIp() + ":" + portMapper.getComputedPort("nginx-80"));
        int status = TestUtils.responseStatus(url);
        assertThat(status).isEqualTo(200);
    }
}
