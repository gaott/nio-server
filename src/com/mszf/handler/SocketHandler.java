package com.mszf.handler;

import com.mszf.util.ByteUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class SocketHandler {

    private byte[] result = null;

    private long length = 0;

    private long numOfRead = 0;

    private boolean reading = true;

    private long start;

    public boolean isReading() {
        return reading;
    }

    public void close() {
        numOfRead = 0;
        length = 0;
        reading = false;
    }

    public SocketHandler() {
        start = System.currentTimeMillis();
    }

    public int read(SocketChannel socketChannel, ByteBuffer readBuffer) throws IOException{
        // read from channel
        int currentRead = socketChannel.read(readBuffer);
        if (currentRead < 0) {
            reading = false;
            length = 0;
            return currentRead;
        }

        if (currentRead > 0) {
            numOfRead = currentRead + numOfRead;

            // get header to cal length of bytes
            if (length == 0) {
                length = ByteUtils.getLongHeader(readBuffer.array());
            }

            // get bytes from buffer
            readBuffer.flip();
            byte[] dataOfBuffer = new byte[readBuffer.limit()];
            readBuffer.get(dataOfBuffer);

            // combine the before read and the current.
            if (result == null) {
                result = dataOfBuffer;
            } else {
                result = ByteUtils.combine(result, dataOfBuffer);
            }
        }

        if (numOfRead >= length + Long.BYTES) {
            close();
            return 0;
        }

        return currentRead;
    }

    public byte[] getBytes() {
        return result;
    }

    public long startTime() {
        return start;
    }
}
