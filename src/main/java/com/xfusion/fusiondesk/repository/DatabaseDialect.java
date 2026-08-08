package com.xfusion.fusiondesk.repository;
import java.sql.*;import java.time.Instant;import java.util.List;
public interface DatabaseDialect {
    DatabaseType type();void configure(Connection connection)throws SQLException;List<String> schemaStatements();void bindInstant(PreparedStatement statement,int index,Instant instant)throws SQLException;Instant readInstant(ResultSet rs,String column)throws SQLException;String metadataKeyColumn();
    default boolean isDuplicateKey(Throwable error){for(Throwable t=error;t!=null;t=t.getCause())if(t instanceof SQLException sql){if(type()==DatabaseType.SQLITE&&sql.getErrorCode()==19)return true;if(type()==DatabaseType.MYSQL&&("23000".equals(sql.getSQLState())||sql.getErrorCode()==1062))return true;}return false;}
}
