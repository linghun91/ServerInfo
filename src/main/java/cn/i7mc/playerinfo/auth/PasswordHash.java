package cn.i7mc.playerinfo.auth;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * 密码哈希工具类
 * 使用PBKDF2算法进行密码哈希和验证
 */
public class PasswordHash {
    private static final Logger logger = Logger.getLogger("PasswordHash");
    // 算法名称
    private static final String ALGORITHM = "PBKDF2WithHmacSHA1";
    // 迭代次数
    private static final int ITERATIONS = 10000;
    // 密钥长度
    private static final int KEY_LENGTH = 256;
    // 盐长度
    private static final int SALT_LENGTH = 16;
    
    /**
     * 对密码进行哈希
     * @param password 明文密码
     * @return 包含盐的哈希结果，格式为 "salt:hash"
     */
    public static String hashPassword(String password) {
        try {
            // 生成随机盐
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[SALT_LENGTH];
            random.nextBytes(salt);
            
            // 计算哈希
            PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(), 
                salt, 
                ITERATIONS, 
                KEY_LENGTH
            );
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] hash = factory.generateSecret(spec).getEncoded();
            
            // Base64编码盐和哈希结果
            String saltBase64 = Base64.getEncoder().encodeToString(salt);
            String hashBase64 = Base64.getEncoder().encodeToString(hash);
            
            // 返回格式为 "salt:hash"
            return saltBase64 + ":" + hashBase64;
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            logger.severe("密码哈希失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 验证密码是否匹配
     * @param password 待验证的明文密码
     * @param storedHash 存储的哈希值（格式为 "salt:hash"）
     * @return 是否匹配
     */
    public static boolean verifyPassword(String password, String storedHash) {
        try {
            // 分离盐和哈希值
            String[] parts = storedHash.split(":");
            if (parts.length != 2) {
                return false;
            }
            
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] hash = Base64.getDecoder().decode(parts[1]);
            
            // 使用相同的盐计算哈希
            PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(), 
                salt, 
                ITERATIONS, 
                KEY_LENGTH
            );
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] testHash = factory.generateSecret(spec).getEncoded();
            
            // 比较哈希值
            int diff = hash.length ^ testHash.length;
            for (int i = 0; i < hash.length && i < testHash.length; i++) {
                diff |= hash[i] ^ testHash[i];
            }
            
            return diff == 0;
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IllegalArgumentException e) {
            logger.severe("密码验证错误: " + e.getMessage());
            return false;
        }
    }

    /**
     * 验证密码是否匹配（verifyPassword的别名）
     * @param password 待验证的明文密码
     * @param storedHash 存储的哈希值（格式为 "salt:hash"）
     * @return 是否匹配
     */
    public static boolean check(String password, String storedHash) {
        return verifyPassword(password, storedHash);
    }
} 