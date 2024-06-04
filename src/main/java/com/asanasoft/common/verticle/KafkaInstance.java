package com.asanasoft.common.verticle;

import io.vertx.core.Future;
import io.vertx.kafka.admin.KafkaAdminClient;
import kafka.server.KafkaConfig;
import kafka.server.KafkaServerStartable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.admin.AdminClient;

public class KafkaInstance extends DefaultQuartzVerticle {
    private Logger logger = LoggerFactory.getLogger(KafkaInstance.class);
    private KafkaServerStartable kafka;
    private KafkaAdminClient kafkaAdminClient;

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {
        logger.info("Stopping local kafka broker...");
        kafka.shutdown();
        logger.info("Local kafka broker stopped!");
    }

    @Override
    public void start() throws Exception {
        logger.debug("In start()...");
        Map<String, Object> kafkaConfigMap = this.config().getJsonObject("config").getMap();

        KafkaConfig kafkaConfig = null;
        try {
            kafkaConfig = new KafkaConfig(kafkaConfigMap);
        } catch (Exception e) {
            logger.error("An error occurred configuring Kafka...", e);
            throw e;
        }

        //start local kafka broker
        kafka = new KafkaServerStartable(kafkaConfig);
        logger.info("Starting local kafka broker...");

        //Even though startup() starts up threads, it still blocks the eventbus,
        //so we'll put it on its own thread...
        new Thread(() -> {
            kafka.startup();
        }).start();

        logger.info("Local kafka broker started!");
        logger.info("Creating Kafka admin tool...");

        Map<String, String> kafkaAdminConfig = new HashMap<>();
        kafkaAdminConfig.put("","");
        kafkaAdminClient = KafkaAdminClient.create(this.getVertx(), kafkaAdminConfig);
    }
}
