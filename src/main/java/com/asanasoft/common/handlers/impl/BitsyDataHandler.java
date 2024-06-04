package com.asanasoft.common.handlers.impl;

import com.asanasoft.common.handlers.DataHandler;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class BitsyDataHandler implements DataHandler<JsonObject> {
    @Override
    public List<JsonObject> getData(JsonObject params) {
        List<JsonObject> result = new ArrayList();
        return result;
    }

    @Override
    public JsonObject putData(JsonObject data) {
        JsonObject result = null;
        return result;
    }

    @Override
    public JsonObject deleteData(JsonObject data) {
        JsonObject result = null;
        return result;
    }
}
