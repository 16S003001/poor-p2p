package com.nov21th.tcp.p2p;

import com.nov21th.common.Constant;
import com.nov21th.tcp.file.FileTransferCallback;
import com.nov21th.tcp.file.FileTransferServer;
import com.nov21th.util.MD5Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Random;
import java.util.Scanner;

/**
 * Created by GuoYonghui on 2017/6/29.
 */
public class Peer implements FileTransferCallback {

    private static final Logger logger = LoggerFactory.getLogger(Peer.class);

    private SocketAddress centralAddr;

    private String repository;

    private FileInfo currentTask;

    private volatile boolean currentTaskDone;

    public Peer(String centralIP, int centralPort, String repository) {
        if (!repository.endsWith("/")) {
            repository += "/";
        }
        this.repository = repository;

        centralAddr = new InetSocketAddress(centralIP, centralPort);
    }

    private void doConnect() throws Exception {
        logger.info("与中央服务器进行身份认证");

        String response = requestToCentral(Constant.CMD_CONNECT, centralAddr, true);
        if (Constant.CMD_ACCEPT.equals(response)) {
            logger.info("身份认证成功");
        }
    }

    private void doQuit() throws Exception {
        requestToCentral(Constant.CMD_QUIT, centralAddr, false);

        logger.info("已与中央服务器断开连接");
        System.exit(0);
    }

    private void doAdd() throws Exception {
        File dir = new File(repository);
        File[] files = dir.listFiles();

        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                File file = files[i];
                logger.info(i + "\t" + file.getName());
            }

            Scanner input = new Scanner(System.in);

            logger.info("请输入要共享的文件序号");
            int select = input.nextInt();
            if (select < 0 || select >= files.length) {
                logger.error("无效的文件序号");
                return;
            }

            File file = files[select];
            String hash = MD5Util.getMD5(file);

            StringBuilder sb = new StringBuilder();
            sb.append("add\n");
            sb.append(file.getName());
            sb.append("\t");
            sb.append(hash);
            sb.append("\t");
            sb.append(file.length());

            String response = requestToCentral(sb.toString(), centralAddr, true);
            if (response != null) {
                if (response.startsWith(Constant.CMD_ERROR)) {
                    logger.error("请求失败，原因：{}", response.split("\n")[1]);
                    return;
                } else if (response.startsWith(Constant.CMD_OK)) {
                    logger.info("请求添加共享文件成功");
                    return;
                }
            }

            logger.error("未知的文件头");
        }
    }

    private void doList() throws Exception {
        logger.info("向中央服务器请求可下载文件列表");

        String response = requestToCentral(Constant.CMD_LIST, centralAddr, true);
        if (response != null) {
            if (response.startsWith(Constant.CMD_OK)) {
                response = response.substring(Constant.CMD_OK.length() + 1);

                String[] files = response.split("\n");
                for (int i = 0; i < files.length; i++) {
                    logger.info("{}\t{}", i, files[i]);
                }
                logger.info("请选择要下载的文件序号（输入-1不进行下载）:");

                Scanner input = new Scanner(System.in);
                int seq = input.nextInt();

                if (seq < 0 || seq > files.length - 1) {
                    return;
                } else {
                    String[] infos = files[seq].split("\t");
                    currentTask = new FileInfo(infos[0], infos[1], Integer.parseInt(infos[2]));
                    currentTaskDone = false;

                    String ownerIP = infos[3];
                    if (ownerIP.contains("/")) {
                        String[] candidates = ownerIP.split("/");
                        ownerIP = candidates[new Random().nextInt(candidates.length)];
                    }

                    SocketAddress anotherPeer = new InetSocketAddress(ownerIP, 12345);

                    String shake = requestToCentral(Constant.CMD_CONNECT + "\n", anotherPeer, true);
                    if (shake != null && shake.startsWith(Constant.CMD_ACCEPT)) {
                        logger.info("与对等方认证成功");

                        StringBuilder sb = new StringBuilder();
                        sb.append(Constant.CMD_REQUEST);
                        sb.append("\n");
                        sb.append(currentTask.getName());

                        requestToCentral(sb.toString(), anotherPeer, false);

                        while (!currentTaskDone) {
                        }
                    } else {
                        logger.error("与对等方认证失败");
                    }

                    return;
                }
            } else if (response.startsWith(Constant.CMD_ERROR)) {
                logger.error("请求失败，原因：{}", response.split("\n")[1]);
                return;
            }
        }

        logger.info("未知的响应头");
    }

    private String requestToCentral(String msg, SocketAddress addr, boolean needResponse) throws Exception {
        SocketChannel sc = SocketChannel.open();
        sc.connect(addr);
        sc.configureBlocking(true);

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.put(msg.getBytes("UTF-8"));
        buffer.flip();
        sc.write(buffer);
        buffer.clear();

        sc.shutdownOutput();

        if (needResponse) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            while ((sc.read(buffer)) != -1) {
                buffer.flip();
                while (buffer.hasRemaining()) {
                    baos.write(buffer.get());
                }
                buffer.clear();
            }

            return new String(baos.toByteArray(), "UTF-8");
        }
        return null;
    }

    @Override
    public void onFileDownloaded(byte[] data) {
        try {
            String md5;
            try {
                md5 = MD5Util.getMD5(data);
            } catch (Exception e) {
                logger.error("获取MD5失败", e);
                return;
            }

            if (!md5.equals(currentTask.getHash())) {
                logger.error("MD5校验失败");
                return;
            }

            try {
                FileOutputStream fos = new FileOutputStream(repository + currentTask.getName());
                fos.write(data);
                fos.flush();
                fos.close();

                logger.info("文件已保存为：{}", repository + currentTask.getName());
            } catch (IOException e) {
                logger.error("保存文件过程中出现异常", e);
            }
        } finally {
            currentTaskDone = true;
        }
    }

    public static void main(String[] args) throws Exception {
        Scanner input = new Scanner(System.in);

        Peer peer = new Peer(args[0], Integer.parseInt(args[1]), args[2]);
        new FileTransferServer(12345, args[2], peer).start();

        while (true) {
            logger.info("请输入指令");
            String cmd = input.nextLine();

            if (cmd.equals(Constant.CMD_CONNECT)) {
                peer.doConnect();
            } else if (cmd.equals(Constant.CMD_ADD)) {
                peer.doAdd();
            } else if (cmd.equals(Constant.CMD_LIST)) {
                peer.doList();
            } else if (cmd.equals(Constant.CMD_QUIT)) {
                peer.doQuit();
            }
        }
    }
}
