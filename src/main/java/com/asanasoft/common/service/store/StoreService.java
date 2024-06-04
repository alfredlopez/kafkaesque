package com.asanasoft.common.service.store;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public interface StoreService {
    /**
     * These constants *can* live in a config fiile, however,
     * since they accompany an implementation, it's ok to make
     * a "code change" and add a new StoreService type here;
     * especially since this is not a data-driven architecture.
     */
    String MSSQL_STORE      = "MSSQLStore";
    String DB_STORE         = "DBStore";
    String FILE_STORE       = "FileStore";
    String MEMORY_STORE     = "MemoryStore";
    String NETWORK_STORE    = "NetworkStore";
    String SFTP_STORE       = "SftpStore";

    default String dateToString(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd'-'HHmm");
        return sdf.format(date);
    }

    void configure(StoreConfigurator configurator);

    String getDestination();

    String getFilePermissions();

    String getName();

    String getSource();

    void setDestination(String destinationContainer);

    /**
     * Set the file permissions to be applied to all files...
     * @param permissions - in the form of UGO (###)
     */
    void setFilePermissions(String permissions);

    void setSource(String sourceContainer);

    void store(String fileName, Handler<AsyncResult<Boolean>> handler);
    void store(String storeType, InputStream data, Handler<AsyncResult<Boolean>> handler);
    void store(String storeType, String label, InputStream data, Handler<AsyncResult<Boolean>> handler);
    void delete(String fileName, Handler<AsyncResult<Boolean>> handler);
    void deleteAll(String storeType, Handler<AsyncResult<Boolean>> handler);
    void delete(String storeType, String label, Handler<AsyncResult<Boolean>> handler);
    void getLatest(String storeType, Handler<AsyncResult<InputStream>> handler);
    void getVersionByTimestamp(Date timeStamp, String storeType, Handler<AsyncResult<InputStream>> handler);
    void getVersionByLabel(String label, String storeType, Handler<AsyncResult<InputStream>> handler);
    void getVersionList(String storeType, Handler<AsyncResult<List<String>>> handler);
    InputStream encrypt(InputStream payload);
    InputStream decrypt(InputStream payload);
    void setPassword(String password);
    String getPassword();
    void setEncrypt(boolean doEncryption);
    boolean isEncryption();
}
