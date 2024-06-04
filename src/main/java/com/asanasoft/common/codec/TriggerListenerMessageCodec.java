package com.asanasoft.common.codec;

import org.quartz.TriggerListener;

public class TriggerListenerMessageCodec extends ObjectMessageCodec<TriggerListener, TriggerListener> {
    public TriggerListenerMessageCodec() {
        setName("TriggerListener");
    }

    @Override
    public TriggerListener transform(TriggerListener triggerListener) {
        return triggerListener;
    }
}
