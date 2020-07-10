package com.chw.filedownloader.config;

import android.content.Context;

import androidx.annotation.NonNull;

import com.chw.filedownloader.constant.ByteConstants;
import com.chw.filedownloader.utils.FileCacheUtils;

import java.io.File;

/**
 * @author chaihongwei 2020-07-09 13:52
 * 文件下载配置信息
 */
public class FileDownloaderConfig {
    /**
     * 缓存文件版本号,版本号不一致,以前的缓存数据会被清空
     */
    private static final int CACHE_VERSION = 1;
    /**
     * 最大缓存大小,200M
     */
    private static final long MAX_CACHE_SIZE = 200 * ByteConstants.MB;
    /**
     * 默认缓存文件目录名称
     */
    private static final String DEFAULT_CACHE_DIR_NAME = "__file_cache_dir__";

    private Context mAppContext;
    private int mCacheVersion;
    private long mMaxCacheSize;
    private File mCacheDir;

    private FileDownloaderConfig(Builder builder) {
        this.mAppContext = builder.mAppContext;
        this.mCacheVersion = builder.mCacheVersion;

        if (builder.mMaxCacheSize == 0) {
            this.mMaxCacheSize = MAX_CACHE_SIZE;
        }

        if (builder.mCacheDir == null) {
            mCacheDir = getDiskCacheDir(mAppContext, DEFAULT_CACHE_DIR_NAME);
        }
    }

    public Context getAppContext() {
        return mAppContext;
    }

    public int getCacheVersion() {
        return mCacheVersion;
    }

    public long getMaxCacheSize() {
        return mMaxCacheSize;
    }

    public File getCacheDir() {
        return mCacheDir;
    }

    public static class Builder {
        private Context mAppContext;
        private int mCacheVersion = CACHE_VERSION;
        private long mMaxCacheSize;
        private File mCacheDir;

        public Builder(@NonNull Context appContext) {
            this.mAppContext = appContext.getApplicationContext();
        }

        /**
         * 设置缓存文件版本号,版本号不一致,以前的缓存数据会被清空
         */
        public Builder setCacheVersion(int cacheVersion) {
            mCacheVersion = cacheVersion;
            return this;
        }

        /**
         * 设置最大缓存大小,默认200M
         */
        public Builder setMaxCacheSize(long maxCacheSize) {
            mMaxCacheSize = maxCacheSize;
            return this;
        }

        /**
         * 设置缓存文件目录
         */
        public Builder setCacheDir(File cacheDir) {
            mCacheDir = cacheDir;
            return this;
        }

        public FileDownloaderConfig build() {
            return new FileDownloaderConfig(this);
        }
    }

    /**
     * 获取磁盘缓存目录
     */
    private static File getDiskCacheDir(Context context, String uniqueName) {
        File newFile = new File(FileCacheUtils.getCacheDirectory(context, "") + File.separator + uniqueName);
        if (!newFile.exists()) {
            newFile.mkdirs();
        }

        return newFile;
    }
}
