package com.asanasoft.common.verticle;

import com.asanasoft.common.Application;
import com.asanasoft.common.Context;
import com.asanasoft.common.init.AbstractInitializer;
import io.vertx.core.*;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.LocalMap;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class DefaultQuartzVerticle extends AbstractVerticle implements TriggerListener, JobListener {
    private Logger logger = LoggerFactory.getLogger(DefaultQuartzVerticle.class);
    private final String WORKER = "worker";
    private final String DEFAULT_NAME = "DefaultQuartzVerticle";
    private String name;
    private final String uuid;

    public DefaultQuartzVerticle() {
        uuid = this.getName() + "." + UUID.randomUUID().toString();
        logger.debug("Created UUID " + this.getName());
    }

    @Override
    public String getName() {
        if (name == null) {
            name = this.getClass().getSimpleName();
        }

        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * This is the entry point into the class. There really is no reason to override this method
     * unless you're creating a specialized version of this class.
     * @param startFuture
     * @throws Exception
     */
    @Override
    public void start(Future<Void> startFuture) throws Exception {
        Future<Boolean> deployFuture = Future.future();
        this.setName(config().getString("verticleName"));

        logger.info("Starting verticle " + this.getName());

        deployDependencies(deployFuture);

        deployFuture.compose(r -> {
            try {
                logger.debug("Running pre-Initializers...");
                runPreInitializers();

                logger.debug("Running start() for " + this.getName());
                start();
                logger.debug("Started " + this.getName());

                //Create an eventbus consumer using this object's name as the address...
                MessageConsumer<JsonObject> consumer = vertx.eventBus().consumer(this.getName());
                consumer.handler(handleMessage -> {
                    JsonObject reply;

                    handleMessage.body().getString("type");
                    reply = handleMessage(handleMessage.body());
                    handleMessage.reply(reply);
                });

                logger.debug("Running post-Initializers...");
                runPostInitializers();

                startFuture.complete();
                logger.info("Verticle " + this.getName() + " started!");
            }
            catch(Exception e) {
                logger.error("Verticle " + this.getName() + " not started due to the following:", e);
                startFuture.fail(e);
            }
        }, null);
    }

    /**************** Start of Scheduler Methods ***************/

    /**
     * This is probably the only method you need to implement if this object is registered with the
     * Quartz Scheduler. Please see the Quartz documentation for a description if this method and
     * the method below.
     * @param trigger
     * @param context
     */
    @Override
    public void triggerFired(Trigger trigger, JobExecutionContext context) {

    }

    @Override
    public boolean vetoJobExecution(Trigger trigger, JobExecutionContext context) {
        return false;
    }

    @Override
    public void triggerMisfired(Trigger trigger) {

    }

    @Override
    public void triggerComplete(Trigger trigger, JobExecutionContext context, Trigger.CompletedExecutionInstruction triggerInstructionCode) {

    }

    @Override
    public void jobToBeExecuted(JobExecutionContext context) {

    }

    @Override
    public void jobExecutionVetoed(JobExecutionContext context) {

    }

    @Override
    public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {

    }

    /**************** End of Scheduler Methods ***************/

    /**
     * This method will get called when this object receives a message via the eventbus. The message and the result
     * depends on the implementation of this method.
     * @param message
     * @return
     */
    protected JsonObject handleMessage(JsonObject message) {
        JsonObject result = new JsonObject();
        return result;
    }

    /**
     * Deploy all dependent verticles. If ALL are successful, then this verticle can start.
     * @param future
     */
    protected void deployDependencies(Future<Boolean> future) {
        JsonArray dependents = config().getJsonArray("dependents");

        AtomicInteger noOfDeps      = new AtomicInteger(dependents.getList().size());

        if (noOfDeps.get() > 0) {
            AtomicInteger depCounter    = new AtomicInteger(0);
            AtomicInteger depDeployed   = new AtomicInteger(0);
            AtomicBoolean isDeployError = new AtomicBoolean(false);

            MessageConsumer<JsonObject> deployProgress = Application.globalVertx.eventBus().localConsumer(uuid);

            deployProgress.handler(deployEvent -> {
                if (!deployEvent.body().getBoolean("success")) {
                    isDeployError.set(true);
                }
                else {
                    depDeployed.incrementAndGet();
                }
                depCounter.incrementAndGet();

                if (depDeployed.get() == noOfDeps.get()) {
                    logger.debug("Completing dependent deployments for " + this.getName());
                    logger.debug("====> Future for " + this.getName() + " is " + (future != null?"not null":"null"));
                    future.complete(true);
                }

                if (depCounter.get() == noOfDeps.get() && depDeployed.get() != noOfDeps.get()) {
                    future.fail(new Throwable(deployEvent.body().getString("cause")));
                }
            });

            for (Object dependent : dependents) {
                JsonObject configObject = (JsonObject)dependent;
                boolean deploy = (configObject.getString("instantiate")!=null?Boolean.valueOf(configObject.getString("instantiate")):false);

                if (deploy) deployDependency(configObject);
            }
        }
        else {
            future.complete(true);
        }
    }

    protected void deployDependency(JsonObject configObject) {
        JsonObject verticleInformation = new JsonObject();
        String serviceName = configObject.getString("verticleName");
        verticleInformation.put("serviceName", serviceName);

        try {
            if (!configObject.isEmpty()) {
                String instanceClass = configObject.getString("instanceClass");
                verticleInformation.put("instanceClass", instanceClass);

                logger.info("Deploying verticle: " + instanceClass);
                boolean isWorker = WORKER.equals(configObject.getString("instanceType"));
                logger.info("isWorker = " + isWorker);

                vertx.deployVerticle(
                        instanceClass,
                        new DeploymentOptions().setConfig(configObject)
                                .setWorker(isWorker),
                        deployResult -> {
                            JsonObject result = new JsonObject();

                            if (deployResult.succeeded()) {
                                result.put("success", true);
//                                verticleInformation.put("deploymentId", deployResult.result());
                                logger.info(serviceName + " deployed...");

//                                persistVerticleInformation(serviceName, verticleInformation);
                            }
                            else {
                                result.put("success", false);
                                result.put("cause", "Deployment failed!!");
                                logger.error(serviceName + " not deployed...", deployResult.cause());
                            }

                            Application.globalVertx.eventBus().send(this.uuid, result);
                        }
                );
            }
            else {
                JsonObject result = new JsonObject();
                result.put("success", false);
                result.put("cause", new Throwable("No configuration!"));
                Application.globalVertx.eventBus().send(this.uuid, result);
                logger.error(serviceName + " not deployed!");
            }
        } catch (Exception e) {
            logger.error("Could not load " + serviceName + ".properties file...", e);
            e.printStackTrace();
        }
    }

    /**
     * Run all initializers BEFORE starting this verticle...
     *
     * This is based on the verticle's configuration in <code>verticles.json</code> file.
     */
    protected void runPreInitializers() {
        JsonArray inits = config().getJsonArray("preInits");
        runInitializers(inits);
    }

    /**
     * Run all initializers AFTER starting this verticle...
     *
     * This is based on the verticle's configuration in <code>verticles.json</code> file.
     */
    protected void runPostInitializers() {
        JsonArray inits = config().getJsonArray("postInits");
        runInitializers(inits);
    }

    protected void runInitializers(JsonArray inits) {
        if (inits != null) {
            for (Object initObject : inits) {
                JsonObject init = (JsonObject)initObject;
                Context initContext = new Context();
                initContext.putAll(init.getJsonObject("config").getMap());
                initContext.put("vertx", vertx);

                try {
                    AbstractInitializer initializer = (AbstractInitializer)Class.forName(init.getString("instanceClass")).newInstance();
                    initializer.init(initContext);
                } catch (Exception e) {
                    //Just log the error
                    logger.error("Could not initialize " + init.getString("instanceClass"),e);
                }
            }
        }
    }

    @Deprecated
    protected void undeployVerticle(String serviceName, Handler<AsyncResult<JsonObject>> handler) {
        vertx.executeBlocking(block -> {
            Future<JsonObject> future = Future.future();

            logger.debug("In undeployVerticle...");

            if (vertx.isClustered()) {

                logger.debug("We are in a cluster...");

                vertx.sharedData().<String, JsonObject>getClusterWideMap("verticles", verticlesResult -> {
                    AsyncMap<String, JsonObject> verticles = verticlesResult.result();
                    verticles.get(serviceName, getResult -> {
                        if (getResult.succeeded()) {
                            JsonObject verticleInformation = getResult.result();
                            vertx.undeploy(verticleInformation.getString("deploymentId"), undeployResult -> {
                                future.complete(verticleInformation);
                                handler.handle(future);
                            });
                        }
                        else {
                            future.fail("Nothing to Undeployed");
                            handler.handle(future);
                        }
                    });
                });
            }
            else {
                LocalMap<String, JsonObject> verticles = vertx.sharedData().<String, JsonObject>getLocalMap("verticles");
                JsonObject verticleInformation = verticles.get(serviceName);
                vertx.undeploy(verticleInformation.getString("deploymentId"), undeployResult -> {
                    future.complete(verticleInformation);
                    handler.handle(future);
                });
            }
        }, result -> {

        });
    }

    @Deprecated
    protected void persistVerticleInformation(String serviceName, JsonObject verticleInformation) {
        if (vertx.isClustered()) {
            vertx.sharedData().<String, JsonObject>getClusterWideMap("verticles", verticlesResult -> {
                AsyncMap<String, JsonObject> verticles = verticlesResult.result();
                verticles.put(serviceName, verticleInformation, putResult -> {
                    //I'm going to trust that the object is stored...
                });
            });
        }
        else {
            LocalMap<String, JsonObject> verticles = vertx.sharedData().getLocalMap("verticles");
            verticles.put(serviceName, verticleInformation);
        }
    }
}
