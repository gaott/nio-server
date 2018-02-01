package com.mszf;


import com.mszf.handler.RequestHandler;
import com.mszf.nio.EventWorker;
import com.mszf.nio.NioServer;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.mszf.util.Constant.HOST;
import static com.mszf.util.Constant.PORT;

public class SimpleServer {

    private NioServer server;
    private RequestHandler requestHandler;

    private ExecutorService executorService = Executors.newFixedThreadPool(1);

    public SimpleServer(String host, int port) {
        requestHandler = new RequestHandler() {
            @Override
            public byte[] process(byte[] data) {
                return "OK".getBytes();
            }
        };

        try {
            server = new NioServer(host, port, new EventWorker(requestHandler));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        executorService.submit(server);
    }

    public void stop() {
        try {
            executorService.shutdownNow();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new SimpleServer(HOST, PORT).start();
    }
}
