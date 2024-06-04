package com.asanasoft.common.service;

import com.asanasoft.common.Context;
import com.asanasoft.common.init.Initializer;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Created by lopealf on 6/12/17.
 */
public class AbstractFactory<T> implements Factory<T>, Initializer {
    private Map<String, Supplier<T>> components;

    protected Function<String, T> getComponent = (name) -> getComponents().get(name).get();

    public AbstractFactory() {
        init();
    }

    @Override
    public T getInstance(String objectType) {
        T result = (T) getComponent.apply(objectType);
        return result;
    }

    public Map<String, Supplier<T>> getComponents() {
        if (components == null) {
            components = new HashMap<String,Supplier<T>>();
            init();
        }

        return components;
    }

    @Override
    public void init(Context newContext, Handler<AsyncResult<Boolean>> handler) {

    }

    @Override
    public boolean init() {
        return init(null);
    }

    @Override
    public boolean init(Context newContext) {
        return false;
    }

    @Override
    public Context getResult() {
        return null;
    }
}
