package com.asanasoft.common.model.connector;

import org.quartz.JobListener;
import org.quartz.TriggerListener;

public interface PolledDataStreamConnector extends DataStreamConnector, TriggerListener, JobListener {
    void setCron(String cronString);
    void setCron(String jobGroup, String jobName, String cronString);
}
