package com.github.jeanbaptistewatenberg;

import com.github.jeanbaptistewatenberg.impl.GenericPodBuilder;
import com.github.jeanbaptistewatenberg.wait.impl.pod.PodWaitLogStrategy;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@JunitKubernetes
public class TestWaitForLog {

    public static final String POSTGRESQL_PORT = "postgres-5432";
    public static final String DB_NAME = "test";
    public static final String USERNAME = "test";
    public static final String PASSWORD = "test";
    private final PortMapper portMapper = new PortMapper();

    @KubernetesObject
    private KubernetesGenericObject pod = new GenericPodBuilder()
            .withNewSpec()
            .addNewContainer()
            .withName("testpostgres")
            .withImage("postgres:9.6.17")
            .addNewPort()
            .withHostPort(portMapper.computeAvailablePort(POSTGRESQL_PORT))
            .withContainerPort(5432)
            .endPort()
            .addNewEnv()
            .withName("POSTGRES_USER")
            .withValue(USERNAME)
            .endEnv()
            .addNewEnv()
            .withName("POSTGRES_PASSWORD")
            .withValue(PASSWORD)
            .endEnv()
            .addNewEnv()
            .withName("POSTGRES_DB")
            .withValue(DB_NAME)
            .endEnv()
            .endContainer()
            .endSpec()
            .withWaitStrategy(new PodWaitLogStrategy(".*database system is ready to accept connections.*", 2, Duration.ofSeconds(60)))
            .build();

    @Test
    void should_start_a_postgres_pod() throws SQLException {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(String.format("jdbc:postgresql://%s:%d/%s?loggerLevel=OFF", pod.getObjectHostIp(), portMapper.getComputedPort(POSTGRESQL_PORT), DB_NAME));
        hikariConfig.setUsername(USERNAME);
        hikariConfig.setPassword(PASSWORD);

        try (HikariDataSource ds = new HikariDataSource(hikariConfig)) {
            Statement statement = ds.getConnection().createStatement();
            statement.execute("SELECT 1");
            ResultSet resultSet = statement.getResultSet();
            resultSet.next();

            int resultSetInt = resultSet.getInt(1);
            assertThat(resultSetInt).isEqualTo(1);
        }
    }
}
