package com.asanasoft.common.service.store;

import com.asanasoft.common.init.impl.Environment;

import java.lang.reflect.Method;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class holds all the possible configuration parameterrs for a
 * StoreService. It will be used to configure an StoreService object
 * by invoking all implemented setters of the SotreService to configure.
 * See AbstractStoreService.configure(...) method.
 */
public class StoreConfigurator {
    private Logger logger = LoggerFactory.getLogger(StoreConfigurator.class);
    private String destination;
    private String source;
    private String password;
    private String name;
    private String username;
    private String host;
    private int port;
    private boolean encrypt;
    private int timeout;
    private String protocol;
    private String keyFilename;
    private String permissions;
    private String STORE_CONFIG_FILE = "storeConfig.properties";
    private String datasourceName;

    public void configure() {
        configure(null);
    }

    public void configure(String propertiesFileName) {
        String propertiesFile = (propertiesFileName != null?propertiesFileName:STORE_CONFIG_FILE);

        PropertiesFileStoreService propertiesFileStoreService = Environment.getInstance().getPropertiesFileStoreService();
        Properties properties = new Properties();

        try {
            properties.load(propertiesFileStoreService.getVersionByLabelBlocking("", propertiesFile));

            for (String property : properties.stringPropertyNames()) {
                String testProperty = "set" + Character.toUpperCase(property.charAt(0)) + property.substring(1);

                try {
                    Method targetMethod = this.getClass().getMethod(testProperty, String.class);
                    targetMethod.invoke(this, properties.get(property));
                }
                catch (NoSuchMethodException nsm) {
                    logger.trace(nsm.getMessage());
                }
                catch (Exception e) {
                    logger.error("An error occurred configuring store:", e);
                }
            }
        } catch (Exception e) {
            logger.error("An error occurred configuring the store configurator:", e);
        }
    }

    public String getDatasourceName() {
        return datasourceName;
    }

    public void setDatasourceName(String datasourceName) {
        this.datasourceName = datasourceName;
    }

    public String getDestination() {
        return destination;
    }

    
    public String getFilePermissions() {
        return permissions;
    }

    
    public String getName() {
        return name;
    }

    
    public String getSource() {
        return source;
    }

    
    public void setDestination(String destinationContainer) {
        this.destination = destinationContainer;
    }

    
    public void setFilePermissions(String permissions) {
        this.permissions = permissions;
    }

    
    public void setSource(String sourceContainer) {
        this.source = sourceContainer;
    }

    
    public void setPassword(String password) {
        this.password = password;
    }

    
    public String getPassword() {
        return password;
    }

    
    public void setEncrypt(String doEncryption) {
        this.encrypt = Boolean.valueOf(doEncryption);
    }

    
    public boolean isEncryption() {
        return encrypt;
    }

    
    public void setUserName(String userName) {
        this.username = userName;
    }

    
    public String getUserName() {
        return username;
    }

    
    public void setHost(String host) {
        this.host = host;
    }

    
    public String getHost() {
        return host;
    }

    
    public void setPort(String port) {
        this.port = Integer.valueOf(port);
    }

    
    public int getPort() {
        return port;
    }

    
    public void setTimeout(String timeout) {
        this.timeout = Integer.valueOf(timeout);
    }

    
    public int getTimeout() {
        return timeout;
    }

    
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    
    public String getProtocol() {
        return protocol;
    }

    
    public void setKeyFilename(String keyFilename) {
        this.keyFilename = keyFilename;
    }

    
    public String getKeyFilename() {
        return keyFilename;
    }
}
