package com.asanasoft.common.kafka.connect;

import com.asanasoft.common.Context;
import com.asanasoft.common.model.connector.impl.schemacrawler.SchemaCrawlerDataStreamConnector;
import com.asanasoft.common.model.listener.StreamListener;
import io.vertx.core.json.JsonObject;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.Task;
import org.apache.kafka.connect.source.SourceConnector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.asanasoft.common.kafka.connect.SchemaCrawlerConstants.*;

public class SchemaCrawlerSourceConnector extends SourceConnector implements StreamListener {
    private String schema;
    private String topic;
    private String cron;
    private String schemaCrawlerName;
    private String datasourceName;

    @Override
    public void handle(JsonObject context) {

    }

    @Override
    public void start(Map<String, String> props) {
        this.schema             = props.get(SCHEMA_NAME);
        this.topic              = props.get(TOPIC_NAME);
        this.cron               = props.get(UPDATE_SCHEDULE);
        this.datasourceName     = props.get(DATASOURCE_NAME);
        this.schemaCrawlerName  = props.get(CRAWLER_NAME);
    }

    @Override
    public Class<? extends Task> taskClass() {
        return SchemaCrawlerSourceTask.class;
    }

    @Override
    public List<Map<String, String>> taskConfigs(int maxTasks) {
        ArrayList<Map<String, String>> configs = new ArrayList<>();
        Map<String, String> config = new HashMap<>();
        config.put(SCHEMA_NAME,     this.schema);
        config.put(TOPIC_NAME,      this.topic);
        config.put(DATASOURCE_NAME, this.datasourceName);

        configs.add(config);

        return configs;
    }

    @Override
    public void stop() {

    }

    @Override
    public ConfigDef config() {
        return null;
    }

    @Override
    public String version() {
        return null;
    }

    protected JsonObject getSchemaDiffs() {
        JsonObject result = new JsonObject();
        return result;
    }
}
