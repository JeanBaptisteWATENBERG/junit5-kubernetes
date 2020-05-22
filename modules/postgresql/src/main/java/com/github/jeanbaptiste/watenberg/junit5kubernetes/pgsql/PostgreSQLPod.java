package com.github.jeanbaptiste.watenberg.junit5kubernetes.pgsql;

import com.github.jeanbaptistewatenberg.junit5kubernetes.core.PortMapper;
import com.github.jeanbaptistewatenberg.junit5kubernetes.core.wait.impl.pod.PodWaitLogStrategy;
import com.github.jeanbaptistewatenberg.junit5kubernetes.jdbc.JdbcDatabasePod;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1EnvVar;

import java.time.Duration;

public class PostgreSQLPod<SELF extends PostgreSQLPod<SELF>> extends JdbcDatabasePod<SELF> {
    public static final String DEFAULT_IMAGE = "postgres";
    public static final String DEFAULT_TAG = "9-alpine";

    private static final String FSYNC_OFF_OPTION = "fsync=off";
    private static final String QUERY_PARAM_SEPARATOR = "&";
    private static final String DEFAULT_USER = "test";
    private static final String DEFAULT_PASSWORD = "test";
    private static final String DEFAULT_DATABASE_NAME = "test";
    private static final String POSTGRESQL_NAMED_PORT = "postgres-5432";
    private static final int POSTGRESQL_PORT = 5432;

    private String databaseName = DEFAULT_DATABASE_NAME;
    private String username = DEFAULT_USER;
    private String password = DEFAULT_PASSWORD;

    private final PortMapper portMapper = new PortMapper();

    public PostgreSQLPod() {
        this(DEFAULT_IMAGE + ":" + DEFAULT_TAG);
    }

    public PostgreSQLPod(String dockerImageName) {
        super(dockerImageName);
        this.waitStrategy = new PodWaitLogStrategy(".*database system is ready to accept connections.*", 2, Duration.ofSeconds(60));
    }

    @Override
    public String getDriverClassName() {
        return "org.postgresql.Driver";
    }

    @Override
    public String getJdbcUrl() {
        // Disable Postgres driver use of java.util.logging to reduce noise at startup time
        return String.format("jdbc:postgresql://%s:%d/%s?loggerLevel=OFF", getObjectHostIp(), portMapper.getComputedPort(POSTGRESQL_NAMED_PORT), databaseName);
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public SELF withDatabaseName(final String databaseName) {
        this.databaseName = databaseName;
        return (SELF) this;
    }

    @Override
    public SELF withUsername(final String username) {
        this.username = username;
        return (SELF) this;
    }

    @Override
    public SELF withPassword(final String password) {
        this.password = password;
        return (SELF) this;
    }

    @Override
    protected String constructUrlForConnection(String queryString) {
        String baseUrl = getJdbcUrl();

        if ("".equals(queryString)) {
            return baseUrl;
        }

        if (!queryString.startsWith("?")) {
            throw new IllegalArgumentException("The '?' character must be included");
        }

        return baseUrl.contains("?")
                ? baseUrl + QUERY_PARAM_SEPARATOR + queryString.substring(1)
                : baseUrl + queryString;
    }

    @Override
    protected void onBeforeCreateKubernetesObject() {
        super.onBeforeCreateKubernetesObject();
        V1Container postgresContainer = this.podToCreate.getSpec().getContainers().get(0);
        postgresContainer
            .addArgsItem("-c").addArgsItem(FSYNC_OFF_OPTION)
            .addPortsItem(new V1ContainerPort()
                    .hostPort(portMapper.computeAvailablePort(POSTGRESQL_NAMED_PORT))
                    .containerPort(POSTGRESQL_PORT)
            )
            .addEnvItem(new V1EnvVar()
                    .name("POSTGRES_USER")
                    .value(username)
            )
            .addEnvItem(new V1EnvVar()
                    .name("POSTGRES_PASSWORD")
                    .value(password)
            )
            .addEnvItem(new V1EnvVar()
                    .name("POSTGRES_DB")
                    .value(databaseName)
            )
        ;
    }
}
