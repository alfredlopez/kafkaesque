package com.asanasoft.common.model.connector.impl.schemacrawler;

import com.asanasoft.common.Application;
import com.asanasoft.common.Context;
import com.asanasoft.common.init.impl.DataSources;
import com.asanasoft.common.model.connector.impl.AbstractDataStreamConnector;
import com.asanasoft.common.model.connector.impl.AbstractPolledDataStreamConnector;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import schemacrawler.crawl.SchemaCrawler;
import schemacrawler.schema.Catalog;
import schemacrawler.schema.Schema;
import schemacrawler.schemacrawler.*;
import schemacrawler.utility.SchemaCrawlerUtility;
import schemacrawler.tools.executable.SchemaCrawlerExecutable;
import static com.asanasoft.common.kafka.connect.SchemaCrawlerConstants.*;

import java.sql.Connection;

public class SchemaCrawlerDataStreamConnector extends AbstractPolledDataStreamConnector {
    private Logger logger = LoggerFactory.getLogger(SchemaCrawlerDataStreamConnector.class);
    private SchemaCrawlerOptions options;
    private SchemaRetrievalOptions retrievalOptions;
    private DataSources dataSources;
    private Connection sourceConnection;
    private Catalog sourceCatalog;
    private Boolean canCompare = false;
    private String JOB_NAME = "update_schema";
    private String JOB_GROUP = "schemaCrawler";
    private String TRIGGER_NAME = "crawl_schema";
    private String SchemaName;
    private String[] tableWhiteList;
    private String[] tableBlackList;

    @Override
    protected boolean canFire(String jobName, String jobGroup) {
        return (JOB_NAME.equals(jobName + "-" + getName()) && JOB_GROUP.equals(jobGroup));
    }

    @Override
    protected void doWork() {
        start(); //We'll just call start() since this is a single fire task...
    }

    @Override
    public void setCron(String cronString) {
        JsonObject message = new JsonObject();
        message.put("jobName",      JOB_NAME);
        message.put("jobGroup",     JOB_GROUP);
        message.put("triggerName",  TRIGGER_NAME);
        message.put("cron",         cronString);

        /**`
         * Register with the Scheduler to receive crawl triggers...
         */
        DeliveryOptions options = new DeliveryOptions();
        options.setCodecName("TriggerListener");
        Application.globalVertx.eventBus().send("Scheduler.registerListener", this, options);

        //...and set the scheduled crawl job.
        Application.globalVertx.eventBus().send("Scheduler", message);
    }

    @Override
    public void init(Context config) {
        dataSources = DataSources.getInstance();

        sourceConnection = dataSources.getConnection((String)config.getValue(DATASOURCE_NAME));

        // Create the options
        final SchemaCrawlerOptionsBuilder optionsBuilder = SchemaCrawlerOptionsBuilder
                .builder()
                // Set what details are required in the schema - this affects the
                // time taken to crawl the schema
                .withSchemaInfoLevel(SchemaInfoLevelBuilder.standard())
                ;
        options = optionsBuilder.toOptions();

        setName((String)config.getValue(CRAWLER_NAME));
        setCron((String)config.getValue(UPDATE_SCHEDULE));
    }

    @Override
    public void start() {
        if (canCompare) {
            // Get the schema definition
            try {
                sourceCatalog = SchemaCrawlerUtility.getCatalog(sourceConnection, options);
                for (Schema schema : sourceCatalog.getSchemas()) {
                    schema.getCatalogName();
                }
            }
            catch (Exception e) {
                logger.error("An error occurred getting the schemas:", e);
            }
        }
    }

    @Override
    public void stop() {
        canCompare = false;
    }

    protected void compareCatalogs(Catalog catalog, Catalog otherCatalog) {

    }
}
