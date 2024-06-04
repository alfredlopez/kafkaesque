package com.asanasoft.common.service.cron.impl;

import com.asanasoft.common.Application;
import com.asanasoft.common.Context;
import com.asanasoft.common.service.cron.PeriodicWorker;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract public class AbstractPeriodicWorker implements PeriodicWorker {
    private Logger logger = LoggerFactory.getLogger(AbstractPeriodicWorker.class);

    private Context context;
    private int period = 1000; //default to one second...
    private Handler<AsyncResult<Context>> handler = request -> {}; //default handler
    private long timerId;
    private String state = "WAITING";
    private static String BUSY = "BUSY";
    private static String WAITING = "WAITING";
    private String fireAddress = "FIRE_WORKER"; //Not intended to be subliminal or anything...
    private MessageConsumer<JsonObject> consumer = null;

    protected Vertx vertx;

    public AbstractPeriodicWorker() {
        this(null);
    }

    public AbstractPeriodicWorker(Vertx newVertx) {
        if (newVertx == null) {
            this.vertx = Application.globalVertx;
        }
        else {
            this.vertx = newVertx;
        }
    }

    @Override
    public void setContext(Context context) {
        this.context = context;
    }

    @Override
    public void setPeriodic(long period, Handler<AsyncResult<Context>> handler) {
        this.handler = handler;

        timerId = vertx.setPeriodic(period, f -> {
            doFire(this.handler);
        });
    }

    @Override
    public boolean cancelPeriodic() {
        return vertx.cancelTimer(timerId);
    }

    @Override
    public void fireRequest(JsonObject message) {
        if (allowFire(message)) {
            doFire(this.handler);
        }
    }

    protected boolean allowFire(JsonObject message) {
        return true;
    }

    abstract public Context fire(Context context) throws Exception;

    protected void doFire(Handler<AsyncResult<Context>> handler) {
        vertx.executeBlocking(f -> {
            try {
                if (WAITING.equals(this.getState())) {
                    logger.debug("Worker in WAITING state...will fire now...");
                    this.setState(AbstractPeriodicWorker.BUSY);
                    Context newContext = fire(getContext());
                    f.complete(newContext);
                }
                else {
                    logger.debug("Worker in BUSY state...will skip now...");
                    ProcessBusyException busy = new ProcessBusyException();
                    f.fail(busy);
                }
            } catch (Exception e) {
                f.fail(e);
            }
        }, r -> {
            Future<Context> backToThe = Future.future();

            if (r.succeeded()) {
                backToThe.complete((Context)r.result());
            }
            else {
                backToThe.fail(r.cause());
            }

            this.setState(AbstractPeriodicWorker.WAITING);

            handler.handle(backToThe);
        });
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getFireAddress() {
        return fireAddress;
    }

    public void setFireAddress(String fireAddress) {
        this.fireAddress = fireAddress;

        if (consumer != null && consumer.isRegistered()) {
            consumer.unregister();
            consumer = null;
        }

        consumer = vertx.eventBus().localConsumer(fireAddress);
        consumer.handler(request -> {
            fireRequest(request.body());
        });
    }

    @Override
    public long getTimerId() {
        return timerId;
    }

    @Override
    public void setHandler(Handler<AsyncResult<Context>> handler) {
        this.handler = handler;
    }

    public Context getContext() {
        if (this.context == null) {
            this.context = new Context();
        }
        return context;
    }
}
