import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

// ── Cargador de configuración desde database.properties ─────
public class ConfigLoader {
    
    private Properties properties;
    // ── Carga el archivo database.properties ────────────────
    public ConfigLoader() throws Exception {
        this("database.properties");
    }
    // ── Carga un archivo de configuración específico ────────
    public ConfigLoader(String configFile) throws Exception {
        properties = new Properties();
        
        // ── Intentar cargar desde el directorio del proyecto
        try (InputStream input = new FileInputStream(configFile)) {
            properties.load(input);
            System.out.println("Configuración cargada desde: " + configFile);
        } catch (Exception e) {
        	
            // ── Intentar cargar desde classpath ────────
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(configFile)) {
                if (input == null) {
                    throw new Exception("No se encontró el archivo: " + configFile);
                }
                properties.load(input);
                System.out.println("Configuración cargada desde classpath: " + configFile);
            }
        }
    }
    
    // Métodos para Base de Datos
    public String getDbUrl() {
        return properties.getProperty("db.url");
    }
    
    public String getDbUser() {
        return properties.getProperty("db.user");
    }
    
    public String getDbPassword() {
        return properties.getProperty("db.password");
    }
    
    public String getTableName() {
        return properties.getProperty("db.table.name");
    }
    
    public String getColumnId() {
        return properties.getProperty("db.column.id");
    }
    
    public String getColumnPdf() {
        return properties.getProperty("db.column.pdf");
    }
    
    public String getColumnEstado() {
        return properties.getProperty("db.column.estado");
    }
    
    // Métodos para Keystore
    public String getKeystorePath() {
        return properties.getProperty("keystore.path");
    }
    
    public String getKeystorePassword() {
        return properties.getProperty("keystore.password");
    }
    
    public String getKeystoreAlias() {
        return properties.getProperty("keystore.alias");
    }
    
    // Método genérico
    public String getProperty(String key) {
        return properties.getProperty(key);
    }
    
    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
}
