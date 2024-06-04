package com.asanasoft.common.codec;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

abstract public class ObjectMessageCodec<T, R> implements MessageCodec<T, R> {
    private Logger logger = LoggerFactory.getLogger(HandlerMessageCodec.class);
    private String name = "Object";

    @Override
    public void encodeToWire(Buffer buffer, T t) {
        try {
            buffer.setBytes(0, toByteArray(t));
        }
        catch (Exception e) {
            logger.error("An error occurred encoding the object:", e);
        }
    }

    @Override
    public R decodeFromWire(int pos, Buffer buffer) {
        R result = null;

        try {
            result = (R) toObject(buffer.getBytes());
        }
        catch (Exception e) {
            logger.error("An error occurred decoding the object:", e);
        }

        return result;
    }

    @Override
    public String name() {
        return name;
    }

    /**
     * Return the systemCodecID
     * @return a -1 to indicate that this is NOT a system codec
     */
    @Override
    public byte systemCodecID() {
        return -1;
    }


    // toByteArray and toObject are taken from: http://tinyurl.com/69h8l7x
    protected byte[] toByteArray(Object obj) throws IOException {
        byte[] bytes = null;
        ByteArrayOutputStream bos = null;
        ObjectOutputStream oos = null;
        try {
            bos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(bos);
            oos.writeObject(obj);
            oos.flush();
            bytes = bos.toByteArray();
        } finally {
            if (oos != null) {
                oos.close();
            }
            if (bos != null) {
                bos.close();
            }
        }
        return bytes;
    }

    protected Object toObject(byte[] bytes) throws IOException, ClassNotFoundException {
        Object obj = null;
        ByteArrayInputStream bis = null;
        ObjectInputStream ois = null;
        try {
            bis = new ByteArrayInputStream(bytes);
            ois = new ObjectInputStream(bis);
            obj = ois.readObject();
        } finally {
            if (bis != null) {
                bis.close();
            }
            if (ois != null) {
                ois.close();
            }
        }
        return obj;
    }

    protected String toString(byte[] bytes) {
        return new String(bytes);
    }

    public void setName(String name) {
        this.name = name;
    }
}
