package com.asanasoft.common.init;

import com.asanasoft.common.Application;
import com.asanasoft.common.Context;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

/**
 * Created by lopealf on 6/7/17.
 */
public abstract class AbstractInitializer implements Initializer {
    private Logger logger = LoggerFactory.getLogger(AbstractInitializer.class);
    protected Context   context = null;
    protected Vertx     vertx   = null;

    public AbstractInitializer() {

    }

    public AbstractInitializer(Vertx vertx) {
        this.vertx = vertx;
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
        if (newContext == null) {
            context = new Context();
        }
        else {
            context = newContext;
        }

        if (vertx == null) {
            if (context.containsKey("vertx")) {
                vertx = (Vertx)context.getValue("vertx");
            }
            else {
                vertx = Application.globalVertx;
            }
        }

        return true;
    }

    @Override
    public Context getResult() {
        return context;
    }
}
