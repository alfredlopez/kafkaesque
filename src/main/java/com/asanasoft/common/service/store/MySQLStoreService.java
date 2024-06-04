package com.asanasoft.common.service.store;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;

import java.io.InputStream;

public class MySQLStoreService extends DatabaseStoreServiceImpl {
    public MySQLStoreService(String storeName) {
        super(storeName);
    }

    @Override
    protected String getTableCreateStatement() throws Exception {
        StringBuilder result = new StringBuilder();

        result.append("CREATE TABLE `" + this.getSchemaName() + "`.`" + this.getSource() + "` (");
        result.append("   `label`      VARCHAR(20) NOT NULL,");
        result.append("   `store_name` VARCHAR(20) NOT NULL,");
        result.append("   `data`       LONGBLOB NULL,");
        result.append(" PRIMARY KEY (`label`)");
        result.append(");");

        return result.toString();
    }
    @Override
    public void getLatest(String storeType, Handler<AsyncResult<InputStream>> handler) {
        String sql = "SELECT * from `" + this.getSource() + "` WHERE `store_name` = ? ORDER BY `label` DESC LIMIT 1";
        JsonArray params = new JsonArray();
        params.add(storeType);

        getDataAsInputStream(sql, params, handler);
    }

    @Override
    public void getVersionByLabel(String label, String storeType, Handler<AsyncResult<InputStream>> handler) {
        String sql = "SELECT * from `" + this.getSource() + "` WHERE `label` = ? AND `store_name` = ?";
        JsonArray params = new JsonArray();
        params.add(label);
        params.add(storeType);

        getDataAsInputStream(sql, params, handler);
    }
}
