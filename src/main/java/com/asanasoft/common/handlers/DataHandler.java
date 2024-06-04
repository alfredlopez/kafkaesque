package com.asanasoft.common.handlers;

import io.vertx.core.json.JsonObject;

import java.util.List;

public interface DataHandler<T> {
    List<T>     getData(JsonObject params);
    JsonObject  putData(T data);
    JsonObject  deleteData(T data);
}
