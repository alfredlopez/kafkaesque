package com.asanasoft.common.model.listener.impl;

import com.asanasoft.common.model.connector.StreamException;
import io.vertx.core.json.JsonObject;

public class SchemaChangeStreamListener extends AbstractStreamListener {
    @Override
    protected Boolean shouldHandle(JsonObject context) {
        return super.shouldHandle(context);
    }

    @Override
    protected void doHandle(JsonObject context) throws StreamException {
        super.doHandle(context);
    }

    @Override
    protected void postHandle(JsonObject context) {
        super.postHandle(context);
    }

    @Override
    protected void handleError(Throwable cause) {
        super.handleError(cause);
    }
}
