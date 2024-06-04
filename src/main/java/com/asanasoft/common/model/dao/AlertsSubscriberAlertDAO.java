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
public class AlertsSubscriberAlertDAO extends JdbcDAO<JsonArray> {
    private Logger logger = LoggerFactory.getLogger(AlertsSubscriberAlertDAO.class);

    /*
     * This method returns a JsonArray of all alerts_subscriber_alert rows for the requested user_id.
     * All unread rows (in descending order by create_dtm) appear first, followed by
     * all read rows (in descending order by create_dtm).
     * For example:
     *                  [
     *                    {alertId: 'ALERT120',
     *                     isUnread: false,
     *                     createDtm: '2017-01-22 15:37:54.0',
     *                     originator: 'Performance Team',
     *                     shortMessage: 'New card: Asset Class By Jove'},
     *                    {alertId: 'ALERT141',
     *                     isUnread: true,
     *                     createDtm: '2017-01-08 09:07:03.0',
     *                     originator: 'Performance Team',
     *                     shortMessage: 'New card: Strategy Roundup'}
     *                  ]
     */
    @Override
    public void retrieve(Context context, Handler<AsyncResult<JsonArray>> handler) {
        JsonArray result = new JsonArray();
        JsonArray hasBeenRead = new JsonArray();

        getRawData(context, results -> {
            Future<JsonArray> future = Future.future();
            List<JsonObject> resultSet;

            if (results.succeeded()) {
                resultSet = results.result();
                List<JsonObject> alerts = resultSet;

                for (JsonObject record : alerts) {
                    // Store alert in final result array (if "unread") or temp read array (if "read").
                    JsonObject row = new JsonObject();

                    String alertId      = record.getString("alert_id");
                    boolean isUnread    = record.getBoolean("is_unread");
                    //Apparently, SQLConnection is returning the dates as yyyy-mm-ddTHH:mm:sss.S. We need the date without the "T"
                    String createDtm    = record.getString("create_dtm").replace("T", " ");
                    String originator   = record.getString("originator");
                    String shortMessage = record.getString("short_message");
                    String longMessage  = record.getString("long_message");

                    row.put("alertId",alertId);
                    row.put("isUnread",isUnread);
                    row.put("createDtm",createDtm);
                    row.put("originator",originator);
                    row.put("shortMessage",shortMessage);
                    row.put("fullMessage", longMessage);

                    if (isUnread) {
                        result.add(row);
                    } else {
                        hasBeenRead.add(row);
                    }
                }

                // Append any read alerts to result.
                for (int i = 0; i < hasBeenRead.size(); i++) {
                    result.add(hasBeenRead.getJsonObject(i));
                }

                future.complete(result);
            }
            else {
                future.fail(results.cause());
            }

            handler.handle(future);
        });
    }

    /*
     * This method returns a list of all alerts_subscriber_alert rows for the requested user,
     * in descending order by create_dtm.
     */
    @Override
    public void getRawData(Context context, Handler<AsyncResult<List<JsonObject>>> handler) {
        String userId = (String)context.getValue("user");
        String userIdLowerCase = userId.toLowerCase();
        String sqlQuery = "SELECT CONCAT('ALERT',CAST(sa.alert_id AS CHAR)) AS alert_id, " +
                    "sa.is_unread, a.create_dtm, a.originator, a.short_message, a.long_message " +
                "FROM alerts_subscriber_alert sa " +
                "    JOIN alert a ON (a.alert_id = sa.alert_id) " +
                "    JOIN alerts_subscriber s ON (s.subscriber_id = sa.subscriber_id) " +
                "WHERE s.user_id = '" + userIdLowerCase + "' " +
                "AND sa.deleted = 0 " +
                "ORDER BY a.create_dtm DESC";

        context.putValue("sqlQuery", sqlQuery);
        super.getRawData(context, handler);
    }

    /*
     * This method inserts a row. Caller supplies all column values except is_unread, which is always inserted with value true.
     */
    public void insert(Context context, Handler<AsyncResult<Boolean>> handler) {

        // Get supplied values to insert.
        Integer subscriberId = (Integer)context.getValue("subscriberId");
        Integer alertId = (Integer)context.getValue("alertId");

        // Insert row.
        String sqlStmt = "INSERT INTO alerts_subscriber_alert " +
                "(subscriber_id, alert_id, is_unread, deleted) " +
                "VALUES (" +
                subscriberId.toString() + "," +
                alertId.toString() + "," +
                "true" +                        // is_unread
                ")";

        doUpdate(sqlStmt, handler);
    }

    /*
     * This method inserts a row for each subscriber that has requested alerts of the type for the supplied alert_id.
     */
    public void insertAllForAlertId(Context context, Handler<AsyncResult<Boolean>> handler) {

        // Get supplied values.
        Integer alertId = (Integer)context.getValue("alertId");

        // Insert row.
        String sqlStmt = "INSERT INTO alerts_subscriber_alert " +
                "(subscriber_id, alert_id, is_unread, deleted) " +
                "SELECT r.subscriber_id, a.alert_id, true, CASE WHEN notification_type = 'email' THEN 1 ELSE 0 END " +
                "FROM alert a " +
                "    JOIN alerts_subscriber_request r ON (r.alert_type = a.alert_type) " +
                "WHERE a.alert_id = " + alertId.toString();

        doUpdate(sqlStmt, handler);
    }

    /*
     * This method updates the requested alerts, for the requested user_id, to "read".
     */
    public void markAsRead(Context context, Handler<AsyncResult<Boolean>> handler) {

        String userId = (String)context.getValue("userName");
        String userIdLowerCase = userId.toLowerCase();
        JsonArray alertIds = (JsonArray)context.getValue("ids");
        // From supplied JsonArray, build id list to use in WHERE clause, e.g. "73,298,15"
        String idList = buildWhereClauseListFromJsonArray(alertIds);

        String sqlStmt = "UPDATE alerts_subscriber_alert " +
                " SET is_unread = false " +
                " WHERE subscriber_id = " +
                "         (SELECT subscriber_id FROM alerts_subscriber " +
                "          WHERE user_id = '" + userIdLowerCase + "')" +
                "   AND alert_id IN (" + idList + ")";

        doUpdate(sqlStmt, handler);
    }

    /*
     * This method soft-deletes the requested alerts, for the requested user_id.
     */
    public void delete(Context context, Handler<AsyncResult<Boolean>> handler) {

        String userId = (String)context.getValue("userName");
        String userIdLowerCase = userId.toLowerCase();
        JsonArray alertIds = (JsonArray)context.getValue("ids");
        // From supplied JsonArray, build id list to use in WHERE clause, e.g. "73,298,15"
        String idList = buildWhereClauseListFromJsonArray(alertIds);

        String sqlStmtSoftDel = "UPDATE alerts_subscriber_alert " +
                " SET deleted=1" +
                " WHERE subscriber_id = " +
                "         (SELECT subscriber_id FROM alerts_subscriber " +
                "          WHERE user_id = '" + userIdLowerCase + "')" +
                "   AND alert_id IN (" + idList + ")";

        doUpdate(sqlStmtSoftDel, handler);
    }

    /*
     * This method soft-deletes all alerts for the user that have alert id <= the supplied id.
     * The "<=" condition is used to avoid deleting brand new alerts that the front end is not even aware of yet.
     */
    public void deleteAllThruId(Context context, Handler<AsyncResult<Boolean>> handler) {
        String userId = (String)context.getValue("userName");
        String userIdLowerCase = userId.toLowerCase();
        String thruAlertId = (String)context.getValue("thruAlertId");
        Integer thruAlertIdInteger = new Integer(thruAlertId.substring(5));      // strips leading 'ALERT'
        String sqlStmtSoftDel = "UPDATE alerts_subscriber_alert " +
                " SET deleted=1" +
                " WHERE subscriber_id = " +
                "         (SELECT subscriber_id FROM alerts_subscriber " +
                "          WHERE user_id = '" + userIdLowerCase + "')" +
                "   AND alert_id <= " + thruAlertIdInteger +
                "   AND deleted != 1";

        doUpdate(sqlStmtSoftDel, handler);
    }

    /*
     * This method executes an INSERT, UPDATE, or DELETE sql statement.
     */
    protected void doUpdate(String sql, Handler<AsyncResult<Boolean>> handler) {
        Future<Boolean> future = Future.future();

        sqlClient.getConnection(connResult -> {
            if (connResult.succeeded()) {
                SQLConnection sqlConnection = connResult.result();

                sqlConnection.execute(sql, updateResult -> {
                    try {
                        Boolean result = new Boolean(updateResult.succeeded());
                        future.complete(result);
                        handler.handle(future);
                    } catch (Exception e) {
                        logger.error("An error occurred:", e);
                        future.complete(new Boolean(false));
                        handler.handle(future);
                    } finally {
                        sqlConnection.close();
                    }
                });
            }
            else {
                future.complete(new Boolean(false));
                handler.handle(future);
            }
        });
    }

    /*
     * This method translates a JsonArray of ids in string format, e.g.:
     *      ["ALERT73","ALERT298","ALERT15"]
     * into a string containing a comma-delimited list of ids in integer format, e.g.:
     *      "73,298,15"
     */
    protected String buildWhereClauseListFromJsonArray(JsonArray array) {
        String list = "";
        for (int i = 0; i < array.size(); i++) {
            String idString = array.getString(i);
            if (!list.isEmpty()) {
                list += ",";
            }
            Integer idInteger = new Integer(idString.substring(5));
            list += idInteger.toString();
        }
        return list;
    }

}