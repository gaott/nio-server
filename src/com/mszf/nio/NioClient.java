package com.mszf.nio;

import com.mszf.handler.RequestContent;
import com.mszf.handler.ResponseHandler;
import com.mszf.util.Message;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.*;

import static com.mszf.util.Constant.CLIENT_CONNECTION_CHECK_SECONDS;
import static com.mszf.util.Constant.CONNECT_REFUSED;


public class NioClient extends AbstractNioAllocator {

    private volatile boolean isRunning = true;

    private InetSocketAddress hostAddress;

    private Selector selector;

    private long startTime;

    private Map<SocketChannel, ResponseHandler> responseHandlers = Collections.synchronizedMap(new HashMap<>());

    public NioClient(String host, int port) throws IOException {
        this.hostAddress = new InetSocketAddress(host, port);
        this.selector = SelectorProvider.provider().openSelector();
    }

    /**
     * Send message
     *
     * @param message
     * @param handler
     * @throws IOException
     */
    public void send(Message message, ResponseHandler handler) throws IOException {
        send(message.getBody(), handler);
    }

    /**
     * Send message.
     *
     * @param message
     * @param handler
     * @param millis timeout
     * @throws IOException
     */
    public void send(Message message, ResponseHandler handler, long millis) throws IOException {
        send(message.getBody(), handler, millis);
    }


    /**
     *
     * @param data
     * @param handler
     * @throws IOException
     */
    private void send(byte[] data, ResponseHandler handler) throws IOException {
        SocketChannel socket = this.initiateConnection();
        this.responseHandlers.put(socket, handler);

        synchronized (this.pendingData) {
            List<ByteBuffer> queue = this.pendingData.get(socket);
            if (queue == null) {
                queue = new ArrayList<>();
                this.pendingData.put(socket, queue);
            }
            queue.add(ByteBuffer.wrap(data));
        }

        this.selector.wakeup();
    }

    /**
     * Send data and wait for the response in the specified number of milliseconds
     *
     * @param data
     * @param handler
     * @param millis
     * @throws IOException
     */
    private void send(byte[] data, ResponseHandler handler, long millis) throws IOException {
        send(data, handler);
        millis = millis > 0 ? millis : CLIENT_CONNECTION_CHECK_SECONDS * 1000;
        handler.waitAndGet(millis);
    }

    public void run() {

        // set the checking info for connection
        startTime = System.currentTimeMillis();

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
                                break;
                            case RequestContent.TYPE_REGISTER:
                                change.socket.register(this.selector, change.ops);
                                break;
                        }
                    }
                    this.pendingChanges.clear();
                }

                // Wait for an event one of the registered channels
                int keyNums = this.selector.select(500);
                if (keyNums == 0) {
                    checkConnection();
                    continue;
                }

                // Iterate over the set of keys for which events are available
                Iterator<SelectionKey> selectedKeys = this.selector.selectedKeys().iterator();
                while (selectedKeys.hasNext()) {
                    SelectionKey key = selectedKeys.next();
                    selectedKeys.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    // Check what event is available and deal with it
                    if (key.isConnectable()) {
                        this.finishConnection(key);
                    } else if (key.isReadable()) {
                        this.read(key);
                    } else if (key.isWritable()) {
                        this.write(key);
                    }
                }
            } catch (Exception e) {
                System.out.println("ERROR:" + e.getMessage());
            }
        }
    }

    private void read(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        ResponseHandler handler = this.responseHandlers.get(socketChannel);

        byte[] data = readData(key);
        if (data == null) return;

        if (handler != null) {
            handler.handleMessage(data);
        }

        socketChannel.close();
        socketChannel.keyFor(this.selector).cancel();
        this.responseHandlers.remove(socketChannel);
    }

    private void write(SelectionKey key) throws IOException {
        writeData(key);
    }

    private void finishConnection(SelectionKey key) {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        try {
            socketChannel.finishConnect();
        } catch (Exception e) {
            System.out.println("ERROR:" + e.getMessage());
            this.pendingData.remove(socketChannel);

            if (this.responseHandlers.containsKey(socketChannel)) {
                this.responseHandlers.get(socketChannel).handleMessage(CONNECT_REFUSED.getBytes());
            }
            key.cancel();
            return;
        }

        // Register an interest in writing on this channel
        key.interestOps(SelectionKey.OP_WRITE);
    }

    private SocketChannel initiateConnection() throws IOException {
        // Create a non-blocking socket channel
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);

        // Kick off connection establishment
        socketChannel.connect(hostAddress);

        /**
         * Queue a channel registration since the caller is not the
         * selecting thread. As part of the registration we'll register
         * an interest in connection events. These are raised when a channel
         * is ready to complete connection establishment.
         **/
        synchronized (this.pendingChanges) {
            this.pendingChanges.add(new RequestContent(socketChannel, RequestContent.TYPE_REGISTER, SelectionKey.OP_CONNECT));
        }

        return socketChannel;
    }

    public void checkConnection() {
        // Check the connect per 1 second.
        if ((System.currentTimeMillis() - startTime) < CLIENT_CONNECTION_CHECK_SECONDS * 1000) {
            return;
        } else {
            startTime = System.currentTimeMillis();
        }

        // checkout the closed socket and handle message.
        Map<SocketChannel, ResponseHandler> map = new HashMap<>(this.responseHandlers);
        map.forEach(((socketChannel, responseHandler) -> {
            if (responseHandler.isBlocked() && (!socketChannel.isConnected())) {
                this.responseHandlers.remove(socketChannel).handleMessage(CONNECT_REFUSED.getBytes());
                System.out.println("NioClient: handle the broken connection.");
            }
        }));

    }

    @Override
    public void close() throws IOException {
        isRunning = false;

        if (selector != null && selector.isOpen()) {
            selector.close();
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