package com.asanasoft.common.verticle;

import com.asanasoft.common.init.impl.DataSources;
import de.danielbechler.diff.ObjectDiffer;
import de.danielbechler.diff.ObjectDifferBuilder;
import de.danielbechler.diff.node.DiffNode;
import de.danielbechler.diff.node.Visit;
import io.vertx.core.json.JsonObject;
import org.quartz.JobExecutionContext;
import org.quartz.Trigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import schemacrawler.schema.Catalog;
import schemacrawler.schemacrawler.SchemaCrawlerOptions;
import schemacrawler.schemacrawler.SchemaCrawlerOptionsBuilder;
import schemacrawler.utility.SchemaCrawlerUtility;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * SchemaCrawlerInstance monitors changes on source databases and performs the changes to all monitoring targets
 */
public class SchemaCrawlerInstance extends DefaultQuartzVerticle {
    private Logger logger = LoggerFactory.getLogger(SchemaCrawlerInstance.class);
    private String targetUrlConnectionString;
    private String sourceDatasourceName;
    private String targetDatasourceName;
    private DataSource sourceDatasource;
    private DataSource targetDatasource;
    private ObjectDiffer differ;
    private JsonObject schemaCrawlerConfiguration;

    public String getSourceDatasourceName() {
        return sourceDatasourceName;
    }

    public void setSourceDatasourceName(String sourceDatasourceName) {
        this.sourceDatasourceName = sourceDatasourceName;
    }

    public String getTargetDatasourceName() {
        return targetDatasourceName;
    }

    public void setTargetDatasourceName(String targetDatasourceName) {
        this.targetDatasourceName = targetDatasourceName;
    }

    @Override
    public void start() throws Exception {
        schemaCrawlerConfiguration = config().getJsonObject("config");

        setSourceDatasourceName(schemaCrawlerConfiguration.getString("sourceDatasource"));
        setTargetDatasourceName(schemaCrawlerConfiguration.getString("targetDatasource"));

        compare();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
    }

    @Override
    public void triggerFired(Trigger trigger, JobExecutionContext context) {
        super.triggerFired(trigger, context);
    }

    @Override
    protected JsonObject handleMessage(JsonObject message) {
        return super.handleMessage(message);
    }

    protected void compare() {
        vertx.executeBlocking(e -> {
            DataSource sourceDatasource = DataSources.getInstance().getDataSource(getSourceDatasourceName());
            DataSource targetDatasource = DataSources.getInstance().getDataSource(getTargetDatasourceName());

            compare(sourceDatasource, targetDatasource, schemaCrawlerConfiguration.getString("sourceSchemaName"), schemaCrawlerConfiguration.getString("targetSchemaName"));
            e.complete();
        }, r -> {

        });
    }

    protected void compare(DataSource source, DataSource target, String sourceSchemaName, String targetSchemaName) {
        SchemaCrawlerOptions schemaCrawlerOptions = SchemaCrawlerOptionsBuilder.newSchemaCrawlerOptions();

        try {
            Connection sourceConnection = source.getConnection();
            Connection targetConnection = target.getConnection();

            Catalog sourceCatalog = SchemaCrawlerUtility.getCatalog(sourceConnection, schemaCrawlerOptions);
            Catalog targetCatalog = SchemaCrawlerUtility.getCatalog(targetConnection, schemaCrawlerOptions);

            compare(sourceCatalog, targetCatalog);
        }
        catch (Exception e) {

        }
    }

    protected void compare(Catalog source, Catalog target) {
        logger.info("Starting database compare...");

        DiffNode diff = getDiffer().compare(source, target);

        diff.visit(new DiffNode.Visitor() {
            @Override
            public void node(DiffNode node, Visit visit) {
                logger.debug(node.toString());
            }
        });
    }

    protected Boolean createOrAlterTable(JsonObject tableConfig) {
        Boolean result = Boolean.FALSE;
        return result;
    }

    protected ObjectDiffer getDiffer() {
        if (differ == null) {
            ObjectDifferBuilder result = ObjectDifferBuilder.startBuilding();

            result.filtering().omitNodesWithState(DiffNode.State.UNTOUCHED);
            result.filtering().omitNodesWithState(DiffNode.State.CIRCULAR);
            result.inclusion().exclude().propertyName("fullName");
            result.inclusion().exclude().propertyName("parent");
            result.inclusion().exclude().propertyName("exportedForeignKeys");
            result.inclusion().exclude().propertyName("importedForeignKeys");
            result.inclusion().exclude().propertyName("deferrable");
            result.inclusion().exclude().propertyName("initiallyDeferred");

            differ = result.build();
        }

        return differ;
    }
}
