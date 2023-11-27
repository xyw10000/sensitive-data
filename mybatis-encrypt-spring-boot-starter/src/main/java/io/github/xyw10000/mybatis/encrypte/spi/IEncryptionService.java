package io.github.xyw10000.mybatis.encrypte.spi;

/**
 * @author one.xu
 */
public interface IEncryptionService {
    String encrypt(String content, String password);

    String decrypt(String content, String password);
}
