package com.asanasoft.common.service.store;

import com.asanasoft.common.init.impl.Environment;
import com.asanasoft.common.service.AbstractFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;

public class StoreServiceFactory extends AbstractFactory<Class<? extends StoreService>> {
    private Logger logger = LoggerFactory.getLogger(StoreServiceFactory.class);
    private String DEFAULT_CONFIG_BASE_FILENAME = "storeConfig";

    public StoreService getStoreService(String storeStype, String storeName) {
        StoreService result = null;
        Class<? extends StoreService> storeServiceClass = null;
        String storeClassName = null;

        try {
            storeServiceClass = getInstance(storeStype);
            storeClassName = storeServiceClass.getName();

            if (storeServiceClass.isInterface()) {
                storeClassName = storeClassName + "Impl";
            }

            Constructor storeClassConstructor = Class.forName(storeClassName).getConstructor(String.class);
            result = (StoreService)storeClassConstructor.newInstance(storeName);
        } catch (Exception e) {
            logger.error("An error occurred in getStoreService:", e);
            result = null;
        }

        return result;
    }

    /**
     * This method sets up the known types that will be returned
     * to client code as a StoreService. See StoreService.java
     * for the resoning behind this implementation...
     * @return
     */
    @Override
    public boolean init() {
        getComponents().put(StoreService.MSSQL_STORE,   ()-> MSSQLStoreService.class);
        getComponents().put(StoreService.DB_STORE,      ()-> MySQLStoreService.class);
        getComponents().put(StoreService.FILE_STORE,    ()-> FileStoreService.class);
        getComponents().put(StoreService.MEMORY_STORE,  ()-> MemoryStoreService.class);
        getComponents().put(StoreService.NETWORK_STORE, ()-> SftpStoreService.class);
        getComponents().put(StoreService.SFTP_STORE,    ()-> SftpStoreService.class);

        return true;
    }

    public StoreService getDefaultStore(String storeName) {
        StoreService store = null;

        /**
         * See if there is a defined default store...
         */
        String storeService = Environment.getInstance().getString("defaultStore");
        String env = Environment.getInstance().getRunningEnv();

        if (storeService == null || storeService.isEmpty()) {
            /**
             * ...let's default to a FileStore...
             */
            storeService = StoreService.FILE_STORE;
        }

        /**
         * ...and ask for the store...
         */
        store = getStoreService(storeService, storeName);

        //Configure the store...
        StoreConfigurator configurator = new StoreConfigurator();
        configurator.configure(this.DEFAULT_CONFIG_BASE_FILENAME + "_" + env + ".properties");
        store.configure(configurator);

        return store;
    }
}
