package com.asanasoft.common.init.impl;

import com.asanasoft.common.Context;
import com.asanasoft.common.init.AbstractInitializer;

import com.asanasoft.common.model.dao.*;
import com.asanasoft.common.service.graphdb.BitsyDBService;
import graphql.GraphQL;
import graphql.scalars.ExtendedScalars;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.graphql.GraphQLHandler;
import io.vertx.ext.web.handler.graphql.GraphQLHandlerOptions;
import io.vertx.ext.web.handler.graphql.GraphiQLOptions;
import io.vertx.ext.web.handler.graphql.VertxDataFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.asanasoft.common.Application;
import com.asanasoft.common.Context;
import com.asanasoft.common.service.graphdb.GraphDBService;
import com.asanasoft.common.service.graphdb.GraphDBServiceFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring;
import static com.asanasoft.common.tools.CacheConstants.*;

public class GraphQLInitializer extends AbstractInitializer {
    private Logger logger = LoggerFactory.getLogger(GraphQLInitializer.class);
    private GraphDBService graphDatabaseService = null;
    private String cacheAddress = null;
    private JdbcDAO dao;

    @Override
    public boolean init(Context context) {
        boolean result = true;
        super.init(context);

        cacheAddress = (String)context.getValue("cacheAddress");

        Router router = (Router)this.context.getValue("router");
        if (router == null) {
            router = Router.router(vertx);
            context.putValue("router", router);
        }

        graphDatabaseService = BitsyDBService.createProxy(this.vertx, "Bitsy");

        GraphQLHandlerOptions options = new GraphQLHandlerOptions().setGraphiQLOptions(new GraphiQLOptions()
                        .setEnabled(true)
                );
        router.route("/graphql").handler(GraphQLHandler.create(createGraphQL(), options));
        return result;
    }

    private GraphQL createGraphQL() {
        String schema = Environment.loadStringFromFile("schemas.graphql");

        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(schema);

        RuntimeWiring runtimeWiring = newRuntimeWiring()
                .scalar(ExtendedScalars.Object)
                .scalar(ExtendedScalars.Json)
                .type("Query", builder -> {
                    VertxDataFetcher<JsonObject> getAllRows = new VertxDataFetcher<>(this::getAllRows);
                    return builder.dataFetcher("getRows", getAllRows);
                })
                .type("Query", builder -> {
                    VertxDataFetcher<JsonObject> getObjects = new VertxDataFetcher<>(this::getObjects);
                    return builder.dataFetcher("getObjects", getObjects);
                })
                .type("Query", builder -> {
                    VertxDataFetcher<JsonObject> getObject = new VertxDataFetcher<>(this::getObject);
                    return builder.dataFetcher("getObject", getObject);
                })
                .type("Mutation", builder -> {
                    VertxDataFetcher<JsonObject> setObject = new VertxDataFetcher<>(this::setObject);
                    return builder.dataFetcher("setObject", setObject);
                })
                .build();


        SchemaGenerator schemaGenerator = new SchemaGenerator();
        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);

        return GraphQL.newGraphQL(graphQLSchema)
                .build();
    }

    protected void getAllRows(DataFetchingEnvironment env, Future<JsonObject> future) {
        JsonObject result = new JsonObject();
        Environment environment = Environment.getInstance();
        String defaultDataSourceName = environment.getString("defaultDataSource");
        String jdbcUrl = environment.getString(defaultDataSourceName + "_jdbcUrl");
        String[] jdbcUrlParts = jdbcUrl.split(":");
        String serverType = jdbcUrlParts[1];

        JdbcDAOFactory daoFactory = new JdbcDAOFactory();
        daoFactory.init();

        dao = daoFactory.getInstance(serverType);

        Context context = new Context();
        context.putAll(env.getArguments());
        context.putValue(JdbcDAO.DATASOURCE_KEY, defaultDataSourceName);

        result.getMap().putAll(env.getArguments());

        dao.init(context, initResult -> {
            String sql = dao.buildSql(context);
            result.put("sql", sql);
            Future getRowsFuture = Future.future();

            if (cacheAddress != null) {
                JsonObject message = new JsonObject();

                message.put("method", CACHE_GET);
                message.put("key", sql);
                vertx.eventBus().send(cacheAddress, message, cacheResult -> {
                    logger.debug("Received result from cache...");
                    JsonObject cacheMessage = (JsonObject)cacheResult.result().body();
                    logger.debug(cacheMessage.encodePrettily());
                    if (cacheMessage.getString("message").equals(CACHE_SUCCESS)) {
                        result.put("values", cacheMessage.getJsonObject("value").getJsonArray("rows"));
                        future.complete(result);
                    }
                    else {
                        getRowsFuture.complete();
                    }
                });
            }
            else {
                getRowsFuture.complete();
            }

            //If we didn't get the result from the cache....
            getRowsFuture.compose(f -> {
                context.put(JdbcDAO.SQL_KEY, sql);
                dao.getResultSet(context, (Handler<AsyncResult<ResultSet>>) rawDataResult -> {
                    JsonArray rows = new JsonArray();

                    if (rawDataResult.succeeded()) {
                        rows.getList().addAll(rawDataResult.result().getRows());
                    }
                    else {
                        rows = new JsonArray();
                    }

                    result.put("values", rows);

                    //if the cache exists, store the result in the cache...
                    if (cacheAddress != null) {
                        JsonObject message = new JsonObject();
                        JsonObject value = new JsonObject();
                        value.put("rows", rows);

                        message.put("method", CACHE_PUT);
                        message.put("key", sql);
                        message.put("value", value);

                        vertx.eventBus().send(cacheAddress, message, cacheResult -> {
                            JsonObject cacheMessage = (JsonObject)cacheResult.result().body();
                            if (cacheMessage.getString("message").equals(CACHE_FAILURE)) {
                                logger.error("An error occurred putting rows in the cache:", cacheMessage.getString("error"));
                            }
                        });
                    }
                    logger.debug("Records delivered: " + result.encodePrettily());
                    future.complete(result);
                });
            }, null);
        });
    }

    protected void getObject(DataFetchingEnvironment env, Future<JsonObject> future) {
        String _class   = env.getArgument("entityType");
        String _id      = env.getArgument("id");
        String _key     = env.getArgument("keyField");

        BitsyObject bitsyObject = new BitsyObject(_class, _key, _id);
        bitsyObject.fetch(fetchResult -> {
            JsonObject response = new JsonObject();

            if (fetchResult.succeeded()) {
                response.put("success", true);
                response.put("message","");
                response.put("object", bitsyObject);
                future.complete(response);
            }
            else {
                response.put("success", false);
                response.put("message",fetchResult.cause().getLocalizedMessage());
                response.put("object", "{}");
                future.complete(response);
            }
        });
    }

    protected void getObjects(DataFetchingEnvironment env, Future<JsonObject> future) {
        JsonObject prototype = new JsonObject((Map<String, Object>)env.getArgument("prototype"));
        StringBuilder gremlinScript = new StringBuilder("g.V()");

        for (String key : prototype.fieldNames()) {
            gremlinScript.append(".has('").append(key).append("','").append(prototype.getString(key)).append("')");
        }

        logger.debug("Executing the following gremlin script: " + gremlinScript);

        Map<String, String> params = new HashMap(); //ProxyUtils will throw a NPE

        if (env.getArgument("deep")) {
            logger.debug("Fetching deep...");
            graphDatabaseService.gremlinScript(params, gremlinScript.toString(), scriptResult -> {
                getObjectsCompleteFuture(future, scriptResult);
            });
        }
        else {
            logger.debug("Fetching shallow...");
            graphDatabaseService.gremlinScriptShallow(params, gremlinScript.toString(), scriptResult -> {
                getObjectsCompleteFuture(future, scriptResult);
            });
        }
    }

    /**
     * My very first private method!! :-)
     *
     * Normally, I use "protected" because "private" breaks the extensibility rule, but in this case, I didn't want
     * to duplicate code in <code>getObjects()</code>
     * @param future
     * @param scriptResult
     */
    private void getObjectsCompleteFuture(Future<JsonObject> future, AsyncResult<List<JsonObject>> scriptResult) {
        JsonObject response = new JsonObject();
        if (scriptResult.succeeded()) {
            JsonArray objects = new JsonArray(scriptResult.result());
            response.put("success", true);
            response.put("message","");
            response.put("objects", objects);
        }
        else {
            response.put("success", false);
            response.put("message",scriptResult.cause().getLocalizedMessage());
            response.put("objects", "[]");
        }
        future.complete(response);
    }

    protected void setObject(DataFetchingEnvironment env, Future<JsonObject> future) {
        BitsyObject bitsyObject = new BitsyObject((Map<String, Object>)env.getArgument("content"));

        bitsyObject.store(storeResult -> {
            JsonObject response = new JsonObject();
            if (storeResult.succeeded()) {

                response.put("success", true);
                response.put("message","");
                response.put("object", (JsonObject)storeResult.result());
                future.complete(response);
            }
            else {
                response.put("success", false);
                response.put("message",storeResult.cause().getLocalizedMessage());
                response.put("object", "{}");
                future.complete(response);
            }
        });
    }
}
