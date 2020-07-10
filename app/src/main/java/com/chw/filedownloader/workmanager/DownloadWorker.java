package com.chw.filedownloader.workmanager;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.chw.filedownloader.IDownloadListener;
import com.chw.filedownloader.constant.Constants;
import com.chw.filedownloader.FileDownloader;

/**
 * @author chaihongwei 2020-07-10 08:34
 * 用于文件下载的后台任务
 */
public class DownloadWorker extends Worker implements IDownloadListener {

    public DownloadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        // 设置初始进度为0
        setProgressAsync(new Data.Builder().putInt(Constants.PROGRESS, 0).build());
    }

    @NonNull
    @Override
    public Result doWork() {

        //获取输入参数
        String fileUrl = getInputData().getString(Constants.KEY_DOWNLOAD_FILE_URL);
        final String cachePath = FileDownloader.getInstance(getApplicationContext(), this).download(fileUrl);

        if (TextUtils.isEmpty(cachePath)) {
            return Result.retry();
        }

        //下载完成后的输出数据
        Data outputData = new Data.Builder()
                .putString(Constants.KEY_CACHE_FILE_PATH, cachePath)
                .putInt(Constants.PROGRESS, 100)
                .build();

        return Result.success(outputData);
    }

    @Override
    public boolean isCanceled() {
        return isStopped();
    }

    @Override
    public void onProgress(int progress, long downloadedSize, long totalSize) {
        Data progressData = new Data.Builder()
                .putInt(Constants.PROGRESS, progress)
                .putLong(Constants.DOWNLOADED_SIZE, downloadedSize)
                .putLong(Constants.TOTAL_SIZE, totalSize)
                .build();
        //设置执行进度
        setProgressAsync(progressData);
    }
}
