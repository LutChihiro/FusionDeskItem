package com.xfusion.fusiondesk.repository;

import com.xfusion.fusiondesk.exception.DatabaseException;
import com.xfusion.fusiondesk.model.*;

import java.sql.*;
import java.time.Instant;
import java.util.*;

public class TicketRepository {
    private static final String COLUMNS = "id,title,description,submitter,status,category,priority,version,dedup_key,created_at,updated_at";
    private final DatabaseManager database;
    public TicketRepository(DatabaseManager database) { this.database = database; }

    public Ticket insert(Connection c, String title, String description, String submitter,
                         TicketCategory category, TicketPriority priority, String dedupKey, Instant now) throws SQLException {
        String sql = "INSERT INTO tickets(title,description,submitter,status,category,priority,version,dedup_key,created_at,updated_at) VALUES(?,?,?,'NEW',?,?,0,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1,title); ps.setString(2,description); ps.setString(3,submitter);
            if (category == null) ps.setNull(4,Types.VARCHAR); else ps.setString(4,category.name());
            ps.setString(5,priority.name()); ps.setString(6,dedupKey); ps.setString(7,now.toString()); ps.setString(8,now.toString());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("No generated ticket ID");
                return new Ticket(keys.getLong(1),title,description,submitter,TicketStatus.NEW,category,priority,0,dedupKey,now,now);
            }
        }
    }

    public Optional<Ticket> findById(long id) {
        try (Connection c=database.openConnection()) { return findById(c,id); }
        catch(SQLException e){ throw new DatabaseException("Failed to query ticket",e); }
    }
    public Optional<Ticket> findById(Connection c,long id) throws SQLException {
        try(PreparedStatement ps=c.prepareStatement("SELECT "+COLUMNS+" FROM tickets WHERE id=?")){
            ps.setLong(1,id); try(ResultSet rs=ps.executeQuery()){ return rs.next()?Optional.of(map(rs)):Optional.empty(); }
        }
    }
    public Optional<Ticket> findActiveByDedupKey(String key) {
        try(Connection c=database.openConnection()){ return findActiveByDedupKey(c,key); }
        catch(SQLException e){ throw new DatabaseException("Failed to check duplicate ticket",e); }
    }
    public Optional<Ticket> findActiveByDedupKey(Connection c,String key) throws SQLException {
        try(PreparedStatement ps=c.prepareStatement("SELECT "+COLUMNS+" FROM tickets WHERE dedup_key=? AND status<>'CLOSED' LIMIT 1")){
            ps.setString(1,key); try(ResultSet rs=ps.executeQuery()){ return rs.next()?Optional.of(map(rs)):Optional.empty(); }
        }
    }
    public List<Ticket> find(TicketFilter filter) {
        StringBuilder sql=new StringBuilder("SELECT "+COLUMNS+" FROM tickets WHERE 1=1"); List<Object> args=new ArrayList<>();
        if(filter!=null){
            if(filter.status()!=null){sql.append(" AND status=?");args.add(filter.status().name());}
            if(filter.category()!=null){sql.append(" AND category=?");args.add(filter.category().name());}
            if(filter.priority()!=null){sql.append(" AND priority=?");args.add(filter.priority().name());}
            if(filter.submitter()!=null&&!filter.submitter().isBlank()){sql.append(" AND submitter=?");args.add(filter.submitter().strip());}
        }
        sql.append(" ORDER BY id");
        try(Connection c=database.openConnection();PreparedStatement ps=c.prepareStatement(sql.toString())){
            for(int i=0;i<args.size();i++)ps.setObject(i+1,args.get(i)); List<Ticket> result=new ArrayList<>();
            try(ResultSet rs=ps.executeQuery()){while(rs.next())result.add(map(rs));} return result;
        }catch(SQLException e){throw new DatabaseException("Failed to list tickets",e);}
    }
    public int updateStatusWithVersion(Connection c,long id,TicketStatus status,long version,Instant now)throws SQLException{
        try(PreparedStatement ps=c.prepareStatement("UPDATE tickets SET status=?,version=version+1,updated_at=? WHERE id=? AND version=?")){
            ps.setString(1,status.name());ps.setString(2,now.toString());ps.setLong(3,id);ps.setLong(4,version);return ps.executeUpdate();
        }
    }
    private Ticket map(ResultSet rs)throws SQLException{
        String category=rs.getString("category");
        return new Ticket(rs.getLong("id"),rs.getString("title"),rs.getString("description"),rs.getString("submitter"),
            TicketStatus.valueOf(rs.getString("status")),category==null?null:TicketCategory.valueOf(category),TicketPriority.valueOf(rs.getString("priority")),
            rs.getLong("version"),rs.getString("dedup_key"),Instant.parse(rs.getString("created_at")),Instant.parse(rs.getString("updated_at")));
    }
}
