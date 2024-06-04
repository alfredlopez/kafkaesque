package com.asanasoft.common.model.connector;

//import com.asanasoft.common.model.connector.impl.debezium.PostgresDataStreamConnector;
import com.asanasoft.common.model.connector.impl.diffkit.DiffKitDataStreamConnector;
import com.asanasoft.common.model.connector.impl.schemacrawler.SchemaCrawlerDataStreamConnector;
import com.asanasoft.common.service.AbstractFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;

public class DataStreamConnectorFactory extends AbstractFactory<Class<? extends DataStreamConnector>> {
    private Logger logger = LoggerFactory.getLogger(DataStreamConnectorFactory.class);

    public DataStreamConnector getConnector(String connectorName) {
        DataStreamConnector result = null;
        Class<? extends DataStreamConnector> storeServiceClass = null;
        String storeClassName = null;

        try {
            storeServiceClass = getInstance("");
            storeClassName = storeServiceClass.getName();

            if (storeServiceClass.isInterface()) {
                storeClassName = storeClassName + "Impl";
            }

            Constructor storeClassConstructor = Class.forName(storeClassName).getConstructor(String.class);
            result = (DataStreamConnector)storeClassConstructor.newInstance("");
        } catch (Exception e) {
            logger.error("An error occurred in getStoreService:", e);
            result = null;
        }
        return result;
    }
    @Override
    public boolean init() {
        getComponents().put("SchemaCrawler",    ()-> SchemaCrawlerDataStreamConnector.class);
        getComponents().put("DiffKit",          ()-> DiffKitDataStreamConnector.class);

        return true;
    }
}
