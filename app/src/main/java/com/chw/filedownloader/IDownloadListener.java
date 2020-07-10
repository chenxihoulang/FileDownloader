package com.chw.filedownloader;

/**
 * @author chaihongwei 2020-07-10 10:03
 * 控制下载
 */
public interface IDownloadListener {
    /**
     * 下载任务是否需要停止
     */
    boolean isCanceled();

    /**
     * 下载进度
     */
    void onProgress(int progress, long downloadedSize, long totalSize);
}
