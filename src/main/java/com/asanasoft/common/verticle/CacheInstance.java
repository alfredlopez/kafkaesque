package com.asanasoft.common.verticle;

import io.vertx.core.json.JsonObject;
import org.apache.commons.jcs.JCS;
import org.apache.commons.jcs.access.CacheAccess;
import org.apache.commons.jcs.access.exception.CacheException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.asanasoft.common.tools.CacheConstants.*;
import static com.asanasoft.common.verticle.CacheInstance.*;

public class CacheInstance extends DefaultQuartzVerticle {
    private Logger logger = LoggerFactory.getLogger(CacheInstance.class);
    private CacheAccess<String, JsonObject> cache;

    @Override
    protected JsonObject handleMessage(JsonObject message) {
        JsonObject result = new JsonObject();

        String method = message.getString("method");
        String key = message.getString("key");

        try {
            switch (method) {
                case CACHE_PUT :
                    cache.put(key, message.getJsonObject("value"));
                    result.put("message", CACHE_SUCCESS);
                    logger.debug("Cache put...");
                    break;

                case CACHE_GET :
                    JsonObject value = cache.get(key);
                    result.put("message", (value != null?CACHE_SUCCESS:CACHE_NOT_FOUND));
                    result.put("value", value);
                    logger.debug("Got value for [ " + key + " ] from cache");
                    break;
                default:
            }
        } catch (Exception e) {
            logger.error("An error occurred with cache " + method, e);
            result.put("message", CACHE_FAILURE);
            result.put("error", e.getLocalizedMessage());
        }

        return result;
    }

    @Override
    public void start() throws Exception {
        logger.debug("My name is " + getName());

        try {
            cache = JCS.getInstance(config().getJsonObject("config").getString("defaultRegion") );
        }
        catch (CacheException ce) {
            logger.error("An error occurred getting the cache:", ce);
            throw ce;
        }
    }

    @Override
    public void stop() throws Exception {
        super.stop();
    }
}
