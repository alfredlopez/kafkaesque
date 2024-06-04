package com.asanasoft.common.model.dao;

//import com.jcabi.aspects.Loggable;
import com.asanasoft.common.Context;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Created by retproc on 6/28/2017.
 */
public class AlertDAO extends JdbcDAO<JsonArray> {
    private Logger logger = LoggerFactory.getLogger(AlertDAO.class);

    /*
     * This is a dummy override method.
     */
    @Override
    public void retrieve(Context context, Handler<AsyncResult<JsonArray>> handler) {
        JsonArray result = new JsonArray();
    }

    /*
     * This is a dummy override method.
     */
    @Override
    public void getRawData(Context context, Handler<AsyncResult<List<JsonObject>>> handler) {
        ResultSet result = null;
    }

    /*
     * This method inserts a row.
     * Caller supplies any non-null column values for the row, except for alert_id (the primary key) which is
     * a generated value. The generated value for alert_id is returned to the caller.
     */
    public void insert(Context context, Handler<AsyncResult<Integer>> handler) {
        Future<Integer> future = Future.future();
        logger.info("insert()...");
        sqlClient.getConnection(connResult -> {
            if (connResult.succeeded()) {
                String createDtm = (String)context.getValue("createDtm");
                String alertType = (String)context.getValue("alertType");
                String originator = (String)context.getValue("originator");
                String shortMessage = (String)context.getValue("shortMessage");
                String longMessage = (String)context.getValue("longMessage");

                String sql = " { call pr_insert_to_alert(?, ?, ?, ?, ?, ?) } ";
                JsonArray paramIn = new JsonArray();
                JsonArray paramOut = new JsonArray();
                paramIn.addNull().add(createDtm).add(alertType).add(originator).add(shortMessage).add(longMessage);
                paramOut.add("INTEGER");  // Note: if OUT param not first, must do preceding ".addNull()" for each preceding IN param

                SQLConnection connection = connResult.result();
                connection.callWithParams(sql, paramIn, paramOut, sqlResult -> {
                    try {
                        if (sqlResult.succeeded()) {
                            JsonObject json = sqlResult.result().toJson();
                            JsonArray output = json.getJsonArray("output");
                            Integer id = output.getInteger(0);
                            logger.debug("proc returned " + id);
                            future.complete(id);
                        } else {
                            logger.error("proc failed cause: " + sqlResult.cause());
                            sqlResult.cause().printStackTrace();
                            future.fail(sqlResult.cause());
                        }
                        handler.handle(future);
                    } catch (Exception e) {
                        logger.error("An error occurred:", e);
                    } finally {
                        connection.close();
                    }
                });
            }
            else {
                logger.error("getConnection failed cause: " + connResult.cause());
                future.fail(connResult.cause());
                handler.handle(future);
            }
        });
    }
}
