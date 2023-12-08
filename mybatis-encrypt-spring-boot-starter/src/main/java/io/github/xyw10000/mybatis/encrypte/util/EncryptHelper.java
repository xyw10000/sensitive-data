package io.github.xyw10000.mybatis.encrypte.util;


import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import io.github.xyw10000.mybatis.encrypte.annotation.EnableEncrypt;
import io.github.xyw10000.mybatis.encrypte.annotation.EncryptField;
import io.github.xyw10000.mybatis.encrypte.spi.IEncryptionService;
import lombok.Data;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author one.xu
 */
@Data
public class EncryptHelper {
    public EncryptHelper(IEncryptionService encryptionService, String encryptKey) {
        this.encryptionService = encryptionService;
        this.encryptKey = encryptKey;
    }

    private IEncryptionService encryptionService;
    private String encryptKey;

    public Set<String> getEncryptField(Object param) {
        Set<String> encryptFieldSet = Sets.newHashSet();
        Object object = param;
        boolean isList = object instanceof List;
        if (isList) {
            List<Object> resultList = (List<Object>) object;
            if (CollectionUtils.isEmpty(resultList)) {
                return encryptFieldSet;
            }
            object = resultList.get(0);
        }
        Class<?> objectClass = object.getClass();
        EnableEncrypt enableEncrypt = AnnotationUtils.findAnnotation(objectClass, EnableEncrypt.class);
        if (enableEncrypt == null) {
            return encryptFieldSet;
        }
        for (Field[] fields : getFields(objectClass)) {
            for (Field field : fields) {
                EncryptField encryptField = field.getAnnotation(EncryptField.class);
                if (encryptField == null) {
                    continue;
                }
                encryptFieldSet.add(field.getName());
            }
        }
        return encryptFieldSet;
    }

    private List<Field[]> getFields(Class<?> clazz) {
        List<Field[]> fieldsList = Lists.newArrayList();
        Class<?> resultClass = clazz;
        while (resultClass != null) {
            fieldsList.add(resultClass.getDeclaredFields());
            resultClass = resultClass.getSuperclass();
        }
        return fieldsList;
    }

    public <T> T execute(T result, Set<String> encryptFields, boolean isEncrypt) throws IllegalAccessException {
        if (result == null || CollectionUtils.isEmpty(encryptFields)) {
            return result;
        }
        boolean isList = result instanceof List;
        if (!isList) {
            return executeSingle(result, encryptFields, isEncrypt);
        }
        List<Object> resultList = (List<Object>) result;
        for (Object obj : resultList) {
            executeSingle(obj, encryptFields, isEncrypt);
        }
        return result;
    }

    private <T> T executeSingle(T result, Set<String> encryptFields, boolean isEncrypt) throws IllegalAccessException {
        if (result == null || CollectionUtils.isEmpty(encryptFields)) {
            return result;
        }
        if (result instanceof Map) {
            Map<String, Object> map = ((Map<String, Object>) result);
            for (String f : encryptFields) {
                Object o = map.get(f);
                if (o == null) {
                    continue;
                }
                if (o instanceof List) {
                    List tmpList = (List) o;
                    if (CollectionUtils.isEmpty(tmpList)) {
                        continue;
                    }
                    Set<String> encryptFields2 = getEncryptField(tmpList.get(0));
                    if (CollectionUtils.isEmpty(encryptFields2)) {
                        continue;
                    }
                    for (Object o1 : tmpList) {
                        executeSingle(o1, encryptFields2, isEncrypt);
                    }
                } else if (o.getClass().getClassLoader() != null) {
                    executeSingle(o, getEncryptField(o), isEncrypt);
                } else {
                    String value = String.valueOf(o);
                    value = isEncrypt ? encrypt(value) : decrypt(value);
                    map.put(f, value);
                }
            }
            return result;
        }

        if (result.getClass().getClassLoader() == null) {
            result = (T) (isEncrypt ? encrypt(String.valueOf(result)) : decrypt(String.valueOf(result)));
            return result;
        }
        //自定义对象
        for (Field[] fields : getFields(result.getClass())) {
            for (Field field : fields) {
                if (!encryptFields.contains(field.getName())) {
                    continue;
                }
                field.setAccessible(true);
                Object o = field.get(result);
                if (o == null) {
                    continue;
                }
                if (o instanceof List) {
                    List tmpList = (List) o;
                    if (CollectionUtils.isEmpty(tmpList)) {
                        continue;
                    }
                    Set<String> encryptFields2 = getEncryptField(tmpList.get(0));
                    if (CollectionUtils.isEmpty(encryptFields2)) {
                        continue;
                    }
                    for (Object o1 : tmpList) {
                        executeSingle(o1, encryptFields2, isEncrypt);
                    }
                } else if (o.getClass().getClassLoader() != null) {
                    executeSingle(o, getEncryptField(o), isEncrypt);
                } else {
                    String value = String.valueOf(o);
                    value = isEncrypt ? encrypt(value) : decrypt(value);
                    field.set(result, value);
                }
            }
        }
        return result;
    }

    public String encrypt(String value) {
        if(value==null||value.length()==0){
          return value;
        }
        return encryptionService.encrypt(value, this.getEncryptKey());
    }

    public String decrypt(String value) {
        if(value==null||value.length()==0){
            return value;
        }
        return encryptionService.decrypt(value, this.getEncryptKey());
    }
}
