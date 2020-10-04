package com.github.jeanbaptistewatenberg.junit5kubernetes.elasticsearch;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ElasticSearchPodTest {

    @Test
    void shouldCreateElasticSearchPod() {
        try(ElasticSearchPod pod = new ElasticSearchPod()){
            pod.create();
            assertThat(pod.getHttpUrl()).isEqualTo(
                    String.format("http://%s:%d", pod.getObjectHostIp(), pod.getHttpPort()));


        }
    }
}
