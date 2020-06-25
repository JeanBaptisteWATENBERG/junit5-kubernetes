package com.github.jeanbaptistewatenberg.junit5kubernetes.rabbitmq;

import com.github.jeanbaptistewatenberg.junit5kubernetes.core.Pod;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitMQPodTest {

    @Test
    public void shouldCreateRabbitMQPod() {
        try (RabbitMQPod pod = new RabbitMQPod()) {

            assertThat(pod.getAdminPassword()).isEqualTo("guest");
            assertThat(pod.getAdminUsername()).isEqualTo("guest");

            pod.create();

            assertThat(pod.getAmqpsUrl()).isEqualTo(
                    String.format("amqps://%s:%d", pod.getObjectHostIp(), pod.getAmqpsPort()));
            assertThat(pod.getAmqpUrl()).isEqualTo(
                    String.format("amqp://%s:%d", pod.getObjectHostIp(), pod.getAmqpPort()));
            assertThat(pod.getHttpsUrl()).isEqualTo(
                    String.format("https://%s:%d", pod.getObjectHostIp(), pod.getHttpsPort()));
            assertThat(pod.getHttpUrl()).isEqualTo(
                    String.format("http://%s:%d", pod.getObjectHostIp(), pod.getHttpPort()));
        }
    }

    @Test
    public void shouldCreateRabbitMQPodWithExchange() {
        try (RabbitMQPod pod = new RabbitMQPod()) {
            pod.withExchange("test-exchange", "direct");

            pod.create();

            try (Pod.ExecResult execResult = pod
                    .execInPod("rabbitmqctl", "list_exchanges")) {
                assertThat(
                        execResult
                                .consumeStandardOutAsString(StandardCharsets.UTF_8)
                ).containsPattern("test-exchange\\s+direct");
            }
        }
    }
}