package com.github.jeanbaptistewatenberg.junit5kubernetes.jdbc;

import org.testcontainers.delegate.AbstractDatabaseDelegate;
import org.testcontainers.ext.ScriptUtils;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JdbcDatabaseDelegate extends AbstractDatabaseDelegate<Statement> {
    private final JdbcDatabasePod pod;
    private final String queryString;
    private static final Logger LOGGER = Logger.getLogger(JdbcDatabaseDelegate.class.getName());

    public JdbcDatabaseDelegate(JdbcDatabasePod pod, String queryString) {
        this.pod = pod;
        this.queryString = queryString;
    }


    @Override
    protected Statement createNewConnection() {
        try {
            return pod.createConnection(queryString).createStatement();
        } catch (SQLException e) {
            throw new RuntimeException("Could not obtain JDBC connection", e);
        }
    }


    @Override
    public void execute(String statement, String scriptPath, int lineNumber, boolean continueOnError, boolean ignoreFailedDrops) {
        try {
            boolean rowsAffected = getConnection().execute(statement);
        } catch (SQLException ex) {
            boolean dropStatement = statement.trim().toLowerCase().startsWith("drop");
            if (continueOnError || (dropStatement && ignoreFailedDrops)) {
                LOGGER.log(Level.FINE, String.format("Failed to execute SQL script statement at line %s of resource %s: %s", lineNumber, scriptPath, statement), ex);
            } else {
                throw new ScriptUtils.ScriptStatementFailedException(statement, lineNumber, scriptPath, ex);
            }
        }
    }

    @Override
    protected void closeConnectionQuietly(Statement statement) {
        try {
            statement.close();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Could not close JDBC connection", e);
        }
    }
}
