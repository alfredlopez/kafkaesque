package com.asanasoft.common.service.store;

public class PropertiesFileStoreService extends DefaultFileStoreService {
    @Override
    protected boolean fileNameFilter(String storeType, String fileName) {
        boolean result = super.fileNameFilter(storeType, fileName);
        result = (result && fileName.contains(".properties"));

        return result;
    }
}
