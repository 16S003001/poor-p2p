import com.nov21th.FTTask;
import com.nov21th.common.Constant;
import com.nov21th.util.FTCallback;
import com.nov21th.util.FTServer;
import com.nov21th.util.FileInfo;
import com.nov21th.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.security.MessageDigest;
import java.util.Scanner;

/**
 * @author 郭永辉
 * @since 1.0 2017/6/24.
 */
public class P2PClient implements FTCallback {

    private static final Logger logger = LoggerFactory.getLogger(P2PClient.class);

    private SocketAddress centralAddr;

    private String repository;

    private FTTask currentFT;

    private volatile boolean currentDone;

    public P2PClient(String centralIP, int centralPort, String repository) {
        centralAddr = new InetSocketAddress(centralIP, centralPort);

        this.repository = repository;
    }

    private void doConnect() throws Exception {
        logger.info("与中央服务器进行身份认证：{}", centralAddr);
        String response = requestToCentral(Constant.CMD_CONNECT, true);
        if (Constant.CMD_ACCEPT.equals(response)) {
            logger.info("身份认证成功");
        }
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
            logger.info("请输入要共享的文件序号：");
            int select = input.nextInt();
            if (select < 0 || select >= files.length) {
                logger.error("无效的文件序号");
                return;
            }

            File file = files[select];

            String hash = FileUtil.getMD5(file);

            StringBuilder sb = new StringBuilder();

            sb.append("add\n");
            sb.append(file.getName());
            sb.append('\t');
            sb.append(hash);
            sb.append('\t');
            sb.append(file.length());

            String response = requestToCentral(sb.toString(), true);
            if (response != null) {
                if (response.startsWith(Constant.CMD_ERROR)) {
                    logger.error("请求失败，原因：{}", response.split("\n")[1]);
                    return;
                } else if (response.startsWith(Constant.CMD_OK)) {
                    logger.info("请求添加共享文件成功");
                    return;
                }
            }

            logger.info("未知的响应头");
        }
    }

    private void doList() throws Exception {
        logger.info("向中央服务器请求可下载文件列表");
        String response = requestToCentral(Constant.CMD_LIST, true);
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
                    FileInfo info = new FileInfo(infos[0], infos[1], Integer.parseInt(infos[2]));

                    currentDone = false;
                    currentFT = new FTTask(info, infos[3]);
                    currentFT.download();

                    while (!currentDone) {

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

    private String requestToCentral(String requestMsg, boolean needResponse) throws Exception {
        Socket socket = null;
        ByteArrayOutputStream baos = null;
        try {
            socket = new Socket();
            socket.connect(centralAddr);

            socket.getOutputStream().write(requestMsg.getBytes("UTF-8"));
            socket.shutdownOutput();

            if (needResponse) {
                baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = socket.getInputStream().read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                baos.flush();

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

    @Override
    public void onFileTransferDone(byte[] data) {
        String md5 = null;
        try {
            md5 = FileUtil.getMD5(data);
        } catch (Exception e) {
            e.printStackTrace();
        }

        FileInfo info = currentFT.getFileInfo();
        if (!md5.equals(info.getHash())) {
            logger.error("下载失败，MD5校验未通过");
        }

        try {
            FileOutputStream fos = new FileOutputStream(repository + info.getName());
            fos.write(data);
            fos.flush();
            fos.close();

            logger.info("文件已保存为：{}", repository + info.getName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        currentDone = true;
    }

    public static void main(String[] args) throws Exception {

        Scanner input = new Scanner(System.in);

        P2PClient client = new P2PClient(args[0], Integer.parseInt(args[1]), args[2]);
        new FTServer(12345, args[2], client).start();

        while (true) {
            logger.info("请输入指令：");
            String cmd = input.nextLine();

            if (cmd.equals(Constant.CMD_CONNECT)) {
                client.doConnect();
            } else if (cmd.equals(Constant.CMD_ADD)) {
                client.doAdd();
            } else if (cmd.equals(Constant.CMD_LIST)) {
                client.doList();
            } else {
                logger.error("未知的指令");
            }
        }
    }
}
