package com.asanasoft.common.service;

/**
 *
 */
public interface Factory<T> {
    T getInstance(String objectType);
}
