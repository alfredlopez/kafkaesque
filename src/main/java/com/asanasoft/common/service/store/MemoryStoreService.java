package com.asanasoft.common.service.store;

public interface MemoryStoreService extends StoreService {

    default MemoryStoreService getInstance(String storeName) {
        return null;
    }
}
