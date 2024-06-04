package com.asanasoft.common.service.store;

public interface NetworkStoreService extends StoreService {

    default NetworkStoreService getInstance(String storeName) {
        return null;
    }

    void setUserName(String userName);
    String getUserName();
    void setNetworkPassword(String password);
    String getNetworkPassword();
    void setHost(String host);
    String getHost();
    void setPort(Integer port);
    int getPort();
    void setTimeout(Integer timeout);
    int getTimeout();
    void setProtocol(String protocol);
    String getProtocol();
    void setKeyFilename(String keyFilename);
    String getKeyFilename();
}
