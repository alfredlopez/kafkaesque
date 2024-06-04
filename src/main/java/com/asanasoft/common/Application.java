package com.asanasoft.common;

import com.asanasoft.common.codec.HandlerMessageCodec;
import com.asanasoft.common.codec.TriggerListenerMessageCodec;
import com.asanasoft.common.handlers.KafkaConnectProxyHandler;
import com.asanasoft.common.init.impl.Environment;
import com.asanasoft.common.init.impl.GraphQLInitializer;
import com.asanasoft.common.model.dao.BitsyObject;
import com.asanasoft.common.codec.BitsyObjectMessageCodec;
import com.asanasoft.common.verticle.DefaultQuartzVerticle;
import io.vertx.core.*;
import io.vertx.core.Context;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.NodeListener;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import org.quartz.TriggerListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

public class Application extends DefaultQuartzVerticle {
    private Logger logger = LoggerFactory.getLogger(Application.class);
    private Environment environment;
    private boolean deployAll = true;
    private String firstNode = "";
    private String nodeId = "";
    private int port = 8080; //Default
    private ClusterManager clusterManager;
    private LocalMap<String, JsonObject> verticles;
    private KafkaConnectProxyHandler kafkaConnectProxyHandler;
    private GraphQLInitializer graphQLInitializer;

    protected Router router;

    public static Vertx globalVertx;


    @Override
    public void init(Vertx vertx, Context context) {
        logger.debug("In init...");

        super.init(vertx, context);

        router = Router.router(vertx);
        if (Application.globalVertx == null) {
            Application.globalVertx = this.vertx;
        }
        HandlerMessageCodec handlerMessageCodec = new HandlerMessageCodec();
        TriggerListenerMessageCodec triggerListenerMessageCodec = new TriggerListenerMessageCodec();
        BitsyObjectMessageCodec bitsyObjectMessageCodec = new BitsyObjectMessageCodec();

        vertx.eventBus().registerDefaultCodec(Handler.class, handlerMessageCodec);
        vertx.eventBus().registerDefaultCodec(TriggerListener.class, triggerListenerMessageCodec);
        vertx.eventBus().registerDefaultCodec(BitsyObject.class, bitsyObjectMessageCodec);

    }

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        logger.debug("In start...");
        environment = new Environment();
        environment.init(null, envResult -> {
            Future<Void> beforeStartFuture = Future.future();

            try {
                super.start(beforeStartFuture);
            }
            catch(Exception e) {
                logger.error("An error occurred in start:", e);
            }

            beforeStartFuture.compose(r -> {
                /**
                 * Wait for the web server to start...
                 */
                MessageConsumer futureConsumer = vertx.eventBus().localConsumer("WebServerStarted");

                futureConsumer.handler(consumerResult -> {
                    startFuture.complete();
                });
            }, null);
        });
    }

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {
        super.stop(stopFuture);
    }

    @Override
    public void start() throws Exception {
        setupRoutes();
        vertx.createHttpServer().requestHandler(router)
                .websocketHandler(socketConnection -> {
                    //TODO: something with sockets
                })
                .listen(port, environment.getString("socketAddress"),
                        result -> {
                            if (result.succeeded()) {
                                logger.info("Started the web server on port " + port);
                                vertx.eventBus().send("WebServerStarted", null);
                            } else {
                                logger.error("Web server failed to start on port " + port, result.cause());
                            }
                        }
                );
    }

    @Override
    public void stop() throws Exception {
        super.stop();
    }

    @Override
    protected void deployDependencies(Future future) {
        String env = Environment.getInstance().getRunningEnv();
        String verticlesString = Environment.loadStringFromFile("verticles_" + env + ".json");

        logger.info(env + ":\n" + verticlesString);

        if (verticlesString == null) {
            verticlesString = Environment.loadStringFromFile("verticles.json");
        }

        JsonObject verticles = new JsonObject(verticlesString);

        this.config().mergeIn(verticles, true);
        super.deployDependencies(future);
    }

    protected void setupRoutes() {
        Environment env = Environment.getInstance();
        com.asanasoft.common.Context appContext = new com.asanasoft.common.Context();

        if (env.getString("cors") != null) {
            logger.info("Setting up CORS...");
            String corsAddress = env.getString("cors");

            Set<HttpMethod> methodsAllowed = new HashSet<>();

            methodsAllowed.add(HttpMethod.GET);
            methodsAllowed.add(HttpMethod.PUT);
            methodsAllowed.add(HttpMethod.POST);
            methodsAllowed.add(HttpMethod.OPTIONS);

            logger.debug("...for address " + corsAddress);

            if (router == null) {
                router = Router.router(vertx);
            }

            router.route().handler(CorsHandler.create(corsAddress)
                    .allowCredentials(Boolean.valueOf(env.getString("allowCredentials")))
                    .allowedHeader("Access-Control-Allow-Method")
                    .allowedHeader("Access-Control-Allow-Origin")
                    .allowedHeader("Access-Control-Allow-Credentials")
                    .allowedHeader("Content-Type")
                    .allowedHeader("Origin")
                    .allowedHeader("accept")
                    .allowedHeader("x-requested-with")
                    .allowedMethods(methodsAllowed)
            );
        }

        appContext.putValue("vertx", vertx).putValue("router", router).putValue("cacheAddress","KafkaCache");
        graphQLInitializer = new GraphQLInitializer();
        graphQLInitializer.init(appContext);

        router.route().handler(BodyHandler.create());

        kafkaConnectProxyHandler = new KafkaConnectProxyHandler();
        router.route("/connectors/*").handler(routingContext -> {
            logger.debug("Proxying a call to /connectors");

            routingContext.put("port", 8083);
            kafkaConnectProxyHandler.handle(routingContext);
        });

        router.route("/zookeeper/*").handler(routingContext -> {
            routingContext.put("port", 2181);
            kafkaConnectProxyHandler.handle(routingContext);
        });

        router.route("/boostrap/*").handler(routingContext -> {
            routingContext.put("port", 9092);
            kafkaConnectProxyHandler.handle(routingContext);
        });

        router.route("/schema_reg/*").handler(routingContext -> {
            routingContext.put("port", 8081);
            kafkaConnectProxyHandler.handle(routingContext);
        });

        router.route("/ksql/*").handler(routingContext -> {
            routingContext.put("port", 8088);
            kafkaConnectProxyHandler.handle(routingContext);
        });
    }
}
