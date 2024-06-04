package com.asanasoft.common.service.cron.impl;

import com.asanasoft.common.Context;
import com.asanasoft.common.init.impl.Environment;
import com.asanasoft.common.model.dao.AlertDAO;
import com.asanasoft.common.model.dao.AlertsSubscriberAlertDAO;
import com.asanasoft.common.model.dao.AlertsSubscriberDAO;
import com.asanasoft.common.model.dao.AlertsSubscriberRequestDAO;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mail.MailClient;
import io.vertx.ext.mail.MailConfig;
import io.vertx.ext.mail.MailMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;


/**
 * This program receives messages, each detailing an event that has occurred that might need alerts generated.
 * This program determines what alerts, if any, to generate, and then generates them. Generating an alert consists
 * of preparing and inserting a row to the "alert" table, then propagating that alert to individual subscribers
 * by inserting rows to the "alerts_subscriber_alert" table as appropriate. A given subscriber gets a row inserted
 * only if they have subscribed to receive that type of alert.
 *
 * This program uses these tables:
 *      alert - Each row contains an alert.
 *      alerts_subscriber - A user or email group that is known to this program and thus can subscribe to alerts.
 *      alerts_subscriber_request - Each row defines a request, by a given subscriber, to receive alerts of a certain type.
 *      alerts_subscriber_alert - Each row contains an alert for an individual subscriber.
 *
 * Created by Ken Proctor on 6/28/2017.
 */

public class AlertsGenerator {
    private Logger logger = LoggerFactory.getLogger(AlertsGenerator.class);

    protected Vertx vertx;
    protected String alertsDataSourceName = null;
    protected String runningLevel = null;
    protected String alertsFromEmailAddress = null;
    protected MailClient mailClient = null;

    public final static String EVENT_TYPE_ABNORMAL_ROW_COUNT = "Abnormal Row Count";
    public final static String EVENT_TYPE_USER_LOGIN = "User Login";

    public AlertsGenerator(Vertx vertx) {
        this.vertx = vertx;
        Environment env = Environment.getInstance();
        this.alertsDataSourceName = env.getString("alertsDataSourceName");
        this.runningLevel = env.getRunningEnv();
        this.alertsFromEmailAddress = env.getString("alertsFromEmailAddress");

        MailConfig mailConfig = new MailConfig();
        mailConfig.setHostname((String) env.getValue("smtpHost"));
        mailConfig.setPort(Integer.parseInt((String) env.getValue("smtpPort")));
        mailConfig.setUsername((String) env.getValue("smtpUser"));
        mailConfig.setPassword((String) env.getValue("smtpPassword"));
        this.mailClient = MailClient.createShared(this.vertx, mailConfig, "connectionPoolPMAR");
    }

    /*
     * This method receives messages detailing alert-worthy events and generates alerts.
     */
    public void handleMessage(JsonObject message) {

        String eventType = message.getString("eventType");

        String msgTimestamp = establishTimestamp();
        logger.info("AlertEvent of type " + eventType + " assigned timestamp " + msgTimestamp);

        if (EVENT_TYPE_ABNORMAL_ROW_COUNT.equals(eventType)) {
            handleAbnormalRowCount(message, msgTimestamp);
        } else if (EVENT_TYPE_USER_LOGIN.equals(eventType)) {
            handleUserLogin(message, msgTimestamp);
        } else {
            logger.info("Ignoring invalid AlertEvent message - unrecognized eventType: " + eventType);
        }
    }

    /*
     * This method handles a DataMonitor event in which a table was found to have an unexpected number
     * of rows that satisfied a particular condition, such as rows for previous day.
     */
    protected void handleAbnormalRowCount(JsonObject message, String msgTimestamp) {

        String alertType = message.getString("alertType");
        String tableOrViewName = message.getString("tableOrViewName");
        String whereCondition = message.getString("whereCondition");
        int rowCount = message.getInteger("rowCount");
        int loThreshold = message.getInteger("loThreshold");
        int hiThreshold = message.getInteger("hiThreshold");

        String originator = "Investment Division Portal";

        if (alertType.equals("MissingDailyPerfData")) {
            String prevDate = message.getString("prevDate");

            String shortMessage = "Missing daily performance data for " + prevDate;
            String longMessage = null;
            if (rowCount == 0) {
                longMessage = "No daily data has been loaded on the Investment Division Portal as of " +
                        msgTimestamp.substring(0, 16) + " for " + prevDate + ".";
            } else {
                longMessage = "For " + tableOrViewName + ", row count as of " + msgTimestamp.substring(0, 16) +
                        " is " + rowCount + ", which is outside the expected range of " + loThreshold + " to " + hiThreshold + " rows.";
                if (whereCondition != null && !whereCondition.isEmpty()) {
                    longMessage += "\nRow count is for the following conditions: \n" + whereCondition;
                }
            }

            insertAlertAndPropagateToSubscribers(msgTimestamp, alertType, originator, shortMessage, longMessage);

        } else {
            logger.info("Ignoring invalid row count AlertEvent message - unrecognized alertType: " + alertType);
        }

    }

    /*
     * This method handles a user login event.
     * If the user is not yet present on the alerts subscriber table, a row is added. If present but
     * their info has changed, the existing row is updated.
     */
    protected void handleUserLogin(JsonObject message, String msgTimestamp) {

        String userId = message.getString("userId");
        String firstName = message.getString("firstName");
        String lastName = message.getString("lastName");
        String emailAddress = message.getString("emailAddress");

        logger.debug("Retrieving user from alerts_subscriber...");
        AlertsSubscriberDAO dao = getAlertsSubscriberDAO();
        Context context1 = new Context();
        context1.putValue("whereClause","WHERE user_id = '" + userId.toLowerCase() + "'");
        dao.retrieve(context1, res1 -> {
            if (res1.succeeded()) {
                JsonArray rows = res1.result();
                if (rows.size() == 0) {
                    // Add user.
                    logger.debug("User not found - adding new user...");
                    Context context2 = new Context();
                    context2.putValue("subscriberType","user");
                    context2.putValue("userId",userId.toLowerCase());
                    context2.putValue("firstName",firstName);
                    context2.putValue("lastName",lastName);
                    context2.putValue("emailAddress",emailAddress);
                    context2.putValue("createDtm",msgTimestamp);
                    dao.insert(context2, res2 -> {
                        if (res2.succeeded()) {
                            Integer subscriberId = res2.result();
                            logger.info("New user " + subscriberId + " " + userId + " added: " + firstName + " " + lastName + " " + emailAddress);

                            insertDefaultRequestRowsForUser(subscriberId);
                        } else {
                            logger.error("Insert of new user " + userId + " at login failed! Cause: " + res2.cause());
                        }
                    });
                } else {
                    // Existing user - check for changes.
                    logger.debug("Found existing user");
                    JsonObject row = rows.getJsonObject(0);
                    Integer subscriberId = row.getInteger("subscriberId");
                    String firstNameDb = row.getString("firstName");
                    String lastNameDb = row.getString("lastName");
                    String emailAddressDb = row.getString("emailAddress");
                    if ((firstNameDb == null && firstName != null) ||
                        (firstNameDb != null && firstName == null) ||
                        (firstNameDb != null && !firstNameDb.equals(firstName)) ||
                        (lastNameDb == null && lastName != null) ||
                        (lastNameDb != null && lastName == null) ||
                        (lastNameDb != null && !lastNameDb.equals(lastName)) ||
                        (emailAddressDb == null && emailAddress != null) ||
                        (emailAddressDb != null && emailAddress == null) ||
                        (emailAddressDb != null && !emailAddressDb.equals(emailAddress))
                       )
                    {
                        // Update existing row.
                        logger.info("Updating user name and/or email info...");
                        Context context3 = new Context();
                        context3.putValue("subscriberId",subscriberId);
                        context3.putValue("firstName",firstName);
                        context3.putValue("lastName",lastName);
                        context3.putValue("emailAddress",emailAddress);
                        dao.updateUserDetails(context3, res3 -> {
                            if (res3.succeeded()) {
                                logger.info("Existing user " + subscriberId + " " + userId + " updated to: " + firstName + " " + lastName + " " + emailAddress);
                            } else {
                                logger.error("Update of existing user " + userId + " at login failed! Cause: " + res3.cause());
                            }
                        });
                    }
                }
            } else {
                logger.error("Query of user " + userId + " at login failed! Cause: " + res1.cause());
            }
        });
    }

    /*
     * This method inserts default alerts_subscriber_request rows for the supplied subscriber.
     */
    protected void insertDefaultRequestRowsForUser(Integer subscriberId) {
        // Future enhancement...............................
    }

    /*
     * This method inserts a row to table 'alert', then inserts a row to alerts_subscriber_alert for each subscriber
     * to alerts of that type.
     */
    protected void insertAlertAndPropagateToSubscribers(String msgTimestamp, String alertType,
                                                        String originator, String shortMessage, String longMessage) {
        // Insert an alert.
        logger.info("Inserting to alert...");
        AlertDAO alertDAO = getAlertDAO();
        Context daoContext = new Context();
        daoContext.putValue("createDtm", msgTimestamp);
        daoContext.putValue("alertType", alertType);
        daoContext.putValue("originator", originator);
        daoContext.putValue("shortMessage", shortMessage);
        daoContext.putValue("longMessage", longMessage);

        alertDAO.insert(daoContext, res0 -> {
            if (res0.succeeded()) {
                Integer alertId = res0.result();
                logger.info("Inserted alertId " + alertId);

                Context context1 = new Context();
                context1.put("alertId", alertId);

                // Propagate alert to subscribers who requested notification via PMAR Portal alerts.
                propagateToSubscribers(context1, res1 -> {
                    if (res1.succeeded()) {
                        logger.debug ("Alert propagated to subscribers");

                        Context context2 = new Context();
                        context2.put("alertType", alertType);
                        context2.putValue("originator", originator);
                        context2.putValue("shortMessage", shortMessage);
                        context2.putValue("longMessage", longMessage);

                        // Propagate alert to subscribers who requested notification via email.
                        sendEmailsToSubscribers(context2, res2 -> {
                            if (res2.succeeded()) {
                                Integer sentCt = res2.result();
                                logger.debug ("Alert emails sent to " + sentCt + " subscribers");
                            } else {
                                logger.error("Failed propagating alert to subscribers (as emails)! Alert details: " + daoContext.toString() + " Cause: " + res2.cause());
                            }
                        });
                    } else {
                        logger.error("Failed propagating alert to subscribers (as alerts)! Alert details: " + daoContext.toString() + " Cause: " + res1.cause());
                    }
                });
            } else {
                logger.error("Failed inserting alert! Alert details: " + daoContext.toString() + " Cause: " + res0.cause());
            }
        });
    }

    /**
     * This method inserts a row to alerts_subscriber_alert for each subscriber that requested alerts of this type.
     * For users who requested notification via email only, the row that gets inserted is marked deleted=1 (true) so
     * that it will not be retrieved for online display of user alerts. Otherwise it is marked deleted=0 (false).
     */
    protected void propagateToSubscribers(Context context, Handler<AsyncResult<Void>> handler) {
        Future<Void> future = Future.future();
        logger.info("Inserting to alerts_subscriber_alert...");

        Integer alertId = (Integer)context.getValue("alertId");

        AlertsSubscriberAlertDAO subscriberAlertDAO = getAlertsSubscriberAlertDAO();
        Context daoContext = new Context();
        daoContext.putValue("alertId", alertId);

        subscriberAlertDAO.insertAllForAlertId(daoContext, res0 -> {
            if (res0.succeeded()) {
                future.complete();
                handler.handle(future);
            } else {
                future.fail(res0.cause());
                handler.handle(future);
            }
        });
    }

    /**
     * This method inserts a row to alerts_subscriber_alert for each subscriber that requested alerts of this type
     * with notification via email.
     */
    protected void sendEmailsToSubscribers(Context context, Handler<AsyncResult<Integer>> handler) {
        Future<Integer> future = Future.future();
        logger.info("Sending emails to subscribers...");

        String alertType = (String)context.getValue("alertType");
        String originator = (String)context.getValue("originator");
        String shortMessage = (String)context.getValue("shortMessage");
        String longMessage = (String)context.getValue("longMessage");

        // Format email subject and body.
        String subject = shortMessage;
        String body = longMessage + "\n\n" + originator;
        if (!this.runningLevel.equals("PROD")) {
            subject += " (" + this.runningLevel + " test)";
            body += "\n\n** This email sent from the " + this.runningLevel + " test environment **";
        }

        // Prepare portions of email that are common to all recipients.
        MailMessage mailMsg = new MailMessage();
        mailMsg.setFrom(alertsFromEmailAddress);
        //mailMsg.setBcc(alertsFromEmailAddress);
        mailMsg.setSubject(subject);
        mailMsg.setText(body);

        // Get email address of each subscriber who requested alerts of this type with notification via email.
        AlertsSubscriberRequestDAO requestDAO = getAlertsSubscriberRequestDAO();
        Context daoContext = new Context();
        daoContext.putValue("whereClause", "WHERE notification_type IN ('email','both') AND alert_type = '" + alertType + "'");

        requestDAO.retrieve(daoContext, res0 -> {
            if (res0.succeeded()) {
                // Send an email to each subscriber.
                JsonArray rows = res0.result();
                Context context1 = new Context();
                context1.putValue("indexOfItemToProcess", new Integer(0));
                context1.putValue("subscribers", rows);
                context1.putValue("mailMsg", mailMsg);

                // Method recursively processes all subscribers sequentially, one at a time
                sendEmailToSubscriber(context1, res1 -> {
                    Integer emailCt = rows.size();
                    future.complete(emailCt);
                    handler.handle(future);
                });
            } else {
                logger.error("Retrieving email recipients failed for alert type " + alertType + ". Cause: " + res0.cause());
                future.fail("Retrieving email recipients failed");
                handler.handle(future);
            }
        });
    }

    /**
     * This method sends an email for one subscriber in the subscriber array, then recursively calls itself to
     * process the next subscriber in the array.
     */
    protected void sendEmailToSubscriber(Context context1, Handler<AsyncResult<Void>> handler) {
        Future<Void> future = Future.future();

        int indexOfItemToProcess = (Integer)context1.get("indexOfItemToProcess");
        JsonArray subscribers = (JsonArray)context1.get("subscribers");
        MailMessage mailMsg = (MailMessage) context1.getValue("mailMsg");
        int targetCount = subscribers.size();

        // Are we done?
        if (indexOfItemToProcess == targetCount) {
            // Done - success.
            logger.debug("Total number of emails sent: " + targetCount);
            future.complete();
            handler.handle(future);
        } else {
            JsonObject subscriber = subscribers.getJsonObject(indexOfItemToProcess);
            String emailAddress = subscriber.getString("emailAddress");
            mailMsg.setTo(emailAddress);

            // Send email.
            mailClient.sendMail(mailMsg, mailResult -> {
                if (mailResult.succeeded()) {
                    logger.debug("Email sent successfully to " + emailAddress);
                } else {
                    logger.error("Email failed to send! Mail msg: " + mailMsg.toString());
                }
                // Call self to send next email.
                context1.putValue("indexOfItemToProcess", indexOfItemToProcess + 1);

                sendEmailToSubscriber(context1, handler);
            });
        }
    }

    /**
     * This method establishes the timestamp to be used as the "create" timestamp for all rows inserted for
     * the message being processed. (Nothing in the code relies on these timestamps matching - we just use one
     * timestamp as a convenience for humans looking at table rows.)
     * Format of string: "2017-02-28 14:53:38.0"
     */
    protected String establishTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S");
        return sdf.format(new Date());
    }

    /*
     * This method creates and initializes a DAO object to access standard alerts.
     */
    protected AlertDAO getAlertDAO() {
        AlertDAO dao = new AlertDAO();
        Context context = new Context();
        context.putValue("dataSourceName", this.alertsDataSourceName);
        context.putValue("vertx", this.vertx);
        dao.init(context, initResult -> {});

        return dao;
    }

    /*
     * This method creates and initializes a DAO object to access standard alerts.
     */
    protected AlertsSubscriberDAO getAlertsSubscriberDAO() {
        AlertsSubscriberDAO dao = new AlertsSubscriberDAO();
        Context context = new Context();
        context.putValue("dataSourceName", this.alertsDataSourceName);
        context.putValue("vertx", this.vertx);
        dao.init(context, initResult -> {});

        return dao;
    }

    /*
     * This method creates and initializes a DAO object to access user alert requests.
     */
    protected AlertsSubscriberRequestDAO getAlertsSubscriberRequestDAO() {
        AlertsSubscriberRequestDAO dao = new AlertsSubscriberRequestDAO();
        Context context = new Context();
        context.putValue("dataSourceName", this.alertsDataSourceName);
        context.putValue("vertx", this.vertx);
        dao.init(context, initResult -> {});

        return dao;
    }

    /*
     * This method creates and initializes a DAO object to access user alerts.
     */
    protected AlertsSubscriberAlertDAO getAlertsSubscriberAlertDAO() {
        AlertsSubscriberAlertDAO dao = new AlertsSubscriberAlertDAO();
        Context context = new Context();
        context.putValue("dataSourceName", this.alertsDataSourceName);
        context.putValue("vertx", this.vertx);
        dao.init(context, initResult -> {});

        return dao;
    }

}
