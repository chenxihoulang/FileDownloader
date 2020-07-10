package com.chw.filedownloader;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.chw.filedownloader.config.FileDownloaderConfig;
import com.chw.filedownloader.utils.DiskLruCache;
import com.chw.filedownloader.utils.EncryptUtils;
import com.chw.filedownloader.utils.IOUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * @author chaihongwei 2020-07-09 13:47
 * 文件下载
 * 参考流程图:https://www.processon.com/view/link/5d25529ee4b0fdb331d90d55
 */
public class FileDownloader {
    private static final String TAG = FileDownloader.class.getSimpleName();
    /**
     * 下载中的缓存文件后缀
     */
    private static final String DOWNLOADING_FILE_SUFFIX = "_downloading";
    /**
     * 已经下载完成的缓存文件后缀
     */
    private static final String DOWNLOAD_COMPLETED_FILE_SUFFIX = "_done";

    private FileDownloaderConfig mConfig;
    private DiskLruCache mDiskLruCache;

    /**
     * 控制下载是否应停止
     */
    private IDownloadListener mDownloadListener;
    /**
     * 已经下载完的字节大小,用于计算下载进度
     */
    private long mDownloadedSize;
    /**
     * 文件总大小,用于计算下载进度
     */
    private long mTotalSize;

    private static Handler sMainHandler = new Handler(Looper.getMainLooper());

    public static FileDownloader getInstance(@NonNull Context appContext,
                                             @Nullable IDownloadListener downloadListener) {
        FileDownloaderConfig config = new FileDownloaderConfig.Builder(appContext).build();
        return getInstance(config, downloadListener);
    }

    public static FileDownloader getInstance(@NonNull FileDownloaderConfig config,
                                             @Nullable IDownloadListener downloadListener) {
        return new FileDownloader(config, downloadListener);
    }

    private FileDownloader(@NonNull FileDownloaderConfig config,
                           @Nullable IDownloadListener downloadListener) {
        this.mConfig = config;
        this.mDownloadListener = downloadListener;

        try {
            // 创建DiskLruCache实例，初始化缓存数据
            mDiskLruCache = DiskLruCache.open(config.getCacheDir(), config.getCacheVersion(),
                    1, config.getMaxCacheSize());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * 下载文件
     *
     * @param fileUrl 网络文件地址
     * @return 返回下载完成后的缓存文件路径, 若没有对应的缓存文件, 或者下载过程中失败了, 则返回""
     */
    public String download(String fileUrl) {
        if (TextUtils.isEmpty(fileUrl)) {
            return "";
        }

        //文件缓存对应的key
        String fileCacheKey = EncryptUtils.hashKeyForDisk(fileUrl);

        DiskLruCache.Snapshot snapShot;
        try {
            //查找key对应的缓存
            snapShot = mDiskLruCache.get(fileCacheKey);

            //有对应的缓存文件
            if (snapShot != null) {
                return getCacheFilePath(fileCacheKey);
            }

            //整个文件都下载完后对应的存储路径
            String downloadCompleteFilePath = fileCacheKey + DOWNLOAD_COMPLETED_FILE_SUFFIX;
            File downloadCompleteFile = new File(mConfig.getCacheDir(), downloadCompleteFilePath);

            //如果此文件存在,说明上次下载完成后,由于某种情况还没有同步到缓存目录中
            if (downloadCompleteFile.exists()) {
                //将下载完成的文件拷贝到缓存目录中
                if (copyFileToCache(downloadCompleteFile, fileCacheKey)) {
                    //拷贝成功后,删除文件
                    downloadCompleteFile.delete();

                    return getCacheFilePath(fileCacheKey);
                } else {
                    //拷贝失败,直接返回下载完成的完整文件路径,一般不会走到这里
                    return downloadCompleteFilePath;
                }
            }

            //缓存中的文件存储路径
            String downloadingFilePath = fileCacheKey + DOWNLOADING_FILE_SUFFIX;
            File downloadingFile = new File(mConfig.getCacheDir(), downloadingFilePath);

            long partialDownloadedSize = 0;
            //如果文件存在,说明已经有部分缓存文件
            if (downloadingFile.exists()) {
                partialDownloadedSize = downloadingFile.length();
            } else {
                downloadingFile.createNewFile();
            }

            //走到这里,说明本地没有完整的缓存文件,需要从网络下载文件
            if (downloadFromNet(fileUrl, downloadingFile, partialDownloadedSize)) {
                //文件下载成功后,进行文件重命名
                if (IOUtils.renameFileName(downloadingFile, downloadCompleteFile)) {
                    //将下载完成的文件拷贝到缓存目录中
                    if (copyFileToCache(downloadCompleteFile, fileCacheKey)) {
                        //拷贝成功后,删除文件
                        downloadCompleteFile.delete();

                        return getCacheFilePath(fileCacheKey);
                    } else {
                        //拷贝失败,直接返回下载完成的完整文件路径,一般不会走到这里
                        return downloadCompleteFilePath;
                    }
                }
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return "";
    }

    /**
     * 通过网络下载文件
     *
     * @param fileUrl    文件请求路径
     * @param saveFile   保存到的文件
     * @param rangeStart 分块下载文件的开始位置
     */
    private boolean downloadFromNet(String fileUrl, File saveFile, long rangeStart) {
        boolean flag = false;

        HttpURLConnection urlConnection = null;
        BufferedInputStream in = null;
        RandomAccessFile raf = null;
        try {
            final URL url = new URL(fileUrl);
            urlConnection = (HttpURLConnection) url.openConnection();
            //设置分块下载文件,从rangeStart到最后
            // http分块header的个数 "Range":"bytes=start-[end]",end可选,但一定要有start后面的'-'
            urlConnection.setRequestProperty("Range", "bytes=" + rangeStart + "-");

            //服务器支持分块下载
            if (urlConnection.getResponseCode() == HttpURLConnection.HTTP_PARTIAL) {
                raf = new RandomAccessFile(saveFile, "rwd");
                raf.seek(rangeStart);

                //当前返回的分块大小
                String contentRange = urlConnection.getHeaderField("Content-Range");
                Log.e(TAG, "contentRange:" + contentRange);

                mDownloadedSize = rangeStart;
                //文件总大小
                mTotalSize = Long.parseLong(contentRange.split("/")[1]);

                in = new BufferedInputStream(urlConnection.getInputStream(), 8 * 1024);
                byte[] buffer = new byte[8 * 1024];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    mDownloadedSize += len;

                    //如果用户已经取消,则停止文件写入
                    if (mDownloadListener != null) {
                        if (mDownloadListener.isCanceled()) {
                            return false;
                        } else {
                            publishProgress(mDownloadedSize, mTotalSize);
                        }
                    }

                    SystemClock.sleep(20);

                    //写入文件
                    raf.write(buffer, 0, len);
                }

                flag = true;
            } else if (urlConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                //服务器不支持分块下载
                //先删除文件,再重新创建一个新文件,不确定使用RandomAccessFile.seek(0)覆盖能否可行?如果可行就能与上面的合并了
                saveFile.delete();
                saveFile.createNewFile();

                mDownloadedSize = 0;
                //文件总大小
                mTotalSize = urlConnection.getContentLength();

                flag = copyStream(true, urlConnection.getInputStream(), new FileOutputStream(saveFile));
            } else {
                flag = false;
            }

        } catch (final Exception e) {
            e.printStackTrace();
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }

            IOUtils.closeQuietly(raf);
            IOUtils.closeQuietly(in);
        }
        return flag;
    }

    /**
     * 获取缓存文件完整路径
     */
    private String getCacheFilePath(String fileCacheKey) {
        File cacheFile = new File(mConfig.getCacheDir(), fileCacheKey);
        return cacheFile.getAbsolutePath();
    }

    /**
     * 将下载好的文件拷贝到缓存目录
     *
     * @return 返回拷贝是否成功, 成功返回true, 失败返回false, 比如同一个fileCacheKey同时被编辑就会出错返回false
     */
    private boolean copyFileToCache(File sourceFile, String fileCacheKey) throws Exception {
        boolean flag = false;

        DiskLruCache.Editor editor = mDiskLruCache.edit(fileCacheKey);
        if (editor != null) {
            OutputStream outputStream = editor.newOutputStream(0);
            if (copyStream(false, new FileInputStream(sourceFile), outputStream)) {
                editor.commit();

                flag = true;
            } else {
                editor.abort();
            }
        }

        //刷新缓存日志数据
        mDiskLruCache.flush();

        return flag;
    }

    /**
     * 拷贝文件流
     *
     * @param isNetworkStream 是否是网络流拷贝,网络流拷贝有进度提示,本地流无进度提示
     */
    private boolean copyStream(boolean isNetworkStream, InputStream inputStream, OutputStream outputStream) {
        boolean flag = false;

        //在次方法中,每次都重置为0,防止网络流拷贝和本地流拷贝导致重复累加
        mDownloadedSize = 0L;

        BufferedInputStream in = null;
        BufferedOutputStream out = null;
        try {
            in = new BufferedInputStream(inputStream, 8 * 1024);
            out = new BufferedOutputStream(outputStream, 8 * 1024);
            byte[] buffer = new byte[8 * 1024];
            int len;
            while ((len = in.read(buffer)) != -1) {
                mDownloadedSize += len;

                //如果用户已经取消,则停止文件写入
                if (mDownloadListener != null) {
                    if (mDownloadListener.isCanceled()) {
                        return false;
                    } else if (isNetworkStream) {
                        publishProgress(mDownloadedSize, mTotalSize);
                    }
                }

                //写入文件
                out.write(buffer, 0, len);
            }

            flag = true;
        } catch (final Exception e) {
            e.printStackTrace();
        } finally {
            IOUtils.closeQuietly(in);
            IOUtils.closeQuietly(out);
        }
        return flag;
    }

    /**
     * 在主线程中发布进度
     */
    private void publishProgress(final long downloadedSize, final long totalSize) {
        if (mDownloadListener == null) {
            return;
        }

        sMainHandler.post(new Runnable() {
            @Override
            public void run() {
                int progress = 0;
                if (totalSize > 0 && downloadedSize >= 0) {
                    progress = (int) (1.0F * downloadedSize / totalSize * 100);
                }
                mDownloadListener.onProgress(progress, downloadedSize, totalSize);
            }
        });
    }

    /**
     * 清除缓存目录中的所有文件,包括不是该目录中不是DiskLruCache创建的文件
     */
    public void clearCache() {
        try {
            mDiskLruCache.delete();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
