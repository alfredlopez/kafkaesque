package com.asanasoft.common.codec;

import com.asanasoft.common.model.connector.DataStreamConnector;

public class DataStreamConnectorCodec  extends ObjectMessageCodec<DataStreamConnector, DataStreamConnector>{
    public DataStreamConnectorCodec() {setName("DataStreamConnector");}

    @Override
    public DataStreamConnector transform(DataStreamConnector dataStreamConnector) {
        return dataStreamConnector;
    }
}
