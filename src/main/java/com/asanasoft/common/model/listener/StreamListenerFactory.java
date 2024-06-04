package com.asanasoft.common.model.listener;

import com.asanasoft.common.model.listener.impl.SchemaChangeStreamListener;
import com.asanasoft.common.model.listener.impl.TableChangeStreamListener;
import com.asanasoft.common.service.AbstractFactory;

public class StreamListenerFactory extends AbstractFactory<Class<? extends StreamListener>> {
    @Override
    public boolean init() {
        getComponents().put("schema",   ()-> SchemaChangeStreamListener.class);
        getComponents().put("table",    ()-> TableChangeStreamListener.class);

        return true;
    }
}
