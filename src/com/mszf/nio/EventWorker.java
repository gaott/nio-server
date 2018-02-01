package com.mszf.nio;

import com.mszf.handler.RequestHandler;

import java.nio.channels.SocketChannel;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

import static com.mszf.util.ByteUtils.removerLongHeader;

public final class EventWorker {

    private final RequestHandler handler;

    private final ForkJoinPool forkJoinPool = new ForkJoinPool();

    public EventWorker(RequestHandler handler) {
        this.handler = handler;
    }

    public void processData(NioServer server, SocketChannel socket, byte[] data) {

        forkJoinPool.execute(new ServerEventTask(server, socket, data));
    }

    class ServerEventTask extends RecursiveTask<Boolean>{

        public NioServer server;
        public SocketChannel socket;
        public byte[] data;

        public ServerEventTask(NioServer server, SocketChannel socket, byte[] data) {
            this.server = server;
            this.socket = socket;
            this.data = data;
        }

        @Override
        protected Boolean compute() {
            try {
                System.out.println("Event worker, data:" + new String(removerLongHeader(data)));
                server.send(socket, handler.process(data));
            } catch (Exception e) {
                e.printStackTrace();
            }

            return true;
        }
    }
}