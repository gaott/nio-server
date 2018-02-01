package com.mszf.util;

import java.nio.ByteBuffer;


public class ByteUtils {


    public static byte[] longToBytes(long l) {
        ByteBuffer longBuffer = ByteBuffer.allocate(Long.BYTES);
        longBuffer.putLong(0, l);
        return longBuffer.array();
    }

    public static long bytesToLong(byte[] bytes) {
        ByteBuffer longBuffer = ByteBuffer.allocate(Long.BYTES);
        longBuffer.put(bytes, 0, bytes.length);
        longBuffer.flip();//need flip
        return longBuffer.getLong();
    }

    public static byte[] combine(byte[] first, byte[] second) {
        byte[] combined = new byte[first.length + second.length];

        System.arraycopy(first, 0, combined, 0, first.length);
        System.arraycopy(second, 0, combined, first.length, second.length);

        return combined;
    }

    public static long getLongHeader(byte[] bytes) {
        byte[] header = new byte[Long.BYTES];
        System.arraycopy(bytes, 0, header, 0, Long.BYTES);

        return bytesToLong(header);
    }

    public static byte[] removerLongHeader(byte[] bytes) {
        byte[] content = new byte[bytes.length - Long.BYTES];
        System.arraycopy(bytes, Long.BYTES, content, 0, content.length);

        return content;
    }

}
