package com.github.jeanbaptistewatenberg.junit5kubernetes.jdbc;

import com.github.jeanbaptistewatenberg.junit5kubernetes.core.Pod;
import com.github.jeanbaptistewatenberg.junit5kubernetes.core.impl.GenericPodBuilder;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodBuilder;
import org.testcontainers.delegate.DatabaseDelegate;
import org.testcontainers.ext.ScriptUtils;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Properties;
import java.util.logging.Logger;

public abstract class JdbcDatabasePod<SELF extends JdbcDatabasePod<SELF>> extends Pod {
    private static final Object DRIVER_LOAD_MUTEX = new Object();
    protected static final String JUNIT_5_KUBERNETES_DB_CONTAINER = "junit5kubernetesdbcontainer";
    private String initScriptPath;
    private Driver driver;
    private static final Logger LOGGER = Logger.getLogger(JdbcDatabasePod.class.getName());
    private Duration connectTimeout = Duration.ofSeconds(120);

    protected JdbcDatabasePod(final String dockerImageName) {
        super(new V1PodBuilder()
                .withNewSpec()
                .addNewContainer()
                .withName(JUNIT_5_KUBERNETES_DB_CONTAINER)
                .withImage(dockerImageName)
                .endContainer()
                .endSpec()
        .build());
    }

    public abstract String getDriverClassName();
    public abstract String getJdbcUrl();
    public abstract String getUsername();
    public abstract String getPassword();

    public String getDatabaseName() {
        throw new UnsupportedOperationException();
    }

    public SELF withUsername(String username) {
        throw new UnsupportedOperationException();
    }

    public SELF withPassword(String password) {
        throw new UnsupportedOperationException();
    }

    public SELF withDatabaseName(String dbName) {
        throw new UnsupportedOperationException();
    }

    public SELF withInitScript(String initScriptPath) {
        this.initScriptPath = initScriptPath;
        return (SELF) this;
    }

    /**
     * Set time to allow for the database to start and establish an initial connection.
     *
     * @param connectTimeout time to allow for the database to start and establish an initial connection
     * @return self
     */
    public SELF withConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
        return (SELF) this;
    }

    /**
     * Template method for constructing the JDBC URL to be used for creating {@link Connection}s.
     * This should be overridden if the JDBC URL and query string concatenation or URL string
     * construction needs to be different to normal.
     *
     * @param queryString query string parameters that should be appended to the JDBC connection URL.
     *                    The '?' character must be included
     * @return a full JDBC URL including queryString
     */
    protected String constructUrlForConnection(String queryString) {
        return getJdbcUrl() + queryString;
    }

    @Override
    protected void onKubernetesObjectReady() {
        runInitScriptIfRequired();
    }

    protected void runInitScriptIfRequired() {
        if (initScriptPath != null) {
            ScriptUtils.runInitScript(getDatabaseDelegate(), initScriptPath);
        }
    }

    protected DatabaseDelegate getDatabaseDelegate() {
        return new JdbcDatabaseDelegate(this, "");
    }

    public Connection createConnection(String queryString) throws SQLException, NoDriverFoundException {
        final Properties info = new Properties();
        info.put("user", this.getUsername());
        info.put("password", this.getPassword());
        final String url = constructUrlForConnection(queryString);

        final Driver jdbcDriverInstance = getJdbcDriverInstance();

        SQLException lastException = null;
        try {
            LocalDateTime start = LocalDateTime.now();
            // give up if we hit the time limit
            while (LocalDateTime.now().isBefore(start.plus(connectTimeout))) {
                try {
                    LOGGER.fine(String.format("Trying to create JDBC connection using %s to %s with properties: %s", driver.getClass().getName(), url, info));

                    return jdbcDriverInstance.connect(url, info);
                } catch (SQLException e) {
                    lastException = e;
                    Thread.sleep(100L);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        throw new SQLException("Could not create new connection", lastException);
    }

    public Driver getJdbcDriverInstance() throws NoDriverFoundException {
        synchronized (DRIVER_LOAD_MUTEX) {
            if (driver == null) {
                try {
                    driver = (Driver) Class.forName(this.getDriverClassName()).newInstance();
                } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
                    throw new NoDriverFoundException("Could not get Driver", e);
                }
            }
        }

        return driver;
    }

    public static class NoDriverFoundException extends RuntimeException {
        public NoDriverFoundException(String message, Throwable e) {
            super(message, e);
        }
    }
}
