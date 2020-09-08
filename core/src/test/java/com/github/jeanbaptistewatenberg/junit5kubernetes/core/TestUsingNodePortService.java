package com.github.jeanbaptistewatenberg.junit5kubernetes.core;

import com.github.jeanbaptistewatenberg.junit5kubernetes.core.impl.GenericPodBuilder;
import com.github.jeanbaptistewatenberg.junit5kubernetes.core.wait.impl.pod.PodWaitRunningStatusStrategy;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

public class TestUsingNodePortService {

    @Test
    public void should_use_service() throws IOException {
        System.setProperty("junitKubernetesUsePortService", "true");
        try (Pod pod = new GenericPodBuilder()
                .withNewSpec()
                .addNewContainer()
                .withName("testnginx")
                .withImage("nginx")
                .addNewPort()
                .withContainerPort(80)
                .endPort()
                .endContainer()
                .endSpec()
                .withWaitStrategy(new PodWaitRunningStatusStrategy())
                .build()) {
            pod.create();

            assertThat(pod.getObjectHostIp()).isNotBlank();
            assertThat(pod.getMappedPorts().get(80)).isNotNull();

            URL url = new URL("http://" + pod.getObjectHostIp() + ":" + pod.getMappedPorts().get(80));
            int status = TestUtils.responseStatus(url);
            assertThat(status).isEqualTo(200);
        }
        System.setProperty("junitKubernetesUsePortService", "false");
    }
}
