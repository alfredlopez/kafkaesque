package com.asanasoft.common.service.store;

import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.mssmb2.SMBApiException;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.share.DiskShare;
import com.asanasoft.common.Application;
import com.asanasoft.common.init.impl.Environment;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class SmbStoreService extends NetworkStoreServiceImpl implements FileStoreService {
    private Logger logger = LoggerFactory.getLogger(SmbStoreService.class);

    private SMBClient   smbClient = null;
    private String      domain    = null;
    private String      shareName = null;
    private String      sourceDir = null;
    private String      destDir   = null;

    public SmbStoreService(String storeName) {
        super(storeName);

        setUserName(Environment.getInstance().getString("smbUsername"));
        setNetworkPassword(Environment.getInstance().getString("smbPassword"));
        setHost(Environment.getInstance().getString("smbHost"));
        setDomain(Environment.getInstance().getString("smbDomain"));
    }

    @Override
    public void delete(String fileName, Handler<AsyncResult<Boolean>> handler) {
        getShare(shareResult -> {
            if (shareResult.succeeded()) {
                DiskShare diskShare = shareResult.result();

                try {
                    diskShare.rm(getDestination() + fileName);
                } catch (SMBApiException e) {
                    logger.error("An error occurred in delete:", e);
                }
            }
        });
    }

    @Override
    public void deleteAll(String storeType, Handler<AsyncResult<Boolean>> handler) {
        delete("*" + storeType + "*", handler);
    }

    @Override
    public void getVersionByTimestamp(Date timeStamp, String storeType, Handler<AsyncResult<InputStream>> handler) {
        String label = this.dateToString(timeStamp);
        getVersionByLabel(label, storeType, handler);
    }

    @Override
    public void store(String storeType, String label, InputStream data, Handler<AsyncResult<Boolean>> handler) {
        super.store(storeType, label, data, handler);
    }

    @Override
    public void getLatest(String storeType, Handler<AsyncResult<InputStream>> handler) {
        super.getLatest(storeType, handler);
    }

    @Override
    public void getVersionByLabel(String label, String storeType, Handler<AsyncResult<InputStream>> handler) {
        super.getVersionByLabel(label, storeType, handler);
    }

    @Override
    public void getVersionList(String storeType, Handler<AsyncResult<List<String>>> handler) {
        getShare(shareResult -> {
            Future<List<String>> future = Future.future();

            if (shareResult.succeeded()) {
                String matchFilename = ("*".equals(storeType)?".*":".*" + storeType + ".*");
                DiskShare diskShare = shareResult.result();

                try {
                    List<String> fileResults = new ArrayList<>();
                    List<FileIdBothDirectoryInformation> fileList = diskShare.list(getDestination(), matchFilename);

                    for (FileIdBothDirectoryInformation file : fileList) {
                        fileResults.add(file.getFileName());
                    }

                    future.complete(fileResults);
                } catch (SMBApiException e) {
                    logger.error("An error occurred in delete:", e);
                    future.fail(e);
                }
            }
            else {
                future.fail(shareResult.cause());
            }

            handler.handle(future);
        });
    }

    protected SMBClient getSmb() {
        if (smbClient == null) {
            smbClient = new SMBClient();
        }

        return smbClient;
    }

    public void getShare(Handler<AsyncResult<DiskShare>> handler) {
        getShare(shareName, handler);
    }

    public void getShare(String shareName, Handler<AsyncResult<DiskShare>> handler) {
        Application.globalVertx.executeBlocking(f -> {
            DiskShare result = null;
            AuthenticationContext ac = new AuthenticationContext(getUserName(), getPassword().toCharArray(), getDomain());

            try {
                result = (DiskShare)getSmb().connect(getHost()).authenticate(ac).connectShare(shareName);
                f.complete(result);
            } catch (IOException e) {
                logger.error("An error occurred getting share:", e);
                f.fail(e);
            }
        }, r -> {
            Future future = Future.future();

            if (r.succeeded()) {
                future.complete(r.result());
            }
            else {
                future.fail(r.cause());
            }

            handler.handle(future);
        });
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    @Override
    public void setSource(String sourceContainer) {
        sourceDir = sourceContainer;
    }

    @Override
    public String getSource() {
        return sourceDir;
    }

    @Override
    public void setDestination(String destinationContainer) {
        destDir = destinationContainer;
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
    public void setFilePermissions(String permissions) {

    }
}
