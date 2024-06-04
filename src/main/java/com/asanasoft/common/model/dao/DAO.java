package com.asanasoft.common.model.dao;

import com.asanasoft.common.Context;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

/**
 * Created by retproc on 5/5/2017.
 */
public interface DAO<T,R> {

    void init(Context context, Handler<AsyncResult> handler);

    void retrieve(Context context, Handler<AsyncResult<T>> handler);

    void getRawData(Context context, Handler<AsyncResult<R>> handler);

    void dropEntities(Context context, Handler<AsyncResult> handler);

    void createEntities(Context context, Handler<AsyncResult> handler);

    boolean isReady();

    void setReady(boolean newReady);
}
