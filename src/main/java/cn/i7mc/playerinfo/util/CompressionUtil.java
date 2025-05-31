package cn.i7mc.playerinfo.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.logging.Logger;

/**
 * 数据压缩工具类
 * 用于GZIP压缩和解压缩数据，解决BungeeCord消息大小限制问题
 */
public class CompressionUtil {

    /**
     * 使用GZIP压缩数据
     * @param data 原始数据
     * @param logger 日志记录器，用于记录压缩信息
     * @param debug 是否启用调试日志
     * @return 压缩后的数据
     */
    public static byte[] compress(byte[] data, Logger logger, boolean debug) {
        if (data == null || data.length == 0) {
            return new byte[0];
        }
        
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream(data.length);
        try {
            GZIPOutputStream gzipStream = new GZIPOutputStream(byteStream) {
                {
                    // 设置压缩级别为最高 (9)
                    def.setLevel(9);
                }
            };
            
            gzipStream.write(data);
            gzipStream.close();
            byte[] compressedData = byteStream.toByteArray();
            
            // 记录压缩效率日志
            if (debug) {
                double ratio = (1.0 - (double) compressedData.length / data.length) * 100.0;
                logger.info(String.format("数据压缩: %d -> %d 字节 (压缩率: %.2f%%)",
                        data.length, compressedData.length, ratio));
            }
            
            return compressedData;
        } catch (IOException e) {
            logger.severe("压缩数据失败: " + e.getMessage());
            if (debug) {
                e.printStackTrace();
            }
            return data; // 出错时返回原始数据
        }
    }

    /**
     * 解压缩GZIP数据
     * @param compressedData 压缩数据
     * @param logger 日志记录器
     * @param debug 是否启用调试日志
     * @return 解压后的原始数据
     */
    public static byte[] decompress(byte[] compressedData, Logger logger, boolean debug) {
        if (compressedData == null || compressedData.length == 0) {
            return new byte[0];
        }
        
        try {
            ByteArrayInputStream byteStream = new ByteArrayInputStream(compressedData);
            GZIPInputStream gzipStream = new GZIPInputStream(byteStream);
            ByteArrayOutputStream resultStream = new ByteArrayOutputStream();
            
            byte[] buffer = new byte[8192];
            int length;
            while ((length = gzipStream.read(buffer)) > 0) {
                resultStream.write(buffer, 0, length);
            }
            
            gzipStream.close();
            resultStream.close();
            byte[] decompressedData = resultStream.toByteArray();
            
            if (debug) {
                logger.info(String.format("数据解压: %d -> %d 字节",
                        compressedData.length, decompressedData.length));
            }
            
            return decompressedData;
        } catch (IOException e) {
            logger.severe("解压数据失败: " + e.getMessage());
            if (debug) {
                e.printStackTrace();
            }
            return new byte[0]; // 出错时返回空数组
        }
    }
} 