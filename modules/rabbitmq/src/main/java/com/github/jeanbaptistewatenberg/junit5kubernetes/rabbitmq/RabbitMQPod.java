package com.github.jeanbaptistewatenberg.junit5kubernetes.rabbitmq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jeanbaptistewatenberg.junit5kubernetes.core.Pod;
import com.github.jeanbaptistewatenberg.junit5kubernetes.core.PortMapper;
import com.github.jeanbaptistewatenberg.junit5kubernetes.core.wait.impl.pod.PodWaitLogStrategy;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.PatchUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.lang.String.join;
import static java.util.Arrays.asList;

public class RabbitMQPod extends Pod {
    public static final String DEFAULT_IMAGE = "rabbitmq";
    public static final String DEFAULT_TAG = "3.7.25-management-alpine";

    private static final int DEFAULT_AMQP_PORT = 5672;
    private static final int DEFAULT_AMQPS_PORT = 5671;
    private static final int DEFAULT_HTTPS_PORT = 15671;
    private static final int DEFAULT_HTTP_PORT = 15672;

    private static final String NAMED_AMQP_PORT = "AMQP_PORT_5672";
    private static final String NAMED_AMQPS_PORT = "AMQPS_PORT_5671";
    private static final String NAMED_HTTPS_PORT = "HTTPS_PORT_15671";
    private static final String NAMED_HTTP_PORT = "HTTP_PORT_15672";

    public static final String JUNIT_5_KUBERNETES_RABBIT_MQ_CONTAINER = "junit5kubernetesrabbitmqcontainer";

    private static final Logger LOGGER = Logger.getLogger(RabbitMQPod.class.getName());

    private String adminUsername = "guest";
    private String adminPassword = "guest";
    private final List<List<String>> commands = new ArrayList<>();

    private final PortMapper portMapper = new PortMapper();

    public RabbitMQPod() {
        this(DEFAULT_IMAGE + ":" + DEFAULT_TAG);
    }

    public RabbitMQPod(final String dockerImageName) {
        super(new V1PodBuilder()
                .withNewSpec()
                .addNewContainer()
                .withName(JUNIT_5_KUBERNETES_RABBIT_MQ_CONTAINER)
                .withImage(dockerImageName)
                .endContainer()
                .endSpec()
                .build());
        this.waitStrategy = new PodWaitLogStrategy(".*Server startup complete.*", Duration.ofSeconds(60));
    }

    /**
     * @return The admin password for the <code>admin</code> account
     */
    public String getAdminPassword() {
        return adminPassword;
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public Integer getAmqpPort() {
        return portMapper.getComputedPort(NAMED_AMQP_PORT);
    }

    public Integer getAmqpsPort() {
        return portMapper.getComputedPort(NAMED_AMQPS_PORT);
    }

    public Integer getHttpsPort() {
        return portMapper.getComputedPort(NAMED_HTTPS_PORT);
    }

    public Integer getHttpPort() {
        return portMapper.getComputedPort(NAMED_HTTP_PORT);
    }

    /**
     * @return AMQP URL for use with AMQP clients.
     */
    public String getAmqpUrl() {
        return "amqp://" + getObjectHostIp() + ":" + getAmqpPort();
    }

    /**
     * @return AMQPS URL for use with AMQPS clients.
     */
    public String getAmqpsUrl() {
        return "amqps://" + getObjectHostIp() + ":" + getAmqpsPort();
    }

    /**
     * @return URL of the HTTP management endpoint.
     */
    public String getHttpUrl() {
        return "http://" + getObjectHostIp() + ":" + getHttpPort();
    }

    /**
     * @return URL of the HTTPS management endpoint.
     */
    public String getHttpsUrl() {
        return "https://" + getObjectHostIp() + ":" + getHttpsPort();
    }

    /**
     * Sets the password for the admin (default is <pre>guest</pre>)
     *
     * @param adminPassword The admin password.
     * @return This container.
     */
    public RabbitMQPod withAdminPassword(final String adminPassword) {
        this.adminPassword = adminPassword;
        return this;
    }

    public RabbitMQPod withPluginsEnabled(String... pluginNames) {
        List<String> command = new ArrayList<>(asList("rabbitmq-plugins", "enable"));
        command.addAll(asList(pluginNames));
        commands.add(command);
        return this;
    }

    public RabbitMQPod withBinding(String source, String destination) {
        commands.add(asList("rabbitmqadmin", "declare", "binding",
                "source=" + source,
                "destination=" + destination));
        return this;
    }

    public RabbitMQPod withBinding(String source, String destination, Map<String, Object> arguments, String routingKey, String destinationType) {
        commands.add(asList("rabbitmqadmin", "declare", "binding",
                "source=" + source,
                "destination=" + destination,
                "routing_key=" + routingKey,
                "destination_type=" + destinationType,
                "arguments=" + toJson(arguments)));
        return this;
    }

    public RabbitMQPod withParameter(String component, String name, String value) {
        commands.add(asList("rabbitmqadmin", "declare", "parameter",
                "component=" + component,
                "name=" + name,
                "value=" + value));
        return this;
    }

    public RabbitMQPod withPermission(String vhost, String user, String configure, String write, String read) {
        commands.add(asList("rabbitmqadmin", "declare", "permission",
                "vhost=" + vhost,
                "user=" + user,
                "configure=" + configure,
                "write=" + write,
                "read=" + read));
        return this;
    }

    public RabbitMQPod withUser(String name, String password) {
        commands.add(asList("rabbitmqadmin", "declare", "user",
                "name=" + name,
                "password=" + password,
                "tags="));
        return this;
    }

    public RabbitMQPod withUser(String name, String password, Set<String> tags) {
        commands.add(asList("rabbitmqadmin", "declare", "user",
                "name=" + name,
                "password=" + password,
                "tags=" + join(",", tags)));
        return this;
    }

    public RabbitMQPod withPolicy(String name, String pattern, Map<String, Object> definition) {
        commands.add(asList("rabbitmqadmin", "declare", "policy",
                "name=" + name,
                "pattern=" + pattern,
                "definition=" + toJson(definition)));
        return this;
    }

    public RabbitMQPod withPolicy(String vhost, String name, String pattern, Map<String, Object> definition) {
        commands.add(asList("rabbitmqadmin", "declare", "policy",
                "--vhost=" + vhost,
                "name=" + name,
                "pattern=" + pattern,
                "definition=" + toJson(definition)));
        return this;
    }

    public RabbitMQPod withPolicy(String name, String pattern, Map<String, Object> definition, int priority, String applyTo) {
        commands.add(asList("rabbitmqadmin", "declare", "policy",
                "name=" + name,
                "pattern=" + pattern,
                "priority=" + priority,
                "apply-to=" + applyTo,
                "definition=" + toJson(definition)));
        return this;
    }

    public RabbitMQPod withOperatorPolicy(String name, String pattern, Map<String, Object> definition) {
        commands.add(new ArrayList<>(asList("rabbitmqadmin", "declare", "operator_policy",
                "name=" + name,
                "pattern=" + pattern,
                "definition=" + toJson(definition))));
        return this;
    }

    public RabbitMQPod withOperatorPolicy(String name, String pattern, Map<String, Object> definition, int priority, String applyTo) {
        commands.add(asList("rabbitmqadmin", "declare", "operator_policy",
                "name=" + name,
                "pattern=" + pattern,
                "priority=" + priority,
                "apply-to=" + applyTo,
                "definition=" + toJson(definition)));
        return this;
    }

    public RabbitMQPod withVhost(String name) {
        commands.add(asList("rabbitmqadmin", "declare", "vhost",
                "name=" + name));
        return this;
    }

    public RabbitMQPod withVhost(String name, boolean tracing) {
        commands.add(asList("rabbitmqadmin", "declare", "vhost",
                "name=" + name,
                "tracing=" + tracing));
        return this;
    }

    public RabbitMQPod withVhostLimit(String vhost, String name, int value) {
        commands.add(asList("rabbitmqadmin", "declare", "vhost_limit",
                "vhost=" + vhost,
                "name=" + name,
                "value=" + value));
        return this;
    }

    public RabbitMQPod withQueue(String name) {
        commands.add(asList("rabbitmqadmin", "declare", "queue",
                "name=" + name));
        return this;
    }

    public RabbitMQPod withQueue(String name, boolean autoDelete, boolean durable, Map<String, Object> arguments) {
        commands.add(asList("rabbitmqadmin", "declare", "queue",
                "name=" + name,
                "auto_delete=" + autoDelete,
                "durable=" + durable,
                "arguments=" + toJson(arguments)));
        return this;
    }

    public RabbitMQPod withExchange(String name, String type) {
        commands.add(asList("rabbitmqadmin", "declare", "exchange",
                "name=" + name,
                "type=" + type));
        return this;
    }

    public RabbitMQPod withExchange(String name, String type, boolean autoDelete, boolean internal, boolean durable, Map<String, Object> arguments) {
        commands.add(asList("rabbitmqadmin", "declare", "exchange",
                "name=" + name,
                "type=" + type,
                "auto_delete=" + autoDelete,
                "internal=" + internal,
                "durable=" + durable,
                "arguments=" + toJson(arguments)));
        return this;
    }

    /**
     * Overwrites the default RabbitMQ configuration file with the supplied one.
     *
     * @param rabbitMQConf The rabbitmq.conf file to use (in sysctl format, don't forget empty line in the end of file)
     * @return This pod.
     */
    public RabbitMQPod withRabbitMQConfig(Path rabbitMQConf) {
        return withRabbitMQConfigSysctl(rabbitMQConf);
    }

    /**
     * Overwrites the default RabbitMQ configuration file with the supplied one.
     *
     * This function doesn't work with RabbitMQ &lt; 3.7.
     *
     * This function and the Sysctl format is recommended for RabbitMQ &gt;= 3.7
     *
     * @param rabbitMQConf The rabbitmq.config file to use (in sysctl format, don't forget empty line in the end of file)
     * @return This pod.
     */
    public RabbitMQPod withRabbitMQConfigSysctl(Path rabbitMQConf) {
        withEnv("RABBITMQ_CONFIG_FILE", "/etc/rabbitmq/rabbitmq-custom");
        withCopyFileToPodContainer(JUNIT_5_KUBERNETES_RABBIT_MQ_CONTAINER, rabbitMQConf, Paths.get("/etc/rabbitmq/rabbitmq-custom.conf"));
        return this;
    }

    /**
     * Overwrites the default RabbitMQ configuration file with the supplied one.
     *
     * @deprecated Deprecated with rabbitmq 3.7+, use withRabbitMQConfigSysctl instead
     * @param rabbitMQConf The rabbitmq.config file to use (in erlang format)
     * @return This pod.
     */
    @Deprecated
    public RabbitMQPod withRabbitMQConfigErlang(Path rabbitMQConf) {
        withEnv("RABBITMQ_CONFIG_FILE", "/etc/rabbitmq/rabbitmq-custom.config");
        withCopyFileToPodContainer(JUNIT_5_KUBERNETES_RABBIT_MQ_CONTAINER, rabbitMQConf, Paths.get("/etc/rabbitmq/rabbitmq-custom.config"));
        return this;
    }

    public RabbitMQPod withEnv(String key, String value) {
        V1Container rabbitMQContainer = this.podToCreate.getSpec().getContainers().get(0);
        rabbitMQContainer.addEnvItem(new V1EnvVar()
            .name(key)
            .value(value)
        );
        return this;
    }

    public enum SslVerification {
        VERIFY_NONE("verify_none"), VERIFY_PEER("verify_peer");

        SslVerification(String value) {
            this.value = value;
        }
        private final String value;

    }

    @Override
    protected void onBeforeCreateKubernetesObject() {
        super.onBeforeCreateKubernetesObject();
        if (adminPassword != null) {
            V1Container rabbitMQContainer = this.podToCreate.getSpec().getContainers().get(0);
            rabbitMQContainer
                .addPortsItem(new V1ContainerPort()
                        .hostPort(portMapper.computeAvailablePort(NAMED_AMQP_PORT))
                        .containerPort(DEFAULT_AMQP_PORT)
                ).addPortsItem(new V1ContainerPort()
                        .hostPort(portMapper.computeAvailablePort(NAMED_AMQPS_PORT))
                        .containerPort(DEFAULT_AMQPS_PORT)
                ).addPortsItem(new V1ContainerPort()
                        .hostPort(portMapper.computeAvailablePort(NAMED_HTTPS_PORT))
                        .containerPort(DEFAULT_HTTPS_PORT)
                ).addPortsItem(new V1ContainerPort()
                        .hostPort(portMapper.computeAvailablePort(NAMED_HTTP_PORT))
                        .containerPort(DEFAULT_HTTP_PORT)
                )
                .addEnvItem(new V1EnvVar()
                    .name("RABBITMQ_DEFAULT_PASS")
                    .value(adminPassword)
                );
        }
    }

    @Override
    protected void onKubernetesObjectReady() {
        super.onKubernetesObjectReady();
        commands.forEach(command -> {
            try(ExecResult execResult = execInPod(command.toArray(new String[0]))) {
                if (execResult.getExitCode() != 0) {
                    LOGGER.severe(String.format("Could not execute command %s: %s", command, execResult.consumeStandardErrorAsString(StandardCharsets.UTF_8)));
                }
            }
        });
    }

    private String toJson(Map<String, Object> arguments) {
        try {
            return new ObjectMapper().writeValueAsString(arguments);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert arguments into json: " + e.getMessage(), e);
        }
    }
}
