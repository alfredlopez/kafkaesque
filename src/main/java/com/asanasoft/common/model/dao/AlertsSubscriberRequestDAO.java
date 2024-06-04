package com.asanasoft.common.model.dao;

//import com.jcabi.aspects.Loggable;
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
 * Created by retproc on 6/21/2017.
 */
public class AlertsSubscriberRequestDAO extends JdbcDAO<JsonArray> {
    private Logger logger = LoggerFactory.getLogger(AlertsSubscriberRequestDAO.class);

    /*
     * This method returns a JsonArray of all alerts_subscriber_request rows (joined with columns from alerts_subscriber)
     * for the requested conditions. Caller supplies requested conditions, if any, in the context parameter.
     * Sample context contents:
     *      "whereClause": "WHERE alert_type = 'Missing Data'"
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
                    row.put("alertType",request.getString("alert_type"));
                    row.put("notificationType",request.getString("notification_type"));
                    row.put("createDtm",request.getString("create_dtm"));
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
     * This method returns a list of all alerts_subscriber_request rows for the requested conditions.
     * Caller supplies requested conditions, if any, in the context parameter.
     * Sample context contents:
     *      "whereClause": "WHERE alert_type = 'Missing Data'"
     * If whereClause is null or does not start with "WHERE", then all rows will be returned.
     */
    @Override
    public void getRawData(Context context, Handler<AsyncResult<List<JsonObject>>> handler) {

        String whereClause = (String)context.getValue("whereClause");

        String sqlQuery = "SELECT " +
                            "r.subscriber_id," +
                            "r.alert_type," +
                            "r.notification_type," +
                            "r.create_dtm," +
                            "s.subscriber_type," +
                            "s.user_id," +
                            "s.first_name," +
                            "s.last_name," +
                            "s.email_address " +
                          " FROM alerts_subscriber_request r " +
                          "     JOIN alerts_subscriber s ON (s.subscriber_id = r.subscriber_id) ";
        if (whereClause != null && whereClause.startsWith("WHERE")) {
            sqlQuery += whereClause;
        }
        sqlQuery += " ORDER BY r.subscriber_id, r.alert_type";

        context.putValue("sqlQuery", sqlQuery);
        super.getRawData(context, handler);
    }

    /*
     * This method inserts a row. Caller supplies all column values.
     */
    public void insert(Context context, Handler<AsyncResult<Void>> handler) {

        // Get supplied values to insert.
        Integer subscriberId = (Integer)context.getValue("subscriberId");
        String alertType = (String)context.getValue("alertType");
        String notificationType = (String)context.getValue("notificationType");
        String createDtm = (String)context.getValue("createDtm");

        // Insert row.
        String sqlStmt = "INSERT INTO alerts_subscriber_request " +
                "(subscriber_id, alert_type, notification_type, create_dtm) " +
                "VALUES (" +
                subscriberId.toString() + "," +
                "'" + alertType + "'," +
                "'" + notificationType + "'," +
                "'" + createDtm + "'" +
                ")";

        doUpdate(sqlStmt, handler);
    }

    /*
     * This method executes an INSERT, UPDATE, or DELETE sql statement.
     */
    protected void doUpdate(String sql, Handler<AsyncResult<Void>> handler) {
        Future<Void> future = Future.future();

        sqlClient.getConnection(connResult -> {
            if (connResult.succeeded()) {
                SQLConnection sqlConnection = connResult.result();

                sqlConnection.execute(sql, updateResult -> {
                    try {
                        if (updateResult.succeeded()) {
                            future.complete();
                        } else {
                            logger.error("Update failed! Sql: " + sql + "\n Cause: " + updateResult.cause());
                            future.fail("Update failed! " + updateResult.cause());
                        }
                    } catch (Exception e) {
                        logger.error("An error occurred:", e);
                        future.fail(e);
                    } finally {
                        sqlConnection.close();
                        handler.handle(future);
                    }
                });
            }
            else {
                logger.error("Failed to get sql connection: " + connResult.cause());
                future.fail("Failed to get sql connection: " + connResult.cause());
                handler.handle(future);
            }
        });
    }

}