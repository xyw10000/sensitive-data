package io.github.xyw10000.mybatis.encrypte.interceptor;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.one.mybatis.encrypte.annotation.EnableEncrypt;
import com.github.one.mybatis.encrypte.annotation.EncryptField;
import com.github.one.mybatis.encrypte.spi.DefaultEncryptionService;
import com.github.one.mybatis.encrypte.spi.IEncryptionService;
import com.github.one.mybatis.encrypte.util.EncryptHelper;
import com.github.one.mybatis.encrypte.util.SpringContextUtil;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.util.CollectionUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author one.xu
 */
@Slf4j
@Intercepts({
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class}),
        @Signature(type = Executor.class, method = "queryCursor", args = {MappedStatement.class, Object.class, RowBounds.class}),
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})})
public class EncryptionInterceptor implements Interceptor {
    private EncryptHelper encryptHelper;
    /**
     * {"examples":{"com.xx.model.BusinessInfoExample":["app_id"]},
     * "resluts":{"com.xx.model.BusinessInfo":["appId"]}}
     */
    private Map<String, Set<String>> exampleClassConfigMap;
    private ConcurrentHashMap<String, Class> mapperClassMap = new ConcurrentHashMap<>();

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        invocation.getArgs()[1] = parameterHand(invocation, true);
        Boolean hasCache = invocation.getArgs().length > 2 ? hasCache(invocation) : false;

        Object result = invocation.proceed();
        invocation.getArgs()[1] = parameterHand(invocation, false);
        if (hasCache) {
            return result;
        }
        return resultDecrypt(result, invocation);
    }

    /**
     * 只有 query,queryCursor 进入
     *
     * @param invocation
     * @return
     */
    boolean hasCache(Invocation invocation) {
        Object[] args = invocation.getArgs();
        MappedStatement ms = (MappedStatement) args[0];
        Object parameter = args[1];
        RowBounds rowBounds = (RowBounds) args[2];
//        ResultHandler resultHandler = (ResultHandler) args[3];
        Executor executor = (Executor) invocation.getTarget();
        CacheKey cacheKey;
        BoundSql boundSql;
        //由于逻辑关系，只会进入一次
        if (args.length <= 4) {
            //4 个参数时
            boundSql = ms.getBoundSql(parameter);
            cacheKey = executor.createCacheKey(ms, parameter, rowBounds, boundSql);
        } else {
            //6 个参数时
            cacheKey = (CacheKey) args[4];
            boundSql = (BoundSql) args[5];
        }
        return executor.isCached(ms, cacheKey);
    }


    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }


    @SneakyThrows
    @Override
    public void setProperties(Properties properties) {
        String encryptKey = properties.getProperty("encryptKey", "N2Y1ZjA0MjhhNjI2ZGNhOQ==");
        if (encryptKey.startsWith("${") && encryptKey.endsWith("}")) {
            encryptKey = SpringContextUtil.getApplicationContext().getEnvironment().getProperty(encryptKey.replace("${", "").replace("}", ""));
        }
        String encryptionService = properties.getProperty("encryptionService", DefaultEncryptionService.class.getName());
        if (encryptionService.startsWith("${") && encryptionService.endsWith("}")) {
            encryptionService = SpringContextUtil.getApplicationContext().getEnvironment().getProperty(encryptionService.replace("${", "").replace("}", ""));
        }
        Class<?> cls = Class.forName(encryptionService);
        if (!IEncryptionService.class.isAssignableFrom(cls)) {
            throw new RuntimeException("encryptionService 没有实现IEncryptionService接口");
        }
        this.encryptHelper = new EncryptHelper((IEncryptionService) cls.newInstance(), encryptKey);


        //解析example配置
        this.exampleClassConfigMap = new HashMap<>(16);
        String exampleClass = properties.getProperty("exampleClassConfig");
        if (StringUtils.isBlank(exampleClass)) {
            return;
        }
        JSONObject exampleClassConfigObject = JSON.parseObject(exampleClass);
        for (Map.Entry<String, Object> m1 : exampleClassConfigObject.entrySet()) {
            JSONObject tmp = exampleClassConfigObject.getJSONObject(m1.getKey());
            for (Map.Entry<String, Object> m2 : tmp.entrySet()) {

                String newKey = buildConfigKey(m2.getKey(), "examples".equals(m1.getKey()));
                Set<String> fields = exampleClassConfigMap.get(newKey);
                if (fields == null) {
                    fields = new HashSet<>();
                }
                fields.addAll(tmp.getJSONArray(m2.getKey()).toJavaList(String.class));
                this.exampleClassConfigMap.put(newKey, fields);
            }
        }

    }

    private String buildConfigKey(String key, boolean isExamples) {
        String newKey = isExamples ? "examples" : "resluts";
        return newKey + "#" + key;
    }

    private Object resultDecrypt(Object resultObject, Invocation invocation) throws Throwable {
        if (Objects.isNull(resultObject)) {
            return null;
        }
//        log.info("resultObject:{}",JSONObject.toJSONString(resultObject));
        if (resultObject instanceof List) {
            List<Object> resultList = (List<Object>) resultObject;
            if (CollectionUtils.isEmpty(resultList)) {
                return resultList;
            }
        }
        Set<String> decryptFields = getDecryptFields(resultObject);
        MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
        Method method = getMapperMethod(mappedStatement);
        EnableEncrypt enableEncrypt = null;
        if (method != null && method.getAnnotation(EnableEncrypt.class) != null) {
            enableEncrypt = method.getAnnotation(EnableEncrypt.class);
        }
        if (enableEncrypt != null && StringUtils.isNotBlank(enableEncrypt.keys())) {
            decryptFields.addAll(Arrays.asList(enableEncrypt.keys().split(",")));
        }

        this.encryptHelper.execute(resultObject, decryptFields, false);
        return resultObject;
    }

    private Set<String> getDecryptFields(Object resultObject) {
        if (resultObject == null) {
            return new HashSet<>();
        }
        boolean isList = resultObject instanceof List;
        Object reallySingleObj = resultObject;
        if (isList) {
            List<Object> resultList = (List<Object>) resultObject;
            if (CollectionUtils.isEmpty(resultList)) {
                return new HashSet<>();
            }
            reallySingleObj = resultList.get(0);
        }
        //提取注解字段
        Set<String> decryptFields = this.encryptHelper.getEncryptField(reallySingleObj);
        //针对继承类处理
        Class tmpClass = reallySingleObj.getClass();
        while (!tmpClass.getName().equals(Object.class.getName())) {
            Set<String> fields = this.exampleClassConfigMap.get(buildConfigKey(tmpClass.getName(), false));
            if (fields != null) {
                decryptFields.addAll(fields);
            }
            tmpClass = tmpClass.getSuperclass();
        }
        return decryptFields;
    }

    private Object parameterHand(Invocation invocation, boolean isEncrypt) throws Throwable {
        Object parameter = invocation.getArgs()[1];
        MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
        Method method = getMapperMethod(mappedStatement);
        if (parameter == null || method == null) {
            return parameter;
        }
        Object newParameter = parameter;
        //没有EnableEncrypt注解 且 Example,Result没有加密字段
        if (method.getAnnotation(EnableEncrypt.class) == null && !hasExampleOrResult(newParameter)) {
            return parameter;
        }
        log.debug("开始修改方法:{}入参", mappedStatement.getId());
        //扫描方法入参注解
        Map<String, Set<String>> paramAnnotation = searchParamAnnotation(method);
        //多个参数场景
        if (newParameter instanceof MapperMethod.ParamMap) {
            Map<String, Object> map = ((Map<String, Object>) newParameter);
            for (Map.Entry<String, Object> e : map.entrySet()) {
                Object value = e.getValue();
                if (value == null) {
                    continue;
                }
                //没有注解，且无Example Result
                if (!paramAnnotation.containsKey(e.getKey()) && !hasExampleOrResult(value)) {
                    continue;
                }
                if (value instanceof Map) {
                    encryptHelper.execute(value, paramAnnotation.get(e.getKey()), isEncrypt);
                } else if (value instanceof List) {
                    List list = (List) value;
                    if (CollectionUtils.isEmpty(list)) {
                        continue;
                    }
                    boolean isMap = list.get(0) instanceof Map;
                    if (isMap && !CollectionUtils.isEmpty(list)) {
                        encryptHelper.execute(value, paramAnnotation.get(e.getKey()), isEncrypt);
                    } else if (list.get(0).getClass().getClassLoader() != null) {
                        Set<String> encryptFields = getDecryptFields(list.get(0));
                        encryptHelper.execute(list, encryptFields, isEncrypt);
                    } else {
                        int i = 0;
                        for (Object o : list) {
                            list.set(i, encryptHelper.encrypt(String.valueOf(o)));
                            i = i + 1;
                        }
                    }
                } else if (hasExample(value)) {
                    handExample(value, isEncrypt);
                } else if (value.getClass().getClassLoader() != null) {
                    this.encryptHelper.execute(value, getDecryptFields(value), isEncrypt);
                } else {
                    map.put(e.getKey(), isEncrypt ? encryptHelper.encrypt(String.valueOf(value)) : encryptHelper.decrypt(String.valueOf(value)));
                }
            }
        } else if (newParameter instanceof Map) {
            Set<String> encryptFields = Sets.newHashSet();
            for (Set<String> s : paramAnnotation.values()) {
                encryptFields.addAll(s);
            }
            encryptHelper.execute(newParameter, encryptFields, isEncrypt);
        } else if (hasExample(newParameter)) {
            handExample(newParameter, isEncrypt);
        } else if (newParameter.getClass().getClassLoader() == null && paramAnnotation.size() == 1) {
            newParameter = isEncrypt ? encryptHelper.encrypt(String.valueOf(newParameter)) : encryptHelper.decrypt(String.valueOf(newParameter));
        } else {
            this.encryptHelper.execute(newParameter, getDecryptFields(newParameter), isEncrypt);
        }

        return newParameter;
    }

    private boolean hasExampleOrResult(Object parameterObject) {
        boolean isParamMap = parameterObject instanceof MapperMethod.ParamMap;
        if (!isParamMap) {
            return hasExample(parameterObject) || hasResult(parameterObject);
        }
        Map<String, Object> map = ((Map<String, Object>) parameterObject);
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (hasExample(e.getValue()) || hasResult(e.getValue())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasExample(Object parameterObject) {
        if(parameterObject==null){
            return false;
        }
        return !CollectionUtils.isEmpty(exampleClassConfigMap.get(buildConfigKey(parameterObject.getClass().getName(), true)));
    }

    private boolean hasResult(Object parameterObject) {
        if (parameterObject == null) {
            return false;
        }
        return !CollectionUtils.isEmpty(getDecryptFields(parameterObject));
    }

    private boolean handExample(Object parameterObject, boolean isEncrypt) throws Exception {
        Set<String> fields = null;
        if (!(parameterObject instanceof MapperMethod.ParamMap)) {
            fields = exampleClassConfigMap.get(buildConfigKey(parameterObject.getClass().getName(), true));
            return handExampleParam(parameterObject, isEncrypt, fields);
        }
        Map<String, Object> map = ((Map<String, Object>) parameterObject);
        for (Map.Entry<String, Object> e : map.entrySet()) {
            fields = exampleClassConfigMap.get(buildConfigKey(e.getValue().getClass().getName(), true));
            if (fields != null) {
                return handExampleParam(e.getValue(), isEncrypt, fields);
            }
        }
        return false;
    }

    private boolean handExampleParam(Object parameterObject, boolean isEncrypt, Set<String> fields) throws Exception {

        if (fields == null) {
            return false;
        }
        Method method = parameterObject.getClass().getMethod("getOredCriteria");
        List oredCriterias = (List) method.invoke(parameterObject);
        if (CollectionUtils.isEmpty(oredCriterias)) {
            return false;
        }
        boolean result = false;
        for (Object oj : oredCriterias) {
            method = oj.getClass().getMethod("getCriteria");
            for (Object o : (List) method.invoke(oj)) {
                Field f = o.getClass().getDeclaredField("condition");
                f.setAccessible(true);
                Object v = f.get(o);
                aa:
                for (String f1 : fields) {
                    if (!v.toString().toUpperCase().replace("`", "").startsWith((f1 + " ").toUpperCase())) {
                        continue;
                    }
                    for (String dd : "value,secondValue".split(",")) {
                        f = o.getClass().getDeclaredField(dd);
                        f.setAccessible(true);
                        v = f.get(o);
                        if (v == null) {
                            break aa;
                        }
                        if (v instanceof List) {
                            List tList = (List) v;
                            for (int i = 0; i < tList.size(); i++) {
                                tList.set(i, isEncrypt ? encryptHelper.encrypt(tList.get(i).toString()) : encryptHelper.decrypt(tList.get(i).toString()));
                            }
                            v = tList;
                        } else {
                            v = isEncrypt ? encryptHelper.encrypt(v.toString()) : encryptHelper.decrypt(v.toString());
                        }
                        f.set(o, v);
                        if (!result) {
                            result = true;
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * key-属性名
     * value-map类型时加密字段key
     *
     * @param method
     * @return
     */
    private Map<String, Set<String>> searchParamAnnotation(Method method) {

        Map<String, Set<String>> paramNames = Maps.newHashMap();
        if (method == null) {
            return paramNames;
        }
        Annotation[][] pa = method.getParameterAnnotations();
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < pa.length; i++) {
            for (Annotation annotation : pa[i]) {
                Parameter p = parameters[i];
                if (annotation instanceof EncryptField) {
                    EncryptField encryptField = (EncryptField) annotation;
                    Set<String> ff = null;
                    if (Map.class.isAssignableFrom(p.getType()) && StringUtils.isNotBlank(encryptField.keys())) {
                        ff = new HashSet<>(Arrays.asList(encryptField.keys().split(",")));
                    }
                    Param param = p.getAnnotation(Param.class);
                    String paramName = param == null ? p.getName() : param.value();
                    paramNames.put(paramName, ff);
                }
            }
        }
        return paramNames;
    }

    private Method getMapperMethod(MappedStatement mappedStatement) {
        Class<?> mapperClass = getMapperClass(mappedStatement);
        String methodName = mappedStatement.getId();
        methodName = methodName.substring(methodName.lastIndexOf('.') + 1);
        Method[] methods = mapperClass.getDeclaredMethods();
        Method method = null;
        for (Method m : methods) {
            if (m.getName().equals(methodName)) {
                method = m;
                break;
            }
        }
        return method;
    }

    @SneakyThrows
    private Class<?> getMapperClass(MappedStatement mappedStatement) {
        String mapperClassKey = mappedStatement.getId().substring(0, mappedStatement.getId().lastIndexOf('.'));
        Class<?> mapperClass = mapperClassMap.get(mapperClassKey);
        if (mapperClass == null) {
            mapperClassMap.putIfAbsent(mapperClassKey, Class.forName(mapperClassKey));
            mapperClass = mapperClassMap.get(mapperClassKey);
        }
        return mapperClass;
    }
}