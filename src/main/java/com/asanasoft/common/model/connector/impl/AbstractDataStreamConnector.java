package com.asanasoft.common.model.connector.impl;

import com.asanasoft.common.Context;
import com.asanasoft.common.model.connector.DataStreamConnector;
import com.asanasoft.common.model.listener.StreamListener;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractDataStreamConnector implements DataStreamConnector {
    private List<StreamListener> observers;
    private Context config;

    @Override
    abstract public void init(Context config);

    @Override
    public void init() {
        this.init(new Context());
    }

    @Override
    public void register(StreamListener listener) {
        getObservers().add(listener);
    }

    @Override
    public void unregister(StreamListener listener) {
        getObservers().remove(listener);
    }

    @Override
    abstract public void start();

    @Override
    abstract public void stop();

    protected void notify(JsonObject context) {
        for (StreamListener listener : getObservers()) {
            listener.handle(context);
        }
    }

    protected List<StreamListener> getObservers() {
        if (observers == null) {
            observers = new ArrayList<>();
        }

        return observers;
    }

}
