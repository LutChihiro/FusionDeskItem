package com.xfusion.fusiondesk.repository;

import com.xfusion.fusiondesk.ai.AiAnalysisResult;
import com.xfusion.fusiondesk.exception.DatabaseException;
import com.xfusion.fusiondesk.model.*;

import java.sql.*;
import java.time.Instant;
import java.util.*;

public class AiSuggestionRepository {
    private static final String COLUMNS="id,ticket_id,suggested_category,suggested_priority,summary,reason,raw_response,model,prompt_version,status,final_category,final_priority,created_at,reviewed_at";
    private final DatabaseManager database;
    public AiSuggestionRepository(DatabaseManager database){this.database=database;}

    public AiSuggestion insert(Connection c,long ticketId,AiAnalysisResult result,String raw,String model,String promptVersion,Instant now)throws SQLException{
        String sql="INSERT INTO ai_suggestions(ticket_id,suggested_category,suggested_priority,summary,reason,raw_response,model,prompt_version,status,final_category,final_priority,created_at,reviewed_at) VALUES(?,?,?,?,?,?,?,?,'PENDING',NULL,NULL,?,NULL)";
        try(PreparedStatement ps=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){
            ps.setLong(1,ticketId);ps.setString(2,result.category().name());ps.setString(3,result.priority().name());ps.setString(4,result.summary());ps.setString(5,result.reason());
            ps.setString(6,raw);ps.setString(7,model);ps.setString(8,promptVersion);database.bindInstant(ps,9,now);ps.executeUpdate();
            try(ResultSet keys=ps.getGeneratedKeys()){if(!keys.next())throw new SQLException("No generated suggestion ID");return new AiSuggestion(keys.getLong(1),ticketId,result.category(),result.priority(),result.summary(),result.reason(),raw,model,promptVersion,SuggestionStatus.PENDING,null,null,now,null);}
        }
    }
    public Optional<AiSuggestion> findById(long id){
        try(Connection c=database.openConnection();PreparedStatement ps=c.prepareStatement("SELECT "+COLUMNS+" FROM ai_suggestions WHERE id=?")){ps.setLong(1,id);try(ResultSet rs=ps.executeQuery()){return rs.next()?Optional.of(map(rs)):Optional.empty();}}
        catch(SQLException e){throw new DatabaseException("Failed to query AI suggestion",e);}
    }
    public Optional<AiSuggestion> findById(Connection c,long id)throws SQLException{
        try(PreparedStatement ps=c.prepareStatement("SELECT "+COLUMNS+" FROM ai_suggestions WHERE id=?")){ps.setLong(1,id);try(ResultSet rs=ps.executeQuery()){return rs.next()?Optional.of(map(rs)):Optional.empty();}}
    }
    public int markReviewed(Connection c,long id,SuggestionStatus status,TicketCategory finalCategory,TicketPriority finalPriority,Instant reviewedAt)throws SQLException{
        String sql="UPDATE ai_suggestions SET status=?,final_category=?,final_priority=?,reviewed_at=? WHERE id=? AND status='PENDING'";
        try(PreparedStatement ps=c.prepareStatement(sql)){ps.setString(1,status.name());if(finalCategory==null)ps.setNull(2,Types.VARCHAR);else ps.setString(2,finalCategory.name());if(finalPriority==null)ps.setNull(3,Types.VARCHAR);else ps.setString(3,finalPriority.name());database.bindInstant(ps,4,reviewedAt);ps.setLong(5,id);return ps.executeUpdate();}
    }
    public List<AiSuggestion> findByTicketId(long ticketId){
        try(Connection c=database.openConnection();PreparedStatement ps=c.prepareStatement("SELECT "+COLUMNS+" FROM ai_suggestions WHERE ticket_id=? ORDER BY created_at DESC,id DESC")){ps.setLong(1,ticketId);List<AiSuggestion> out=new ArrayList<>();try(ResultSet rs=ps.executeQuery()){while(rs.next())out.add(map(rs));}return out;}
        catch(SQLException e){throw new DatabaseException("Failed to list AI suggestions",e);}
    }
    private AiSuggestion map(ResultSet rs)throws SQLException{String fc=rs.getString("final_category"),fp=rs.getString("final_priority");return new AiSuggestion(rs.getLong("id"),rs.getLong("ticket_id"),TicketCategory.valueOf(rs.getString("suggested_category")),TicketPriority.valueOf(rs.getString("suggested_priority")),rs.getString("summary"),rs.getString("reason"),rs.getString("raw_response"),rs.getString("model"),rs.getString("prompt_version"),SuggestionStatus.valueOf(rs.getString("status")),fc==null?null:TicketCategory.valueOf(fc),fp==null?null:TicketPriority.valueOf(fp),database.readInstant(rs,"created_at"),database.readInstant(rs,"reviewed_at"));}
}
