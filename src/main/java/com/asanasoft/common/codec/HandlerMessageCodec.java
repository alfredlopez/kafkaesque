package com.asanasoft.common.codec;

import io.vertx.core.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HandlerMessageCodec extends ObjectMessageCodec<Handler, Handler> {
    private Logger logger = LoggerFactory.getLogger(HandlerMessageCodec.class);

    public HandlerMessageCodec() {
        setName("Handler");
    }

    @Override
    public Handler transform(Handler handler) {
        return handler;
    }
}
