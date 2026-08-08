package com.xfusion.fusiondesk.repository;

import com.xfusion.fusiondesk.exception.DatabaseException;
import com.xfusion.fusiondesk.model.AuditEvent;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public class AuditRepository {
    private final DatabaseManager database;
    public AuditRepository(DatabaseManager database){this.database=database;}
    public void insert(Connection c,long ticketId,String type,String before,String after,Instant now)throws SQLException{
        try(PreparedStatement ps=c.prepareStatement("INSERT INTO audit_events(ticket_id,event_type,before_data,after_data,created_at) VALUES(?,?,?,?,?)")){
            ps.setLong(1,ticketId);ps.setString(2,type);ps.setString(3,before);ps.setString(4,after);database.bindInstant(ps,5,now);ps.executeUpdate();
        }
    }
    public List<AuditEvent> findByTicketId(long ticketId){
        String sql="SELECT id,ticket_id,event_type,before_data,after_data,created_at FROM audit_events WHERE ticket_id=? ORDER BY created_at,id";
        try(Connection c=database.openConnection();PreparedStatement ps=c.prepareStatement(sql)){ps.setLong(1,ticketId);List<AuditEvent> out=new ArrayList<>();
            try(ResultSet rs=ps.executeQuery()){while(rs.next())out.add(new AuditEvent(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getString(4),rs.getString(5),database.readInstant(rs,"created_at")));}return out;
        }catch(SQLException e){throw new DatabaseException("Failed to query audit events",e);}
    }
}
