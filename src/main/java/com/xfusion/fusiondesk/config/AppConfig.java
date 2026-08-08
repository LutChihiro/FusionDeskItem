package com.xfusion.fusiondesk.config;

import com.xfusion.fusiondesk.exception.BusinessException;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Loads optional local configuration without ever logging secret values. */
public final class AppConfig {
    public static final Path DEFAULT_PATH = Path.of("config", "fusiondesk.properties");
    private final Properties properties;

    private AppConfig(Properties properties) { this.properties = properties; }

    public static AppConfig current() {
        String configuredPath = System.getProperty("fusiondesk.config");
        if (blank(configuredPath)) configuredPath = System.getenv("FUSIONDESK_CONFIG");
        return load(blank(configuredPath) ? DEFAULT_PATH : Path.of(configuredPath.strip()));
    }

    public static AppConfig load(Path path) {
        Properties values = new Properties();
        if (!Files.exists(path)) return new AppConfig(values);
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            values.load(reader);
            return new AppConfig(values);
        } catch (IOException e) {
            throw new BusinessException("Failed to read FusionDesk configuration file: " + path, e);
        }
    }

    /** Environment variables intentionally override file values for deployment compatibility. */
    public String value(String environmentName, String propertyName) {
        String environmentValue = System.getenv(environmentName);
        if (!blank(environmentValue)) return environmentValue.strip();
        String fileValue = properties.getProperty(propertyName);
        return blank(fileValue) ? null : fileValue.strip();
    }

    public int intValue(String propertyName,int defaultValue){String value=properties.getProperty(propertyName);if(blank(value))return defaultValue;try{return Integer.parseInt(value.strip());}catch(NumberFormatException e){throw new BusinessException("Invalid integer configuration: "+propertyName,e);}}
    public double doubleValue(String propertyName,double defaultValue){String value=properties.getProperty(propertyName);if(blank(value))return defaultValue;try{return Double.parseDouble(value.strip());}catch(NumberFormatException e){throw new BusinessException("Invalid decimal configuration: "+propertyName,e);}}
    public boolean booleanValue(String propertyName,boolean defaultValue){String value=properties.getProperty(propertyName);if(blank(value))return defaultValue;if("true".equalsIgnoreCase(value.strip()))return true;if("false".equalsIgnoreCase(value.strip()))return false;throw new BusinessException("Invalid boolean configuration: "+propertyName);}

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
