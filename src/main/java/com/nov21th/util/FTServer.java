package com.nov21th.util;

import com.nov21th.common.Constant;
import com.nov21th.server.CentralServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * @author 郭永辉
 * @since 1.0 2017/6/23.
 */
public class FTServer extends Thread {

    private static final Logger logger = LoggerFactory.getLogger(FTServer.class);

    private int tcpPort;

    private String repository;

    private Map<SocketAddress, TCPTransferState> tcpRequestMap;

    private ByteBuffer buffer;

    private FTCallback callback;

    public FTServer(int tcpPort, String repository, FTCallback callback) {
        if (!repository.endsWith("/")) {
            repository += "/";
        }

        this.tcpPort = tcpPort;
        this.repository = repository;

        tcpRequestMap = new HashMap<>();

        buffer = ByteBuffer.allocate(100 * 1024);
        this.callback = callback;
    }

    @Override
    public void run() {
        try {
            startServer();
        } catch (Exception e) {
            logger.error("文件服务器发生异常", e);
        }
    }

    private void startServer() throws Exception {
        Selector selector = Selector.open();

        ServerSocketChannel tcpServer = ServerSocketChannel.open();
        tcpServer.configureBlocking(false);
        tcpServer.bind(new InetSocketAddress(tcpPort));

        tcpServer.register(selector, SelectionKey.OP_ACCEPT);

        logger.info("文件服务器已启动");
        logger.info("文件存储路径：{}", repository);
        logger.info("TCP端口：{}", tcpPort);

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

                        logger.info("TCP连接建立：{}", sc.getRemoteAddress());
                    } else if (sk.isReadable()) {
                        receiveTCP(sk);
                    }

                    iterator.remove();
                }
            }
        }
    }

    private void receiveTCP(SelectionKey sk) throws Exception {
        SocketChannel sc = (SocketChannel) sk.channel();
        SocketAddress addr = sc.getRemoteAddress();

        TCPTransferState state = tcpRequestMap.get(addr);
        if (state == null) {
            state = new TCPTransferState();
            tcpRequestMap.put(addr, state);
        }

        ByteArrayOutputStream baos = state.baos;

        buffer.clear();

        int bytesRead;

        try {
            while ((bytesRead = sc.read(buffer)) > 0) {
                buffer.flip();

                while (buffer.hasRemaining()) {
                    baos.write(buffer.get());
                }

                buffer.clear();
            }
        } catch (IOException e) {
            bytesRead = -1;
        }

        logger.info("接收到TCP数据，接收自：{}，已接收：{}字节", addr, baos.size());

        if (bytesRead == -1) {
            try {
                logger.info("数据接收完毕，来自：{}", addr);

                byte[] data = baos.toByteArray();

                logger.info(new String(data));

                int headerLength = -1;
                for (int i = 0; i < Math.min(data.length, 100); i++) {
                    if ((char) data[i] == '\n') {
                        headerLength = i;
                        break;
                    }
                }
                if (headerLength == -1) {
                    return;
                }

                String header = new String(data, 0, headerLength);

                logger.info(header);
                logger.info(Constant.CMD_CONNECT);

                if (header.equals(Constant.CMD_CONNECT)) {
                    logger.info("对等方请求进行身份认证：{}", sc.getRemoteAddress());

                    buffer.clear();
                    buffer.put((Constant.CMD_ACCEPT + "\n").getBytes());
                    buffer.flip();
                    while (buffer.hasRemaining()) {
                        sc.write(buffer);
                    }
                    sc.shutdownOutput();
                    buffer.clear();
                } else if (header.equals(Constant.CMD_REQUEST)) {
                    String filename = new String(data, Constant.CMD_REQUEST.length() + 1, data.length - Constant.CMD_REQUEST.length() - 1);

                    FileInputStream fis = new FileInputStream(repository + filename);

                    Socket socket = new Socket();
                    socket.connect(new InetSocketAddress(CentralServer.getIPAddress(sc.getRemoteAddress()), 12345));
                    byte[] buffer = new byte[1024];
                    socket.getOutputStream().write((Constant.CMD_FILE + "\n").getBytes());
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        socket.getOutputStream().write(buffer, 0, bytesRead);
                    }
                    socket.getOutputStream().flush();
                    socket.getOutputStream().close();

//                    FileChannel fc = fis.getChannel();
//
//                    buffer.clear();
//                    while (fc.read(buffer) != -1) {
//                        buffer.flip();
//                        while (buffer.hasRemaining()) {
//                            sc.write(buffer);
//                        }
//                        buffer.clear();
//                    }
                } else if (header.equals(Constant.CMD_FILE)) {
                    byte[] temp = new byte[data.length - Constant.CMD_FILE.length() - 1];
                    System.arraycopy(data, Constant.CMD_FILE.length() + 1, temp, 0, temp.length);

                    callback.onFileTransferDone(temp);
                }

            } finally {
                tcpRequestMap.remove(addr);
                baos.close();
                sc.close();
                sk.cancel();
            }
        }

        buffer.clear();
    }

    private class TCPTransferState {

        private ByteArrayOutputStream baos;

        public TCPTransferState() {
            baos = new ByteArrayOutputStream();
        }
    }


}
