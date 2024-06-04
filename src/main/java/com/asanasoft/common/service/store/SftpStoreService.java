package com.asanasoft.common.service.store;

import com.jcraft.jsch.*;
import com.asanasoft.common.Application;
import com.asanasoft.common.init.impl.Environment;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.*;

public class SftpStoreService extends NetworkStoreServiceImpl implements FileStoreService {
    private Logger      logger          = LoggerFactory.getLogger(SftpStoreService.class);
    private ChannelSftp sftpClient      = null;
    private String      baseDir         = null;
    private String      destDir         = null;
    private String      filePermissions = "777";
    private String      parentFolder    = null;
    private JSch        jsch            = new JSch();
    private Session     session         = null;

    public SftpStoreService(String storeName) {
        super(storeName);

//        setUserName(Environment.getInstance().getString("sftpUsername"));
//        setNetworkPassword(Environment.getInstance().getString("sftpPassword"));
//        setPort(Integer.valueOf(Environment.getInstance().getString("sftpPort")));
//        setHost(Environment.getInstance().getString("sftpHost"));
    }

    public void getSftp(Handler<AsyncResult<ChannelSftp>> handler) {
        Application.globalVertx.executeBlocking(f -> {
            getSftpBlocking(getSftpResult -> {
                if (getSftpResult.succeeded()) {
                    f.complete(getSftpResult.result());
                }
                else {
                    f.fail(getSftpResult.cause());
                }
            });
        }, r -> {
            Future<ChannelSftp> result = Future.future();

            if (r.succeeded()) {
                result.complete((ChannelSftp)r.result());
            }
            else {
                result.fail(r.cause());
            }

            handler.handle(result);
        });
    }

    public void getSftpBlocking(Handler<AsyncResult<ChannelSftp>> handler) {
        logger.debug("In getSftpBlocking...");
        if (sftpClient == null || !sftpClient.isConnected()) {
            logger.debug("Need to reconnect...");

            jsch = new JSch();

            try {
                //Use key authentication if it is set, else use password auth
                File privateKey = new File(getKeyFilename());
                File encryptedPrivateKey = new File(getKeyFilename() + ".gpg");
                logger.info(" Key file:" + getKeyFilename() + ".gpg");

                if (privateKey.exists()) {
                    logger.debug("Private Key exists...");
                    logger.info("Path to key:" + privateKey.getAbsolutePath());
                    logger.info("UserName:" + getUserName());
                    logger.info("Host:" + getHost());
                    logger.info("Port #:" + getPort());

                    jsch.addIdentity(privateKey.getAbsolutePath());
                    session = jsch.getSession(getUserName(), getHost() , getPort());
                }
                else if (encryptedPrivateKey.exists()) {
                    logger.debug("Encrypted Private Key exists...");
                    FileStoreService fileStoreService1 = new DefaultFileStoreService("fs1");
                    FileStoreService fileStoreService2 = new DefaultFileStoreService("fs2");

                    fileStoreService1.setEncrypt(true);
                    fileStoreService2.setEncrypt(false);

                    fileStoreService1.setSource(encryptedPrivateKey.getParent());
                    fileStoreService2.setSource(encryptedPrivateKey.getParent());

                    fileStoreService1.setDestination(encryptedPrivateKey.getParent());
                    fileStoreService2.setDestination(encryptedPrivateKey.getParent());

                    InputStream decryptedPayload = ((DefaultFileStoreService) fileStoreService1).getVersionByLabelBlocking("",encryptedPrivateKey.getName());
                    fileStoreService2.setFilePermissions("600"); //read/write by user only...
                    ((DefaultFileStoreService) fileStoreService2).storeBlocking(privateKey.getName(), "", decryptedPayload);
                    File privateKey2 = new File(getKeyFilename());

                    jsch.addIdentity(privateKey2.getAbsolutePath());
                    session = jsch.getSession(getUserName(), getHost() , getPort());

                    logger.debug("Decrypted private key stored. Loading key...");
                }
                else if (getNetworkPassword() != null && !"".equals(getNetworkPassword())) {
                    logger.debug("Using password...");
                    session = jsch.getSession(getUserName(), getHost() , getPort());
                    session.setPassword(getNetworkPassword());
                }

                connectSftp(session, handler);
            } catch (Exception e) {
                logger.error("An error occurred in getSftp:", e);
                Future<ChannelSftp> result = Future.future();
                result.fail(e);
                handler.handle(result);
            }
        }
        else {
            logger.debug("Connection still open...");
            Future<ChannelSftp> result = Future.future();
            result.complete(sftpClient);
            handler.handle(result);
        }
    }

    protected void connectSftp(Session session, Handler<AsyncResult<ChannelSftp>> handler) {
        Future<ChannelSftp> future = Future.future();

        try {
            //Make it so we do not do host key checking.  Enabling this would require some extra code and maintenance, but would increase security.
            session.setConfig("StrictHostKeyChecking", "no");
            session.setTimeout(Integer.parseInt(Environment.getInstance().getString("sftpTimeout")));

            session.connect();

            sftpClient = (ChannelSftp)session.openChannel("sftp");
            sftpClient.connect();

            setParentFolder(sftpClient.pwd());

            future.complete(sftpClient);
        } catch (Exception e) {
            logger.error("An error occurred connecting to Sftp:", e);
            future.fail(e);
        }

        handler.handle(future);
    }


    @Override
    public void store(String fileName, Handler<AsyncResult<Boolean>> handler) {
        try {
            FileInputStream data = new FileInputStream(new File(fileName));
            store(fileName, "", data, handler);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void store(String storeType, String label, final InputStream data, Handler<AsyncResult<Boolean>> handler) {
        Application.globalVertx.executeBlocking(f -> {
            Future<Boolean> future = Future.future();
            getSftp(sftpResult -> {
                if (sftpResult.succeeded()) {
                    InputStream payload = data;
                    ChannelSftp sftp = sftpResult.result();

                    try {
                        StringBuilder fileName = new StringBuilder();

                        String destFolder = (getDestination().startsWith(getParentFolder())? getDestination():getParentFolder() + getDestination());

                        fileName.append(destFolder);

                        if (label != null && !label.isEmpty()) {
                            fileName.append(label + "-");
                        }

                        fileName.append(storeType);

                        if (isEncryption()) {
                            payload = this.encrypt(data);
                        }

                        logger.debug("About to put " + fileName.toString());
                        sftp.put(payload, fileName.toString());

                        f.complete(true);
                    } catch (Exception e) {
                        logger.error("An error occurred in store:", e);
                        f.fail(e);
                    }
                }
            });
        }, false, r -> {
            Future<Boolean> future = Future.future();

            if (r.succeeded()) {
                future.complete((Boolean)r.result());
            }
            else {
                future.fail(r.cause());
            }

            handler.handle(future);
        });
    }

    @Override
    public void store(String storeType, InputStream data, Handler<AsyncResult<Boolean>> handler) {
        store(storeType, this.dateToString(new Date()), data, handler);
    }

    @Override
    public void delete(String storeType, String label, Handler<AsyncResult<Boolean>> handler) {
        StringBuilder fileName = new StringBuilder();

        if (label != null && !label.isEmpty()) {
            fileName.append(label + "-");
        }

        fileName.append(storeType);

        delete(fileName.toString(), handler);
    }

    @Override
    protected void createContainer(String containerName) throws Exception {
        createRemoteDir(containerName, createResult -> {
            if (createResult.failed()) {
                logger.error("There was an error creating target container " + containerName, createResult.cause());
            }
        });
    }

    @Override
    public void delete(String fileName, Handler<AsyncResult<Boolean>> handler) {
        Application.globalVertx.executeBlocking(f -> {
            Future<Boolean> future = Future.future();
            getSftp(sftpResult -> {
                if (sftpResult.succeeded()) {
                    ChannelSftp sftp = sftpResult.result();
                    try {
                        String fileToDelete = fqn(fileName);
                        sftp.rm(fileToDelete);
                        f.complete(true);
                    }
                    catch (Exception e) {
                        logger.error("An error occurred in delete:", e);
                        f.fail(e);
                    }
                }
                else {
                    f.fail(sftpResult.cause());
                }
            });
        }, r -> {
            Future<Boolean> future = Future.future();

            if (r.succeeded()) {
                future.complete((Boolean)r.result());
            }
            else {
                future.fail(r.cause());
            }

            handler.handle(future);
        });
    }

    @Override
    public void getLatest(String storeType, Handler<AsyncResult<InputStream>> handler) {
        Application.globalVertx.executeBlocking(f -> {
            logger.debug("In getLatest...." + storeType);

            try {
                logger.debug("Getting version list....");

                getVersionList(storeType, versionListResult -> {
                    if (versionListResult.succeeded()) {
                        logger.debug("Version List succeeded....");

                        try {
                            List<String> fileNames = versionListResult.result();

                            if (!fileNames.isEmpty()) {
                                logger.debug("We have files....");

                                Collections.reverse(fileNames);

                                getSftp(sftpResult -> {
                                    if (sftpResult.succeeded()) {
                                        ChannelSftp sftp = sftpResult.result();
                                        try {
                                            String fileToLoad       = fileNames.get(0);
                                            String fqnFileToLoad    = fqn(fileToLoad);

                                            logger.debug("fileToLoad = " + fileToLoad);
                                            logger.debug("fqnFileToLoad = " + fqnFileToLoad);

                                            InputStream rawPayload = sftp.get(fqnFileToLoad);
                                            InputStream payload = null;

                                            if (isEncryption()) {
                                                payload = decrypt(rawPayload);
                                            }
                                            else {
                                                payload = rawPayload;
                                            }

                                            logger.debug("Returning payload...");

                                            f.complete(payload);
                                        }
                                        catch (Exception e) {
                                            logger.error("An error occurred in getting sftp file:", e);
                                            f.fail(e);
                                        }

                                    }
                                    else {
                                        f.fail(sftpResult.cause());
                                    }
                                });
                            }
                            else {
                                Exception e = new Exception("No files found");
                                logger.error("An error occurred in getLatest:", e);
                                f.fail(e);
                            }
                        } catch (Exception e) {
                            logger.error("An error occurred in getLatest:", e);
                            f.fail(e);
                        }
                    }
                    else {
                        f.fail(versionListResult.cause());
                    }
                });
            }
            catch (Exception e) {
                logger.error("An error occurred in getLatest:", e);
                f.fail(e);
            }

            /**
             * Delete any temp files...
             */
//            String tempFile = getSource() + "temp" + storeType;
//            File file = new File(tempFile);
//
//            if (file.exists()) {
//                file.delete();
//            }
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

    /**
     * This method returns an input stream for a file from the remote server (folder returned by getDestination).
     * If the file is encrypted, the input stream returned is a decrypted copy of the file.
     */
    @Override
    public void getVersionByLabel(String label, String storeType, Handler<AsyncResult<InputStream>> handler) {

        Application.globalVertx.executeBlocking(f -> {
            String fileName = storeType;
            if (label != null && !label.isEmpty()) {
                fileName = label + "-" + fileName;
            }

            String fileToLoad = fileName;
            logger.debug("In getFileAsInputStream...." + fileToLoad);

            try {
                getSftp(sftpResult -> {
                    if (sftpResult.succeeded()) {
                        ChannelSftp sftp = sftpResult.result();
                        try {
                            String fqnFileToLoad    = fqn(fileToLoad);

                            logger.debug("fileToLoad = " + fileToLoad);
                            logger.debug("fqnFileToLoad = " + fqnFileToLoad);

                            InputStream rawPayload = sftp.get(fqnFileToLoad);
                            InputStream payload = null;

                            if (isEncryption()) {
                                payload = decrypt(rawPayload);
                            }
                            else {
                                payload = rawPayload;
                            }

                            logger.debug("Returning payload...");

                            f.complete(payload);
                        }
                        catch (Exception e) {
                            logger.error("An error occurred in getting sftp file:", e);
                            f.fail(e);
                        }

                    }
                    else {
                        f.fail(sftpResult.cause());
                    }
                });
            }
            catch (Exception e) {
                logger.error("An error occurred in getFileAsInputStream:", e);
                f.fail(e);
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
        Application.globalVertx.executeBlocking(f -> {

            /**
             * since String.matches() matches the entire string, we have to take the storeType and make it 'regex safe'.
             * If it's an asterisk, then add a dot in front. This will be "one or more of ANY character", so it'll match
             * everything. If it's a string literal, then quote it with \\Q and \\E and surround with '.*'. This has the
             * same effect as String.contains(storeType).
             */
//            String matchFilename = ("*".equals(storeType)?".*":".*" + Pattern.quote(storeType) + ".*");
            String matchFilename = ("*".equals(storeType)?"*":"*" + storeType + "*");

            List<String> result = new ArrayList<>();

            try {
                getSftp(sftpResult -> {
                    if (sftpResult.succeeded()) {
                        logger.debug("File to match = " + fqn(matchFilename));

                        ChannelSftp sftp = sftpResult.result();

                        try {
                            Vector<ChannelSftp.LsEntry> vector = sftp.ls(fqn(matchFilename));
                            for (ChannelSftp.LsEntry entry : vector) {
                                result.add(entry.getFilename());
                            }
                            Collections.sort(result);
                            f.complete(result);
                        } catch (Exception e) {
                            logger.error("An error occurred getting version list:", e);
                        }
                    }
                    else {
                        f.fail(sftpResult.cause());
                    }
                });
            }
            catch (Exception e) {
                logger.error("An error occurred in getVersionList:", e);
                f.fail(e);
            }
        }, false, r -> {
            Future<List<String>> future = Future.future();

            if (r.succeeded()) {
                future.complete((List<String>)r.result());
            }
            else {
                future.fail(r.cause());
            }

            handler.handle(future);
        });
    }

    @Override
    public void setSource(String sourceContainer) {
        this.baseDir = terminateDirectory(sourceContainer, false);
    }

    @Override
    public String getSource() {
        if (baseDir == null) {
            setSource(".");
        }
        return baseDir;
    }

    @Override
    public String getFilePermissions() {
        return filePermissions;
    }

    @Override
    public void setDestination(String destinationContainer) {
        destDir = terminateDirectory(destinationContainer, true);
    }

    @Override
    public String getDestination() {
        if (destDir == null) {
            setDestination(".");
        }
        return destDir;
    }

    @Override
    public void setFilePermissions(String permissions) {
        filePermissions = permissions;
    }

    public String getParentFolder() {
        if (parentFolder == null) {
            logger.error("Please connect to sftp BEFORE getting the parent folder!");
        }
        return parentFolder;
    }

    public void setParentFolder(String parentFolder) {
        this.parentFolder = terminateDirectory(parentFolder, false);
    }

    protected String fqn(String filename) {
        StringBuilder result = new StringBuilder();

        if (!filename.startsWith(getParentFolder())) {
            result.append(getParentFolder());

            if (!filename.startsWith(getDestination())) {
                result.append(getDestination());
            }
        }

        result.append(filename);

        return result.toString();
    }

    public void createRemoteDir(String theDir, Handler<AsyncResult<Boolean>> handler) {
        boolean isCreated = false;
        Application.globalVertx.executeBlocking(f -> {
            Future<Boolean> future = Future.future();

            getSftp(sftpResult -> {
                if (sftpResult.succeeded()) {
                    SftpATTRS attrs = null;
                    try {
                        ChannelSftp channelSftp = sftpResult.result();
                        String currentDirectory = channelSftp.pwd();
                        //    channelSftp.mkdir(theDir);
                        try {
                            attrs = channelSftp.lstat(currentDirectory + "/" + theDir);
                            if (attrs != null) {
                                logger.info("directory already exists " + theDir);
                                f.complete(true);
                            }
                        }catch(Exception e){
                            channelSftp.mkdir(theDir);
                            currentDirectory = channelSftp.pwd();
                            setFilePermissions("xxx");
                            logger.info("created remote directory " + theDir);
                            f.complete(true);
                        }

                    } catch (Exception e) {
                        logger.error("FTP Folder creation:" + e.getLocalizedMessage(), e);
                        f.fail(e);
                    }

                }
                if (sftpResult.failed()) {
                    logger.error("FTP Folder creation failed:" + sftpResult.cause());
                }
            });
        }, r -> {
            Future<Boolean> future = Future.future();

            if (r.succeeded()) {
                future.complete((Boolean) r.result());
            } else {
                future.fail(r.cause());
            }

            handler.handle(future);
        });

    }
}
