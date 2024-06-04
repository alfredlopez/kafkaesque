package com.asanasoft.common.service.cron.impl;

import io.vertx.core.json.JsonObject;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract public class AbstractQuartzPeriodicWorker extends AbstractPeriodicWorker implements TriggerListener, JobListener {
    private Logger logger = LoggerFactory.getLogger(AbstractQuartzPeriodicWorker.class);
    private String name = null;

    @Override
    public void jobToBeExecuted(JobExecutionContext jobExecutionContext) {

    }

    @Override
    public void jobExecutionVetoed(JobExecutionContext jobExecutionContext) {

    }

    @Override
    public void jobWasExecuted(JobExecutionContext jobExecutionContext, JobExecutionException e) {

    }

    public void setName(String newName) {
        this.name = newName;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void triggerFired(Trigger trigger, JobExecutionContext jobExecutionContext) {
        JsonObject message = new JsonObject();

        message.put("jobName", jobExecutionContext.getJobDetail().getKey().getName());
        message.put("jobGroup", jobExecutionContext.getJobDetail().getKey().getGroup());
        message.getMap().putAll(jobExecutionContext.getJobDetail().getJobDataMap());

        logger.debug("received a trigger: " + message.encodePrettily());

        /**
         * We could use Quartz's vetoJobExecution method, but we only want Quartz to trigger, and our classes to
         * determine if they want to do the work or not.
         */
        fireRequest(message);
    }

    @Override
    public boolean vetoJobExecution(Trigger trigger, JobExecutionContext jobExecutionContext) {
        return false;
    }

    @Override
    public void triggerMisfired(Trigger trigger) {

    }

    @Override
    public void triggerComplete(Trigger trigger, JobExecutionContext jobExecutionContext, Trigger.CompletedExecutionInstruction completedExecutionInstruction) {

    }
}
