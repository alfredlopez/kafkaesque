package com.asanasoft.common.model.connector;

import com.asanasoft.common.Context;
import com.asanasoft.common.model.listener.StreamListener;

public interface DataStreamConnector {
    void init(Context config);
    void init();
    void register(StreamListener listener);
    void unregister(StreamListener listener);
    void start();
    void stop();
}
