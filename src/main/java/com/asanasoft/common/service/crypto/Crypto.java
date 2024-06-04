package com.asanasoft.common.service.crypto;

import com.asanasoft.common.init.impl.Environment;
import com.asanasoft.common.service.crypto.PGPUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.stream.Collectors;

public class Crypto {
    private static Logger logger = LoggerFactory.getLogger(Crypto.class);

    public static InputStream encrypt(InputStream inData) {
        InputStream result = null;

        try {
            byte[] encrypted = PGPUtils.createPbeEncryptedObject(getPassword().toCharArray(), inputStreamToByteArray(inData));
            result = new ByteArrayInputStream(encrypted);
        }
        catch (Exception e) {
            System.out.println("An error occurred in encrypt:" + e);
        }

        return result;
    }

    public static InputStream decrypt(InputStream inData) {
        InputStream result = null;

        try {
            byte[] encrypted = PGPUtils.extractPbeEncryptedObject(getPassword().toCharArray(), inputStreamToByteArray(inData));
            result = new ByteArrayInputStream(encrypted);
        }
        catch (Exception e) {
            System.out.println("An error occurred in decrypt:" + e);
        }

        return result;
    }

    protected static String getPassword() {
        return loadStringFromFile("PGP_KEY_TO_KEYS");
    }

    protected static String loadStringFromFile(String filename) {
        String result = null;

        InputStream jsonFile = Crypto.class.getClassLoader().getResourceAsStream(filename);
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

    protected static byte[] inputStreamToByteArray(InputStream inputStream) {
        byte[] result = inputStreamToBAOS(inputStream).toByteArray();
        return result;
    }

    protected static ByteArrayOutputStream inputStreamToBAOS(InputStream inputStream) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
        } catch (Exception e) {
            logger.error("An error occurred converting inputStreamToBAOS:", e);
            System.out.println("An error occurred converting inputStreamToBAOS:" + e);
        }

        return outputStream;
    }
}
