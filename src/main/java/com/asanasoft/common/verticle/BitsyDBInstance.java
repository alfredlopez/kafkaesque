package com.asanasoft.common.verticle;

import com.lambdazen.bitsy.BitsyGraph;
import com.lambdazen.bitsy.BitsyIsolationLevel;
import com.lambdazen.bitsy.wrapper.BitsyAutoReloadingGraph;
import com.asanasoft.common.init.impl.Environment;
import com.asanasoft.common.service.graphdb.BitsyDBService;
import com.asanasoft.common.service.graphdb.DBConstants;
import com.asanasoft.common.service.graphdb.GraphDBService;
import com.asanasoft.common.service.store.*;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ServiceBinder;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.io.graphml.GraphMLIo;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Created by lopealf on 6/12/17.
 * This verticle registers the BitsyGraph service. It gets deployed by the main verticle, JumbotronApplication.
 */

public class BitsyDBInstance extends DefaultQuartzVerticle {
    private Logger logger = LoggerFactory.getLogger(BitsyDBInstance.class);
    private Graph graph;
    private StoreService store = null;
    private String localBitsyAddress = "Bitsy-local";
    private Environment env = null;

    public BitsyDBInstance() {
        super();
        env = Environment.getInstance();
    }

    @Override
    public void start() throws Exception {
        logger.info("Starting Bitsy Instance...");

        registerWithScheduler();
        configureStore();

        /**
         * openDatabase takes a database name as a parameter even though BitsyGraph only handles one DB.
         * There are plans for Bitsy to manage multiple DBs, so this is in preparation for that.
         */
        openDatabase(env.getString("dbName"), res -> {
            /**
             * Currently, only register the service if it succeeded in connecting/opening the database...
             * Later, the service will do the connections...
             */
            if (res.succeeded()) {
                logger.info("Database open...");
                Graph graphDB = res.result();

                if (registerService()) {
                    if (!graphDB.vertices().hasNext()) {
                        //First see if there's a data store, if so, then load the latest...
                        try {
                            File bitsyDataFile = new File(env.getString("GraphDBBackup") + "bitsy-data.xml");

                            if (bitsyDataFile.exists()) {
                                bitsyDataFile.delete();
                            }

                            String environment = env.getString("RUNNING_ENV");

                            getStore().getLatest(environment + "-bitsy-data.xml", getLatestResult -> {
                                if (getLatestResult.succeeded()) {
                                    logger.debug("Bitsy data extracted from Store");

                                    /**
                                     * The transaction may have succeeded, but there may be no data...
                                     */
                                    InputStream bitsyStoredData = getLatestResult.result();
                                    if (bitsyStoredData != null) {
                                        String baseDir = env.getString("GraphDBBackup");
                                        StoreServiceFactory storeServiceFactory = new StoreServiceFactory();
                                        FileStoreService fileStore = (FileStoreService)storeServiceFactory.getStoreService(StoreService.FILE_STORE, "BitsyFileData");
                                        fileStore.setSource(baseDir);
                                        fileStore.setDestination(baseDir);

                                        /**
                                         * Store the backup onto the file Store...
                                         */
                                        fileStore.store("bitsy-data.xml", "", bitsyStoredData, storeResult -> {

                                            try {
                                                /**
                                                 * Load Bitsy database from the file Store...
                                                 */
                                                GraphMLIo.Builder builder = new GraphMLIo.Builder();
                                                graph.io(builder).readGraph(baseDir + "bitsy-data.xml");

                                                /**
                                                 * Bitsy's author generates GUIDs for vertices' ids, I'm not sure if
                                                 * the backup writes these ids to the file, but if it does, they should
                                                 * be read back in. However, Bitsy generates NEW GUIDs on a restore.
                                                 * Possibly because the author is expecting a clash, even though they
                                                 * *are* GUIDs!
                                                 *
                                                 * So we need to sync the vertex id and our internal id (_id).
                                                 */

                                                List<Vertex> vertices = graph.traversal().V().toList();

                                                if (!graph.tx().isOpen()) {
                                                    graph.tx().open();
                                                }

                                                for (Vertex v : vertices) {
                                                    logger.trace("vertex id = " + v.id().toString());
                                                    v.property(DBConstants.INTERNAL_ID, v.id().toString());
                                                    logger.trace("vertex _id = " + v.value(DBConstants.INTERNAL_ID));
                                                }

                                                graph.tx().commit();

                                                logger.debug("Bitsy database loaded from file");
                                            }
                                            catch (IOException e) {
                                                logger.error("An error occurred reading in Bitsy data from file: ", e);
                                            }
                                        });
                                    }
                                }
                                else {
                                    logger.error("An error occurred getting Bitsy data from store: ", getLatestResult.cause());
                                }
                            });
                        } catch (Exception e) {
                            logger.error("An error occurred reading Bitsy data from file: ", e);
                        }
                    }
                }
            }
            else {
                logger.error("An error occurred starting BitsyGraph service: ", res.cause());
            }
        });
    }

    protected void registerWithScheduler() {
        vertx.setTimer(3000, timerResult -> {
            DeliveryOptions options = new DeliveryOptions();
            options.setCodecName("TriggerListener");
            vertx.eventBus().send("Scheduler.registerListener", this, options);
        });
    }


    @Override
    public void stop(Future<Void> stopFuture) throws Exception {
        logger.info("Shutting down Bitsy...");
        graph.close();
        super.stop(stopFuture);
    }

    protected void backupGraphDB(Date timestamp) throws Exception {
        logger.info("Backing up Bitsy...");

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd'-'HHmm");

        StoreService storeService = getStore();
        String backupFolder = env.getString("GraphDBBackup");
        String label = storeService.dateToString(timestamp);
        String environment = env.getString("RUNNING_ENV");
        String fileName = label + "-" + environment + "-bitsy-data.xml";
        String backupFile = backupFolder + fileName;
        logger.debug("Bitsy Backup Folder:>>>>>>" +  backupFile);

        GraphMLIo.Builder builder = new GraphMLIo.Builder();
        graph.io(builder).writeGraph(backupFile);

        logger.debug("Bitsy file written to disk...preparing to store...");

        /**
         * Store the backup file to an external source...
         */

        /**
         * A StoreService takes an InputStream as input, so let's create one from the backup file...
         */
        File bitsyData = new File(backupFile);
        InputStream bitsyPayload = new FileInputStream(bitsyData);

        /**
         * ...now get the store and store it using a file name as the "store name". We *can* provide a
         * label, like "ver 1", etc, but by omitting it, the store will use a timestamp as a label...
         */
        getStore().store(environment + "-bitsy-data.xml", label, bitsyPayload, storeResult -> {
            if (storeResult.succeeded()) {
                //Delete the temp file...
                boolean deleteSuccess1stAttempt = bitsyData.delete();
                if (deleteSuccess1stAttempt) {
                    logger.info("1st attempt to delete Bitsy temp file succeeded");
                } else {
                    logger.info("Failed 1st attempt to delete Bitsy temp file " + backupFile);
                    try {
                        bitsyPayload.close();
                        Path p = FileSystems.getDefault().getPath(backupFolder, fileName);
                        Files.delete(p);
                        logger.info("2nd attempt to delete Bitsy temp file succeeded");
                    } catch (Exception e) {
                        logger.error("Failed 2nd attempt to delete Bitsy temp file! Msg: " + e.getMessage());
                    }
                }

                logger.info("Bitsy Successfully stored!");
                logger.info("Deleting yesterday's backup...");

                Calendar yesterday = Calendar.getInstance();
                yesterday.setTime(timestamp);
                yesterday.add(Calendar.DATE, -1);

                String newLabel = sdf.format(yesterday.getTime());

                getStore().delete(environment + "-bitsy-data.xml", newLabel, deleteResult -> {
                    if (deleteResult.failed()) {
                        logger.error("An error occurred deleting the sftp file " + newLabel + "-bitsy-data.xml", deleteResult.cause());
                    }
                });
            }
            else {
                logger.error("Error storing Bitsy data:", storeResult.cause());
            }
        });
    }

    protected void openDatabase(String dbName, Handler<AsyncResult<Graph>> handler) {
        //Wrap the method in an executeBlocking to avoid blocking the event bus...
        vertx.executeBlocking(f -> {
            try {
                Path dbPath = Paths.get(env.getString("dbPath") + dbName);
                logger.debug("Bitsy Path >>>>>" + env.getString("dbPath")  + "  dbName:" + dbName+ " db" + dbPath.getFileName());
                if (!dbPath.toFile().exists()) {
                    dbPath.toFile().mkdirs();
                }

                BitsyGraph bitsyGraphOrig = new BitsyGraph(dbPath);
                bitsyGraphOrig.setDefaultIsolationLevel(BitsyIsolationLevel.READ_COMMITTED);
                bitsyGraphOrig.setReorgFactor(3);
                Graph bitsyGraph = new BitsyAutoReloadingGraph(bitsyGraphOrig);
                f.complete(bitsyGraph);
            }
            catch (Exception e) {
                f.fail(e);
            }
        }, r -> {
            Future<Graph> future = Future.future();

            if (r.succeeded()) {
                setGraph((Graph)r.result());
                future.complete(getGraph());
            }
            else {
                logger.error("An error occurred opening BitsyGraph database: ", r.cause());
                future.fail(r.cause());
            }

            handler.handle(future);
        });
    }

    protected boolean registerService() {
        boolean result = true;

        try {
            GraphDBService bitsyDBService = BitsyDBService.create(getGraph(), vertx);

            // Register system-wide handler
            MessageConsumer serviceBinder = new ServiceBinder(vertx)
                    .setAddress("Bitsy")
                    .register(GraphDBService.class, bitsyDBService);

        }
        catch (Exception e) {
            logger.error("Failed to register service BitsyDB:", e);
            result = false;
        }
        return result;
    }

    protected void configureStore() {
        logger.debug("Configuring Store...");

        JsonObject scriptConfiguration = config().getJsonObject("config");
        StoreServiceFactory storeFactory = new StoreServiceFactory();
        StoreConfigurator configurator = new StoreConfigurator();

        configurator.configure(scriptConfiguration.getString("backupStoreConfig"));
        store = storeFactory.getStoreService(scriptConfiguration.getString("backupStore"), "BackupStore");
        store.configure(configurator);

        logger.debug("Store configured...");
    }


    protected StoreService getStore() {
        if (store == null) {
            configureStore();
        }

        return store;
    }

    public Graph getGraph() {
        return graph;
    }

    public void setGraph(Graph graph) {
        this.graph = graph;
    }

    /**
     * Quartz hooks...
     */

    @Override
    public void jobToBeExecuted(JobExecutionContext jobExecutionContext) {

    }

    @Override
    public void jobExecutionVetoed(JobExecutionContext jobExecutionContext) {

    }

    @Override
    public void jobWasExecuted(JobExecutionContext jobExecutionContext, JobExecutionException e) {

    }

    @Override
    public String getName() {
        return "BitsyDBInstance";
    }

    @Override
    public void triggerFired(Trigger trigger, JobExecutionContext jobExecutionContext) {
        String jobName = jobExecutionContext.getJobDetail().getKey().getName();
        String jobGroup = jobExecutionContext.getJobDetail().getKey().getGroup();

        logger.trace("Received a trigger:");
        logger.trace("  jobName = "  + jobName);
        logger.trace("  jobGroup = " + jobGroup);

        if ("GraphDBBackup".equals(jobName)) {
            try {
                logger.debug("Backing up Bitsy...");

                backupGraphDB(new Date());
            } catch (Exception e) {
                logger.error("An error occurred backing up Bitsy:", e);
            }
        }
    }

    @Override
    public boolean vetoJobExecution(Trigger trigger, JobExecutionContext jobExecutionContext) {
        return false;
    }

    @Override
    public void triggerMisfired(Trigger trigger) {

    }

    @Override
    public void triggerComplete(Trigger trigger, JobExecutionContext jobExecutionContext, Trigger.CompletedExecutionInstruction completedExecutionInstruction) {

    }
}
