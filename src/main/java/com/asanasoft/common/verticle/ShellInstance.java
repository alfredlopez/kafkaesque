package com.asanasoft.common.verticle;

import com.asanasoft.common.init.impl.Environment;
import com.asanasoft.common.model.dao.BitsyObject;
import com.asanasoft.common.service.store.StoreConfigurator;
import com.asanasoft.common.service.store.StoreService;
import com.asanasoft.common.service.store.StoreServiceFactory;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import org.apache.commons.io.IOUtils;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.*;
import java.io.*;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.List;

/**
 * Shell verticle -- will rename to As ShellIntance on the next release
 *
 */

public class ShellInstance extends DefaultQuartzVerticle {
    private Logger logger = LoggerFactory.getLogger(ShellInstance.class);
    private StoreService scriptStore;
    private boolean isExecuting = false;

    @Override
    protected JsonObject handleMessage(JsonObject message) {
        JsonObject result = new JsonObject();

        try {
            execute(message.getString("fileName"));
            result.put("success", true);
        } catch (ScriptException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }

        return result;
    }
    @Override
    public void start() throws Exception {
        logger.info("BeanShell Instance..." + getName());

        registerWithScheduler();
        configureStore();
        logger.info("BeanShell Instance Started...");

        checkForNewScripts(); //Initially do the check...
    }

    protected void registerWithScheduler() {
        vertx.setTimer(3000, timerResult -> {
            DeliveryOptions options = new DeliveryOptions();
            options.setCodecName("TriggerListener");
            vertx.eventBus().send("Scheduler.registerListener", this, options);
        });
    }

    protected void configureStore() {
        logger.debug("Configuring Store...");

        JsonObject scriptConfiguration = config().getJsonObject("config");
        StoreServiceFactory storeFactory = new StoreServiceFactory();
        StoreConfigurator configurator = new StoreConfigurator();

        configurator.configure(scriptConfiguration.getString("scriptStoreConfig"));
        scriptStore = storeFactory.getStoreService(scriptConfiguration.getString("scriptStore"), "ScriptStore");
        scriptStore.configure(configurator);

        logger.debug("Store configured...");
    }

    public void execute(String script, String engineType) throws ScriptException {
        String engineName = (engineType != null?engineType:"groovy");
        ScriptEngineManager factory = new ScriptEngineManager();
        ScriptEngine engine = factory.getEngineByName(engineName);
        ScriptContext newContext = new SimpleScriptContext();
        Bindings engineScope = newContext.getBindings(ScriptContext.ENGINE_SCOPE);
        engineScope.put("eventBus", vertx.eventBus());
        engine.eval(script, newContext);
    }

    public void execute(String fileName) throws ScriptException {
        int dot = fileName.lastIndexOf(".");
        String extension = fileName.substring(dot + 1).toUpperCase();
        String engineName;

        switch (extension) {
            case "BSH" :
                engineName = "beanshell";
                break;
            case "GROOVY":
            default:
                engineName = "groovy";
        }

        String theScript = Environment.loadStringFromFile(fileName);
        execute(theScript, engineName);
    }

    public void execute(Reader script, String engineType) throws ScriptException {
        String engineName = (engineType != null?engineType:"groovy");
        ScriptEngineManager factory = new ScriptEngineManager();
        ScriptEngine engine = factory.getEngineByName(engineName);
        ScriptContext newContext = new SimpleScriptContext();
        Bindings engineScope = newContext.getBindings(ScriptContext.ENGINE_SCOPE);
        engineScope.put("eventBus", vertx.eventBus());
        engine.eval(script, newContext);
    }


    @Override
    public void stop() throws Exception {
        super.stop();
    }

    @Override
    public void triggerFired(Trigger trigger, JobExecutionContext jobExecutionContext) {
        String jobName = jobExecutionContext.getJobDetail().getKey().getName();
        String jobGroup = jobExecutionContext.getJobDetail().getKey().getGroup();

        logger.trace("Received a trigger:");
        logger.trace("  jobName = "  + jobName);
        logger.trace("  jobGroup = " + jobGroup);

        // Remote reading files
        if ("CheckForNewScripts".equals(jobName)) {
            if (!isExecuting) {
                checkForNewScripts();
            }
        }
    }
    /**
     * Quartz hooks...
     */

    @Override
    public void jobToBeExecuted(JobExecutionContext jobExecutionContext) {

    }

    @Override
    public void jobExecutionVetoed(JobExecutionContext jobExecutionContext) {

    }

    @Override
    public void jobWasExecuted(JobExecutionContext jobExecutionContext, JobExecutionException e) {

    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public boolean vetoJobExecution(Trigger trigger, JobExecutionContext jobExecutionContext) {
        return false;
    }

    @Override
    public void triggerMisfired(Trigger trigger) {

    }

    @Override
    public void triggerComplete(Trigger trigger, JobExecutionContext jobExecutionContext, Trigger.CompletedExecutionInstruction completedExecutionInstruction) {

    }

    protected void executeScripts(Handler<AsyncResult<Boolean>> handler) {

    }

    protected void checkForNewScripts() {
        try {
            logger.debug("Checking for new scripts to run ...");
            isExecuting = true;

            scriptStore.getVersionList("*", scriptsResult -> {
                if (scriptsResult.succeeded()) {
                    List<String> beanShellFiles = scriptsResult.result();
                    beanShellFiles.forEach(fileName -> {
                        scriptStore.getVersionByLabel("", fileName, streamResult -> {
                            InputStream payload = streamResult.result();

                            if (streamResult.succeeded() && payload != null) {
                                Reader scriptReader = new InputStreamReader(payload);
                                try {
                                    String theScript = IOUtils.toString(payload, Charset.defaultCharset());

                                    execute(theScript, null);

                                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-DD HH:mm:ss");
                                    String timeStamp = sdf.format(System.currentTimeMillis());
                                    BitsyObject bitsyObject = new BitsyObject("ScriptFile", "fileName", fileName);

                                    bitsyObject.put("timestamp", timeStamp);
                                    bitsyObject.store(storeResult -> {});

                                    scriptStore.delete(fileName, deleteResult -> {
                                        if (deleteResult.failed()) {
                                            logger.error("An error occurred deleting file " + fileName, deleteResult.cause());
                                            isExecuting = false;
                                        }
                                    });
                                } catch (Exception e) {
                                    logger.error("An error occurred executing script " + fileName, e);
                                }
                                finally {
                                    isExecuting = false;

                                    try {
                                        scriptReader.close();
                                    } catch (IOException e) {
                                    }
                                }
                            }

                            if (streamResult.failed()) {
                                logger.error(" File Reading Error from Ftp :" + fileName + "  " + streamResult.cause());
                                isExecuting = false;
                            }
                        });
                    });
                }
                if (scriptsResult.failed()) {
                    logger.error(" Remote BeanShell File reading:", scriptsResult.cause());
                    isExecuting = false;
                }
            });
        } catch (Exception e) {
            logger.error("An error occurred reading Shell files:", e);
            isExecuting = false;
        }
        finally {
            isExecuting = false;
        }
    }
}
