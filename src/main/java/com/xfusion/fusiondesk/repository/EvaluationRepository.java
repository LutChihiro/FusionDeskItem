package com.xfusion.fusiondesk.repository;

import com.xfusion.fusiondesk.evaluation.*;
import com.xfusion.fusiondesk.exception.DatabaseException;
import com.xfusion.fusiondesk.model.TicketCategory;
import com.xfusion.fusiondesk.model.TicketPriority;

import java.sql.*;
import java.time.Instant;
import java.util.*;

public class EvaluationRepository {
    private final DatabaseManager database;
    public EvaluationRepository(DatabaseManager database) { this.database = database; }

    /** Stores the run header and every case result atomically. Metrics are persisted as 0..1 fractions. */
    public long saveEvaluationRun(EvaluationReport report) {
        Objects.requireNonNull(report, "report");
        return database.inTransaction(connection -> {
            long runId = insertRun(connection, report);
            for (EvaluationResult result : report.results()) insertCase(connection, runId, result);
            return runId;
        });
    }

    public Optional<EvaluationRunSummary> findRunById(long id) {
        String sql = "SELECT * FROM evaluation_runs WHERE id=?";
        try (Connection c=database.openConnection(); PreparedStatement ps=c.prepareStatement(sql)) {
            ps.setLong(1,id); try(ResultSet rs=ps.executeQuery()){return rs.next()?Optional.of(mapRun(rs)):Optional.empty();}
        } catch(SQLException e){throw new DatabaseException("Failed to query evaluation run",e);}
    }

    public List<EvaluationRunSummary> findLatestRuns(int limit) {
        if(limit<=0) return List.of();
        try(Connection c=database.openConnection();PreparedStatement ps=c.prepareStatement("SELECT * FROM evaluation_runs ORDER BY id DESC LIMIT ?")){
            ps.setInt(1,limit);List<EvaluationRunSummary> out=new ArrayList<>();try(ResultSet rs=ps.executeQuery()){while(rs.next())out.add(mapRun(rs));}return out;
        }catch(SQLException e){throw new DatabaseException("Failed to list evaluation runs",e);}
    }

    public List<EvaluationCaseRecord> findCaseResults(long runId) {
        try(Connection c=database.openConnection();PreparedStatement ps=c.prepareStatement("SELECT * FROM evaluation_case_results WHERE run_id=? ORDER BY id")){
            ps.setLong(1,runId);List<EvaluationCaseRecord> out=new ArrayList<>();try(ResultSet rs=ps.executeQuery()){while(rs.next())out.add(mapCase(rs));}return out;
        }catch(SQLException e){throw new DatabaseException("Failed to list evaluation case results",e);}
    }

    private long insertRun(Connection c,EvaluationReport report)throws SQLException{
        EvaluationMetrics m=report.metrics();String sql="INSERT INTO evaluation_runs(prompt_version,model,total_cases,normal_cases,adversarial_cases,schema_valid_rate,category_accuracy,priority_accuracy,exact_match_rate,injection_resistance_rate,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)";
        try(PreparedStatement ps=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){
            ps.setString(1,report.promptVersion());ps.setString(2,report.model());ps.setInt(3,m.totalCases());ps.setInt(4,m.totalCases()-m.adversarialCases());ps.setInt(5,m.adversarialCases());ps.setDouble(6,fraction(m.schemaValidRate()));ps.setDouble(7,fraction(m.categoryAccuracy()));ps.setDouble(8,fraction(m.priorityAccuracy()));ps.setDouble(9,fraction(m.exactMatchRate()));ps.setDouble(10,fraction(m.injectionResistanceRate()));database.bindInstant(ps,11,Instant.parse(report.createdAt()));ps.executeUpdate();
            try(ResultSet keys=ps.getGeneratedKeys()){if(!keys.next())throw new SQLException("No generated evaluation run ID");return keys.getLong(1);}
        }
    }

    private void insertCase(Connection c,long runId,EvaluationResult r)throws SQLException{
        String sql="INSERT INTO evaluation_case_results(run_id,case_id,expected_category,predicted_category,expected_priority,predicted_priority,schema_valid,category_correct,priority_correct,exact_match,adversarial,injection_passed,summary,reason,error_message) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try(PreparedStatement ps=c.prepareStatement(sql)){ps.setLong(1,runId);ps.setString(2,r.caseId());ps.setString(3,r.expectedCategory().name());setEnum(ps,4,r.predictedCategory());ps.setString(5,r.expectedPriority().name());setEnum(ps,6,r.predictedPriority());ps.setBoolean(7,r.schemaValid());ps.setBoolean(8,r.categoryCorrect());ps.setBoolean(9,r.priorityCorrect());ps.setBoolean(10,r.categoryCorrect()&&r.priorityCorrect());ps.setBoolean(11,r.adversarial());if(r.injectionPassed()==null)ps.setNull(12,Types.BOOLEAN);else ps.setBoolean(12,r.injectionPassed());setNullable(ps,13,r.summary());setNullable(ps,14,r.reason());setNullable(ps,15,r.error());ps.executeUpdate();}
    }

    private EvaluationRunSummary mapRun(ResultSet rs)throws SQLException{return new EvaluationRunSummary(rs.getLong("id"),rs.getString("prompt_version"),rs.getString("model"),rs.getInt("total_cases"),rs.getInt("normal_cases"),rs.getInt("adversarial_cases"),rs.getDouble("schema_valid_rate"),rs.getDouble("category_accuracy"),rs.getDouble("priority_accuracy"),rs.getDouble("exact_match_rate"),rs.getDouble("injection_resistance_rate"),database.readInstant(rs,"created_at"));}
    private EvaluationCaseRecord mapCase(ResultSet rs)throws SQLException{String pc=rs.getString("predicted_category"),pp=rs.getString("predicted_priority");Object ip=rs.getObject("injection_passed");return new EvaluationCaseRecord(rs.getLong("id"),rs.getLong("run_id"),rs.getString("case_id"),TicketCategory.valueOf(rs.getString("expected_category")),pc==null?null:TicketCategory.valueOf(pc),TicketPriority.valueOf(rs.getString("expected_priority")),pp==null?null:TicketPriority.valueOf(pp),rs.getBoolean("schema_valid"),rs.getBoolean("category_correct"),rs.getBoolean("priority_correct"),rs.getBoolean("exact_match"),rs.getBoolean("adversarial"),ip==null?null:rs.getBoolean("injection_passed"),rs.getString("summary"),rs.getString("reason"),rs.getString("error_message"));}
    private static double fraction(double percentage){return percentage/100.0;}
    private static void setNullable(PreparedStatement ps,int index,String value)throws SQLException{if(value==null)ps.setNull(index,Types.VARCHAR);else ps.setString(index,value);}
    private static void setEnum(PreparedStatement ps,int index,Enum<?> value)throws SQLException{if(value==null)ps.setNull(index,Types.VARCHAR);else ps.setString(index,value.name());}
}
