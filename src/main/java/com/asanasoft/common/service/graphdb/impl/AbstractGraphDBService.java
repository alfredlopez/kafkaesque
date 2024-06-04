package com.asanasoft.common.service.graphdb.impl;

import com.asanasoft.common.model.dao.BitsyObject;
import com.asanasoft.common.service.graphdb.DBConstants;
import com.asanasoft.common.service.graphdb.GraphDBService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.tinkerpop.gremlin.groovy.jsr223.GremlinGroovyScriptEngine;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.Bindings;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by lopealf on 6/13/17.
 */
public class AbstractGraphDBService implements GraphDBService {
    protected   Logger                  logger = LoggerFactory.getLogger(AbstractGraphDBService.class);
    private     Vertx                   vertx;
    private     Graph                   graphDB;
    private     GraphTraversalSource    g;

    protected final GremlinGroovyScriptEngine scriptEngine = new GremlinGroovyScriptEngine();

    public void setVertx(Vertx vertx) {
        this.vertx = vertx;
    }

    public void setGraphDB(Graph newGraphDB) {
        graphDB = newGraphDB;
        g = graphDB.traversal();
    }

    @Override
    public void gremlinScript(Map<String, String> params, String script, Handler<AsyncResult<List<JsonObject>>> handler) {
        vertx.executeBlocking(agr -> {
            agr.complete(gremlinScriptBlocking(params, script, true));
            logger.trace("Back from gremlinScriptBlocking...");
        }, r -> {
            Future<List<JsonObject>> future;

            if (r.succeeded()) {
                future = Future.succeededFuture((List<JsonObject>)r.result());
            }
            else {
                future = Future.failedFuture(r.cause());
            }

            logger.trace("Calling handler for gremlinScript...");
            handler.handle(future);
        });
    }

    public void gremlinScriptShallow(Map<String, String> params, String script, Handler<AsyncResult<List<JsonObject>>> handler) {
        vertx.executeBlocking(agr -> {
            agr.complete(gremlinScriptBlocking(params, script, false));
            logger.trace("Back from gremlinScriptBlocking...");
        }, r -> {
            Future<List<JsonObject>> future;

            if (r.succeeded()) {
                future = Future.succeededFuture((List<JsonObject>)r.result());
            }
            else {
                future = Future.failedFuture(r.cause());
            }

            logger.trace("Calling handler for gremlinScript...");
            handler.handle(future);
        });
    }

    protected List<JsonObject> gremlinScriptBlocking(Map<String, String> params, String script, boolean getChildren) {
        List<JsonObject> result = new ArrayList<JsonObject>();

        try {
            Bindings bindings = scriptEngine.createBindings();
            bindings.put("g", g);
            bindings.put("result", result);

            if (params != null) {
                //Copy all the params to the bindings map...
                params.entrySet().forEach(e -> bindings.put(e.getKey(), e.getValue()));
            }

            logger.debug("About to execute: " + script);

            Object scriptObject = scriptEngine.eval(script, bindings);

            logger.trace("Script Engine evaluated script - " + script);

            GraphTraversal scriptResult = (GraphTraversal)scriptObject;

            Vertex vertex;
            Object graphObject;

            logger.trace("hasNext() = " + scriptResult.hasNext());

            while (scriptResult.hasNext()) {
                graphObject = scriptResult.next();

                logger.trace("graphObjet = " + graphObject.getClass().getName());

                //Only aggregate Vertices
                if (graphObject instanceof Vertex) {

                    logger.trace("Retrieving Vertex Graph...");

                    vertex = (Vertex)graphObject;
                    result.add(retrieveVertexGraph(vertex, getChildren, 0));
                }
            }

            graphDB.tx().commit();
        }
        catch(Exception e) {
            graphDB.tx().rollback();
            logger.error("An error executing: " + script, e);
        }

        return result;
    }


    @Override
    public void insertVertex(String clazz, JsonObject vertex, Handler<AsyncResult<JsonObject>> handler) {
        vertx.executeBlocking(f -> {
            logger.debug("About to insert a vertex of class" + clazz);
            if (!g.tx().isOpen()) {
                g.tx().open();
            }

            Vertex resultVertex = createOrUpdateVertexBlocking(clazz, vertex);

            if (resultVertex != null) {
                JsonObject result = getJsonFrom(resultVertex);

                String keyName = result.getString(DBConstants.USE_KEY);
                String keyValue = result.getString(keyName);
                logger.trace("The complete object = " + this.retrieveVertexByKeyBlocking(keyName, keyValue).encodePrettily());

                f.complete(result);
            }
            else {
                f.fail("Vertex NOT inserted!!");
            }
        }, r -> {
            Future<JsonObject> future;

            if (r.succeeded()) {
                logger.debug("Inserting vertex " + ((JsonObject)r.result()).getString(DBConstants.INTERNAL_ID) + " suceeded!");
                future = Future.succeededFuture((JsonObject)r.result());
                g.tx().commit();
            }
            else {
                g.tx().rollback();
                future = Future.failedFuture(r.cause());
            }

            g.tx().close();
            handler.handle(future);
        });
    }

    /**
     * createOrUpdateVertexBlocking takes in a complex JsonObject and persists it using a bottomw-up
     * approach...
     * Here we assume that vertex is a completely marshalled JsonObject.
     *
     * @param clazz - the class name to assign this vertex
     * @param vertex - the Json representation of the vertex
     * @return a Vertex object
     */
    protected Vertex createOrUpdateVertexBlocking(String clazz, JsonObject vertex) {
        Vertex vertexObj = null;

        try {
            logger.debug("In createOrUpdateVertexBlocking...");
            /**
             * The "id" passed into addVertex may be ignored. OrientDB will use this information as the className
             * of the Vertex instance. Bitsy ignores it...
             */

            /**
             * This seq# is for child objects in the vertex object that appear in an JsonArray. This will keep the
             * entry order of the objects...
             */
            AtomicInteger seq = new AtomicInteger(0);

            //First, determine if this is an update or a new vertex...
            String vertexId = vertex.getString(DBConstants.INTERNAL_ID);
            boolean updateVertex = (vertexId != null && !vertexId.isEmpty());

            if (updateVertex) {
                //...this is an update, so look up the vertex...
                logger.trace("Looking up Vertex:" + vertex.encodePrettily());
                try {
                    vertexObj = g.V().has(DBConstants.INTERNAL_ID,vertex.getString(DBConstants.INTERNAL_ID)).next();
                    logger.debug("Updating Vertex with id =" + vertexObj.id().toString());
                }
                catch (NoSuchElementException e) {
                    /**
                     * ...if it doesn't exist,...
                     */
                    updateVertex = false;
                }
            }

            /**
             * ...make a second attempt at finding the vertex...
             */
            if (!updateVertex && vertex.getString(DBConstants.USE_KEY) != null) {
                /**
                 * This JsonObject can be a hand-coded reference to an existing object,
                 * if so, look up the object using the field indicated by DBConstants.USE_KEY...
                 */
                String idKey = vertex.getString(DBConstants.USE_KEY);

                try {
                    vertexObj = g.V().has(idKey, vertex.getValue(idKey)).next();
                    updateVertex = true;
                }
                catch (NoSuchElementException e) {
                    /**
                     * ...if it doesn't exist, create it...
                     */
                    updateVertex = false;
                }
            }

            logger.trace("updateVertex = " + updateVertex);
            if (!updateVertex) {
                //...this is a new vertex, so create one...
                vertexObj = graphDB.addVertex(clazz);
                logger.debug("Vertex inserted. Updating properties...");
            }

            String vertexClass = vertex.getString(DBConstants.INTERNAL_CLASS);

            for (String key : vertex.getMap().keySet()) {
                /**
                 * If the current property is NOT a JsonObject or a JsonArray, then add the key/value entry...
                 * (no need to check whether or not it's an update)
                 */
                if (!(vertex.getValue(key) instanceof JsonArray) && !(vertex.getValue(key) instanceof JsonObject)) {
                    logger.trace("key / value = " + key + "/" + vertex.getValue(key).toString());

                    vertexObj.property(key, vertex.getValue(key));
                }
                else {
                    /**
                     * If the current property points to an JsonArray,...
                     */
                    if (vertex.getValue(key) instanceof JsonArray) {
                        logger.trace("Property " + key + " is an JsonArray...");

                        /**
                         * ...the first thing we want to do is, if this is an update, then we need to reconcile the array.
                         * To do that, we do the following, for all linked vertices that are private to this vertex
                         * (i.e., don't have a clazz associated with them):
                         *
                         * 1) Get the current list of vertices that are linked to this vertex...
                         * 2) Delete all edges to these child vertices...
                         * 3) Loop through the current array...
                         * 4) "Mark" vertices that exists in both arrays...
                         * 5) Delete the vertices that no longer exist in the new array...
                         * 6) Add new vertices (if applicable)...
                         * 7) Re-Link with new edges.
                         */
                        JsonArray jsonArray = (JsonArray)vertex.getValue(key);
                        boolean found = false;

                        if (updateVertex) {
                            Iterator<Vertex> vertices;

                            vertices = g.V(vertexObj.id()).out("array:" + key);
                            BitsyObject currentVertex = null;

                            while (vertices.hasNext()) {
                                currentVertex = new BitsyObject(vertices.next());
                                String currentClass = currentVertex.getString(DBConstants.INTERNAL_CLASS);

                                found = false;

                                for (int i=0; i < jsonArray.size() && !found; i++ ) {
                                    found = currentVertex.equals(new BitsyObject(jsonArray.getJsonObject(i).encode()));
                                }

                                /**
                                 * The current object is NOT in the new array, so delete it from the system...
                                 */
                                if (!found && currentClass.equals(vertexClass + ":" + key)) {
                                    currentVertex.remove();
                                }
                            }

                            /**
                             * Delete the current edges...
                             */

                            logger.debug("Deleting edges with label = array:" + key);

                            g.V(vertexObj.id()).outE("array:" + key).drop().iterate();
                        }

                        seq.set(0);

                        for (Object jsonObject : jsonArray.getList()) {
                            /**
                             * ...take each Json object...
                             */
                            JsonObject json = (JsonObject)jsonObject;
                            Vertex childVertex;

                            /**
                             * ...and create/update the Vertex object...
                             */
                            String newClass = json.getString(DBConstants.INTERNAL_CLASS);
                            String relationship = "relate";

                            if (newClass == null || newClass.equals(clazz + ":" + key)) {
                                newClass = clazz + ":" + key;
                                relationship = "child";
                            }

                            logger.trace("Calling createOrUpdateVertexBlocking with class = " + newClass + " and json = \n" + json.encodePrettily());
                            childVertex = createOrUpdateVertexBlocking(newClass, json);

                            /**
                             * ...then relate it to the parent Vertex via an edge with the type of relationship...
                             */
                            logger.trace("Relating child vertex with id = " + childVertex.id().toString());

                            vertexObj.addEdge("array:" + key, childVertex, "type", relationship, "seq", seq.incrementAndGet(), "key", key);
                        }

                        /**
                         * ...then keep the original structure by creating a "property slot" where the array will be placed
                         * during unmarshalling.
                         */
                        vertexObj.property(key, "array:" + key);
                    }
                    else {
                        /**
                         * ...else, if the property points to an JsonObject...
                         */
                        JsonObject childObject = (JsonObject)vertex.getValue(key);
                        String childClazz = childObject.getString(DBConstants.INTERNAL_CLASS);
                        String relationship = "relate";

                        if (childClazz == null) {
                            childClazz = clazz + ":" + key;
                            relationship = "child";
                        }

                        logger.trace("Calling createOrUpdateVertexBlocking with childClazz = " + childClazz + " and json = \n" + childObject.encodePrettily());

                        Vertex childVertex = createOrUpdateVertexBlocking(childClazz, childObject);

                        /**
                         * ...then relate it to the parent Vertex via an edge with the type "child" designation...
                         */
                        if (!updateVertex) {
                            /**
                             * create the edge if this is a new vertex...
                             *
                             * There's no need to remove the edge and then create it in the case of a "relate".
                             * We only do it with JsonArrays because the arrays between the stored and the updated
                             * may have different number of entries.
                             */
                            vertexObj.addEdge("object:" + key, childVertex, "type", relationship);
                        }

                        /**
                         * ...then keep the original structure by creating a "property slot" where the object will be placed
                         * during unmarshalling.
                         */
                        vertexObj.property(key, "object:" + key);
                    }
                }
            }

            /**
             * We're tracking our own ids and classNames. Some GraphDB implementations take care of these...
             */
            vertexObj.property(DBConstants.INTERNAL_ID, vertexObj.id().toString());
            if (!updateVertex) {
                vertexObj.property(DBConstants.INTERNAL_CLASS, clazz);

                if (vertex.getString(DBConstants.USE_KEY) == null) {
                    vertexObj.property(DBConstants.USE_KEY, DBConstants.INTERNAL_ID);
                }
            }

            /**
             * Finally, mark this vertex with the user who updated it...
             */
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-DD HH:mm:ss");
            String username = (String)vertx.sharedData().getLocalMap("userSession").get("currentUser");
            String timeStamp = sdf.format(System.currentTimeMillis());

            if (username != null) {
                if (!vertexObj.property("createdBy").isPresent()) {
                    vertexObj.property("createdBy", username);
                    vertexObj.property("createdOn", timeStamp);
                }
                vertexObj.property("updatedBy", username);
                vertexObj.property("updatedOn", timeStamp);
            }
        } catch (Exception e) {
            logger.error("An error occurred in createOrUpdateVertexblocking...", e);
            vertexObj = null;
        }

        return vertexObj;
    }

    @Override
    public void updateVertex(String clazz, JsonObject vertex, Handler<AsyncResult<JsonObject>> handler) {
        vertx.executeBlocking(f -> {
            JsonObject result;
            if (!g.tx().isOpen()) {
                g.tx().open();
            }
            Vertex vertexResult = createOrUpdateVertexBlocking(clazz,vertex);

            if (vertexResult != null) {
                result = getJsonFrom(vertexResult);
                f.complete(result);
            }
            else {
                f.fail("Vertex NOT updated!!");
            }
        }, r -> {
            Future<JsonObject> future;

            if (r.succeeded()) {
                logger.debug("Updating vertex " + ((JsonObject)r.result()).getString(DBConstants.INTERNAL_ID) + " suceeded!");
                future = Future.succeededFuture((JsonObject)r.result());
                g.tx().commit();
            }
            else {
                future = Future.failedFuture(r.cause());
                g.tx().rollback();
            }

            g.tx().close();
            handler.handle(future);
        });
    }

    @Override
    public void insertOrUpdateVertex(String clazz, JsonObject vertex, Handler<AsyncResult<JsonObject>> handler) {
        if (vertex.getString(DBConstants.INTERNAL_ID) != null) {

            if (vertex.getString(DBConstants.USE_KEY) == null) {
                vertex.put(DBConstants.USE_KEY, DBConstants.INTERNAL_ID);
            }

            updateVertex(clazz, vertex, handler);
        }
        else {
            insertVertex(clazz, vertex, handler);
        }
    }

    @Override
    public void retrieveVertex(String id, Handler<AsyncResult<JsonArray>> handler) {
        String[] ids = {id};
        retrieveVertex(Arrays.asList(ids), true, handler);
    }

    public void retrieveVertex(String id, boolean getChildren, Handler<AsyncResult<JsonArray>> handler) {
        String[] ids = {id};
        retrieveVertex(Arrays.asList(ids), getChildren, handler);
    }

    public void retrieveVertex(List ids, Handler<AsyncResult<JsonArray>> handler) {
        retrieveVertex(ids, true, handler);
    }

    public void retrieveVertex(List ids, boolean getChildren, Handler<AsyncResult<JsonArray>> handler) {
        vertx.executeBlocking(b -> {
            JsonArray result = retrieveVertexBlocking(ids, getChildren);
            b.complete(result);
        }, r -> {
            Future<JsonArray> future;

            if (r.succeeded()) {
                future = Future.succeededFuture((JsonArray)r.result());
            }
            else {
                future = Future.failedFuture(r.cause());
            }

            handler.handle(future);
        });
    }

    public JsonArray retrieveVertexBlocking(List ids) {
        return retrieveVertexBlocking(ids, true);
    }

    public JsonArray retrieveVertexBlocking(List ids, boolean getChildren) {
        JsonArray result = new JsonArray();

        try {
            Iterator<Vertex> vertices = g.V(ids.toArray());

            while (vertices.hasNext()) {
                JsonObject retrievedVertex = retrieveVertexGraph(vertices.next(), getChildren, 0);

                logger.trace("retrieved vertex = \n" + retrievedVertex.encodePrettily());

                result.add(retrievedVertex);
            }
        }
        catch (Exception e) {
            logger.error("An error occurred in retrieveVertex:",e);
        }

        return result;
    }

    @Override
    public void retrieveVertexByKey(String key, String value, Handler<AsyncResult<JsonArray>> handler) {
        vertx.executeBlocking(b -> {
            JsonArray result = retrieveVertexByKeyBlocking(key, value);

            if (!result.isEmpty()) {
                b.complete(result);
            }
            else {
                b.fail("Vertex with key=" + key + " and value=" + value + " not found1");
            }
        }, r -> {
            Future<JsonArray> future;

            if (r.succeeded()) {
                future = Future.succeededFuture((JsonArray)r.result());
            }
            else {
                future = Future.failedFuture(r.cause());
            }

            handler.handle(future);
        });
    }


    public JsonArray retrieveVertexByKeyBlocking(String key, String value) {
        JsonArray result = new JsonArray();

        try {
            Iterator<Vertex> vertices = g.V().has(key, value);

            while (vertices.hasNext()) {
//                result.add(getJsonFrom(vertices.next()));
                result.add(retrieveVertexGraph(vertices.next()));
            }
        }
        catch (Exception e) {
            logger.error("An error occurred in retrieveVertex:",e);
        }

        return result;
    }

    protected JsonObject retrieveVertexGraph(Vertex startVertex) {
        return retrieveVertexGraph(startVertex,true,0);
    }

    /**
     * retrieveVertexGraph retrieves either a complex Vertex graph (if getChildren=true) or
     * a shallow Vertex graph (if getChildren=false), for the supplied starting Vertex 'startVertex'.
     * @param startVertex
     * @return
     */
    protected JsonObject retrieveVertexGraph(Vertex startVertex, boolean getChildren, int recursionLevel) {
        if (recursionLevel == 0) {
            logger.debug("In retrieveVertexGraph, with getChildren = " + getChildren + ", level=" + recursionLevel + "...");
        }
        JsonObject result = getJsonFrom(startVertex);
        logger.trace("startVertex = \n" + result.encodePrettily());

        Object value = null;

        for (String key : result.getMap().keySet()) {
            value = result.getValue(key);

            logger.trace("value = " + value);

            if (value instanceof String && ((String)value).startsWith("object:")) {
                Iterator<Vertex> vertices = g.V(startVertex.id()).outE((String)value).inV();
                if (getChildren) {
                    result.put(key, retrieveVertexGraph(vertices.next(), true, recursionLevel+1));
                } else {
                    JsonObject shallowObj = buildShallowCopyOfVertex(vertices.next());
                    result.put(key, shallowObj);
                }
            }
            else if (value instanceof String && ((String)value).startsWith("array:")) {
                JsonArray jsonArray = new JsonArray();
                result.put(key, jsonArray);

                Iterator<Vertex> vertices = g.V(startVertex.id()).outE((String)value).order().by("seq").inV();

                while (vertices.hasNext()) {
                    if (getChildren) {
                        jsonArray.add(retrieveVertexGraph(vertices.next(), true, recursionLevel+1));
                    } else {
                        JsonObject shallowObj = buildShallowCopyOfVertex(vertices.next());
                        jsonArray.add(shallowObj);
                    }
                }
            }
        }

        return result;
    }

    /**
     * buildShallowCopyOfVertex returns a JsonObject that is a shallow copy of the supplied vertex. That is, it contains
     * only three of the vertex's fields: _class, _key, and the field named by _key.
     * For example, for a JTCard vertex it returns: { "_class": "JTCard", "_key": "cardId", "cardId": "CARD1200" }
     * @param v
     * @return
     */
    protected JsonObject buildShallowCopyOfVertex(Vertex v) {
        JsonObject obj = getJsonFrom(v);
        JsonObject shallowObj = new JsonObject();
        shallowObj.put(DBConstants.INTERNAL_CLASS,obj.getString(DBConstants.INTERNAL_CLASS));
        String keyFieldName = obj.getString(DBConstants.USE_KEY);
        shallowObj.put(DBConstants.USE_KEY, keyFieldName);
        shallowObj.put(keyFieldName,obj.getValue(keyFieldName));

        return shallowObj;
    }

    @Override
    public void relate(JsonObject thisVertex, String with, JsonObject thatVertex, Handler<AsyncResult<List<JsonObject>>> handler) {
        vertx.executeBlocking(f -> {
            f.complete(Arrays.asList(relateBlocking(thisVertex, with, thatVertex)));
        }, r -> {
            Future<List<JsonObject>> future;

            if (r.succeeded()) {
                future = Future.succeededFuture((List<JsonObject>)r.result());
            }
            else {
                future = Future.failedFuture(r.cause());
            }

            handler.handle(future);
        });
    }

    public JsonObject[] relateBlocking(JsonObject thisVertex, String with, JsonObject thatVertex) {
        JsonObject[] result = new JsonObject[2];

        try {
            Vertex fromVertex = null;
            Vertex toVertex = null;

            /**
             * Find the vertices represented by the prototypes..
             * This is necessary because this class has a proxy for RPC and Vertex as a parameter is not serializable
             * so we have to pass in, basically, a string and find the actual object that it refers to.
             */
            String thisKey = thisVertex.getString(DBConstants.USE_KEY);
            String thatKey = thatVertex.getString(DBConstants.USE_KEY);

            /**
             * "id" is a reserved Vertex property, but it can be implemented however the Vertex implementation deems,
             * so the underlying type may be incompatible with JsonObject's properties. The safest type is String,
             * so we make a copy of the id as String (by calling toString() on the id object) and save it as INTERNAL_ID.
             * When we want to search by id, we tell Tinkerpop that the key field is "id", but the value is coming
             * from INTERNAL_ID. We will let the underlying implementation convert the String to whatever type they
             * need.
             */
            String keyValue1 = thisVertex.getString(thisKey);
            String keyValue2 = thatVertex.getString(thatKey);
            Iterator<Vertex> vertices;

            vertices = g.V().has(thisKey, keyValue1);

            if (vertices.hasNext()) {
                fromVertex = vertices.next();
            }

            vertices = g.V().has(thatKey, keyValue2);

            if (vertices.hasNext()) {
                toVertex = vertices.next();
            }

            if (fromVertex != null && toVertex != null) {
                fromVertex.addEdge(with, toVertex);
                graphDB.tx().commit();

                result[0] = getJsonFrom(fromVertex);
                result[1] = getJsonFrom(toVertex);
            }
        } catch (Exception e) {
            logger.error("An error occurred in relate:", e);
            graphDB.tx().rollback();
        }

        return result;
    }

    @Override
    public void bulkRelate(JsonObject thisVertex, String with, JsonArray thatVertex, Handler<AsyncResult<JsonObject>> handler) {
        vertx.executeBlocking(f -> {
            f.complete(bulkRelateBlocking(thisVertex, with, thatVertex));
        }, r -> {
            Future<JsonObject> future;
            if (r.succeeded()) {
                future = Future.succeededFuture((JsonObject)r.result());
            }
            else {
                future = Future.failedFuture(r.cause());
            }

            handler.handle(future);
        });
    }

    protected void delete(Vertex vertex) {
        delete(vertex, 0);
    }

    protected void delete(Vertex vertex, int recursionLevel) {
        logger.trace("Deleting vertex:" + this.getJsonFrom(vertex).encodePrettily());

        try {
            for (String propertyKey : vertex.keys()) {
                if (vertex.value(propertyKey) instanceof String) {
                    String keyValue = (String) vertex.value(propertyKey);
                    if (keyValue.startsWith("array:") || keyValue.startsWith("object:")) {
                        Iterator<Vertex> childVertices = g.V(vertex.id()).out(keyValue);

                        while (childVertices.hasNext()) {
                            delete(childVertices.next(), recursionLevel + 1);
                        }
                    }
                }
            }

            String currentClass = (String)vertex.property(DBConstants.INTERNAL_CLASS).value();

            if (currentClass.contains(":") || recursionLevel == 0) {
                /**
                 * Delete all edges from this vertex...
                 */
                g.V(vertex.id()).bothE().drop().iterate();

                /**
                 * Delete vertex (it can also be expressed as g.V(vertex.id()).drop())
                 */
                vertex.remove();
            }
        }
        catch (Exception e) {
            logger.error("An error occurred in delete:", e);
        }
    }

    @Override
    public void delete(JsonObject jsonVertex) {
        BitsyObject bitsyObject = (jsonVertex instanceof BitsyObject?(BitsyObject)jsonVertex:new BitsyObject(jsonVertex.toString()));
        delete(bitsyObject);
    }

    public void delete(BitsyObject bitsyObject) {
        String key = bitsyObject.getString(DBConstants.USE_KEY);
        String entityId = bitsyObject.getString(key);

        if (entityId == null) {
            logger.error("Invalid request to delete a vertex: json has no ID!");
        } else {
            Vertex vertexToDelete = null;

            try {
                vertexToDelete = g.V().has(key, entityId).next();
                delete(vertexToDelete);
                graphDB.tx().commit();
            } catch (NoSuchElementException e) {
                logger.error("Vertex to be deleted was not found. _class=" + bitsyObject.getClazz() + " _id=" + entityId);
            }
        }
    }

    public JsonObject bulkRelateBlocking(JsonObject thisVertex, String with, JsonArray thatVertex) {
        Iterator<Vertex> vertices;
        JsonObject response = new JsonObject();

        try {
            Vertex fromVertex = null;

            String thisKey = thisVertex.getString(DBConstants.USE_KEY);
            String keyValue1 = thisVertex.getString(thisKey);

            vertices = g.V().has(thisKey, keyValue1);

            if (vertices.hasNext()) {
                fromVertex = vertices.next();
            }

            Iterator it = thatVertex.iterator();

            Vertex toVertex = null;
            String thatKey;
            Iterator<Vertex> vertices2;
            JsonObject vertex;
            String keyValue2;

            while (it.hasNext()) {
                vertex = (JsonObject)it.next();
                thatKey = vertex.getString(DBConstants.USE_KEY);

                keyValue2 = vertex.getString(thatKey);

                vertices2 = g.V().has(thatKey, keyValue2);

                if (vertices2.hasNext()) {
                    toVertex = vertices2.next();
                }

                if (fromVertex != null && toVertex != null) {
                    fromVertex.addEdge(with, toVertex);
                }
            }

            response.put("success", true);
            response.put("message", "");

            graphDB.tx().commit();
        } catch (Exception e) {
            logger.error("An error occurred in relate:", e);
            graphDB.tx().rollback();
        }

        return response;
    }

    public Graph getGraphDB() {
        return graphDB;
    }

    protected JsonObject getJsonFrom(Vertex vertex) {
        JsonObject result = new JsonObject();

        for (String key : vertex.keys()) {
            result.put(key, vertex.property(key).value());
        }

        return result;
    }
}
