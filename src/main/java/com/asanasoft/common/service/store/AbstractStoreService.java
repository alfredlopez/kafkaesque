package com.asanasoft.common.service.store;

import com.asanasoft.common.Application;
import com.asanasoft.common.init.impl.Environment;
import com.asanasoft.common.service.crypto.PGPUtils;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Security;
import java.util.Date;

abstract public class AbstractStoreService implements StoreService {
    private Logger logger = LoggerFactory.getLogger(AbstractStoreService.class);
    private String name = "AbstractStoreService";
    private String password = null;
    private boolean encrypt = false;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Override
    public String getPassword() {
        if (password == null) {
            setPassword(Environment.getInstance().getString("PGP_KEY_TO_KEYS"));

            if (password == null) {
                setPassword("");
                setEncrypt(false);
            }
            else {
                setEncrypt(true);
            }
        }

        logger.trace("Password = " + password);

        return password;
    }

    @Override
    public void setEncrypt(boolean doEncryption) {
        encrypt = doEncryption;
    }

    @Override
    public boolean isEncryption() {
        return encrypt;
    }

    @Override
    public void setPassword(String newPassword) {
        this.password = newPassword;
    }

    protected Vertx vertx;

    public AbstractStoreService() {

    }

    public AbstractStoreService(Vertx vertx, String storeName) {
        this.vertx = vertx;
        this.name = storeName;

        if (vertx == null) {
            vertx = Application.globalVertx;
        }
    }

    public AbstractStoreService(String storeName) {
        this(null, storeName);
    }

    /**
     * Configure this object using a StoreConfigurator
     * @param config
     */
    @Override
    public void configure(StoreConfigurator config) {
        Method configMethods[] = StoreConfigurator.class.getMethods();

        for (Method method : configMethods) {
            if (method.getName().startsWith("get")) {
                String baseName = method.getName().replace("get", "");

                try {
                    Method targetMethod = this.getClass().getMethod("set" + baseName, String.class);
                    targetMethod.invoke(this, (String)method.invoke(config));
                }
                catch (NoSuchMethodException nsm) {
                    logger.trace(nsm.getMessage());

                    try {
                        Method targetMethod = this.getClass().getMethod("set" + baseName, Integer.class);
                        targetMethod.invoke(this, (Integer)method.invoke(config));
                    } catch (Exception e) {
                        logger.trace(e.getMessage());
                    }
                }
                catch (Exception e) {
                    logger.error("An error occurred configuring store:", e);
                }
            }
        }
    }

    /**
     * store
     *
     * This implementation will satisfy most scenarios...
     *
     * @param fileName
     * @param handler
     */
    @Override
    public void store(String fileName, Handler<AsyncResult<Boolean>> handler) {
        File file = new File(fileName);
        String storeType = file.getName();
        InputStream data = null;

        try {
            data = new FileInputStream(file);
        }
        catch (Exception e) {
            logger.error("An error occurred in store:", e);
        }

        store(storeType, data, handler);
    }

    /**
     * store
     *
     * This implementation will satisfy most scenarios...
     * @param storeType
     * @param data
     * @param handler
     */
    @Override
    public void store(String storeType, InputStream data, Handler<AsyncResult<Boolean>> handler) {
        store(storeType, dateToString(new Date()), data, handler);
    }

    @Override
    public void delete(String fileName, Handler<AsyncResult<Boolean>> handler) {
        Future<Boolean> future = Future.future();
        File file = new File(fileName);

        try {
            boolean deleted = file.delete();
            future.complete(deleted);
        }
        catch (Exception e) {
            logger.error("An error occurred in delete:", e);
            future.fail(e);
        }

        handler.handle(future);
    }

    @Override
    public void deleteAll(String storeType, Handler<AsyncResult<Boolean>> handler) {
    }

    @Override
    public void delete(String storeType, String label, Handler<AsyncResult<Boolean>> handler) {
        String fileToDelete = storeType;
        if (label != null && !label.isEmpty()) {
            fileToDelete = label + "-" + storeType;
        }

        delete(fileToDelete, handler);
    }


    @Override
    public InputStream encrypt(InputStream inData) {
        InputStream result = null;

        try {
            byte[] encrypted = PGPUtils.createPbeEncryptedObject(getPassword().toCharArray(), inputStreamToByteArray(inData));
            result = new ByteArrayInputStream(encrypted);
        }
        catch (Exception e) {
            logger.error("An error occurred in encrypt:", e);
        }

        return result;
    }

    @Override
    public InputStream decrypt(InputStream inData) {
        InputStream result = null;

        try {
            byte[] encrypted = PGPUtils.extractPbeEncryptedObject(getPassword().toCharArray(), inputStreamToByteArray(inData));
            result = new ByteArrayInputStream(encrypted);
        }
        catch (Exception e) {
            logger.error("An error occurred in decrypt:", e);
        }

        return result;
    }

    protected ByteArrayOutputStream inputStreamToBAOS(InputStream inputStream) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
        } catch (Exception e) {
            logger.error("An error occurred converting inputStreamToBAOS:", e);
        }

        return outputStream;
    }

    protected String inputStreamToString(InputStream inputStream) {
        String result = inputStreamToBAOS(inputStream).toString();
        return result;
    }

    protected byte[] inputStreamToByteArray(InputStream inputStream) {
        byte[] result = inputStreamToBAOS(inputStream).toByteArray();
        return result;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void getVersionByTimestamp(Date timeStamp, String storeType, Handler<AsyncResult<InputStream>> handler) {
        getVersionByLabel(dateToString(timeStamp), storeType, handler);
    }

    protected String terminateDirectory(String dir) {
        return terminateDirectory(dir, true);
    }

    protected String terminateDirectory(String dir, Boolean createDir) {
        String result  = (dir != null?dir:".");

        if (result != null && !result.isEmpty()) {
            if (result.lastIndexOf('/') < result.length() - 1) {
                result = result + "/";
            }

            if (createDir) {
                try {
                    createContainer(result);
                }
                catch (Exception e) {
                    result = "./";
                }
            }
        }

        return result;
    }

    protected void createContainer(String containerName) throws Exception {
        try {
            File folder = new File(containerName);

            if (!folder.exists()) {
                folder.mkdirs();
            }
        }
        catch (Exception e) {
            logger.error("An error occurred in setSource. Setting to default:", e);
        }
    }
}
