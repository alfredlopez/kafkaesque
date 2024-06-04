package com.asanasoft.common.service.cron;

import com.asanasoft.common.Context;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

/**
 * Created by lopealf on 7/3/17.
 */
public interface PeriodicWorker {
    void setContext(Context context);
    void setPeriodic(long period, Handler<AsyncResult<Context>> handler);
    boolean cancelPeriodic();
    void fireRequest(JsonObject message);
    long getTimerId();
    void setHandler(Handler<AsyncResult<Context>> handler);
    void setFireAddress(String fireAddress);
    String getFireAddress();
}
