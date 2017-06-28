package com.nov21th.server;

import com.nov21th.common.Constant;
import com.nov21th.util.FileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;

/**
 * @author 郭永辉
 * @since 1.0 2017/6/24.
 */
public class CentralServer {

    private static final Logger logger = LoggerFactory.getLogger(CentralServer.class);

    private Map<FileInfo, Set<String>> fileMap;

    private Map<SocketAddress, ByteArrayOutputStream> requestMap;

    private List<String> trustedClients;

    private ByteBuffer buffer;

    private int port;

    public CentralServer(int port) {
        this.port = port;

        fileMap = new HashMap<>();
        requestMap = new HashMap<>();
        trustedClients = new ArrayList<>();

        buffer = ByteBuffer.allocate(1024);
    }

    public void start() throws Exception {
        Selector selector = Selector.open();

        ServerSocketChannel server = ServerSocketChannel.open();
        server.configureBlocking(false);
        server.bind(new InetSocketAddress(port));

        server.register(selector, SelectionKey.OP_ACCEPT);

        logger.info("中央服务器已启动，端口号：{}", port);

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

                        logger.info("客户端接入，来自：{}", sc.getRemoteAddress());
                    } else if (sk.isReadable()) {
                        read(sk);
                    }

                    iterator.remove();
                }
            }
        }
    }

    private void read(SelectionKey sk) throws Exception {
        SocketChannel sc = (SocketChannel) sk.channel();
        SocketAddress addr = sc.getRemoteAddress();

        ByteArrayOutputStream baos = requestMap.get(addr);
        if (baos == null) {
            baos = new ByteArrayOutputStream();
            requestMap.put(addr, baos);
        }

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

        if (bytesRead == -1) {
            try {
                String recvMsg = new String(baos.toByteArray(), "UTF-8").trim();

                if (recvMsg.startsWith(Constant.CMD_CONNECT)) {
                    doConnect(sc);
                } else if (recvMsg.startsWith(Constant.CMD_QUIT)) {
                    doQuit(sc);
                } else if (recvMsg.startsWith(Constant.CMD_ADD)) {
                    doAdd(sc, recvMsg);
                } else if (recvMsg.startsWith(Constant.CMD_LIST)) {
                    doList(sc);
                }
            } finally {
                requestMap.remove(addr);
                baos.close();
                sc.close();
                sk.cancel();
            }
        }
    }

    private void doConnect(SocketChannel sc) throws Exception {
        System.out.println(sc.getLocalAddress());
        logger.info("收到客户端认证请求，来自：{}", sc.getRemoteAddress());

        responseToClient(sc, Constant.CMD_ACCEPT);

        trustedClients.add(getIPAddress(sc.getRemoteAddress()));
        logger.info("收到客户端认证成功，来自：{}", sc.getRemoteAddress());
    }

    private void doQuit(SocketChannel sc) throws Exception {
        logger.info("客户端会话结束，来自：{}", sc.getRemoteAddress());

        trustedClients.remove(sc.getRemoteAddress());
    }

    private void doAdd(SocketChannel sc, String recvMsg) throws Exception {
        if (!validateClient(sc.getRemoteAddress())) {
            responseToClient(sc, Constant.CMD_ERROR + "\n" + "未与服务器进行身份认证");
            return;
        }

        String[] fileInfos = recvMsg.split("\n")[1].split("\t");
        FileInfo info = new FileInfo();
        info.setName(fileInfos[0]);
        info.setHash(fileInfos[1]);
        info.setSize(Integer.parseInt(fileInfos[2]));

        logger.info("客户端请求共享文件，客户端：{}，文件名：{}，大小：{}字节，MD5：{}", sc.getRemoteAddress(), info.getName(), info.getSize(), info.getHash());

        Set<String> owners = fileMap.get(info);
        if (owners == null) {
            owners = new HashSet<>();
            fileMap.put(info, owners);
        }
        owners.add(getIPAddress(sc.getRemoteAddress()));

        responseToClient(sc, Constant.CMD_OK);
    }

    private void doList(SocketChannel sc) throws Exception {
        if (!validateClient(sc.getRemoteAddress())) {
            responseToClient(sc, Constant.CMD_ERROR + "\n" + "未与服务器进行身份认证");
            return;
        }

        logger.info("客户端请求下载共享文件列表，客户端：{}", sc.getRemoteAddress());

        StringBuilder sb = new StringBuilder();
        sb.append(Constant.CMD_OK);
        for (FileInfo info : fileMap.keySet()) {
            Set<String> owners = fileMap.get(info);
            sb.append('\n');
            sb.append(info);
            sb.append('\t');
            int i = 0;
            for (String owner : owners) {
                sb.append(owner);
                if (++i != owners.size()) {
                    sb.append('/');
                }
            }
        }

        responseToClient(sc, sb.toString());
    }

    private boolean validateClient(SocketAddress addr) {
        if (!trustedClients.contains(getIPAddress(addr))) {
            logger.info("客户端请求失败，来自：{}，原因：未与服务器进行身份认证", addr);

            return false;
        }

        return true;
    }

    private void responseToClient(SocketChannel sc, String sendMsg) throws Exception {
        buffer.clear();
        buffer.put(sendMsg.getBytes("UTF-8"));
        buffer.flip();

        while (buffer.hasRemaining()) {
            sc.write(buffer);
        }

        buffer.clear();
    }

    public static String getIPAddress(SocketAddress addr) {
        String ip = addr.toString();
        if (ip.contains(":")) {
            ip = ip.split(":")[0];
        }
        if (ip.startsWith("/")) {
            ip = ip.substring(1);
        }
        return ip;
    }

    public static void main(String[] args) {
        try {
            new CentralServer(9090).start();
        } catch (Exception e) {
            logger.error("中央服务器出现异常");
        }
    }

}
