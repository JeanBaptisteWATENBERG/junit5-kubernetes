package com.github.jeanbaptistewatenberg.junit5kubernetes.core;

import com.github.jeanbaptistewatenberg.junit5kubernetes.core.impl.GenericPodBuilder;
import com.github.jeanbaptistewatenberg.junit5kubernetes.core.wait.impl.pod.PodWaitReadyStrategy;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;

import static com.github.jeanbaptistewatenberg.junit5kubernetes.core.TestUtils.responseStatus;
import static org.assertj.core.api.Assertions.assertThat;

@JunitKubernetes
public class TestWaitForReady {

    private final PortMapper portMapper = new PortMapper();

    @KubernetesObject
    private KubernetesGenericObject<Pod> pod = new GenericPodBuilder()
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
            .withWaitStrategy(new PodWaitReadyStrategy())
            .build();

    @Test
    void should_start_a_nginx_pod() throws IOException {
        assertThat(pod.getObjectHostIp()).isNotBlank();
        assertThat(portMapper.getComputedPort("nginx-80")).isNotNull();

        URL url = new URL("http://" + pod.getObjectHostIp() + ":" + portMapper.getComputedPort("nginx-80"));
        int status = responseStatus(url);
        assertThat(status).isEqualTo(200);
    }
}
