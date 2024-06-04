package com.asanasoft.common.model.dao;

/**
 * Since we're running the application inside Vertx (actually, *using* is more appropriate),
 * we'll use Vertx's JsonObject implementation, but it is not necessary. As long as the
 * implementation produces a JSON string, we're fine...
 */
import com.asanasoft.common.Application;
import com.asanasoft.common.init.impl.Environment;
import com.asanasoft.common.service.graphdb.BitsyDBService;
import com.asanasoft.common.service.graphdb.DBConstants;
import com.asanasoft.common.service.graphdb.GraphDBService;
import com.asanasoft.common.service.graphdb.GraphDBServiceFactory;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.buffer.impl.BufferImpl;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import java.util.Map;

/**
 * Base class for all things Bitsy. This way, we don't have to keep writing boilerplate code...
 */
public class BitsyObject extends JsonObject {
    private String _class = null; //class of the BitsyObject
    private String _key   = null; //The field to use as a key
    private String _id    = null; //The Bitsy-assigned ID.

    private String DEFAULT_CLASS_NAME = "BitsyObject";
    private String DEFAULT_KEY        = DBConstants.INTERNAL_ID;


        private GraphDBService graphDBService = null;

    /**
     * Force the app developer to use the next constructor...
     */
    public BitsyObject() throws Exception {
        throw new Exception("Must instantiate with a class, key, id, or JSON!");
    }

    public BitsyObject(String className, String keyFieldToUse, String id) {
        _class = (className     != null?className:DEFAULT_CLASS_NAME);
        _key   = (keyFieldToUse != null?keyFieldToUse:DEFAULT_KEY);

        /**
         * Said boilerplate code...
         */
        this.put(_key, id);
        this.put(DBConstants.INTERNAL_CLASS, _class);
        this.put(DBConstants.USE_KEY, _key);
    }

    public BitsyObject(String className, String keyFieldToUse, String id, String json) {
        super(json);

        _class = (className     != null?className:DEFAULT_CLASS_NAME);
        _key   = (keyFieldToUse != null?keyFieldToUse:DEFAULT_KEY);

        /**
         * Said boilerplate code...
         */
        this.put(_key, id);
        this.put(DBConstants.INTERNAL_CLASS, _class);
        this.put(DBConstants.USE_KEY, _key);
    }

    public BitsyObject(String json) {
        super(json);

        String className        = getString(DBConstants.INTERNAL_CLASS);
        String keyFieldToUse    = getString(DBConstants.USE_KEY);

        _class = (className     != null?className:DEFAULT_CLASS_NAME);
        _key   = (keyFieldToUse != null?keyFieldToUse:DEFAULT_KEY);
    }

    public BitsyObject(Map<String, Object> newMap) {
        super(newMap);

        String className        = getString(DBConstants.INTERNAL_CLASS);
        String keyFieldToUse    = getString(DBConstants.USE_KEY);

        _class = (className     != null?className:DEFAULT_CLASS_NAME);
        _key   = (keyFieldToUse != null?keyFieldToUse:DEFAULT_KEY);
    }

    public BitsyObject(Vertex vertex) {
        for (String key : vertex.keys()) {
            put(key, vertex.property(key).value());
        }
    }

    @Override
    public boolean equals(Object o) {
        boolean result = false;

        if (o instanceof BitsyObject) {
            BitsyObject other = (BitsyObject)o;

            if ((_id != null || getString(DBConstants.USE_KEY) != null) && (other._id != null || other.getString(DBConstants.USE_KEY) != null)){
                result = getString(DBConstants.USE_KEY).equals(other.getString(DBConstants.USE_KEY));
            }
            else {
                JsonObject o1 = new JsonObject();
                JsonObject o2 = new JsonObject();

                o1.getMap().putAll(this.getMap());
                o2.getMap().putAll(other.getMap());

                o1.remove(DBConstants.INTERNAL_CLASS);
                o1.remove(DBConstants.INTERNAL_ID);
                o1.remove(DBConstants.USE_KEY);

                o2.remove(DBConstants.INTERNAL_CLASS);
                o2.remove(DBConstants.INTERNAL_ID);
                o2.remove(DBConstants.USE_KEY);

                result = o1.equals(o2);
            }
        }
        return result;
    }

    /**
     * Let's make this class behave like an Object DB by giving the objects
     * CRUD features...
     *
     * These methods are in the react pattern, since AbstractGraphDBService is reactive...
     */

    /**
     * Fetch the previously persisted version of this object or a newly created one.
     * @param handler
     */
    public void fetch(Handler<AsyncResult<BitsyObject>> handler) {
        Future future = Future.future();

        if (this._id == null) {
            getGraphDBService().retrieveVertexByKey(this._key, this.getString(this._key), fetchResult -> {
                if (fetchResult.succeeded()) {
                    /**
                     * If a vertex was fetched from the database, make THIS vertex that vertex...
                     */
                    Buffer buffer = new BufferImpl();
                    fetchResult.result().getJsonObject(0).writeToBuffer(buffer);
                    this.readFromBuffer(0, buffer);
                    future.complete(this);
                }
                else {
                    future.fail(fetchResult.cause());
                }

                handler.handle(future);
            });
        }
        else {
            getGraphDBService().retrieveVertex(this._id, fetchResult -> {
                if (fetchResult.succeeded()) {
                    /**
                     * If a vertex was fetched from the database, make THIS vertex that vertex...
                     */
                    Buffer buffer = new BufferImpl();
                    fetchResult.result().getJsonObject(0).writeToBuffer(buffer);
                    this.readFromBuffer(0, buffer);
                    future.complete(this);
                }
                else {
                    future.fail(fetchResult.cause());
                }

                handler.handle(future);
            });
        }
    }

    public void remove() {
        getGraphDBService().delete(this);
    }

    public void store(Handler<AsyncResult> handler) {
        Future future = Future.future();

        getGraphDBService().insertOrUpdateVertex(_class, this, insertResult -> {
            if (insertResult.succeeded()) {
                JsonObject newVertex = insertResult.result();
                Buffer buffer = new BufferImpl();
                newVertex.writeToBuffer(buffer);
                this.readFromBuffer(0, buffer);
                future.complete(new BitsyObject(newVertex.encode()));
            }
            else {
                future.fail(insertResult.cause());
            }

            handler.handle(future);
        });
    }
    public void update(Handler<AsyncResult> handler) {
        Future future = Future.future();

        getGraphDBService().updateVertex(this._class, this, updateResult -> {
            if (updateResult.succeeded()) {
                future.complete();
            }
            else {
                future.fail(updateResult.cause());
            }

            handler.handle(future);
        });
    }

    /**
     * We'll use the "flow pattern" for our next set of methods...
     */

    /**
     * A flow-ified synonym for 'put(String, Object)'
     * @param key
     * @param value
     * @return
     */
    public BitsyObject add(String key, Object value) {
        this.put(key, value);
        return this;
    }

    public BitsyObject addChild(BitsyObject newObject) {
        /**
         * Let's do something cool here...
         *
         * If this is the first time this _class is added, then we'll
         * make it a field of this JSON
         * else, put it in an array
         */
        Object tempObj = getValue(newObject.getClazz());

        if (tempObj == null) {
            this.put(newObject.getClazz(), newObject);
        }
        else if (tempObj instanceof JsonArray) {
            ((JsonArray) tempObj).add(newObject);
        }
        else {
            JsonArray jsonArray = new JsonArray();
            jsonArray.add(newObject);
            this.put(newObject.getClazz(), jsonArray);
        }

        return this;
    }

    public BitsyObject removeChild(BitsyObject oldObject) {
        Object tempObj = getValue(oldObject.getClazz());

        if (tempObj instanceof JsonArray) {
            ((JsonArray) tempObj).remove(oldObject);
        }
        else {
            this.remove(oldObject.getClazz());
        }

        return this;
    }

    public String getClazz() {
        return this._class;
    }

    /**
     * I never make my methods private. Principles...
     */
    protected GraphDBService getGraphDBService() {
        if (graphDBService == null) {
            graphDBService = BitsyDBService.createProxy(Application.globalVertx, "Bitsy");
        }

        return graphDBService;
    }
}
