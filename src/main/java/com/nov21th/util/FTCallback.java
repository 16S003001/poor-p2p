package com.nov21th.util;

import java.io.ByteArrayOutputStream;

/**
 * @author 郭永辉
 * @since 1.0 2017/6/25.
 */
public interface FTCallback {

    void onFileTransferDone(byte[] data);

}
