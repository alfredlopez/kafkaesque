package com.asanasoft.common.verticle;

import com.asanasoft.common.init.impl.Environment;
import com.asanasoft.common.model.connector.DataStreamConnector;
import com.asanasoft.common.model.connector.DataStreamConnectorFactory;
import com.asanasoft.common.model.listener.StreamListener;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.quartz.*;

import java.util.HashMap;
import java.util.Map;

public class DataStreamingInstance extends DefaultQuartzVerticle {
    private Map<String, DataStreamConnector>    connectors;
    private Map<String, StreamListener>         listeners;
    private DataStreamConnectorFactory          connectorFactory;

    public Map<String, DataStreamConnector> getConnectors() {
        if (connectors == null) {
            connectors = new HashMap();
        }
        return connectors;
    }

    public Map<String, StreamListener> getListeners() {
        if (listeners == null) {
            listeners = new HashMap();
        }
        return listeners;
    }

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        super.start(startFuture);
        DataStreamConnectorFactory dscFactory = new DataStreamConnectorFactory();
        JsonObject dataStreamConnectors = new JsonObject(Environment.loadStringFromFile(this.config().getString("connectors_config")));
        JsonArray connectorsConfig = dataStreamConnectors.getJsonArray("connectors");

        for (int i = 0; i < connectorsConfig.size(); i++) {
            JsonObject connector = connectorsConfig.getJsonObject(i);
            String connectorName = connector.getString("connectorName");
        }
    }

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {
        super.stop(stopFuture);
    }

    @Override
    public void start() throws Exception {
        super.start();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
    }
}
