package com.nov21th.tcp;

import com.nov21th.util.IPUtil;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by g29 on 17-6-28.
 */
public abstract class TCPServer extends Thread {

    protected final Logger logger = getLogger();

    /**
     * 默认缓冲区大小
     */
    private static final int DEFAULT_BUFFER_SIZE = 1024;

    /**
     * TCP服务器运行的端口号
     */
    private int port;

    protected ByteBuffer buffer;

    protected Map<SocketAddress, TCPTransferState> requestMap;

    public TCPServer(int port) {
        this(port, DEFAULT_BUFFER_SIZE);
    }

    public TCPServer(int port, int bufferSize) {
        this.port = port;
        this.buffer = ByteBuffer.allocate(bufferSize);

        requestMap = new HashMap<>();
    }

    @Override
    public void run() {
        try {
            Selector selector = Selector.open();

            ServerSocketChannel server = ServerSocketChannel.open();
            server.configureBlocking(false);
            server.bind(new InetSocketAddress(port));

            server.register(selector, SelectionKey.OP_ACCEPT);

            while (true) {
                if (selector.select() > 0) {
                    Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();

                    while (iterator.hasNext()) {
                        SelectionKey sk = iterator.next();

                        if (sk.isAcceptable()) {
                            ServerSocketChannel ssc = (ServerSocketChannel) sk.channel();

                            SocketChannel sc = ssc.accept();
                            sc.configureBlocking(false);

                            sc.register(selector, SelectionKey.OP_READ);

                            logger.info("对等方连入：{}", IPUtil.extractIP(sc.getRemoteAddress()));
                        } else if (sk.isReadable()) {
                            if (sk.channel() instanceof SocketChannel) {
                                read(sk);
                            } else {
                                logger.error("非TCP数据");
                            }
                        }

                        iterator.remove();
                    }
                }
            }
        } catch (Exception e) {
            logger.error("TCP服务器发生异常", e);
        }
    }

    private void read(SelectionKey sk) throws Exception {
        SocketChannel sc = (SocketChannel) sk.channel();
        SocketAddress addr = sc.getRemoteAddress();

        TCPTransferState state = requestMap.get(addr);
        if (state == null) {
            state = new TCPTransferState();
            requestMap.put(addr, state);
        }
        ByteArrayOutputStream out = state.out;

        buffer.clear();
        int bytesRead;
        try {
            while ((bytesRead = sc.read(buffer)) > 0) {
                buffer.flip();
                while (buffer.hasRemaining()) {
                    out.write(buffer.get());
                }
                buffer.clear();
            }
        } catch (IOException e) {
            bytesRead = -1;
        }
        logger.info("接收到TCP数据，接收自：{}，已接收：{}字节", IPUtil.extractIP(addr), out.size());

        if (bytesRead == -1) {
            try {
                logger.info("数据接收完毕，接收自：{}", IPUtil.extractIP(addr));

                onDataReceived(sk, out.toByteArray());
            } finally {
                requestMap.remove(addr);
                out.close();
                sc.close();
                sk.cancel();
            }
        }
    }

    protected abstract Logger getLogger();

    protected abstract void onDataReceived(SelectionKey sk, byte[] data) throws Exception;

    /**
     * TCP传输状态类，由于TCP本身具有的可靠传输的特性
     * 因此暂时只设置一个输出缓存
     */
    private class TCPTransferState {

        private ByteArrayOutputStream out;

        public TCPTransferState() {
            out = new ByteArrayOutputStream();
        }
    }

}
