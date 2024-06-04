package com.asanasoft.common.service.graphdb;

import com.asanasoft.common.service.graphdb.impl.AbstractGraphDBService;
import com.asanasoft.common.service.graphdb.impl.BitsyDBServiceImpl;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.tinkerpop.gremlin.structure.Graph;

import java.util.List;
import java.util.Map;

/**
 * Created by lopealf on 6/12/17.
 */

@ProxyGen
public interface BitsyDBService extends GraphDBService {
    static GraphDBService create(Graph graph, Vertx vertx) {
        return new BitsyDBServiceImpl(graph, vertx);
    }

    static GraphDBService createProxy(Vertx vertx, String address) {
        return new BitsyDBServiceVertxEBProxy(vertx, address);
    }

    @Override
    void gremlinScript(Map<String, String> params, String script, Handler<AsyncResult<List<JsonObject>>> handler);

    @Override
    void gremlinScriptShallow(Map<String, String> params, String script, Handler<AsyncResult<List<JsonObject>>> handler);

    @Override
    void insertVertex(String clazz, JsonObject vertex, Handler<AsyncResult<JsonObject>> handler);

    @Override
    void updateVertex(String clazz, JsonObject vertex, Handler<AsyncResult<JsonObject>> handler);

    @Override
    void insertOrUpdateVertex(String clazz, JsonObject vertex, Handler<AsyncResult<JsonObject>> handler);

    @Override
    void retrieveVertex(String id, Handler<AsyncResult<JsonArray>> handler);

//    @Override
//    void retrieveVertexByKey(List ids, Handler<AsyncResult<JsonArray>> handler);

    @Override
    void retrieveVertexByKey(String key, String value, Handler<AsyncResult<JsonArray>> handler);

    @Override
    void relate(JsonObject thisVertex, String with, JsonObject thatVertex, Handler<AsyncResult<List<JsonObject>>> handler);

    @Override
    void bulkRelate(JsonObject thisVertex, String with, JsonArray thatVertex, Handler<AsyncResult<JsonObject>> handler);

    @Override
    void delete(JsonObject jsonVertex);
}
