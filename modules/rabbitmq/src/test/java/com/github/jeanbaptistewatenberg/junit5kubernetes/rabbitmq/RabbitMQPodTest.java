package com.github.jeanbaptistewatenberg.junit5kubernetes.rabbitmq;

import com.github.jeanbaptistewatenberg.junit5kubernetes.core.Pod;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import static com.github.jeanbaptistewatenberg.junit5kubernetes.rabbitmq.RabbitMQPod.JUNIT_5_KUBERNETES_RABBIT_MQ_CONTAINER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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

    @Test
    public void shouldCreateRabbitMQPodWithQueues() {
        try (RabbitMQPod pod = new RabbitMQPod()) {

            pod.withQueue("queue-one")
                    .withQueue("queue-two", false, true, ImmutableMap.of("x-message-ttl", 1000));

            pod.create();

            try (Pod.ExecResult execResult = pod.execInPod("rabbitmqctl", "list_queues", "name", "arguments")) {
                assertThat(execResult.consumeStandardOutAsString(StandardCharsets.UTF_8))
                    .containsPattern("queue-one");
            }

            try (Pod.ExecResult execResult = pod.execInPod("rabbitmqctl", "list_queues", "name", "arguments")) {
                assertThat(execResult.consumeStandardOutAsString(StandardCharsets.UTF_8))
                        .containsPattern("queue-two\\s.*x-message-ttl");
            }
        }
    }

    @Test
    public void shouldStartTheWholeEnchilada() {
        try (RabbitMQPod pod = new RabbitMQPod()) {
            pod
                    .withVhost("vhost1")
                    .withVhostLimit("vhost1", "max-connections", 1)
                    .withVhost("vhost2", true)
                    .withExchange("direct-exchange", "direct")
                    .withExchange("topic-exchange", "topic")
                    .withQueue("queue1")
                    .withQueue("queue2", true, false, ImmutableMap.of("x-message-ttl", 1000))
                    .withBinding("direct-exchange", "queue1")
                    .withUser("user1", "password1")
                    .withUser("user2", "password2", ImmutableSet.of("administrator"))
                    .withPermission("vhost1", "user1", ".*", ".*", ".*")
                    .withPolicy("max length policy", "^dog", ImmutableMap.of("max-length", 1), 1, "queues")
                    .withPolicy("alternate exchange policy", "^direct-exchange", ImmutableMap.of("alternate-exchange", "amq.direct"))
                    .withPolicy("vhost2", "ha-all", ".*", ImmutableMap.of("ha-mode", "all", "ha-sync-mode", "automatic"))
                    .withOperatorPolicy("operator policy 1", "^queue1", ImmutableMap.of("message-ttl", 1000), 1, "queues")
                    .withPluginsEnabled("rabbitmq_shovel", "rabbitmq_random_exchange");

            pod.create();

            try (Pod.ExecResult execResult = pod.execInPod("rabbitmqadmin", "list", "queues")) {
                assertThat(execResult
                        .consumeStandardOutAsString(StandardCharsets.UTF_8))
                        .contains("queue1", "queue2");
            }

            try(Pod.ExecResult execResult = pod.execInPod("rabbitmqadmin", "list", "exchanges")) {
                assertThat(execResult.consumeStandardOutAsString(StandardCharsets.UTF_8))
                    .contains("direct-exchange", "topic-exchange");
            }


            try(Pod.ExecResult execResult = pod.execInPod("rabbitmqadmin", "list", "bindings")) {
                assertThat(execResult.consumeStandardOutAsString(StandardCharsets.UTF_8))
                    .contains("direct-exchange");
            }


            try(Pod.ExecResult execResult = pod.execInPod("rabbitmqadmin", "list", "users")) {
                assertThat(execResult.consumeStandardOutAsString(StandardCharsets.UTF_8))
                    .contains("user1", "user2");
            }


            try(Pod.ExecResult execResult = pod.execInPod("rabbitmqadmin", "list", "policies")) {
                assertThat(execResult.consumeStandardOutAsString(StandardCharsets.UTF_8))
                    .contains("max length policy", "alternate exchange policy");
            }


            try(Pod.ExecResult execResult = pod.execInPod("rabbitmqadmin", "list", "policies", "--vhost=vhost2")) {
                assertThat(execResult.consumeStandardOutAsString(StandardCharsets.UTF_8))
                    .contains("ha-all", "ha-sync-mode");
            }


            try(Pod.ExecResult execResult = pod.execInPod("rabbitmqadmin", "list", "operator_policies")) {
                assertThat(execResult.consumeStandardOutAsString(StandardCharsets.UTF_8))
                    .contains("operator policy 1");
            }


            try(Pod.ExecResult execResult = pod.execInPod("rabbitmq-plugins", "is_enabled", "rabbitmq_shovel", "--quiet")) {
                assertThat(execResult.consumeStandardOutAsString(StandardCharsets.UTF_8))
                    .contains("rabbitmq_shovel is enabled");
            }


            try(Pod.ExecResult execResult = pod.execInPod("rabbitmq-plugins", "is_enabled", "rabbitmq_random_exchange", "--quiet")) {
                assertThat(execResult.consumeStandardOutAsString(StandardCharsets.UTF_8))
                    .contains("rabbitmq_random_exchange is enabled");
            }
        }
    }

    @Test
    public void shouldThrowExceptionForDodgyJson() {
        try (RabbitMQPod pod = new RabbitMQPod()) {

            assertThatCode(() ->
                    pod.withQueue(
                            "queue2",
                            true,
                            false,
                            ImmutableMap.of("x-message-ttl", pod))
            ).hasMessageStartingWith("Failed to convert arguments into json");

        }
    }


    @Test
    public void shouldMountConfigurationFile() throws URISyntaxException {
        try (RabbitMQPod pod = new RabbitMQPod()) {

            pod.withRabbitMQConfig(new File(RabbitMQPodTest.class.getResource("/rabbitmq-custom.conf").toURI()).toPath());
            pod.create();

            String logs = pod.getLogs(JUNIT_5_KUBERNETES_RABBIT_MQ_CONTAINER);
            assertThat(logs).contains("config file(s) : /etc/rabbitmq/rabbitmq-custom.conf");
            assertThat(logs).doesNotContain(" (not found)");
        }
    }


    @Test
    public void shouldMountConfigurationFileErlang() throws URISyntaxException {
        try (RabbitMQPod pod = new RabbitMQPod()) {

            pod.withRabbitMQConfigErlang(new File(RabbitMQPodTest.class.getResource("/rabbitmq-custom.conf").toURI()).toPath());
            pod.create();

            String logs = pod.getLogs(JUNIT_5_KUBERNETES_RABBIT_MQ_CONTAINER);
            assertThat(logs).contains("config file(s) : /etc/rabbitmq/rabbitmq-custom.conf");
            assertThat(logs).doesNotContain(" (not found)");
        }
    }


    @Test
    public void shouldMountConfigurationFileSysctl() throws URISyntaxException {
        try (RabbitMQPod pod = new RabbitMQPod()) {

            pod.withRabbitMQConfigSysctl(new File(RabbitMQPodTest.class.getResource("/rabbitmq-custom.conf").toURI()).toPath());
            pod.create();

            String logs = pod.getLogs(JUNIT_5_KUBERNETES_RABBIT_MQ_CONTAINER);
            assertThat(logs).contains("config file(s) : /etc/rabbitmq/rabbitmq-custom.conf");
            assertThat(logs).doesNotContain(" (not found)");
        }
    }
}