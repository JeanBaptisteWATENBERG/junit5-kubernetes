package com.github.jeanbaptiste.watenberg.junit5kubernetes.pgsql;

import com.github.jeanbaptistewatenberg.junit5kubernetes.core.JunitKubernetes;
import com.github.jeanbaptistewatenberg.junit5kubernetes.core.KubernetesObject;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@JunitKubernetes
class PostgreSQLPodTest {
    private static final String DB_NAME = "foo";
    private static final String USER = "bar";
    private static final String PWD = "baz";

    @KubernetesObject
    private PostgreSQLPod pod = new PostgreSQLPod()
            .withUsername(USER)
            .withPassword(PWD)
            .withDatabaseName(DB_NAME);

    @Test
    void should_start_a_postgres_pod() throws SQLException {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(pod.getJdbcUrl());
        hikariConfig.setUsername(pod.getUsername());
        hikariConfig.setPassword(pod.getPassword());

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