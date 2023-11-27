package io.github.xyw10000.mybatis.encrypte.spi;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Base64;

/**
 * @author one.xu
 */
@Slf4j
public class DefaultEncryptionService implements IEncryptionService {
    private static final String KEY_ALGORITHM = "AES";
    private static final String DEFAULT_CIPHER_ALGORITHM = "AES/ECB/PKCS7Padding";
    private static final String CHARSET = "utf-8";

    static {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }

    @Override
    @SneakyThrows
    public String encrypt(String content, String password) {
        // 创建密码器
        Cipher cipher = Cipher.getInstance(DEFAULT_CIPHER_ALGORITHM);

        byte[] byteContent = content.getBytes(CHARSET);
        // 初始化为加密模式的密码器
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(password));

        // 加密
        byte[] result = cipher.doFinal(byteContent);

        //通过Base64转码返回
        return Base64.getEncoder().encodeToString(result);
    }

    @Override
    @SneakyThrows
    public String decrypt(String content, String password) {
        //实例化
        Cipher cipher = Cipher.getInstance(DEFAULT_CIPHER_ALGORITHM);
        //使用密钥初始化，设置为解密模式
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(password));
        try {
        	//执行操作
        	byte[] result = cipher.doFinal(Base64.getDecoder().decode(content));
        	return new String(result, CHARSET);
		} catch (Exception e) {
			log.error("content:{}",content);
			throw e;
		}

    }

    private Key getSecretKey(String password) {
        return new SecretKeySpec(Base64.getDecoder().decode(password), KEY_ALGORITHM);
    }

    /**
     * 随机指定生成的16进制字符串的长度字符串
     *
     * @param length
     * @return
     */
    private String generateKey(int length) {
        SecureRandom secureRandom = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            // 生成0-15的随机数
            int num = secureRandom.nextInt(16);
            // 将随机数转换为16进制字符
            sb.append(Integer.toHexString(num));
        }
        return sb.toString();
    }

    @SneakyThrows
    public static void main(String[] args) {
        DefaultEncryptionService defaultEncryptionService = new DefaultEncryptionService();
        String data = "555555";
        String key = defaultEncryptionService.generateKey(16);
        System.out.println("key:" + key);
        String password = Base64.getEncoder().encodeToString(key.getBytes());
        System.out.println("password:" + password);
        password = "MmJjYzgzOTg4MzNmZDBhNw==";
        System.out.println("原始:" + data);
        String encrypt = defaultEncryptionService.encrypt(data, password);
        System.out.println("加密:" + encrypt);
        String decrypt = defaultEncryptionService.decrypt(encrypt, password);
        System.out.println("解密:" + decrypt);
    }
}
