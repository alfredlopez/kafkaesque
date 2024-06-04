package com.asanasoft.common.init.impl;

import com.asanasoft.common.Application;
import com.asanasoft.common.Context;
import com.asanasoft.common.init.AbstractInitializer;
import com.asanasoft.common.service.graphdb.BitsyDBService;
import com.asanasoft.common.service.graphdb.GraphDBService;
import com.asanasoft.common.service.graphdb.GraphDBServiceFactory;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class DataPatcher extends AbstractInitializer {
    private Vertx vertx = null;
    private String dbService = null;
    private String uuidAddress = null;
    private GraphDBService graphDatabaseService = null;
    private Logger logger = LoggerFactory.getLogger(DataPatcher.class);
    public DataPatcher(){

        graphDatabaseService = BitsyDBService.createProxy(Application.globalVertx, "Bitsy");
    }
    /*public DataPatcher(String dbService, String uniqueAddress) {

         this.vertx = Application.globalVertx;
        this.dbService = dbService;

        this.uuidAddress = uniqueAddress!=null?uniqueAddress:UUID.randomUUID().toString();;

        graphDatabaseService = BitsyDBService.createProxy(Application.globalVertx, "Bitsy");

    }*/
    @Override
    public boolean init(Context context) {
        boolean result = false;
     //   DataPatcher BitsydataPatcher = new DataPatcher(Environment.getInstance().getString("dbName"),null);
        logger.info("MAC>>>>>>>>>>>>>>>>>>>>>");

       /* HashMap<String,String> param = new HashMap();
        String script = "g.V().hasLabel('JTChart').where(inE().count().is(gt(1)))";
        graphDatabaseService.gremlinScript(param,script, handler->{

            if ( handler.succeeded()){
                logger.info("Handler success");
                List<JsonObject> data = handler.result();
                logger.info(" Data Size");
            }
            if ( handler.failed()){
                logger.error(handler.cause() + "  ERRROOR");
            }
        });*/

        return result;
    }

    public void applyPatches(Handler<AsyncResult<Boolean>> handler) {
        Future<Boolean> future =Future.future();
        String script = "createDBindexovy";
        future.complete(true);
        handler.handle(future);
    }

    protected void applyPatch(String script, Context scriptContext) {
        JsonObject scripts = new JsonObject();
        scripts.put("scriptName",script);
        scripts.put("scriptContext",scriptContext);
        Application.globalVertx.eventBus().send("ShellInstance", scripts,reply -> {
            if (reply.succeeded()) {
                logger.info("Received reply: " + reply.result().body());

            } else {
                logger.info("No reply");
            }
        });
    }



}
