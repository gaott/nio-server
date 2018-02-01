package com.mszf.util;

public class Message {

    private String content;

    public Message(String content) {
        this.content = content;
    }

    public byte[] getHeader() {
        return ByteUtils.longToBytes(content.getBytes().length);
    }

    public byte[] getBody() {
        return ByteUtils.combine(getHeader(), content.getBytes());
    }
}
