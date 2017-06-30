package com.nov21th.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;

/**
 * @author 郭永辉
 * @since 1.0 2017/6/24.
 */
public class MD5Util {

    public static String getMD5(byte[] data) throws Exception {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        md5.update(data);
        return toHexString(md5.digest());
    }

    public static String getMD5(File file) throws Exception {
        InputStream fis = new FileInputStream(file);
        byte buffer[] = new byte[1024];
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        for (int bytesRead; (bytesRead = fis.read(buffer)) > 0; ) {
            md5.update(buffer, 0, bytesRead);
        }
        fis.close();
        return toHexString(md5.digest());
    }

    private static String toHexString(byte[] buffer) {
        StringBuilder sb = new StringBuilder();
        for (byte b : buffer) {
            String temp = Integer.toHexString(b & 0xFF);
            if (temp.length() < 2) {
                sb.append(0);
            }
            sb.append(temp);
        }

        return sb.toString();
    }

}
