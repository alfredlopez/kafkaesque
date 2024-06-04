package com.asanasoft.common.model.listener.impl;

import com.asanasoft.common.Application;
import com.asanasoft.common.model.connector.StreamException;
import com.asanasoft.common.model.listener.StreamListener;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractStreamListener implements StreamListener {
    Logger logger = LoggerFactory.getLogger(AbstractStreamListener.class);

    @Override
    public final void handle(JsonObject context) {
        Application.globalVertx.executeBlocking(exec -> {
            if (shouldHandle(context)) {
                try {
                    doHandle(context);
                    exec.complete();
                }
                catch(StreamException e) {
                    exec.fail(e);
                }
                catch(Exception e) {
                    logger.error("An error occurred in handle:" + e.getMessage());
                }
            }
        }, result -> {
            if (result.succeeded()) {
                postHandle(context);
            }
            else {
                handleError(result.cause());
            }
        });
    }

    protected Boolean shouldHandle(JsonObject context) {
        return true;
    }

    protected void doHandle(JsonObject context) throws StreamException {

    }

    protected void postHandle(JsonObject context) {

    }

    protected void handleError(Throwable cause) {
        logger.error(cause.getMessage());
    }
}
