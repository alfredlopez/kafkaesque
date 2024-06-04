package com.asanasoft.common.service.store;

public interface DatabaseStoreService extends StoreService {
    void setDatasourceName(String datasourceName);
    String getDatasourceName();
}
