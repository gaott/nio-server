package com.mszf.handler;

import java.nio.channels.SocketChannel;

public class RequestContent {

    public static final int TYPE_REGISTER = 1;
    public static final int TYPE_CHANGE_OPS = 2;

    public SocketChannel socket;
    public int type;
    public int ops;

    public RequestContent(SocketChannel socket, int type, int ops) {
        this.socket = socket;
        this.type = type;
        this.ops = ops;
    }
}