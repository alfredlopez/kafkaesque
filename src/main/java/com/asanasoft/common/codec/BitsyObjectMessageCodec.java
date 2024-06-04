package com.asanasoft.common.codec;

import com.asanasoft.common.codec.ObjectMessageCodec;
import com.asanasoft.common.model.dao.BitsyObject;

public class BitsyObjectMessageCodec extends ObjectMessageCodec<BitsyObject, BitsyObject> {
    public BitsyObjectMessageCodec() {
        setName("BitsyObject");
    }

    @Override
    public BitsyObject transform(BitsyObject bitsyObject) {
        return bitsyObject;
    }
}
