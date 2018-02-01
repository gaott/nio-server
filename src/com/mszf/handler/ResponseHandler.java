package com.mszf.handler;

import static com.mszf.util.Constant.CLIENT_CONNECTION_CHECK_SECONDS;
import static com.mszf.util.Constant.TIMEOUT;

public class ResponseHandler {

    private byte[] message = null;

    private final long startBlockTime;

    public ResponseHandler() {
        startBlockTime = System.currentTimeMillis();
    }

    public boolean isBlocked() {
        return isBlocked(CLIENT_CONNECTION_CHECK_SECONDS * 1000);
    }

    public boolean isBlocked(long timeout) {
        return (System.currentTimeMillis() - startBlockTime) > timeout;
    }

    public void handleMessage(byte[] data) {
        this.message = data;
    }

    public byte[] waitAndGet() {
        while(this.message == null) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                System.out.println("ERROR:" + e.getMessage());
            }
        }

        return this.message;
    }

    public byte[] waitAndGet(long millis) {

        while(this.message == null) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                System.out.println("ERROR:" + e.getMessage());
            }

            if (isBlocked(millis)){
                return TIMEOUT.getBytes();
            }
        }
        return this.message;
    }
}