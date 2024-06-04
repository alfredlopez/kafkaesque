package com.asanasoft.common;

import java.util.HashMap;

public class Context extends HashMap<String, Object> {

    public Object getValue(String key) {
        return get(key);
    }

    public Context putValue(String key, Object value) {
        put(key, value);
        return this;
    }

    public Context mergeWith(Context other) {
        this.putAll(other);
        return this;
    }
}
