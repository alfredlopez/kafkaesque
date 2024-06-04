package com.asanasoft.common.service.store;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;

import java.io.InputStream;

public class MSSQLStoreService extends DatabaseStoreServiceImpl {
    public MSSQLStoreService(String storeName) {
        super(storeName);
    }

    @Override
    protected String getTableCreateStatement() throws Exception {
        String createSQL =
                "CREATE TABLE [dbo].[BITSY_STORE] (" +
                    "[label] [varchar](20) NOT NULL, " +
                    "[store_name] [varchar](20) NOT NULL, " +
                    "[data] [varchar](max) NULL" +
                ") " +
                    "ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]  ";

        return createSQL;
    }

    @Override
    public String getSchemaName() {
        return "dbo";
    }

    @Override
    public void getLatest(String storeType, Handler<AsyncResult<InputStream>> handler) {
        String sql = "SELECT TOP 1 * FROM " + this.getSource() + " where STORE_NAME = ? order by LABEL desc";
        JsonArray params = new JsonArray();
        params.add(storeType);

        getDataAsInputStream(sql, params, handler);
    }

    @Override
    public void getVersionByLabel(String label, String storeType, Handler<AsyncResult<InputStream>> handler) {
        String sql = "SELECT * from " + this.getSource() + " WHERE label = ? AND store_name = ?";
        JsonArray params = new JsonArray();
        params.add(label);
        params.add(storeType);

        getDataAsInputStream(sql, params, handler);
    }
}
