package com.asanasoft.common.service.store;

import com.asanasoft.common.Application;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileStoreServiceImpl extends AbstractStoreService implements FileStoreService {
    private Logger logger                           = LoggerFactory.getLogger(FileStoreServiceImpl.class);
    private String baseDir                          = null; //defaults to current directory...
    private String destDir                          = null; //defaults to current directory...
    private String filePermissions                  = "";
    private Set<PosixFilePermission> permissions    = null;

    private String FULL_FILE_ACCESS                 = "777";

    public FileStoreServiceImpl() {

    }

    public FileStoreServiceImpl(String storeName) {
        super(storeName);
    }

    @Override
    public void store(String storeType, String label, InputStream data, Handler<AsyncResult<Boolean>> handler) {
        Application.globalVertx.executeBlocking(f -> {
            try {
                storeBlocking(storeType, label, data);
                f.complete(true);
            } catch (Exception e) {
                logger.error("An error occurred in store:", e);
                f.fail(e);
            }
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

    public void storeBlocking(String storeType, String label, InputStream data) throws Exception {
        InputStream dataCopy = data;
        String newLabel = "";

        if (label != null && !label.isEmpty()) {
            newLabel = label + "-";
        }

        String fileName = destDir + newLabel + storeType;

        if (isEncryption()) {
            dataCopy = encrypt(data);
            fileName = destDir + "encrypted-" + newLabel + storeType;
        }

        logger.debug("Outputting file " + fileName);

        OutputStream filedData = new FileOutputStream(fileName);
        int read = 0;
        byte[] bytes = new byte[1024];

        while ((read = dataCopy.read(bytes)) != -1) {
            filedData.write(bytes, 0, read);
        }

        filedData.flush();
        filedData.close();

        applyFilePermissions(new File(fileName));
    }

    @Override
    public void deleteAll(String storeType, Handler<AsyncResult<Boolean>> handler) {
        Future<Boolean> future = Future.future();

        getVersionList(storeType, listResult -> {
            if (listResult.succeeded()) {
                List<String> fileNames = listResult.result();

                File tempFile = null;

                for (String fileName : fileNames) {
                    tempFile = new File(getSource() + fileName);
                    tempFile.delete();
                }

                future.complete(true);
            }
            else {
                future.fail(listResult.cause());
            }

            handler.handle(future);
        });
    }

    @Override
    public void delete(String storeType, String label, Handler<AsyncResult<Boolean>> handler) {
        StringBuilder fileName = new StringBuilder();

        if (!getSource().isEmpty()) {
            fileName.append(getSource());
        }

        if (label != null && !label.isEmpty()) {
            fileName.append(label + "-");
        }

        fileName.append(storeType);

        delete(fileName.toString(), handler);
    }

    @Override
    public void getLatest(String storeType, Handler<AsyncResult<InputStream>> handler) {
        Application.globalVertx.executeBlocking(f -> {
            try {
                getVersionList(storeType, versionListResult -> {
                    if (versionListResult.succeeded()) {
                        try {
                            List<String> fileNames = versionListResult.result();

                            if (!fileNames.isEmpty()) {
                                Collections.reverse(fileNames);
                                InputStream latestFile = new FileInputStream(this.baseDir + fileNames.get(0));


                                if (isEncryption()) {
                                    latestFile = decrypt(latestFile);
                                }

                                f.complete(latestFile);
                            }
                            else {
                                Exception e = new Exception("No files found");
                                logger.error("An error occurred in getLatest:", e);
                                f.fail(e);
                            }
                        } catch (FileNotFoundException e) {
                            logger.error("An error occurred in getLatest:", e);
                            f.fail(e);
                        }
                    }
                });
            }
            catch (Exception e) {
                logger.error("An error occurred in getLatest:", e);
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
    public void getVersionByLabel(String label, String storeType, Handler<AsyncResult<InputStream>> handler) {
        Application.globalVertx.executeBlocking(f -> {
            try {
                InputStream fileInput = getVersionByLabelBlocking(label, storeType);

                f.complete(fileInput);
            } catch (Exception e) {
                logger.error("An error occurred in getVersionByLabel:", e);
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

    public InputStream getVersionByLabelBlocking(String label, String storeType) throws Exception {
        String fileName = baseDir + storeType;
        InputStream fileInput = null;

        if (label != null && !label.isEmpty()) {
            fileName = baseDir + label + "-" + storeType;
        }

        File testFile = new File(fileName);

        if (!testFile.exists()) {
            //We may be running inside an executable JAR...
            fileInput = this.getClass().getClassLoader().getResource(testFile.getName()).openStream();
        }
        else {
            fileInput = new FileInputStream(fileName);
        }

        if (isEncryption()) {
            fileInput = decrypt(fileInput);
        }

        return fileInput;
    }

    @Override
    public void getVersionList(String storeType, Handler<AsyncResult<List<String>>> handler) {
        Application.globalVertx.executeBlocking(f -> {
            logger.debug("Getting version list...");
            logger.debug("...for folder " + getSource());

            List<String> result = null;
            File dir = null;

            try {
                dir = new File(getSource());
                /**
                 * since String.matches() matches the entire string, we have to take the storeType and make it 'regex safe'.
                 * If it's an asterisk, then add a dot in front. This will be "one or more of ANY character", so it'll match
                 * everything. If it's a string literal, then quote it with \\Q and \\E and surround with '.*'. This has the
                 * same effect as String.contains(storeType).
                 */
                String matchFilename = ("*".equals(storeType)?".*":".*" + Pattern.quote(storeType) + ".*");
                String[] fileList = dir.list((folder, fileName) -> {
                    logger.trace(fileName);
                    return fileNameFilter(matchFilename, fileName);
                });

                result = Arrays.asList(fileList);
                Collections.sort(result);
                f.complete(result);
            }
            catch (Exception e) {
                logger.error("An error occurred in getVersionList:", e);
                f.fail(e);
            }
            finally {
            }
        }, r -> {
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
    public void setDestination(String destinationContainer) {
        destDir = terminateDirectory(destinationContainer, true);
    }

    @Override
    public String getDestination() {
        return destDir;
    }

    @Override
    public String getFilePermissions() {
        return filePermissions;
    }

    @Override
    /**
     * Perhaps this is not the best implementation, but...
     */
    public void setFilePermissions(String permissionsString) {
        int USER   = 0;
        int GROUP  = 1;
        int OTHERS = 2;

        int READ    = 0;
        int WRITE   = 1;
        int EXECUTE = 2;

        String[] USER_PERM, GROUP_PERM, OTHERS_PERM;

        try {
            /**
             * permissions should be of the form 777 designating the user, group and others permissions...
             */
            if (permissionsString != null && permissionsString.length() == 3 && permissionsString.equals(String.valueOf(Integer.parseInt(permissionsString)))) {
                this.permissions = new HashSet<>();

                String[] perms = permissionsString.split("");

                /**
                 * Convert each permissions to binary, padding zeros on the left...
                 * So "4" will be "010" instead of "10"
                 */
                String user_perm   = "000" + Integer.toString(Integer.parseInt(perms[USER])  , 2);
                String group_perm  = "000" + Integer.toString(Integer.parseInt(perms[GROUP]) , 2);
                String others_perm = "000" + Integer.toString(Integer.parseInt(perms[OTHERS]), 2);

                logger.debug("user_perm   = " + user_perm);
                logger.debug("group_perm  = " + group_perm);
                logger.debug("others_perm = " + others_perm);

                USER_PERM   = StringUtils.right(user_perm  , 3).split("");
                GROUP_PERM  = StringUtils.right(group_perm , 3).split("");
                OTHERS_PERM = StringUtils.right(others_perm, 3).split("");

                logger.debug("user_perm   = " + USER_PERM.toString());
                logger.debug("group_perm  = " + GROUP_PERM.toString());
                logger.debug("others_perm = " + OTHERS_PERM.toString());

                /**
                 * User permissions...
                 */
                if ("1".equals(USER_PERM[READ])) {
                    this.permissions.add(PosixFilePermission.OWNER_READ);
                }

                if ("1".equals(USER_PERM[WRITE])) {
                    this.permissions.add(PosixFilePermission.OWNER_WRITE);
                }

                if ("1".equals(USER_PERM[EXECUTE])) {
                    this.permissions.add(PosixFilePermission.OWNER_EXECUTE);
                }

                /**
                 * Group permissions...
                 */
                if ("1".equals(GROUP_PERM[READ])) {
                    this.permissions.add(PosixFilePermission.GROUP_READ);
                }

                if ("1".equals(GROUP_PERM[WRITE])) {
                    this.permissions.add(PosixFilePermission.GROUP_WRITE);
                }

                if ("1".equals(GROUP_PERM[EXECUTE])) {
                    this.permissions.add(PosixFilePermission.GROUP_EXECUTE);
                }

                /**
                 * Others permissions...
                 */
                if ("1".equals(OTHERS_PERM[READ])) {
                    this.permissions.add(PosixFilePermission.OTHERS_READ);
                }

                if ("1".equals(OTHERS_PERM[WRITE])) {
                    this.permissions.add(PosixFilePermission.OTHERS_WRITE);
                }

                if ("1".equals(OTHERS_PERM[EXECUTE])) {
                    this.permissions.add(PosixFilePermission.OTHERS_EXECUTE);
                }

                filePermissions = permissionsString;
            }

        } catch (Exception e) {
            logger.error("An error occurred setting the file permissions:", e);
        }
    }

    protected void applyFilePermissions(File file) {
        try {
            Files.setPosixFilePermissions(file.toPath(), getPermissions());
        } catch (Exception e) {
            logger.error("An error occurred applying file permissions:", e);
        }
    }

    protected boolean fileNameFilter(String storeType, String fileName) {
        boolean result = true;

        if (storeType != null && !storeType.isEmpty()) {
            result = fileName.matches(storeType);
        }

        return result;
    }

    protected Set<PosixFilePermission> getPermissions() {
        if (permissions == null) {
            setFilePermissions(FULL_FILE_ACCESS);
        }

        return permissions;
    }

    protected void setPermissions(Set<PosixFilePermission> permissions) {
        this.permissions = permissions;
    }
}
