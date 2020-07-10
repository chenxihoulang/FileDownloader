package com.chw.filedownloader;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.chw.filedownloader.constant.Constants;
import com.chw.filedownloader.workmanager.DownloadWorker;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements IDownloadListener {

    private EditText etFileUrl;
    private EditText etCachePath;
    private Button btnDownload;
    private Button btnCancelDownload;
    private Button btnWorkerDownload;
    private Button btnClearCache;
    private ProgressBar pbProgress;
    private Button btnCancelWorker;
    private TextView tvWorkerState;
    private TextView tvProgress;
    /**
     * 用于标记下载任务是否需要停止
     */
    private volatile boolean mStopped;

    /**
     * 下载请求对象,方便取消下载任务
     */
    private OneTimeWorkRequest mDownloadWorkRequest;
    private String mTag = "2020-07-10";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etFileUrl = findViewById(R.id.etFileUrl);
        etCachePath = findViewById(R.id.etCachePath);
        btnDownload = findViewById(R.id.btnDownload);
        btnCancelDownload = findViewById(R.id.btnCancelDownload);
        btnWorkerDownload = findViewById(R.id.btnWorkerDownload);
        pbProgress = findViewById(R.id.pbProgress);
        btnCancelWorker = findViewById(R.id.btnCancelWorker);
        tvWorkerState = findViewById(R.id.tvWorkerState);
        btnClearCache = findViewById(R.id.btnClearCache);
        tvProgress = findViewById(R.id.tvProgress);

        btnDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String fileUrl = etFileUrl.getText().toString();

                if (TextUtils.isEmpty(fileUrl)) {
                    Toast.makeText(MainActivity.this, "请输入url", Toast.LENGTH_SHORT).show();
                    return;
                }

                downloadFile(fileUrl);
            }
        });

        btnCancelDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mStopped = true;
            }
        });

        btnWorkerDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String fileUrl = etFileUrl.getText().toString();

                if (TextUtils.isEmpty(fileUrl)) {
                    Toast.makeText(MainActivity.this, "请输入url", Toast.LENGTH_SHORT).show();
                    return;
                }

                downloadFileUseWorkerManager(fileUrl);
            }
        });

        btnCancelWorker.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mDownloadWorkRequest != null) {
                    WorkManager.getInstance(MainActivity.this).cancelWorkById(mDownloadWorkRequest.getId());
                }
            }
        });

        btnClearCache.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
                    @Override
                    public void run() {
                        FileDownloader.getInstance(MainActivity.this, MainActivity.this)
                                .clearCache();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                etCachePath.setText("");
                                Toast.makeText(MainActivity.this, "清除缓存目录成功", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
            }
        });
    }

    private void downloadFile(final String fileUrl) {
        mStopped = false;

        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                final String cachePath = FileDownloader.getInstance(MainActivity.this, MainActivity.this).download(fileUrl);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        etCachePath.setText(System.currentTimeMillis() + ":" + cachePath);
                    }
                });
            }
        });
    }

    /**
     * 使用WorkManager进行下载操作
     * 参考:https://developer.android.google.cn/topic/libraries/architecture/workmanager
     */
    private void downloadFileUseWorkerManager(final String fileUrl) {
        //输入参数,Data 对象的大小上限为 10KB
        Data inputData = new Data.Builder()
                .putString(Constants.KEY_DOWNLOAD_FILE_URL, fileUrl)
                .build();

        //约束:网络可用+满足最低存储空间限制
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresStorageNotLow(true)
                .build();


        mDownloadWorkRequest = new OneTimeWorkRequest.Builder(DownloadWorker.class)
                //添加约束
                .setConstraints(constraints)
                //添加重试退避政策,每次失败后,延迟10秒再重试
                .setBackoffCriteria(
                        BackoffPolicy.LINEAR,
                        OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                        TimeUnit.MILLISECONDS)
                //设置输入参数,也就是传入文件url
                .setInputData(inputData)
                //添加标记,将任务进行分组,比如过期的任务无需执行,可进行批量取消
                .addTag(mTag)
                .build();

        //任务入队,约束条件满足后会执行
        WorkManager.getInstance(MainActivity.this).enqueue(mDownloadWorkRequest);

        //监听任务执行状态
        WorkManager.getInstance(MainActivity.this).getWorkInfoByIdLiveData(mDownloadWorkRequest.getId())
                .observe(MainActivity.this, new Observer<WorkInfo>() {
                    @Override
                    public void onChanged(@Nullable WorkInfo workInfo) {
                        if (workInfo != null) {
                            //获取进度信息
                            Data progressData = workInfo.getProgress();

                            int progress = progressData.getInt(Constants.PROGRESS, 0);
                            long downloadedSize = progressData.getLong(Constants.DOWNLOADED_SIZE, 0);
                            long totalSize = progressData.getLong(Constants.TOTAL_SIZE, 0);
                            if (totalSize > 0 && downloadedSize >= 0) {
                                //展示进度信息
                                onProgress(progress, downloadedSize, totalSize);
                            }

                            tvWorkerState.setText(workInfo.getState().toString());

                            if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                                String cachePath = workInfo.getOutputData().getString(Constants.KEY_CACHE_FILE_PATH);
                                etCachePath.setText(System.currentTimeMillis() + ":" + cachePath);
                            }
                        }
                    }
                });
    }

    @Override
    public boolean isCanceled() {
        return mStopped;
    }

    @Override
    public void onProgress(int progress, long downloadedSize, long totalSize) {
        pbProgress.setProgress(progress);
        tvProgress.setText("" + downloadedSize + "/" + totalSize);
    }
}