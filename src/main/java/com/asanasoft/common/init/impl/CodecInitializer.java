package com.asanasoft.common.init.impl;

import com.asanasoft.common.Context;
import com.asanasoft.common.codec.BitsyObjectMessageCodec;
import com.asanasoft.common.codec.HandlerMessageCodec;
import com.asanasoft.common.codec.TriggerListenerMessageCodec;
import com.asanasoft.common.init.AbstractInitializer;
import com.asanasoft.common.model.dao.BitsyObject;
import io.vertx.core.Handler;
import org.quartz.TriggerListener;

public class CodecInitializer extends AbstractInitializer {
    @Override
    public boolean init(Context newContext) {
        HandlerMessageCodec handlerMessageCodec = new HandlerMessageCodec();
        TriggerListenerMessageCodec triggerListenerMessageCodec = new TriggerListenerMessageCodec();
        BitsyObjectMessageCodec bitsyObjectMessageCodec = new BitsyObjectMessageCodec();

        vertx.eventBus().registerDefaultCodec(Handler.class, handlerMessageCodec);
        vertx.eventBus().registerDefaultCodec(TriggerListener.class, triggerListenerMessageCodec);
        vertx.eventBus().registerDefaultCodec(BitsyObject.class, bitsyObjectMessageCodec);
        return true;
    }
}
