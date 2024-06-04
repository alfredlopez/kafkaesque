package com.asanasoft.common.init.impl;

import com.asanasoft.common.Application;
import com.asanasoft.common.Context;
import com.asanasoft.common.init.AbstractInitializer;
import com.mchange.v2.c3p0.ComboPooledDataSource;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;

public class DataSources extends AbstractInitializer {
    private static Logger                       logger      = LoggerFactory.getLogger(DataSources.class);
    private JsonObject                          jdbcConfig  = null;
    private Context dataSources = new Context();
    private JsonObject dataSourcesDef;
    private static DataSources instance = null;
    private final static String BINDING_CONTEXT = "dataSources";

    public DataSources() {
    }

    public static DataSources getInstance() {
        if (instance == null) {
            instance = new DataSources();
            instance.init(null);
        }

        return instance;
    }

    @Override
    public boolean init(Context context) {
        InitialContext initialContext = null;

        try {
            initialContext = new InitialContext();
            initialContext.bind("dataSourceObj", this);
        } catch (NamingException e) {
            logger.error("Could not bind to JNDI", e);
        }

        boolean result = true;

        Environment environment = Environment.getInstance();
        JsonObject dataSourcesDefEnv = new JsonObject();

        for (String key : environment.getResult().keySet()) {
            if (key.endsWith("_dataSourceName")) {
                String dataSourceName = environment.getString(key);
                JsonObject dataSourceJson = new JsonObject();
                dataSourceJson.put("jdbcUrl", environment.getString(dataSourceName + "_jdbcUrl"));
                dataSourceJson.put("username", environment.getString(dataSourceName + "_username"));
                dataSourceJson.put("password", environment.getString(dataSourceName + "_password"));
                dataSourceJson.put("jdbcDriver", environment.getString(dataSourceName + "_jdbcDriver"));

                dataSourcesDefEnv.put(dataSourceName, dataSourceJson);
            }
        }

        if (!dataSourcesDefEnv.isEmpty()) {
            getDataSourcesDef().mergeIn(dataSourcesDefEnv);
        }

        try {
            for (String datasourceName : getDataSourcesDef().getMap().keySet()) {
                //Call getConnection for all datasources to create an instance of each...
                DataSource dataSource = getDataSource(datasourceName);

                //if there's a JNDI provider, bind the datasource object...
                if (initialContext != null) {
                    initialContext.bind(BINDING_CONTEXT + "/" + datasourceName, dataSource);
                }
            }
        }
        catch (Exception e) {
            result = false;
            logger.error("An error occurred in init:", e);
        }

        return result;
    }

    public JsonObject getDataSourcesDef() {
        if (dataSourcesDef == null) {
            dataSourcesDef = new JsonObject();
        }

        return dataSourcesDef;
    }

    public static void setDataSourcesDef(JsonObject dataSourcesDef) {
        dataSourcesDef = dataSourcesDef;
    }

    public void getSQLClient(String dataSourceName, Handler<AsyncResult<SQLClient>> handler) {
        logger.debug("Getting " + dataSourceName);

        Vertx vertx = Application.globalVertx;
        Future<SQLClient> sqlFuture = Future.future();
        Environment env = Environment.getInstance();
        String jdbcClass = null;
        String jdbcClassPropertyName = dataSourceName + "_jdbcDriver";

        JsonObject jdbcConfig = getDataSourceConfig(dataSourceName);

        if (jdbcConfig != null) {
            jdbcClass = jdbcConfig.getString("jdbcDriver");
        }
        else if (env.getValue(jdbcClassPropertyName) != null) {
            jdbcClass = (String)env.getValue(jdbcClassPropertyName);
        } else {
            logger.error("Error - No jdbc driver property named " + jdbcClassPropertyName);
        }

        logger.debug("jdbcClass = " + jdbcClass);

        /**
         * Register JDBC driver...
         */
        try {
            DriverManager.registerDriver((Driver)Class.forName(jdbcClass).newInstance());
        }
        catch (Exception e) {
            logger.error("An error occurred registering Driver:", e);
        }

        try {
            if (jdbcConfig.getString("url") == null) {
                jdbcConfig.put("url", jdbcConfig.getString("jdbcUrl"));
            }
            if (jdbcConfig.getString("user") == null) {
                jdbcConfig.put("user", jdbcConfig.getString("username"));
            }

            logger.debug("Getting SQLClient with " + jdbcConfig.encodePrettily());

            SQLClient client = JDBCClient.createShared(vertx, jdbcConfig, dataSourceName);
            sqlFuture.complete(client);
        }
        catch (Exception e) {
            logger.error("An error occurred in getSQLClient:", e);
            sqlFuture.fail(e);
        }

        handler.handle(sqlFuture);
    }

    public Connection getConnection(String dataSourceName) {
        Connection result = null;

        try {
            result = this.getDataSource(dataSourceName).getConnection();
        }
        catch (Exception e) {
            logger.error("An error occurred getting a connection for data source " + dataSourceName);
        }
        return result;
    }

    public DataSource getDataSource(String dataSourceName) {
        logger.debug("in getConnection...");

        Vertx vertx = Application.globalVertx;

        logger.debug("Getting dataSources localMap...");

        JsonObject  jdbcConfig = getDataSourceConfig(dataSourceName);
        DataSource result = null;

        try {
            if (!dataSources.containsKey(dataSourceName)) {
                logger.debug("Creating a protocol droid for " + dataSourceName);

                ComboPooledDataSource cpds = new ComboPooledDataSource();
                cpds.setDataSourceName(dataSourceName);
                cpds.setDriverClass(jdbcConfig.getString("driver_class"));
                cpds.setJdbcUrl(jdbcConfig.getString("jdbcUrl"));
                cpds.setUser(jdbcConfig.getString("username"));
                cpds.setPassword(jdbcConfig.getString("password"));

                logger.debug("Storing droid in dataSources localmap...");

                dataSources.put(dataSourceName, cpds);

                result = cpds;
            }
            else {
                logger.debug("Getting protocol droid from dataSources localMap...");

                result = (DataSource)dataSources.get(dataSourceName);
            }
        }
        catch (Exception e) {
            logger.error("An error occurred getting data source:" + dataSourceName, e);
        }

        return result;
    }

    protected JsonObject getDataSourceConfig(String dataSourceName) {
        logger.debug("Getting dataSources config for " + dataSourceName);

        JsonObject  jdbcConfig = dataSourcesDef.getJsonObject(dataSourceName);

        if (jdbcConfig != null) {
            jdbcConfig.put("maxPoolSize"    ,10);
            jdbcConfig.put("queryTimeout"   ,10000); //10 seconds
        }

        return jdbcConfig;
    }
}
