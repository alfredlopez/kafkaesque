package com.asanasoft.app.kafkaesque;

import com.asanasoft.common.Application;
import com.asanasoft.common.init.impl.GraphQLInitializer;
import com.asanasoft.common.Context;
import com.asanasoft.common.init.impl.Environment;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.CorsHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

public class KafkaesqueApplication extends Application {
    private Logger logger = LoggerFactory.getLogger(KafkaesqueApplication.class);

    @Override
    protected void setupRoutes() {
        super.setupRoutes();
    }
}