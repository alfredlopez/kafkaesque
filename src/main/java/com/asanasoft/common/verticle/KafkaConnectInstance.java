package com.asanasoft.common.verticle;

import com.asanasoft.common.init.impl.Environment;
import com.asanasoft.common.service.store.DefaultFileStoreService;
import com.asanasoft.common.service.store.FileStoreService;
import io.vertx.core.json.JsonObject;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.common.utils.Utils;
import org.apache.kafka.connect.runtime.*;
import org.apache.kafka.connect.runtime.isolation.Plugins;
import org.apache.kafka.connect.runtime.rest.RestServer;
import org.apache.kafka.connect.runtime.rest.entities.ConnectorInfo;
import org.apache.kafka.connect.runtime.standalone.StandaloneConfig;
import org.apache.kafka.connect.runtime.standalone.StandaloneHerder;
import org.apache.kafka.connect.storage.FileOffsetBackingStore;
import org.apache.kafka.connect.util.Callback;
import org.apache.kafka.connect.util.ConnectUtils;
import org.apache.kafka.connect.util.FutureCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Embedded Kafka Connect instance
 * Code copied from org.apache.kafka.connect.cli.ConnectStandalone
 */
public class KafkaConnectInstance extends DefaultQuartzVerticle {
    private static final Logger logger = LoggerFactory.getLogger(KafkaConnectInstance.class);
    private Connect connect;
    private Herder herder;

    @Override
    protected JsonObject handleMessage(JsonObject message) {
        JsonObject result = new JsonObject();
        String method = message.getString("method");

        if (method != null) {
//            switch (method) {
//                case "addOrUpdateConnector" :
//                    herder.putConnectorConfig();
//            }
        }

        return result;
    }

    @Override
    public void start() throws Exception {
        try {
            Time time = Time.SYSTEM;
            logger.info("Kafka Connect standalone worker initializing ...");
            long initStart = time.hiResClockMs();
            WorkerInfo initInfo = new WorkerInfo();
            initInfo.logAll();

            logger.debug("Configuring Kafka with the following config: " + this.config().getJsonObject("config").encodePrettily());

            Map<String, String> workerProps = this.config().getJsonObject("config").getMap()
                    .entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> String.valueOf(entry.getValue())));

            logger.info("Scanning for plugin classes. This might take a moment ...");
            Plugins plugins = new Plugins(workerProps);
            plugins.compareAndSwapWithDelegatingLoader();
            StandaloneConfig config = new StandaloneConfig(workerProps);

            logger.debug("Looking up Kafka Cluster ID...");
            String kafkaClusterId = ConnectUtils.lookupKafkaClusterId(config);
            logger.debug("Kafka cluster ID: {}", kafkaClusterId);

            RestServer rest = new RestServer(config);
            HerderProvider provider = new HerderProvider();
            rest.start(provider, plugins);

            URI advertisedUrl = rest.advertisedUrl();
            String workerId = advertisedUrl.getHost() + ":" + advertisedUrl.getPort();

            Worker worker = new Worker(workerId, time, plugins, config, new FileOffsetBackingStore());

            herder = new StandaloneHerder(worker, kafkaClusterId);
            connect = new Connect(herder, rest);
            logger.info("Kafka Connect standalone worker initialization took {}ms", time.hiResClockMs() - initStart);

            try {
                connect.start();
                // herder has initialized now, and ready to be used by the RestServer.
                provider.setHerder(herder);

                FileStoreService connectorPropsStore = new DefaultFileStoreService();

                String sourceDir = config().getJsonObject("config").getString("connectors.dir");
                connectorPropsStore.setSource(sourceDir);

                connectorPropsStore.getVersionList("*", connectorsResult -> {
                    if (connectorsResult.succeeded()) {
                        try {
                            for (final String connectorPropsFile : connectorsResult.result()) {
                                Map<String, String> connectorProps = Utils.propsToStringMap(Utils.loadProps(connectorPropsStore.getSource() + "/" + connectorPropsFile));

                                //For each property, see if the value comes from another properties file (this is done, mostly, for encrypted values)...
                                Environment env = Environment.getInstance();
                                for (String key : connectorProps.keySet()) {
                                    String keyValue = env.getString(connectorProps.get(key));
                                    keyValue = (keyValue != null && !"null".equals(keyValue))?keyValue:connectorProps.get(key);
                                    connectorProps.put(key, keyValue);
                                }

                                FutureCallback<Herder.Created<ConnectorInfo>> cb = new FutureCallback<>(new Callback<Herder.Created<ConnectorInfo>>() {
                                    @Override
                                    public void onCompletion(Throwable error, Herder.Created<ConnectorInfo> info) {
                                        if (error != null)
                                            logger.error("Failed to create job for {}", connectorPropsFile);
                                        else
                                            logger.info("Created connect {}", info.result().name());
                                    }
                                });
                                herder.putConnectorConfig(
                                        connectorProps.get(ConnectorConfig.NAME_CONFIG),
                                        connectorProps, false, cb);
                                cb.get();
                            }
                        } catch (IOException e) {
                            logger.error("Error loading connect properties...");
                        } catch (InterruptedException e) {
                            logger.error("Stopping after connect interruption", e);
                            connect.stop();
                        } catch (ExecutionException e) {
                            logger.error("Stopping after connect error", e);
                            connect.stop();
                        }
                    } else {
                        logger.error("Could not configure connectors!!!!:", connectorsResult.cause());
                    }
                });
            } catch (Throwable t) {
                logger.error("Stopping after connect error", t);
                connect.stop();
                throw t;
            }

            // Shutdown will be triggered by Ctrl-C or via HTTP shutdown request
            new Thread(() -> {
                connect.awaitStop();
            }).start();

        } catch (Throwable t) {
            logger.error("Stopping due to error", t);
            throw t;
        }
    }

    @Override
    public void stop() throws Exception {
        connect.stop();
    }
}
