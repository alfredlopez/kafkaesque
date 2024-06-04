package com.asanasoft.common.service.graphdb;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ProxyHelper;

import java.util.List;
import java.util.Map;

/**
 * Created by lopealf on 6/12/17.
 */
@ProxyGen
public interface GraphDBService {
    void gremlinScript(Map<String, String> params,
                       String script,
                       Handler<AsyncResult<List<JsonObject>>> handler);


    void gremlinScriptShallow(Map<String, String> params,
                              String script,
                              Handler<AsyncResult<List<JsonObject>>> handler);


    void insertVertex(String clazz,
                      JsonObject vertex,
                      Handler<AsyncResult<JsonObject>> handler);


    void updateVertex(String clazz,
                      JsonObject vertex,
                      Handler<AsyncResult<JsonObject>> handler);

    void insertOrUpdateVertex(String clazz,
                              JsonObject vertex,
                              Handler<AsyncResult<JsonObject>> handler);

    void retrieveVertex(String id, Handler<AsyncResult<JsonArray>> handler);

//    void retrieveVertexByKey(List ids, Handler<AsyncResult<JsonArray>> handler);

    void retrieveVertexByKey(String key, String value, Handler<AsyncResult<JsonArray>> handler);

    void relate(JsonObject thisVertex,
                String with,
                JsonObject thatVertex,
                Handler<AsyncResult<List<JsonObject>>> handler);

    void bulkRelate(JsonObject thisVertex,
                    String with,
                    JsonArray thatVertex,
                    Handler<AsyncResult<JsonObject>> handler);

    void delete(JsonObject jsonVertex);
}
