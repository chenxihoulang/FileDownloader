package com.chw.filedownloader.utils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

/**
 * @author chaihongwei 2020-07-09 15:58
 * io操作工具类
 */
public class IOUtils {
    /**
     * 关闭流
     */
    public static void closeQuietly(final Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (final IOException ioe) {
            // ignore
        }
    }

    /**
     * 重命名文件
     */
    public static boolean renameFileName(File oldFile, File newFile) {
        try {
            if (newFile.exists()) {
                newFile.delete();
            }

            return oldFile.renameTo(newFile);

        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        } finally {
            if (oldFile.exists()) {
                oldFile.delete();
            }
        }
    }
}
