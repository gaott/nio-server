package com.mszf.nio;

import com.mszf.handler.RequestContent;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class NioServer extends AbstractNioAllocator {

    private volatile boolean isRunning = true;

    // The host:port combination to listen on
    private InetSocketAddress hostAddress;

    // The channel on which we'll accept connections
    private ServerSocketChannel serverSocketChannel;

    // The selector we'll be monitoring
    private Selector selector;

    private EventWorker worker;

    public NioServer(String host, int port, EventWorker worker) throws IOException {
        this.hostAddress = new InetSocketAddress(host, port);
        this.selector = this.initSelector();
        this.worker = worker;
    }

    public void send(SocketChannel socket, byte[] data) {
        synchronized (this.pendingChanges) {
            // Indicate we want the interest ops set changed
            this.pendingChanges.add(new RequestContent(socket, RequestContent.TYPE_CHANGE_OPS, SelectionKey.OP_WRITE));

            // And queue the data we want written
            synchronized (this.pendingData) {
                List<ByteBuffer> queue = this.pendingData.get(socket);
                if (queue == null) {
                    queue = new ArrayList();
                    this.pendingData.put(socket, queue);
                }
                queue.add(ByteBuffer.wrap(data));
            }
        }

        // Finally, wake up our selecting thread so it can make the required changes
        this.selector.wakeup();
    }

    public void run() {
        while (isRunning) {
            try {
                // Process any pending changes
                synchronized (this.pendingChanges) {
                    Iterator<RequestContent> changes = this.pendingChanges.iterator();
                    while (changes.hasNext()) {
                        RequestContent change = changes.next();
                        switch (change.type) {
                            case RequestContent.TYPE_CHANGE_OPS:
                                SelectionKey key = change.socket.keyFor(this.selector);
                                key.interestOps(change.ops);
                        }
                    }
                    this.pendingChanges.clear();
                }

                // Wait for an event one of the registered channels
                int keyNums = this.selector.select(500);
                if (keyNums == 0) continue;

                // Iterate over the set of keys for which events are available
                Iterator<SelectionKey> selectedKeys = this.selector.selectedKeys().iterator();
                while (selectedKeys.hasNext()) {
                    SelectionKey key = selectedKeys.next();
                    selectedKeys.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    // Check what event is available and deal with it
                    if (key.isAcceptable()) {
                        this.accept(key);
                    } else if (key.isReadable()) {
                        this.read(key);
                    } else if (key.isWritable()) {
                        this.write(key);
                    }
                }
            } catch (Exception e) {
                System.out.println("[NIO] SERVER LOOP ERROR" + e.getMessage());
                if (e instanceof NullPointerException) {
                    return;
                }
            }
        }
    }

    private void accept(SelectionKey key) throws IOException {
        // For an accept to be pending the channel must be a server socket channel.
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();

        // Accept the connection and make it non-blocking
        SocketChannel socketChannel = serverSocketChannel.accept();
        socketChannel.configureBlocking(false);

        socketChannel.register(this.selector, SelectionKey.OP_READ);
    }

    private void read(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();

        byte[] data = readData(key);
        if (data == null) {
            return;
        } else if (readingData.get(socketChannel).isReading()){
            return;
        }

        // Hand the data off to our worker thread
        this.worker.processData(this, socketChannel, data);
    }

    private void write(SelectionKey key) throws IOException {
        writeData(key);
    }

    private Selector initSelector() throws IOException {
        // Create a new selector
        Selector socketSelector = SelectorProvider.provider().openSelector();

        // Create a new non-blocking server socket channel
        this.serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);

        // Bind the server socket to the specified address and port
        serverSocketChannel.socket().bind(hostAddress);

        // Register the server socket channel, indicating an interest in accepting new connections
        serverSocketChannel.register(socketSelector, SelectionKey.OP_ACCEPT);

        return socketSelector;
    }

    @Override
    public void close() throws IOException {
        isRunning = false;

        if (null != selector && selector.isOpen()) {
            selector.close();
        }

        if (null != serverSocketChannel && serverSocketChannel.isOpen()) {
            serverSocketChannel.close();
        }
    }

    @Override
    public boolean isAlive() {
        return isRunning;
    }

    public String getHost() {
        return this.hostAddress.getHostString();
    }
}