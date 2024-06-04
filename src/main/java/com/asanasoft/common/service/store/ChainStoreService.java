package com.asanasoft.common.service.store;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ChainStoreService extends AbstractStoreService {
    private Logger logger = LoggerFactory.getLogger(ChainStoreService.class);
    private List<StoreService> stores = null;

    public List<StoreService> getStores() {
        if (stores == null) {
            stores = new ArrayList();
        }

        return stores;
    }

    public void addStore(StoreService storeService) {
        getStores().add(storeService);
    }

    @Override
    public String getDestination() {
        return null;
    }

    @Override
    public String getFilePermissions() {
        return null;
    }

    @Override
    public String getSource() {
        return null;
    }

    @Override
    public void setDestination(String destinationContainer) {

    }

    @Override
    public void setFilePermissions(String permissions) {

    }

    @Override
    public void setSource(String sourceContainer) {

    }

    @Override
    public void store(String storeType, String label, InputStream data, Handler<AsyncResult<Boolean>> handler) {
        try {
            getStores().forEach(store -> {
                store.store(storeType, label, data, handler);
            });
        }
        catch (Exception e) {
            logger.error("An error occurred storing data:", e);
        }
    }

    @Override
    public void getLatest(String storeType, Handler<AsyncResult<InputStream>> handler) {

    }

    @Override
    public void getVersionByLabel(String label, String storeType, Handler<AsyncResult<InputStream>> handler) {

    }

    @Override
    public void getVersionList(String storeType, Handler<AsyncResult<List<String>>> handler) {

    }
}
