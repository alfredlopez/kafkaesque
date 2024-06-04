package com.asanasoft.common.model.dao;

import com.asanasoft.common.Context;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.SQLConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Created by retproc on 6/28/2017.
 */

public class AlertsSubscriberDAO extends JdbcDAO<JsonArray> {
    private Logger logger = LoggerFactory.getLogger(AlertsSubscriberDAO.class);

    /*
     * This method returns a JsonArray of all alerts_subscriber rows for the requested conditions.
     * Caller supplies requested conditions, if any, in the context parameter.
     * Sample context contents:
     *      "whereClause": "WHERE user_id = 'retproc'"
     * If whereClause is null or does not start with "WHERE", then all rows will be returned.
     */
    @Override
    public void retrieve(Context context, Handler<AsyncResult<JsonArray>> handler) {
        Future<JsonArray> future = Future.future();

        getRawData(context, sqlResult -> {
            JsonArray result = new JsonArray();

            if (sqlResult.succeeded()) {
                List<JsonObject> subscriberRequests = sqlResult.result();

                for (JsonObject request : subscriberRequests) {
                    JsonObject row = new JsonObject();
                    row.put("subscriberId",request.getInteger("subscriber_id"));
                    row.put("subscriberType",request.getString("subscriber_type"));
                    row.put("userId",request.getString("user_id"));
                    row.put("firstName",request.getString("first_name"));
                    row.put("lastName",request.getString("last_name"));
                    row.put("emailAddress",request.getString("email_address"));
                    result.add(row);
                }

                future.complete(result);
            }
            else {
                future.fail(sqlResult.cause());
            }

            handler.handle(future);
        });
    }


    /*
     * This method returns a list of all alerts_subscriber rows for the requested conditions.
     * Caller supplies requested conditions, if any, in the context parameter.
     * Sample context contents:
     *      "whereClause": "WHERE user_id = 'retproc'"
     * If whereClause is null or does not start with "WHERE", then all rows will be returned.
     */
    @Override
    public void getRawData(Context context, Handler<AsyncResult<List<JsonObject>>> handler) {

        String whereClause = (String)context.getValue("whereClause");

        String sqlQuery = "SELECT " +
                "subscriber_id," +
                "subscriber_type," +
                "user_id," +
                "first_name," +
                "last_name," +
                "email_address " +
                " FROM alerts_subscriber ";
        if (whereClause != null && whereClause.startsWith("WHERE")) {
            sqlQuery += whereClause;
        }
        sqlQuery += " ORDER BY subscriber_id";

        context.putValue("sqlQuery", sqlQuery);
        super.getRawData(context, handler);
    }

    /*
     * This method updates a subscriber's name and/or email info.
     */
    public void updateUserDetails(Context context, Handler<AsyncResult<Void>> handler) {
        Future<Void> future = Future.future();
        logger.info("updateUserDetails()...");
        sqlClient.getConnection(connResult -> {
            if (connResult.succeeded()) {
                Integer subscriberId = (Integer)context.getValue("subscriberId");
                String firstName = (String)context.getValue("firstName");
                String lastName = (String)context.getValue("lastName");
                String emailAddress = (String)context.getValue("emailAddress");

                String sql = "UPDATE alerts_subscriber " +
                        "SET first_name = '" + firstName + "'," +
                        "    last_name = '" + lastName + "'," +
                        "    email_address = '" + emailAddress + "' " +
                        "WHERE subscriber_id = " + subscriberId.toString();

                SQLConnection connection = connResult.result();
                connection.execute(sql, sqlResult -> {
                    try {
                        if (sqlResult.succeeded()) {
                            logger.info("User details update succeeded");
                            future.complete();
                        } else {
                            logger.error("User details update failed for subscriber_id " + subscriberId + ", cause: " + sqlResult.cause());
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

    /*
     * This method inserts a row.
     * Caller supplies any non-null column values for the row, except for subscriber_id (the primary key) which is
     * a generated value. The generated value for subscriber_id is returned to the caller.
     */
    public void insert(Context context, Handler<AsyncResult<Integer>> handler) {
        Future<Integer> future = Future.future();
        logger.info("insert()...");
        sqlClient.getConnection(connResult -> {
            if (connResult.succeeded()) {
                String subscriberType = (String)context.getValue("subscriberType");
                String userId = (String)context.getValue("userId");
                String firstName = (String)context.getValue("firstName");
                String lastName = (String)context.getValue("lastName");
                String emailAddress = (String)context.getValue("emailAddress");
                String createDtm = (String)context.getValue("createDtm");

                String sql = " { call pr_insert_to_alerts_subscriber(?, ?, ?, ?, ?, ?, ?) } ";
                JsonArray paramIn = new JsonArray();
                JsonArray paramOut = new JsonArray();
                paramIn.addNull().add(subscriberType).add(userId).add(firstName).add(lastName).add(emailAddress).add(createDtm);
                paramOut.add("INTEGER");  // Note: if OUT param not first, must do preceding ".addNull()" for each preceding IN param

                SQLConnection connection = connResult.result();
                connection.callWithParams(sql, paramIn, paramOut, sqlResult -> {
                    try {
                        if (sqlResult.succeeded()) {
                            JsonObject json = sqlResult.result().toJson();
                            JsonArray output = json.getJsonArray("output");
                            Integer id = output.getInteger(0);
                            logger.debug("proc returned = " + id);
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
