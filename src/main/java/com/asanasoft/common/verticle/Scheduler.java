package com.asanasoft.common.verticle;

import com.asanasoft.common.init.impl.Environment;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.jobs.NoOpJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

public class Scheduler extends DefaultQuartzVerticle {
    final public static long START_NOW = -1;
    private Logger logger = LoggerFactory.getLogger(Scheduler.class);
    private org.quartz.Scheduler scheduler = null;
    private MessageConsumer<JsonObject> messageConsumer = null;
    private MessageConsumer<TriggerListener> registrationConsumer = null;

    public void getScheduler(Handler<AsyncResult<org.quartz.Scheduler>> handler) {
        Future<org.quartz.Scheduler> future = Future.future();

        vertx.executeBlocking(f -> {
            if (scheduler == null) {
                try {
                    StdSchedulerFactory schedFactory = new StdSchedulerFactory();
                    schedFactory.initialize("quartz_" + Environment.getInstance().getValue("RUNNING_ENV") + ".properties");
                    scheduler = schedFactory.getScheduler();
                    scheduler.start();
                    f.complete(scheduler);
                } catch (SchedulerException e) {
                    logger.error("Couldn't acquire scheduler...", e);
                    f.fail(e);
                }
            }
            else {
                f.complete(scheduler);
            }
        }, r -> {
            if (r.succeeded()) {
                future.complete((org.quartz.Scheduler)r.result());
            }
            else {
                future.fail(r.cause());
            }

            handler.handle(future);
        });
    }

    @Override
    public void start() throws Exception {
        logger.debug("Starting Scheduler...");

        getScheduler(schedulerResult -> {
            if (schedulerResult.succeeded()) {
                logger.debug("Scheduler started, notifying app...");

                messageConsumer      = vertx.eventBus().localConsumer("Scheduler");
                registrationConsumer = vertx.eventBus().localConsumer("Scheduler.registerListener");

                messageConsumer.handler(scheduleMessage -> {
                    JsonObject message = scheduleMessage.body();

                    logger.debug("Received job: \n" + message.encodePrettily());

                    JobDetail job = newJob(NoOpJob.class).withIdentity(message.getString("jobName"), message.getString("jobGroup")).build();
                    TriggerBuilder  triggerBuilder = newTrigger().withIdentity(message.getString("triggerName"));
                    Long startTime = message.getLong("startAt");
                    String cronSchedule = message.getString("cron");

                    if (startTime != null && startTime != START_NOW) {
                        triggerBuilder = triggerBuilder.startAt(new Date(startTime));
                    }
                    else if (startTime != null && startTime == START_NOW) {
                        triggerBuilder = triggerBuilder.startNow();
                    }

                    if (cronSchedule != null) {
                        triggerBuilder.withSchedule(CronScheduleBuilder.cronSchedule(cronSchedule));
                    }

                    try {
                        scheduler.scheduleJob(job, triggerBuilder.build());
                    } catch (SchedulerException e) {
                        logger.error("An error occurred manually starting job " + message.getString("jobName"), e);
                    }
                });

                registrationConsumer.handler(triggerListenerMessage -> {
                    TriggerListener listener = triggerListenerMessage.body();

                    logger.debug("Registering listener " + listener.getName());

                    try {
                        scheduler.getListenerManager().addTriggerListener(listener);
                    } catch (SchedulerException e) {
                        logger.error("An error occurred adding listener:", e);
                    }
                });
            }
            else {
                logger.error("An error occurred getting Scheduler...", schedulerResult.cause());
            }
        });
    }

    @Override
    public void stop() throws Exception {
        scheduler.shutdown();
    }
}
