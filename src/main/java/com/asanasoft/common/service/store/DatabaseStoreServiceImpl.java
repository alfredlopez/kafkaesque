package com.asanasoft.common.service.store;

import com.asanasoft.common.Application;
import com.asanasoft.common.init.impl.DataSources;
import com.asanasoft.common.init.impl.Environment;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

public class DatabaseStoreServiceImpl extends AbstractStoreService implements DatabaseStoreService {
    protected final String storeTableName = "BITSY_STORE";
    protected String schemaName = null;
    private Logger logger = LoggerFactory.getLogger(DatabaseStoreServiceImpl.class);
    private SQLClient sqlClient = null;
    private DataSources dataSources;
    private String DS_NAME = null;

    public DatabaseStoreServiceImpl(String storeName) {
        super(storeName);

        dataSources = DataSources.getInstance();
    }

    protected void init() {
        logger.debug("Initializing DBStoreService");
        dataSources.getSQLClient(getDatasourceName(), sqlClientResult -> {
            if (sqlClientResult.succeeded()) {
                this.sqlClient = sqlClientResult.result();
                initTable(this.getSource());
            }
            else {
                logger.error("An error occurred getting SQLClient:", sqlClientResult.cause());
            }
        });
    }

    protected void initTable(String newStoreTableName) {
        logger.debug("init() for store table " + storeTableName);
        sqlClient.getConnection(connectionResult -> {
            if (connectionResult.succeeded()) {
                SQLConnection connection = connectionResult.result();
                try {
                    logger.debug("Checking for existing backup table...");
                    connection.query("SELECT COUNT(*) AS TableCount FROM information_schema.tables WHERE table_name = '" + storeTableName + "'", queryResult -> {
                        if (queryResult.succeeded()) {
                            ResultSet rs = queryResult.result();
                            JsonObject row = rs.getRows().get(0);
                            int count = row.getInteger("TableCount");
                            if (count == 0) {
                                try {
                                    logger.debug("Creating the store table " + storeTableName);

                                    sqlClient.getConnection(connectionResult1 -> {
                                        if (connectionResult1.succeeded()) {
                                            SQLConnection connection1 = connectionResult1.result();

                                            try {
                                                String createSql = getTableCreateStatement();
                                                logger.debug("Creating store table with stmt: " + createSql);
                                                connection1.execute(createSql, executeResult -> {
                                                    try {
                                                        if (executeResult.succeeded()) {
                                                            logger.info("Store Table created successfully!");
                                                        } else {
                                                            logger.warn("Store Table NOT created!", executeResult.cause());
                                                        }
                                                    } catch (Exception e) {
                                                        logger.error("An error occurred:", e);
                                                    } finally {
                                                        connection1.close();
                                                    }
                                                });
                                            } catch (Exception e) {
                                                logger.error("Could not create table for Store", e);
                                            }
                                        }
                                    });
                                } catch (Exception e) {
                                    logger.error("Could not initialize store table:", e);
                                }
                            } else {
                                logger.debug("Store table " + storeTableName + " already exists.");
                            }
                        } else {
                            logger.error("Query to information_schema failed:", queryResult.cause());
                        }
                    });
                }
                catch (Exception e) {
                    logger.error("An error occurred:", e);
                }
                finally {
                    connection.close();
                }
            }
            else {
                logger.error("Init failed: ", connectionResult.cause());
            }
        });
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
    public void setDatasourceName(String dataSource) {
        this.DS_NAME = dataSource;
        this.init();
    }

    @Override
    public String getDatasourceName() {
        return DS_NAME;
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
    public void store(String storeType, InputStream data, Handler<AsyncResult<Boolean>> handler) {
        store(storeType, dateToString(new Date()), data, handler);
    }

    @Override
    public void delete(String fileName, Handler<AsyncResult<Boolean>> handler) {
    }

    @Override
    public void deleteAll(String storeType, Handler<AsyncResult<Boolean>> handler) {
        Application.globalVertx.executeBlocking(f -> {
            logger.debug("About to delete payload in DB...");

            Future<Boolean> future = Future.future();
            Connection connection = null;

            try {
                String sqlStm = "DELETE FROM " + getSource() + " WHERE store_name = ?";

                logger.debug("Getting a " + DS_NAME + " connection...");

                connection = DataSources.getInstance().getConnection(DS_NAME);
                PreparedStatement statement = connection.prepareStatement(sqlStm);

                statement.setString(2, storeType);

                logger.debug("Executing statement...");

                statement.execute();

                future.complete(true);
                f.complete();
            } catch (Exception e) {
                logger.error("An error occurred storing data: ", e);
                future.fail(e);
                f.fail(e);
            }
            finally {
                /**
                 * For the record, EVERY time I see this close pattern, I cringe!
                 */
                try {
                    connection.close();
                } catch (SQLException e) {
                    //ignore
                }
            }

            handler.handle(future);
        }, r -> {});
    }

    @Override
    public void delete(String storeType, String label, Handler<AsyncResult<Boolean>> handler) {
        Application.globalVertx.executeBlocking(f -> {
            logger.debug("About to delete payload in DB...");

            Future<Boolean> future = Future.future();
            Connection connection = null;

            try {
                String sqlStm = "DELETE FROM " + getSource() + " WHERE label = ? and store_name = ?";

                logger.debug("Getting a " + DS_NAME + " connection...");

                connection = DataSources.getInstance().getConnection(DS_NAME);
                PreparedStatement statement = connection.prepareStatement(sqlStm);

                statement.setString(1, label);
                statement.setString(2, storeType);

                logger.debug("Executing statement...");

                statement.execute();

                future.complete(true);
                f.complete();
            } catch (Exception e) {
                logger.error("An error occurred storing data: ", e);
                future.fail(e);
                f.fail(e);
            }
            finally {
                /**
                 * For the record, EVERY time I see this close pattern, I cringe!
                 */
                try {
                    connection.close();
                } catch (SQLException e) {
                    //ignore
                }
            }

            handler.handle(future);
        }, r -> {});
    }

    @Override
    public void store(String storeType, String label, InputStream data, Handler<AsyncResult<Boolean>> handler) {
        Application.globalVertx.executeBlocking(f -> {
            logger.debug("About to store payload in DB...label=(" + label + ") storeType=(" + storeType + ")");

            String storeName = storeType;

            Future<Boolean> future = Future.future();
            Connection connection = null;

            try {
                String sqlStm = "INSERT into " + getSource() + " VALUES (?,?,?)";

                logger.debug("Getting a " + DS_NAME + " connection...");

                connection = DataSources.getInstance().getConnection(DS_NAME);
                PreparedStatement statement = connection.prepareStatement(sqlStm);

                Blob blob = connection.createBlob();
                OutputStream blobData = blob.setBinaryStream(1);

                IOUtils.copy(data, blobData);

                logger.debug("Setting statement values...");

                statement.setString(1, label);
                statement.setString(2, storeName);
                statement.setBlob(3, blob);

                logger.debug("Executing statement...");

                statement.execute();

                future.complete(true);
                f.complete();
            } catch (Exception e) {
                logger.error("An error occurred storing data: ", e);
                future.fail(e);
                f.fail(e);
            }
            finally {
                /**
                 * For the record, EVERY time I see this close pattern, I cringe!
                 */
                try {
                    connection.close();
                } catch (SQLException e) {
                    //ignore
                }
            }

            handler.handle(future);
        }, r -> {});
    }

    @Override
    public void getLatest(String storeType, Handler<AsyncResult<InputStream>> handler) {
        String sql = "SELECT from " + this.getSource() + " WHERE label LE ? AND storeType eq ? LIMIT 1";
        JsonArray params = new JsonArray();
        params.add(dateToString(new Date()));
        params.add(storeType);

        getDataAsInputStream(sql, params, handler);
    }

    @Override
    public void getVersionByLabel(String label, String storeType, Handler<AsyncResult<InputStream>> handler) {
        String sql = "SELECT from " + this.getSource() + " WHERE label EQ ? AND storeType eq ?";
        JsonArray params = new JsonArray();
        params.add(label);
        params.add(storeType);

        getDataAsInputStream(sql, params, handler);
    }

    protected void getDataAsInputStream(String sql, JsonArray params, Handler<AsyncResult<InputStream>> handler) {
        Application.globalVertx.executeBlocking(f -> {
            Connection connection = null;

            try {
                connection = DataSources.getInstance().getConnection(DS_NAME);
                PreparedStatement statement = connection.prepareStatement(sql);

                int x = 0;
                for (Object value : params) {
                    x++;
                    statement.setObject(x, value);
                }

                java.sql.ResultSet resultSet = statement.executeQuery();

                InputStream payload = null;

                if (resultSet.next()) {
                    payload = resultSet.getBinaryStream("data");
                }

                f.complete(payload);
            }
            catch (SQLException e) {
                logger.error("An error occurred getting payload:", e);
                f.fail(e);
            }
            finally {
                /**
                 * See previous comments...
                 */
                try {
                    connection.close();
                } catch (SQLException e) {
                    //ignore
                }
            }

        }, r -> {
            Future<InputStream> future = Future.future();

            if (r.succeeded()) {
                future.complete((InputStream)r.result());
            }
            else {
                future.fail(r.cause());
            }

            handler.handle(future);
        });
    }

    @Override
    public void getVersionList(String storeType, Handler<AsyncResult<List<String>>> handler) {

    }

    protected String getTableCreateStatement() throws Exception {
        throw new Exception("Method Not Implemented");
    }

    public String getSchemaName() {
        if (schemaName == null) {
            schemaName = Environment.getInstance().getString(DS_NAME + "_name");
        }
        return schemaName;
    }
}
