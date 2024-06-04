package com.asanasoft.common.service.cron.impl;

public class ProcessBusyException extends Exception {
    @Override
    public String getMessage() {
        return "Process is busy. Skipping this event...";
    }
}
