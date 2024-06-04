package com.asanasoft.common.verticle;

//import com.jcabi.aspects.Loggable;
import com.asanasoft.common.Context;
import com.asanasoft.common.init.impl.DataSources;
import com.asanasoft.common.init.impl.Environment;
import com.asanasoft.common.init.impl.H2Initializer;
import com.asanasoft.common.service.store.FileStoreService;
import com.asanasoft.common.service.store.StoreService;
import com.asanasoft.common.service.store.StoreServiceFactory;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.MessageConsumer;
import org.h2.tools.Server;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class H2DBInstance extends AbstractVerticle implements TriggerListener, JobListener {
    private Logger logger = LoggerFactory.getLogger(H2DBInstance.class);
    private Server h2Server;
    private StoreService store = null;
    private long   backupPeriod = (60000 * 60); //1 hour
    private boolean isReplicating = false;

    @Override
    public void start() throws Exception {
        try {
            String prop = this.config().getString("serverOptions");
            String[] args = prop.split("\\|");

            logger.info("prop = " + prop);

            h2Server = Server.createTcpServer(args).start();

            logger.info("status     = " + h2Server.getStatus());
            logger.info("url        = " + h2Server.getURL());
            logger.info("port       = " + String.valueOf(h2Server.getPort()));
            logger.info("service    = " + h2Server.getService().getName());

            H2Initializer h2Initializer = new H2Initializer();
            Context context = new Context();
            context.putValue("vertx", this.vertx);
            context.putValue("dataSourceName", "JTCards");
            context.putValue("H2Folder", this.config().getString("dbPath"));
            h2Initializer.init(context);

            /**
             * Register with the Scheduler to receive backup triggers...
             */
            DeliveryOptions options = new DeliveryOptions();
            options.setCodecName("TriggerListener");
            vertx.eventBus().send("Scheduler.registerListener", this, options);
        }
        catch (Exception e) {
            logger.error("Server not started: ", e);
        }
    }

    @Override
    public void stop() throws Exception {
        logger.info("Stopping H2...");
        h2Server.stop();
        h2Server = null;
        Server.shutdownTcpServer("tcp://localhost","",true, true);
    }

    protected void backup(Date timestamp) {
        Environment env = Environment.getInstance();
        String datasourceToBackup = env.getString("datasourceToBackup");

        try {
            StoreService storeService = getStore();
            String backupFolder = env.getString("GraphDBBackup");
            String label = storeService.dateToString(timestamp);
            String environment = env.getString("RUNNING_ENV");
            String fullBackupFile = backupFolder + label + "-" + environment + "-h2-data.xml";

            Connection connection = DataSources.getInstance().getConnection(datasourceToBackup);

            try {
                Statement statement = connection.createStatement();
                statement.execute("SCRIPT SIMPLE TO '" + fullBackupFile + "'");
            } catch (SQLException e) {
                logger.error("An error occurred during SQL dump:", e);
            } finally {
                connection.close();
            }

            FileInputStream payload = new FileInputStream(new File(fullBackupFile));

            store.store(environment + "-h2-data.sql", label, payload, storeResult -> {
                File tempFile = new File(fullBackupFile);
                tempFile.delete();

                if (storeResult.failed()) {
                    logger.error("An error occurred storing H@:", storeResult.cause());
                }
                else {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd'-'HHmm");

                    logger.info("H2 Successfully stored!");
                    logger.info("Deleting yesterday's backup...");

                    Calendar yesterday = Calendar.getInstance();
                    yesterday.setTime(timestamp);
                    yesterday.add(Calendar.DATE, -1);

                    String newLabel = sdf.format(yesterday.getTime());

                    getStore().delete(environment + "-h2-data.xml", label, deleteResult -> {
                        if (deleteResult.failed()) {
                            logger.error("An error occurred deleting the sftp file " + newLabel + "-h2-data.xml", deleteResult.cause());
                        }
                    });
                }
            });
        } catch (Exception e) {
            logger.error("An error occurred creating H2 backup:", e);
        }
    }

    protected StoreService getStore() {
        if (store == null) {
            /**
             * ...get a store factory...
             */
            StoreServiceFactory storeServiceFactory = new StoreServiceFactory();

            /**
             * ...and ask for the default store...
             */
            store = storeServiceFactory.getDefaultStore("H2Data");
            store.setEncrypt(true);

            if (store instanceof FileStoreService) {
                ((FileStoreService) store).setSource(Environment.getInstance().getString("GraphDBBackup") + "temp/");
            }
        }

        return store;
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
        return "H2Instance";
    }

    @Override
    public void triggerFired(Trigger trigger, JobExecutionContext jobExecutionContext) {
        String jobName = jobExecutionContext.getJobDetail().getKey().getName();
        String jobGroup = jobExecutionContext.getJobDetail().getKey().getGroup();

        logger.trace("Received a trigger:");
        logger.trace("  jobName = "  + jobName);
        logger.trace("  jobGroup = " + jobGroup);
        isReplicating = "ReplicationService".equals(jobGroup);

        if ("GraphDBBackup".equals(jobName) && !isReplicating) {
            try {
                logger.debug("Backing up H2...");
                logger.debug("Backing up H2 temporarily commented out");
                //backup(new Date());
            } catch (Exception e) {
                logger.error("An error occurred backing up H2:", e);
            }
        }
        else if (isReplicating) {
            MessageConsumer<String> waitForReplication = vertx.eventBus().localConsumer("ReplicationStartupDone");
            waitForReplication.handler(repl -> {
                isReplicating = false;
                waitForReplication.unregister();
            });
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
