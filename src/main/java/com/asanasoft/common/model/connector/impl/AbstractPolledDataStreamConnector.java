package com.asanasoft.common.model.connector.impl;

import com.asanasoft.common.Application;
import com.asanasoft.common.model.connector.PolledDataStreamConnector;
import io.vertx.core.eventbus.DeliveryOptions;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Trigger;

public abstract class AbstractPolledDataStreamConnector extends AbstractDataStreamConnector implements PolledDataStreamConnector {
    protected String SCHEDULER_ADDRESS = "Scheduler";
    protected String LISTENER_REGISTRATION_ADDRESS = "Scheduler.registerListener";
    private String listenerName;

    public void setName(String newName) {
        this.listenerName = newName;
    }

    @Override
    public String getName() {
        return listenerName;
    }

    protected abstract boolean canFire(String jobName, String jobGroup);
    protected abstract void doWork();

    @Override
    public void triggerFired(Trigger trigger, JobExecutionContext jobExecutionContext) {
        String jobName = jobExecutionContext.getJobDetail().getKey().getName();
        String jobGroup = jobExecutionContext.getJobDetail().getKey().getGroup();

        if (canFire(jobName, jobGroup)) {
            doWork();
        }
    }

    @Override
    public boolean vetoJobExecution(Trigger trigger, JobExecutionContext context) {
        return false;
    }

    @Override
    public void triggerMisfired(Trigger trigger) {

    }

    @Override
    public void triggerComplete(Trigger trigger, JobExecutionContext context, Trigger.CompletedExecutionInstruction triggerInstructionCode) {

    }

    @Override
    public void jobToBeExecuted(JobExecutionContext context) {

    }

    @Override
    public void jobExecutionVetoed(JobExecutionContext context) {

    }

    @Override
    public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {

    }

    @Override
    public void setCron(String cronString) {
        setCron("GENERIC_GROUP", "GENERIC_JOB", cronString);
    }

    @Override
    public void setCron(String jobGroup, String jobName, String cronString) {
        /**
         * Register with the Scheduler to receive backup triggers...
         */
        DeliveryOptions options = new DeliveryOptions();
        options.setCodecName("TriggerListener");
        Application.globalVertx.eventBus().send(LISTENER_REGISTRATION_ADDRESS, this, options);
    }
}
