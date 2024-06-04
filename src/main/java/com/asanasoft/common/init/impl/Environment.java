package com.asanasoft.common.init.impl;

import com.asanasoft.common.Application;
import com.asanasoft.common.Context;
import com.asanasoft.common.init.AbstractInitializer;
import com.asanasoft.common.init.RunningEnvironments;
import com.asanasoft.common.service.store.FileStoreService;
import com.asanasoft.common.service.store.DefaultFileStoreService;
import com.asanasoft.common.service.store.PropertiesFileStoreService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.oauth2.OAuth2ClientOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Created by lopealf on 2/17/2017.
 * This class is responsible for discovery of the environment for a Vertx application.
 */
public class Environment extends AbstractInitializer {
    private Logger logger = LoggerFactory.getLogger(Environment.class);
    private String hostName = "";
    private int port = 0;
    private String identityServiceName = "p-identity"; //SSO
    private OAuth2ClientOptions oauth2Options = null;
    private Properties environment = null;
    private static Environment instance = null;
    private String runningEnv = null;
    private Vertx vertx = null;
    private String propsFolder = "./";
    private PropertiesFileStoreService propertiesFileStoreService = null;
    private String DR_ENV_TEST_STRING = "api.sys.cae"; //TODO: Find another less "subjective" way to determine if in DR...
    private boolean inDR = false;

    public Environment() {
        context = new Context();
        environment = new Properties();
        Environment.instance = this; //don't ask...
    }


    public static Environment getInstance(Context newContext) {
        if (instance == null) {
            instance = new Environment();
            instance.init(newContext);
        }

        return instance;
    }

    public static Environment getInstance() {
        if (instance == null) {
            instance = new Environment();
            instance.init(null);
        }

        return instance;
    }

    @Override
    public void init(Context newContext, Handler<AsyncResult<Boolean>> handler) {
        /**
         * First, see if the props path has been set in the environment...
         * if not, get the absolute path of the properties files...
         * We do this by locating environment.properties via the classpath...
         */
        propsFolder = System.getenv("basePropsDir");
        environment = new Properties();

        if (propsFolder == null) {
            String propsPath = this.getClass().getClassLoader().getResource("environment.properties").getPath();
            File tempFile = new File(propsPath);
            propsFolder = tempFile.getParent();
        }

        environment.setProperty("propsFolder", propsFolder);
        logger.debug("propsFolder = " + propsFolder);

        //Load the System properties...
        for (String key : System.getenv().keySet()) {
            environment.putIfAbsent(key, System.getenv(key));
        }

        //Then, default the runtime environment to LOCAL if not loaded above...
        environment.putIfAbsent("RUNNING_ENV", RunningEnvironments.LOCAL.toString());

        runningEnv = environment.getProperty("RUNNING_ENV");
        logger.debug("RUNNING_ENV = " + runningEnv);

        //Next figure out if we're running in Disaster Recovery mode...

        decryptPropertiesFiles(decryptResult -> {
            Future<Boolean> future = Future.future();

            init(context);

            future.complete(true);
            handler.handle(future);
        });
    }

    @Override
    public boolean init(Context newContext) {
        hostName = "localhost"; //default to localhost
        port = 8080; //default

        logger.debug("In init...");

        super.init(newContext);

        logger.debug("Back from Super.......");

        if (context.getValue("vertx") != null) {
            vertx = (Vertx) context.getValue("vertx");
        } else {
            vertx = Application.globalVertx;
        }

        String vcapServicesStr = null;
        String vcapApplicationStr = null;
        JsonObject vcapServices = null;
        JsonObject vcapApplication = null;
        JsonObject credentials = null;

        vcapServicesStr = findVCAP("VCAP_SERVICES");
        vcapApplicationStr = findVCAP("VCAP_APPLICATION");

        if (vcapApplicationStr != null) {
            vcapApplication = new JsonObject(vcapApplicationStr);
            logger.debug("vcapApplication = " + vcapApplication.toString());

            hostName = vcapApplication.getJsonArray("uris").getString(0); //get the first (and possibly, only) url

            inDR = vcapApplication.getString("cf_api").contains(DR_ENV_TEST_STRING);
        }

        if (vcapServicesStr != null) {
            vcapServices = new JsonObject(vcapServicesStr);
            logger.debug("vcapServices = " + vcapServices.toString());

            //Get identity services...
            credentials = getCredentials(identityServiceName, vcapServices);

            /**
             * TODO: refactor as above...
             */
            if (credentials == null) {
                JsonObject tempVCAP = new JsonObject(Environment.loadStringFromFile("VCAP_SERVICES.json"));
                credentials = getCredentials(identityServiceName, tempVCAP);
            }

            logger.debug("credentials = " + credentials);

            if (credentials != null) {
                //NOTE: This is SPECIFICALLY for CloudFoundry!!!
                oauth2Options = new OAuth2ClientOptions(new HttpClientOptions()) //<-- not sure why that's needed
                        .setClientID(credentials.getString("client_id"))
                        .setClientSecret(credentials.getString("client_secret"))
                        .setSite(credentials.getString("auth_domain"))
                        .setTokenPath("/oauth/token")
                        .setAuthorizationPath("/oauth/authorize")
                        .setScopeSeparator(" ")
                ;

                logger.debug("oauth2Options = " + oauth2Options.toString());

                /**
                 * If there's a database defined in the environment (PCF tile, etc), then create and entry like all
                 * other datasources...
                 */
                String dbStoreDataSourceName = this.getString("dbStoreDataSourceName");

                for (String key : vcapServices.getMap().keySet()) {
                    JsonArray vcapServicesObject = vcapServices.getJsonArray(key); //all entries are arrays. Hope they stay that way...
                    JsonObject vcapServicesEntry = vcapServicesObject.getJsonObject(0); //Currently, there's only one object...
                    JsonObject dbCredentials = vcapServicesEntry.getJsonObject("credentials");

                    if (dbCredentials != null && dbCredentials.getString("jdbcUrl") != null) {
                        logger.info("Adding new datasource to env: " + vcapServicesEntry.getString("label"));

                        this.context.putValue(vcapServicesEntry.getString("label") + "_dataSourceName", vcapServicesEntry.getString("label"));

                        for (String credKey : dbCredentials.getMap().keySet()) {
                            this.context.putValue(vcapServicesEntry.getString("label") + "_" + credKey, dbCredentials.getValue(credKey));
                        }
                    }
                }
            }
        }

        loadProperties();

        if (getValue("PORT") != null) {
            port = Integer.parseInt((String) getValue("PORT")); //get the PORT number from the CF environment
        }

        if (environment.getProperty("VCAP_APPLICATION") != null) {
            vcapApplication = new JsonObject((String) getValue("VCAP_APPLICATION"));
            hostName = vcapApplication.getJsonArray("application_uris").getString(0); //get the first URI...
        }

        return true;
    }

    public String getString(String key) {
        String result = null;
        Object objValue = context.getValue(key);
        result = String.valueOf(objValue);

        return result;
    }

    public Object getValue(String key) {
        return context.getValue(key);
    }

    public PropertiesFileStoreService getPropertiesFileStoreService() {
        return propertiesFileStoreService;
    }

    protected String findVCAP(String serviceName) {
        String result = System.getenv(serviceName);

        if (result == null) {
            result = Environment.loadStringFromFile(serviceName + ".json");
        }

        return result;
    }

    protected JsonObject getCredentials(String serviceName, JsonObject vcapServices) {
        boolean found = false;
        int pos = 0;
        JsonObject item = null;
        JsonObject credentials = null;

        if (vcapServices.containsKey(serviceName)) {

            try {
                //The service is stored as a JSON Array...
                JsonArray configArray = vcapServices.getJsonArray(serviceName);

                while (!found) {
                    item = configArray.getJsonObject(pos);
                    if (item.containsKey("credentials")) {
                        logger.debug("Found the credentials...");

                        credentials = item.getJsonObject("credentials");
                        found = true;
                    }
                }
            } catch (Exception e) {

            }
        }

        return credentials;
    }

    protected void loadProperties() {
        String newEnvKey = null;

        //First, load the master properties file...
        try {
            environment.load(propertiesFileStoreService.getVersionByLabelBlocking("", "environment.properties"));
        } catch (Exception e) {
            logger.error("Could not load environment.properties file...", e);
        }

        logger.info("Runtime environment is " + environment.getProperty("RUNNING_ENV"));

        //Then, load all other properties files...
        for (String propertiesName : environment.stringPropertyNames()) {
            if (propertiesName.endsWith("_properties")) {
                String genericFilename = environment.getProperty(propertiesName);
                //Load all generic files...
                if (vertx.fileSystem().existsBlocking(genericFilename)) {
                    try {
                        environment.load(propertiesFileStoreService.getVersionByLabelBlocking("", genericFilename));
                    } catch (Exception e) {
                        logger.error("Failed loading file " + genericFilename, e);
                    }
                } else {
                    logger.error("File " + genericFilename + " does not exist");
                }
                //Load any level-specific files, e.g. database_DEV.properties...
                int periodPos = genericFilename.lastIndexOf('.');
                String levelSpecificFilename = genericFilename.substring(0, periodPos) + "_" +
                        environment.getProperty("RUNNING_ENV") + genericFilename.substring(periodPos);
                if (vertx.fileSystem().existsBlocking(levelSpecificFilename)) {
                    logger.info("Loading level-specific values from file " + levelSpecificFilename + "...");
                    try {
                        environment.load(propertiesFileStoreService.getVersionByLabelBlocking("", levelSpecificFilename));
                    } catch (Exception e) {
                        logger.error("Failed loading file " + levelSpecificFilename, e);
                    }
                }

                //Now do the same if there are any Disaster Recovery specific properties, e.g. database_DR-DEV.properties...
                if (inDR) {
                    String drSpecificFilename = genericFilename.substring(0, periodPos) + "_DR-" +
                            environment.getProperty("RUNNING_ENV") + genericFilename.substring(periodPos);
                    if (vertx.fileSystem().existsBlocking(drSpecificFilename)) {
                        logger.info("Loading DR-specific values from file " + drSpecificFilename + "...");
                        try {
                            environment.load(propertiesFileStoreService.getVersionByLabelBlocking("", drSpecificFilename));
                        } catch (Exception e) {
                            logger.error("Failed loading file " + drSpecificFilename, e);
                        }
                    }
                }
            }
        }

        //Lastly, if any properties in the aforementioned files need to be System properties...
        //put them in the System properties space...
        for (String envKey : environment.stringPropertyNames()) {
            if (envKey.endsWith("_s")) {
                newEnvKey = envKey.substring(0, envKey.lastIndexOf("_s"));

                logger.debug("envkey    = " + envKey);
                logger.debug("newEnvKey = " + newEnvKey);
                logger.debug("value     = " + environment.getProperty(envKey));

                System.setProperty(newEnvKey, environment.getProperty(envKey));
            }
        }

        for (String key : environment.stringPropertyNames()) {
            this.context.put(key, environment.getProperty(key));
        }
    }

    protected void decryptPropertiesFiles(Handler handler) {
        logger.debug("In decryptPropertiesFiles...");

        /**
         * Get the store service...
         */
        propertiesFileStoreService = new PropertiesFileStoreService();
        propertiesFileStoreService.setSource(this.propsFolder);

        /**
         * Next, see if there is a password file to load...
         */
        String pgpKTK = null;

        try {
            logger.debug("About to load password file...");
            pgpKTK = Environment.loadStringFromFile("pgp_key_to_keys");
            logger.trace("pgp_key_to_keys = " + pgpKTK);
            getResult().put("PGP_KEY_TO_KEYS", pgpKTK);
        }
        catch (Exception e) {
            logger.error("An error occurred getting password file...", e);
        }

        /**
         * ...if we have a key to the keys, then assume we have encrypted files...
         */
        if (pgpKTK != null) {
            logger.debug("We have keys (but I'm not going to show you because IRM will have a hissy fit...");

            this.context.put("PGP_KEY_TO_KEYS", pgpKTK);
            propertiesFileStoreService.setPassword(pgpKTK);

            /**
             * There is no storeType for properties files, so it simply returns a list of properties files...
             */
            propertiesFileStoreService.getVersionList("encrypted-", versionListResult -> {
                if (versionListResult.succeeded()) {
                    logger.debug("Got list of encrypted properties...");
                    logger.debug("file count = " + versionListResult.result().size());

                    /**
                     * Might as well use a FileStoreService to save the files back to the file system...
                     */
                    FileStoreService fileStore = new DefaultFileStoreService();
                    String baseDir = propertiesFileStoreService.getSource();

                    /**
                     * ...we set the baseDir of the FileStore to the same one from the PropertiesFileStore...
                     */
                    fileStore.setDestination(baseDir);

                    try {
                        boolean processFile; //this is to determine whether or not to process the current file...

                        for (String fileName : versionListResult.result()) {
                            processFile = true;

                            logger.debug("Decrypting " + baseDir + fileName);

                            /**
                             * ...for every file that is tagged as "encrypted", and, optionally is part of the RUNNING_ENV,
                             * decrypt and save the decrypted payload as a file with a filename sans "encrypted"...
                             */
                            try {
                                /**
                                 * If the file is env-bound, but NOT to the current env, then don't process it...
                                 */
                                if (RunningEnvironments.isEnvironmentBound(fileName) && !fileName.contains(runningEnv)) {
                                    processFile = false;
                                }

                                if (processFile) {
                                    InputStream ePayload = new FileInputStream(baseDir + fileName);
                                    InputStream dPayload = propertiesFileStoreService.decrypt(ePayload);
                                    fileName = fileName.replaceAll("encrypted-", "");

                                    logger.debug("Storing decrypted file " + fileName);

                                    /**
                                     * TODO: THIS IS A TEMPORARY METHOD!!!
                                     * The commented block below is the one to use, however, since it is asynchronous, it is
                                     * cause race issues. See comments for storeFile(), below.
                                     */
                                    storeFile(fileStore.getSource(), fileName, "", dPayload);

    //                                fileStore.store(fileName, "", dPayload, storeResult -> {
    //                                    if (storeResult.failed()) {
    //                                        logger.error("Failed to decrypt file ", storeResult.cause());
    //                                    }
    //                                });
                                }
                            } catch (FileNotFoundException e) {
                                /**
                                 * We want to catch any decrypt errors here. Since each environment has it's own
                                 * key to keys, the decryption may fail when it picks up an encrypted file that's
                                 * for another environment (e.g. decrypting database_DEV.properties when in PROD).
                                 * So we'll log the error (just in case), but we will allow the loop to continue...
                                 */
                                logger.error("An error occurred decrypting " + fileName, e);
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Failed to decrypt file ", e);
                    }
                }

                handler.handle(null);
            });
        } else {
            handler.handle(null);
        }
    }

    /**
     * storeFile
     * <p>
     * Nothing special about this method other than it is a TEMPORARY REPLACEMENT for FileStoreService.store().
     * I don't like private methods, but this is necessary here because it is NOT intended to be extended NOR
     * overridden.
     * <p>
     * TODO: create a blocking version of FileStoreService.store(), and delete this method.
     *
     * @param baseDir
     * @param storeType
     * @param label
     * @param data
     */
    private void storeFile(String baseDir, String storeType, String label, InputStream data) {
        InputStream dataCopy = data;

        try {
            String fileName = baseDir + storeType;

            if (label != null && !label.isEmpty()) {
                fileName = fileName + "-" + label;
            }

            OutputStream filedData = new FileOutputStream(fileName);
            int read = 0;
            byte[] bytes = new byte[1024];

            while ((read = dataCopy.read(bytes)) != -1) {
                filedData.write(bytes, 0, read);
            }

            filedData.flush();
            filedData.close();
        } catch (Exception e) {
            logger.error("An error occurred in storeFile:", e);
        }
    }

    public static String loadStringFromFile(String filename) {
        String result = null;

        InputStream jsonFile = Environment.class.getClassLoader().getResourceAsStream(filename);
        try (BufferedReader buffer = new BufferedReader(new InputStreamReader(jsonFile))) {
            if (buffer != null) {
                result = buffer.lines().collect(Collectors.joining(System.lineSeparator()));
            }
        }
        catch (Exception ioe) {
            result = null; //make sure we don't send half-baked strings...
        }

        return result;
    }

    public String getRunningEnv() {
        return runningEnv;
    }

    public Vertx getVertx() {
        return vertx;
    }
}
