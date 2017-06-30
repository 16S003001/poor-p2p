package com.nov21th.tcp.p2p;

import com.nov21th.common.Constant;
import com.nov21th.tcp.TCPServer;
import com.nov21th.util.IPUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.*;

/**
 * Created by GuoYonghui on 2017/6/29.
 */
public class CentralServer extends TCPServer {

    private Map<FileInfo, Set<String>> availableMap;

    private List<String> trustedClients;

    public CentralServer(int port) {
        super(port);

        availableMap = new HashMap<>();
        trustedClients = new ArrayList<>();
    }

    public CentralServer(int port, int bufferSize) {
        super(port, bufferSize);

        availableMap = new HashMap<>();
        trustedClients = new ArrayList<>();
    }

    @Override
    protected Logger getLogger() {
        return LoggerFactory.getLogger(CentralServer.class);
    }

    @Override
    protected void onDataReceived(SelectionKey sk, byte[] data) throws Exception {
        String msg = new String(data, "UTF-8").trim();

        SocketChannel sc = (SocketChannel) sk.channel();
        if (msg.startsWith(Constant.CMD_CONNECT)) {
            doConnect(sc);
        } else if (msg.startsWith(Constant.CMD_QUIT)) {
            doQuit(sc);
        } else {
            String ip = IPUtil.extractIP(sc.getRemoteAddress());
            if (!isClientTrustful(ip)) {
                logger.error("客户端未与服务器进行认证：{}", ip);

                responseToClient(sc, Constant.CMD_ERROR + "\n未与服务器进行身份认证");
                return;
            }

            if (msg.startsWith(Constant.CMD_ADD)) {
                doAdd(sc, msg);
            } else if (msg.startsWith(Constant.CMD_LIST)) {
                doList(sc);
            } else {
                logger.error("未知的命令");
            }
        }
    }

    private void doConnect(SocketChannel sc) throws Exception {
        String ip = IPUtil.extractIP(sc.getRemoteAddress());

        logger.info("客户端请求认证，来自：{}", ip);

        responseToClient(sc, Constant.CMD_ACCEPT);

        trustedClients.add(ip);
    }

    private void doAdd(SocketChannel sc, String msg) throws Exception {
        String ip = IPUtil.extractIP(sc.getRemoteAddress());

        String[] detail = msg.split("\n")[1].split("\t");
        FileInfo info = new FileInfo();
        info.setName(detail[0]);
        info.setHash(detail[1]);
        info.setSize(Integer.parseInt(detail[2]));

        logger.info("客户端请求共享文件，来自：{}，文件名：{}，大小：{}字节，MD5：{}", ip, info.getName(), info.getSize(), info.getHash());

        Set<String> owners = availableMap.get(info);
        if (owners == null) {
            owners = new HashSet<>();
            availableMap.put(info, owners);
        }
        owners.add(ip);

        responseToClient(sc, Constant.CMD_OK);
    }

    private void doList(SocketChannel sc) throws Exception {
        String ip = IPUtil.extractIP(sc.getRemoteAddress());

        logger.info("客户端请求下载共享文件列表，客户端：{}", ip);

        StringBuilder sb = new StringBuilder();
        sb.append(Constant.CMD_OK);
        for (FileInfo info : availableMap.keySet()) {
            Set<String> owners = availableMap.get(info);
            sb.append('\n');
            sb.append(info.getName());
            sb.append('\t');
            sb.append(info.getHash());
            sb.append('\t');
            sb.append(info.getSize());
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

    private void doQuit(SocketChannel sc) throws Exception {
        String ip = IPUtil.extractIP(sc.getRemoteAddress());

        logger.info("客户端退出P2P网络：{}", ip);

        trustedClients.remove(ip);
        Iterator<FileInfo> iterator = availableMap.keySet().iterator();
        while (iterator.hasNext()) {
            FileInfo info = iterator.next();
            Set<String> owners = availableMap.get(info);

            if (owners != null && owners.contains(ip)) {
                owners.remove(ip);
            }
            if (owners == null || owners.size() == 0) {
                iterator.remove();
            }
        }
    }

    private void responseToClient(SocketChannel sc, String msg) throws Exception {
        buffer.clear();
        buffer.put(msg.getBytes("UTF-8"));
        buffer.flip();

        while (buffer.hasRemaining()) {
            sc.write(buffer);
        }

        buffer.clear();
    }

    private boolean isClientTrustful(String ip) {
        return trustedClients.contains(ip);
    }

    public static void main(String[] args) {
        new CentralServer(Integer.parseInt(args[0])).start();
    }

}
