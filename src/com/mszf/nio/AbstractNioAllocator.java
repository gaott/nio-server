package com.mszf.nio;

import com.mszf.handler.SocketHandler;
import com.mszf.handler.RequestContent;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public abstract class AbstractNioAllocator implements Runnable {

    // The buffer into which we'll read data when it's available
    protected ByteBuffer readBuffer = ByteBuffer.allocate(1024 * 1024);

    // A list of PendingChange instances
    protected List<RequestContent> pendingChanges = new LinkedList<>();

    // Maps a SocketChannel to a list of ByteBuffer instances
    protected Map<SocketChannel, List<ByteBuffer>> pendingData = new HashMap<>();

    // Maps a SocketChannel to a handler for reading instances
    protected Map<SocketChannel, SocketHandler> readingData = new HashMap<>();

    protected byte[] readData(SelectionKey key) throws IOException{
        SocketChannel socketChannel = (SocketChannel) key.channel();
        // Clear out our read buffer so it's ready for new data
        this.readBuffer.clear();

        // Handle the channel
        SocketHandler socketHandler = readingData.get(socketChannel);
        if (socketHandler == null) {
            socketHandler = new SocketHandler();
            synchronized (readingData) {
                readingData.put(socketChannel, socketHandler);
            }
        }

        // Attempt to read off the channel
        int currentRead = 0;
        try {
            currentRead = socketHandler.read(socketChannel, readBuffer);
        } catch (IOException e) {
            // The remote forcibly closed the connection, cancel the selection key and close the channel.
            key.cancel();
            socketChannel.close();
            return null;
        } finally {
            if (currentRead < 0) {
                // Remote entity shut the socket down cleanly. Do the
                // same from our end and cancel the channel.
                key.channel().close();
                key.cancel();
                return null;
            }
        }

        return socketHandler.getBytes();
    }

    protected void writeData(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();

        synchronized (this.pendingData) {
            // check the channel
            if (!this.pendingData.containsKey(socketChannel)) return;

            List<ByteBuffer> queue = this.pendingData.get(socketChannel);

            // Write until there's not more data ...
            while (!queue.isEmpty()) {
                ByteBuffer buf = queue.get(0);
                socketChannel.write(buf);
                if (buf.remaining() > 0) {
                    // ... or the socket's buffer fills up
                    break;
                }
                queue.remove(0);
            }

            if (queue.isEmpty()) {
                // We wrote away all data, so we're no longer interested
                // in writing on this socket. Switch back to waiting for
                // data.
                key.interestOps(SelectionKey.OP_READ);
            }
        }
    }

    public abstract void close() throws IOException;

    public abstract boolean isAlive();
}
