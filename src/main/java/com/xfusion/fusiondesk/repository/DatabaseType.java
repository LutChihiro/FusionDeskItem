package com.xfusion.fusiondesk.repository;
import com.xfusion.fusiondesk.exception.ValidationException;
import java.util.Locale;
public enum DatabaseType { SQLITE, MYSQL;
    public static DatabaseType parse(String value){if(value==null||value.isBlank())return SQLITE;try{return valueOf(value.strip().toUpperCase(Locale.ROOT));}catch(IllegalArgumentException e){throw new ValidationException("FUSIONDESK_DB_TYPE must be sqlite or mysql.");}}
}
