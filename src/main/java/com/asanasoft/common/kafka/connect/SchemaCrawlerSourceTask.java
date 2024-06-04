package com.asanasoft.common.kafka.connect;

import com.asanasoft.common.init.impl.DataSources;
import io.vertx.core.json.JsonObject;
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.source.SourceTask;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import schemacrawler.schema.*;
import schemacrawler.schemacrawler.*;
import schemacrawler.tools.databaseconnector.DatabaseConnectionSource;
import schemacrawler.tools.databaseconnector.SingleUseUserCredentials;
import schemacrawler.utility.SchemaCrawlerUtility;

import static com.asanasoft.common.kafka.connect.SchemaCrawlerConstants.*;

public class SchemaCrawlerSourceTask extends SourceTask {
    private String schemaName;
    private String topicName;
    private String datasourceName;

    @Override
    public String version() {
        return null;
    }

    @Override
    public void start(Map<String, String> map) {
        this.schemaName     = map.get(SCHEMA_NAME);
        this.topicName      = map.get(TOPIC_NAME);
        this.datasourceName = map.get(DATASOURCE_NAME);
    }

    @Override
    public List<SourceRecord> poll() throws InterruptedException {
        ArrayList<SourceRecord> records = new ArrayList<>();

        final SchemaCrawlerOptionsBuilder optionsBuilder = SchemaCrawlerOptionsBuilder
                .builder()
                // Set what details are required in the schema - this affects the
                // time taken to crawl the schema
                .withSchemaInfoLevel(SchemaInfoLevelBuilder.detailed())
                .includeSchemas(new RegularExpressionInclusionRule(this.schemaName));
        final SchemaCrawlerOptions options = optionsBuilder.toOptions();

        try {
            Connection connection = DataSources.getInstance().getConnection(datasourceName);
            // Get the schema definition
            final Catalog catalog = SchemaCrawlerUtility
                    .getCatalog(connection, options);
            Map<String, Object> schemaOffset;
            if (context != null && context.offsetStorageReader() != null) {
                schemaOffset = context.offsetStorageReader().offset(Collections.singletonMap(SCHEMA_NAME, this.schemaName));

                String lastSchemaString = "";

                if (schemaOffset != null) {
                    lastSchemaString = (String)schemaOffset.get(LAST_SCHEMA_SNAPSHOT);
                }
            }
        } catch (SchemaCrawlerException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public void stop() {

    }
}
