package com.nov21th.tcp.file;

import com.nov21th.common.Constant;
import com.nov21th.tcp.TCPServer;
import com.nov21th.util.IPUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

/**
 * Created by GuoYonghui on 2017/6/29.
 */
public class FileTransferServer extends TCPServer {

    private String repository;

    private FileTransferCallback callback;

    public FileTransferServer(int port, String repository, FileTransferCallback callback) {
        super(port);

        if (!repository.endsWith("/")) {
            repository += "/";
        }

        this.repository = repository;
        this.callback = callback;
    }

    public FileTransferServer(int port, int bufferSize, String repository, FileTransferCallback callback) {
        super(port, bufferSize);

        if (!repository.endsWith("/")) {
            repository += "/";
        }

        this.repository = repository;
        this.callback = callback;
    }

    @Override
    protected Logger getLogger() {
        return LoggerFactory.getLogger(FileTransferServer.class);
    }

    @Override
    protected void onDataReceived(SelectionKey sk, byte[] data) throws Exception {
        SocketChannel sc = (SocketChannel) sk.channel();
        SocketAddress addr = sc.getRemoteAddress();

        int headerLength = -1;
        for (int i = 0; i < Math.min(100, data.length); i++) {
            if ((char) data[i] == '\n') {
                headerLength = i;
                break;
            }
        }

        if (headerLength == -1) {
            logger.error("无效的数据头");
            return;
        }

        String header = new String(data, 0, headerLength);

        switch (header) {
            case Constant.CMD_CONNECT:
                logger.info("对等方请求进行身份认证：{}", addr);

                buffer.clear();
                buffer.put((Constant.CMD_ACCEPT + "\n").getBytes());
                buffer.flip();
                while (buffer.hasRemaining()) {
                    sc.write(buffer);
                }
                buffer.clear();

                sc.shutdownOutput();
                break;
            case Constant.CMD_REQUEST:
                String filename = new String(data, Constant.CMD_REQUEST.length() + 1, data.length - Constant.CMD_REQUEST.length() - 1);

                logger.info("对等方请求文件传输：{}，请求来自：{}", filename, addr);

                FileChannel fc = new FileInputStream(repository + filename).getChannel();

                SocketChannel sendChannel = SocketChannel.open();
                sendChannel.configureBlocking(true);
                sendChannel.connect(new InetSocketAddress(IPUtil.extractIP(addr), 12345));

                buffer.clear();
                buffer.put((Constant.CMD_FILE + "\n").getBytes());
                buffer.flip();
                sendChannel.write(buffer);
                buffer.clear();
                while (fc.read(buffer) > -1) {
                    buffer.flip();
                    sendChannel.write(buffer);
                    buffer.clear();
                }
                sendChannel.shutdownOutput();

                fc.close();
                sendChannel.close();

                logger.info("文件传输完毕：{}，传送至：{}", filename, addr);
                break;
            case Constant.CMD_FILE:
                logger.info("接收到对等方传输的文件：{}", addr);

                byte[] temp = new byte[data.length - Constant.CMD_FILE.length() - 1];
                System.arraycopy(data, Constant.CMD_FILE.length() + 1, temp, 0, temp.length);

                callback.onFileDownloaded(temp);
                break;

            default:
                logger.error("未知的命令");
        }
    }
}
