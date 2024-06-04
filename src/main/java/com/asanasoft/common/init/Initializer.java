package com.asanasoft.common.init;

import com.asanasoft.common.Context;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

/**
 * Created by lopealf on 6/7/17.
 */
public interface Initializer {
    void init(Context newContext, Handler<AsyncResult<Boolean>> handler);
    boolean init();
    boolean init(Context newContext);
    Context getResult();
}
