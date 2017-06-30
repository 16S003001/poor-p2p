package com.nov21th.tcp.file;

/**
 * Created by GuoYonghui on 2017/6/28.
 */
public interface FileTransferCallback {

    void onFileDownloaded(byte[] data);

}
