package com.nov21th.util;

import java.net.SocketAddress;

/**
 * Created by GuoYonghui on 2017/6/28.
 */
public abstract class IPUtil {

    public static String extractIP(SocketAddress address) {
        String ip = address.toString();
        if (ip.contains(":")) {
            ip = ip.split(":")[0];
        }
        if (ip.startsWith("/")) {
            ip = ip.substring(1);
        }
        return ip;
    }

}
