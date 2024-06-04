package com.asanasoft.common.model.dao;

import com.asanasoft.common.Context;
import com.asanasoft.common.init.impl.DataSources;
import com.asanasoft.common.init.impl.Environment;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;


public class JdbcDAO<T> implements DAO<T, List<JsonObject>> {
    private Logger logger = LoggerFactory.getLogger(JdbcDAO.class);

    protected Vertx vertx = null;
    protected SQLClient sqlClient = null;
    protected String dataSourceName = null;
    private boolean isReady = false;

    public static final String DATASOURCE_KEY="dataSourceName";
    public static final String SQL_KEY="sqlQuery";

    @Override
    public void init(Context context, Handler<AsyncResult> handler) {
        Future future = Future.future();

        logger.info("In init...");

        if (!isReady()) {
            dataSourceName = (String)context.getValue("dataSourceName");

            logger.debug("dataSourceName = " + dataSourceName);

            this.vertx = (Vertx)context.getValue("vertx");
            DataSources dataSources = DataSources.getInstance();

            setReady(false);
            dataSources.getSQLClient(dataSourceName, result -> {
                if (result.succeeded()) {
                    logger.info("SQLClient success...");
                    sqlClient = result.result();
                    setReady(true);

                    future.complete();
                }
                else {
                    logger.error("SQLClient failed...", result.cause());
                    sqlClient = null;
                    future.fail("Cannot get a Connection");
                }

                handler.handle(future);
            });
        }
        else {
            future.complete();
            handler.handle(future);
        }
    }

    @Override
    public void retrieve(Context context, Handler<AsyncResult<T>> handler) {

    }

    public void getResultSet(Context context, Handler<AsyncResult<ResultSet>> handler) {
        logger.info("In getRawDataAsJsonArray...");

        sqlClient.getConnection(connResult -> {
            Future<ResultSet> future = Future.future();

            String sqlQuery = (String)context.getValue("sqlQuery");

            if (connResult.succeeded()) {
                logger.info("Connection successful...");
                SQLConnection sqlConnection = connResult.result();

                sqlConnection.query(sqlQuery, sqlResult -> {
                    try {
                        if (sqlResult.succeeded()) {
                            ResultSet data;
                            logger.info("Query successful...");
                            data = sqlResult.result();
                            future.complete(data);
                        }
                        else {
                            future.fail(sqlResult.cause());
                        }

                        logger.info("Closing connection...");
                        handler.handle(future);
                    } catch (Exception e) {
                        logger.error("An error occurred:", e);
                    } finally {
                        sqlConnection.close();
                    }
                });
            }
            else {
                future.fail(connResult.cause());
                handler.handle(future);
            }
        });
    }

    public void getRawData(Context context, Handler<AsyncResult<List<JsonObject>>> handler) {
        getResultSet(context, resultSetHandler -> {
            Future<List<JsonObject>> future = Future.future();

            if (resultSetHandler.succeeded()) {
                future.complete(resultSetHandler.result().getRows());
            }
            else {
                future.fail(resultSetHandler.cause());
            }

            handler.handle(future);
        });
    }

    @Override
    public void dropEntities(Context context, Handler<AsyncResult> handler) {
        Future future = Future.future();
        String entityType = (String)context.getValue("entityType");
        String propertyName = dataSourceName + "_" + entityType + "_drop_ddl";
        Context context1 = new Context();
        context1.putValue("propertyName", propertyName);
        executeDDLFile(context1, res -> {
            if (res.succeeded()) {
                logger.info("DDL file for " + propertyName + " executed successfully!");
                future.complete();
            } else {
                logger.error("DDL file for " + propertyName + " failed!");
                future.fail(res.cause());
            }
            handler.handle(future);
        });
    }

    @Override
    public void createEntities(Context context, Handler<AsyncResult> handler) {
        Future future = Future.future();
        String entityType = (String)context.getValue("entityType");
        String propertyName = dataSourceName + "_" + entityType + "_create_ddl";
        Context context1 = new Context();
        context1.putValue("propertyName", propertyName);
        executeDDLFile(context1, res -> {
            if (res.succeeded()) {
                logger.info("DDL file for " + propertyName + " executed successfully!");
                future.complete();
            } else {
                logger.error("DDL file for " + propertyName + " failed!");
                future.fail(res.cause());
            }
            handler.handle(future);
        });
    }

    protected void executeDDLFile(Context context, Handler<AsyncResult> handler) {
        Future future = Future.future();
        String propertyName = (String)context.getValue("propertyName");
        Environment env = Environment.getInstance();
        String ddlFilename = env.getString(propertyName);

        logger.info("Reading " + ddlFilename + "...");

        Buffer fileBuffer = vertx.fileSystem().readFileBlocking(ddlFilename);
        String[] statements = fileBuffer.toString().split(";");
        List<String> batchCreates = new ArrayList<String>();

        for (String statement : statements) {
            logger.debug("statement = " + statement);
            batchCreates.add(statement + ";");
        }

        sqlClient.getConnection(connectionResult -> {
            if (connectionResult.succeeded()) {
                SQLConnection connection = connectionResult.result();
                connection.batch(batchCreates, batchResult -> {
                    try {
                        if (batchResult.failed()) {
                            logger.error("DDL file stmt failed: ", batchResult.cause());
                            future.fail(batchResult.cause());
                        } else {
                            logger.info("DDL file executed successfully!");
                            future.complete();
                        }
                    } catch (Exception e) {
                        logger.error("An error occurred:", e);
                        future.fail(e.getMessage());
                    } finally {
                        connection.close();
                    }
                    handler.handle(future);
                });
            } else {
                logger.error("Unable to get connection: ", connectionResult.cause());
                future.fail(connectionResult.cause());
                handler.handle(future);
            }
        });
    }

    @Override
    public boolean isReady() {
        return isReady;
    }

    @Override
    public void setReady(boolean newReady) {
        isReady = newReady;
    }

    //NOTE: buildSql complies with the TOP syntax as opposed to LIMIT (MSSQL)
    //TODO: I will make this more agnostic in the next iteration
    public String buildSql(Context args) {
        String sql = select(args) + from(args) + where(args) + groupBy(args) + orderBy(args) ;
        logger.debug("Built SQL = " + sql);
        return sql;
    }

    protected String select(Context args) {
        String result = "SELECT " + columns(args);
        return result;
    }

    protected String columns(Context args) {
        String result = " * ";

        if (args.getValue("columns") != null) {
            List<String> columnList = (ArrayList)args.getValue("columns");
            StringJoiner stringOfCols = new StringJoiner(",");
            columnList.forEach(col -> {stringOfCols.add(col);});
            result =  stringOfCols.toString();
        }

        return result;
    }

    protected String from(Context args) {
        String result = " FROM " + args.getValue("tableName");
        return result;
    }

    protected String where(Context args) {
        String result = "";

        if (args.getValue("where") != null) {
            List<String> columnList = (ArrayList)args.getValue("where");
            StringJoiner stringOfCols = new StringJoiner(" AND ");
            columnList.forEach(col -> {stringOfCols.add(col);});
            result =  " WHERE " + stringOfCols.toString();
        }

        return result;
    }

    protected String groupBy(Context args) {
        String result = "";

        if (args.getValue("groupBy") != null) {
            List<String> columnList = (ArrayList)args.getValue("groupBy");
            StringJoiner stringOfCols = new StringJoiner(",");
            columnList.forEach(col -> {stringOfCols.add(col);});
            result =  " GROUP BY " + stringOfCols.toString();
        }

        return result;
    }

    protected String orderBy(Context args) {
        String result = "";

        if (args.getValue("orderBy") != null) {
            List<String> columnList = (ArrayList)args.getValue("orderBy");
            StringJoiner stringOfCols = new StringJoiner(",");
            columnList.forEach(col -> {stringOfCols.add(col);});
            result =  " ORDER BY " + stringOfCols.toString();
        }

        return result;
    }

    protected String limit(Context args) {
        String result = "";

        if (getPageSize(args) > 0) {
            result = " TOP " + getPageSize(args);
        }

        return result;
    }

    protected int getPageSize(Context args) {
        int result = 0;

        if (args.getValue("endRow") != null) {
            result = (Integer)args.getValue("endRow") - (Integer)args.getValue("startRow");
        }

        return result;
    }
}
