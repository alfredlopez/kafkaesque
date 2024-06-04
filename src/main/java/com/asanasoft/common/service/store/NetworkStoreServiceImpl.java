package com.asanasoft.common.service.store;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

import java.io.InputStream;
import java.util.List;

public abstract class NetworkStoreServiceImpl  extends AbstractStoreService implements NetworkStoreService {
    private         String userName         = null;
    private         String password         = null;
    private         String host             = null;
    private         String protocol         = null;
    private         String keyFilename      = null;
    private         int    port             = 0;
    private         int    timeout_ms       = 10000;
    private final   String DEFAULT_KEYFILE  = "id_rsa";

    public NetworkStoreServiceImpl(String storeName) {
        super(storeName);
    }

    @Override
    public void store(String storeType, String label, InputStream data, Handler<AsyncResult<Boolean>> handler) {
        Future<Boolean> future = Future.future();
        future.fail(new Exception("Method not implemented!"));
        handler.handle(future);
    }

    @Override
    public void getLatest(String storeType, Handler<AsyncResult<InputStream>> handler) {
        Future<InputStream> future = Future.future();
        future.fail(new Exception("Method not implemented!"));
        handler.handle(future);
    }

    @Override
    public void getVersionByLabel(String label, String storeType, Handler<AsyncResult<InputStream>> handler) {
        Future<InputStream> future = Future.future();
        future.fail(new Exception("Method not implemented!"));
        handler.handle(future);
    }

    @Override
    public void getVersionList(String storeType, Handler<AsyncResult<List<String>>> handler) {
        Future<List<String>> future = Future.future();
        future.fail(new Exception("Method not implemented!"));
        handler.handle(future);
    }

    @Override
    public void setUserName(String userName) {
        this.userName = userName;
    }

    @Override
    public String getUserName() {
        return userName;
    }

    @Override
    public void setHost(String host) {
        this.host = host;
    }

    @Override
    public String getHost() {
        return host;
    }

    @Override
    public void setPort(Integer port) {
        this.port = port;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public void setTimeout(Integer timeout) {
        this.timeout_ms = timeout;
    }

    @Override
    public int getTimeout() {
        return timeout_ms;
    }

    @Override
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    @Override
    public String getProtocol() {
        return protocol;
    }

    @Override
    public String getNetworkPassword() {
        return password;
    }

    @Override
    public void setNetworkPassword(String password) {
        this.password = password;
    }

    @Override
    public String getKeyFilename() {
        if (keyFilename == null) {
            keyFilename = DEFAULT_KEYFILE;
        }

        return keyFilename;
    }

    @Override
    public void setKeyFilename(String keyFilename) {
        this.keyFilename = keyFilename;
    }

    public int getTimeout_ms() {
        return timeout_ms;
    }

    public void setTimeout_ms(int timeout_ms) {
        this.timeout_ms = timeout_ms;
    }
}
