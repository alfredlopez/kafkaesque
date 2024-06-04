package com.asanasoft.common.model.listener;

import io.vertx.core.json.JsonObject;

public interface StreamListener {
    /**
     * Handle the stream event.
     *
     * This method *must* be asynchronous!!!
     *
     * @param context
     */
    void handle(JsonObject context);
}
