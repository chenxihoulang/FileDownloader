package com.chw.filedownloader.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author chaihongwei 2020-07-09 13:47
 * 加密工具类
 */
public class EncryptUtils {
    /**
     * SHA-1加密
     *
     * @param info 需要加密的字符串
     * @return 加密后的结果
     */
    public static String encryptToSHA(String info) {
        byte[] digesta = null;
        try {
            MessageDigest alga = MessageDigest.getInstance("SHA-1");
            alga.update(info.getBytes());
            digesta = alga.digest();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        String rs = byte2hex(digesta);
        return rs;
    }

    public static String byte2hex(byte[] b) {
        String hs = "";
        String stmp = "";
        for (int n = 0; n < b.length; n++) {
            stmp = (Integer.toHexString(b[n] & 0XFF));
            if (stmp.length() == 1) {
                hs = hs + "0" + stmp;
            } else {
                hs = hs + stmp;
            }
        }
        return hs;
    }

    /**
     * 我的-健康童学url链接拼接的signature,将四个参数排序，并进行sha1加密后返回加过
     *
     * @param token     第三方的
     * @param timestamp 时间戳
     * @param nonce     随机数
     * @param userid    用户id
     * @return 将四个参数排序并加密后的结果
     */
    public static String getEncrypyResult(String token, String timestamp, String nonce, String userid) {
        String encrypyResult = "";
        StringBuilder builder = new StringBuilder();
        List<String> results = new ArrayList<>();
        results.add(token);
        results.add(timestamp);
        results.add(userid);
        results.add(nonce);
        Collections.sort(results);

        for (String result : results) {
            builder.append(result);
        }
        encrypyResult = encryptToSHA(builder.toString());
        return encrypyResult;
    }

    /**
     * 使用MD5算法对传入的key进行加密并返回。
     */
    public static String hashKeyForDisk(String key) {
        String cacheKey;
        try {
            final MessageDigest mDigest = MessageDigest.getInstance("MD5");
            mDigest.update(key.getBytes());
            cacheKey = bytesToHexString(mDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            cacheKey = String.valueOf(key.hashCode());
        }
        return cacheKey;
    }

    private static String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }
}
