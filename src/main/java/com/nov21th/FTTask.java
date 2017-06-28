package com.nov21th;

import com.nov21th.common.Constant;
import com.nov21th.util.FileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Random;

/**
 * @author 郭永辉
 * @since 1.0 2017/6/25.
 */
public class FTTask {

    private static final Logger logger = LoggerFactory.getLogger(FTTask.class);

    private FileInfo fileInfo;

    private SocketAddress peerAddr;

    public FileInfo getFileInfo() {
        return fileInfo;
    }

    public FTTask(FileInfo fileInfo, String ownerIP) {
        this.fileInfo = fileInfo;

        if (ownerIP.contains("/")) {
            String[] candidates = ownerIP.split("/");
            ownerIP = candidates[new Random().nextInt(candidates.length)];
        }

        this.peerAddr = new InetSocketAddress(ownerIP, 12345);
    }

    public boolean download() throws Exception {
        logger.info("与对等方进行身份认证：{}", peerAddr);
        if (sayHi()) {
            logger.info("认证成功");
            StringBuilder sb = new StringBuilder();
            sb.append(Constant.CMD_REQUEST);
            sb.append("\n");
            sb.append(fileInfo.getName());

            requestToPeer(sb.toString(), false);

            return true;
        } else {
            logger.info("认证失败");
            return false;
        }
    }

    private boolean sayHi() throws Exception {
        String response = requestToPeer(Constant.CMD_CONNECT + "\n", true);
        return response != null && response.startsWith(Constant.CMD_ACCEPT);
    }

    private String requestToPeer(String requestMsg, boolean needResponse) throws Exception {
        Socket socket = null;
        ByteArrayOutputStream baos = null;
        try {
            socket = new Socket();
            socket.connect(peerAddr);

            socket.getOutputStream().write(requestMsg.getBytes("UTF-8"));
            socket.shutdownOutput();

            if (needResponse) {
                try {
                    baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = socket.getInputStream().read(buffer)) != -1) {
                        baos.write(buffer, 0, bytesRead);
                    }
                    baos.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                return new String(baos.toByteArray(), "UTF-8").trim();
            } else {
                return null;
            }
        } finally {
            if (socket != null) {
                socket.close();
            }
            if (baos != null) {
                baos.close();
            }
        }

    }
}