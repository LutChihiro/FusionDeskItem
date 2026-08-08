package com.xfusion.fusiondesk.repository;

import com.xfusion.fusiondesk.exception.DatabaseException;
import com.xfusion.fusiondesk.model.LlmCircuitState;
import com.xfusion.fusiondesk.model.LlmProviderEvent;
import com.xfusion.fusiondesk.model.LlmProviderState;
import com.xfusion.fusiondesk.service.LlmFailoverPolicy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LlmProviderStateRepository {
    public static final String SOURCE_ANALYZE = "ANALYZE";
    public static final String SOURCE_MONITOR = "MONITOR";

    private final DatabaseManager database;

    public LlmProviderStateRepository(DatabaseManager database) {
        this.database = database;
    }

    public void ensureDefault(Connection connection) throws SQLException {
        if (find(connection).isEmpty()) {
            insertDefault(connection);
        }
    }

    public LlmProviderState get() {
        try (Connection connection = database.openConnection()) {
            return find(connection).orElse(defaultState());
        } catch (SQLException error) {
            throw new DatabaseException("Failed to read LLM provider state", error);
        }
    }

    public boolean claimPrimaryProbe(Instant now, LlmFailoverPolicy policy) {
        return claimPrimaryProbe(now, policy, SOURCE_ANALYZE);
    }

    public boolean claimPrimaryProbe(Instant now, LlmFailoverPolicy policy, String source) {
        return database.inTransaction(connection -> {
            LlmProviderState state = find(connection).orElse(defaultState());
            if (state.state() == LlmCircuitState.CLOSED) {
                return true;
            }
            if (state.nextRetryAt() != null && state.nextRetryAt().isAfter(now)) {
                return false;
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE llm_provider_state SET state='HALF_OPEN',next_retry_at=?,version=version+1 "
                            + "WHERE id=1 AND version=?")) {
                database.bindInstant(statement, 1, now.plus(policy.retryInterval()));
                statement.setLong(2, state.version());
                boolean claimed = statement.executeUpdate() == 1;
                if (claimed) {
                    insertEvent(connection, "PROBE_STARTED", source, state.state(),
                            LlmCircuitState.HALF_OPEN, state.currentModel(), state.currentModel(), null, now);
                }
                return claimed;
            }
        });
    }

    public void recordFailure(String model, String error, Instant now, LlmFailoverPolicy policy) {
        recordFailure(model, error, now, policy, SOURCE_ANALYZE);
    }

    public void recordFailure(String model, String error, Instant now,
                              LlmFailoverPolicy policy, String source) {
        database.inTransaction(connection -> {
            LlmProviderState previous = existingState(connection);
            int failures = previous.consecutiveFailures() + 1;
            LlmCircuitState next = failures >= policy.failureThreshold()
                    ? LlmCircuitState.OPEN : LlmCircuitState.CLOSED;
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE llm_provider_state SET state=?,consecutive_failures=?,consecutive_successes=0,"
                            + "last_failure_at=?,next_retry_at=?,current_model=?,last_error=?,version=version+1 WHERE id=1")) {
                statement.setString(1, next.name());
                statement.setInt(2, failures);
                database.bindInstant(statement, 3, now);
                if (next == LlmCircuitState.OPEN) {
                    database.bindInstant(statement, 4, now.plus(policy.retryInterval()));
                } else {
                    statement.setNull(4, Types.TIMESTAMP);
                }
                statement.setString(5, model);
                statement.setString(6, truncate(error));
                statement.executeUpdate();
            }
            String eventType = SOURCE_MONITOR.equals(source) ? "PROBE_FAILED" : "PRIMARY_FAILURE";
            insertEvent(connection, eventType, source, previous.state(), next,
                    model, previous.currentModel(), truncate(error), now);
            return null;
        });
    }

    public void recordSuccess(String model, Instant now, LlmFailoverPolicy policy) {
        recordSuccess(model, now, policy, SOURCE_ANALYZE);
    }

    public void recordSuccess(String model, Instant now,
                              LlmFailoverPolicy policy, String source) {
        database.inTransaction(connection -> {
            LlmProviderState previous = existingState(connection);
            int successes = previous.consecutiveSuccesses() + 1;
            boolean recovered = successes >= policy.successThreshold();
            LlmCircuitState next = recovered ? LlmCircuitState.CLOSED : LlmCircuitState.HALF_OPEN;
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE llm_provider_state SET state=?,consecutive_failures=0,consecutive_successes=?,"
                            + "last_success_at=?,next_retry_at=?,current_model=?,last_error=NULL,version=version+1 WHERE id=1")) {
                statement.setString(1, next.name());
                statement.setInt(2, recovered ? 0 : successes);
                database.bindInstant(statement, 3, now);
                if (recovered) {
                    statement.setNull(4, Types.TIMESTAMP);
                } else {
                    database.bindInstant(statement, 4, now.plus(policy.retryInterval()));
                }
                statement.setString(5, model);
                statement.executeUpdate();
            }
            String eventType = SOURCE_MONITOR.equals(source) ? "PROBE_SUCCEEDED" : "PRIMARY_SUCCESS";
            insertEvent(connection, eventType, source, previous.state(), next,
                    model, model, null, now);
            return null;
        });
    }

    public void recordFallbackUse(String model) {
        recordFallbackUse(model, SOURCE_ANALYZE);
    }

    public void recordFallbackUse(String model, String source) {
        database.inTransaction(connection -> {
            LlmProviderState previous = existingState(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE llm_provider_state SET current_model=?,version=version+1 WHERE id=1")) {
                statement.setString(1, model);
                statement.executeUpdate();
            }
            insertEvent(connection, "FALLBACK_USED", source, previous.state(), previous.state(),
                    null, model, null, Instant.now());
            return null;
        });
    }

    public List<LlmProviderEvent> findEvents() {
        String sql = "SELECT id,event_type,source,from_state,to_state,primary_model,active_model,"
                + "error_message,created_at FROM llm_provider_events ORDER BY id";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            List<LlmProviderEvent> events = new ArrayList<>();
            while (results.next()) {
                events.add(new LlmProviderEvent(
                        results.getLong("id"), results.getString("event_type"), results.getString("source"),
                        nullableState(results.getString("from_state")), nullableState(results.getString("to_state")),
                        results.getString("primary_model"), results.getString("active_model"),
                        results.getString("error_message"), database.readInstant(results, "created_at")));
            }
            return events;
        } catch (SQLException error) {
            throw new DatabaseException("Failed to read LLM provider events", error);
        }
    }

    private LlmProviderState existingState(Connection connection) throws SQLException {
        Optional<LlmProviderState> state = find(connection);
        if (state.isEmpty()) {
            insertDefault(connection);
            return find(connection).orElseThrow();
        }
        return state.get();
    }

    private Optional<LlmProviderState> find(Connection connection) throws SQLException {
        String sql = "SELECT state,consecutive_failures,consecutive_successes,last_failure_at,next_retry_at,"
                + "last_success_at,current_model,last_error,version FROM llm_provider_state WHERE id=1";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            if (!results.next()) {
                return Optional.empty();
            }
            return Optional.of(new LlmProviderState(
                    LlmCircuitState.valueOf(results.getString("state")),
                    results.getInt("consecutive_failures"), results.getInt("consecutive_successes"),
                    database.readInstant(results, "last_failure_at"),
                    database.readInstant(results, "next_retry_at"),
                    database.readInstant(results, "last_success_at"),
                    results.getString("current_model"), results.getString("last_error"), results.getLong("version")));
        }
    }

    private void insertDefault(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO llm_provider_state(id,state,consecutive_failures,consecutive_successes,version) "
                        + "VALUES(1,'CLOSED',0,0,0)")) {
            statement.executeUpdate();
        }
    }

    private void insertEvent(Connection connection, String eventType, String source,
                             LlmCircuitState from, LlmCircuitState to, String primaryModel,
                             String activeModel, String error, Instant createdAt) throws SQLException {
        String sql = "INSERT INTO llm_provider_events(event_type,source,from_state,to_state,primary_model,"
                + "active_model,error_message,created_at) VALUES(?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, eventType);
            statement.setString(2, source);
            statement.setString(3, from == null ? null : from.name());
            statement.setString(4, to == null ? null : to.name());
            statement.setString(5, primaryModel);
            statement.setString(6, activeModel);
            statement.setString(7, truncateNullable(error));
            database.bindInstant(statement, 8, createdAt);
            statement.executeUpdate();
        }
    }

    private LlmCircuitState nullableState(String value) {
        return value == null ? null : LlmCircuitState.valueOf(value);
    }

    private LlmProviderState defaultState() {
        return new LlmProviderState(LlmCircuitState.CLOSED, 0, 0,
                null, null, null, null, null, 0);
    }

    private String truncate(String value) {
        return value == null ? "LLM failure" : value.substring(0, Math.min(900, value.length()));
    }

    private String truncateNullable(String value) {
        return value == null ? null : truncate(value);
    }
}
