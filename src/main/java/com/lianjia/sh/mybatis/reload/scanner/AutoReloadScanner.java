package com.lianjia.sh.mybatis.reload.scanner;

import com.google.common.base.Stopwatch;
import org.apache.ibatis.binding.MapperProxyFactory;
import org.apache.ibatis.binding.MapperRegistry;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 自动重载扫描器的具体实现
 *
 * @author thomas
 * @date Mar 31, 2016 6:59:34 PM
 */
public class AutoReloadScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoReloadScanner.class);
    private static final Pattern PATTERN = Pattern.compile("file \\[(.*?\\.xml)\\]");

    //
    private SqlSession sqlSession;

    private MapperRegistry mapperRegistry;

    // 需要扫描的包
    private List<String> mapperLocations;

    public AutoReloadScanner(SqlSession sqlSession) throws Exception {
        this.sqlSession = sqlSession;
        this.mapperLocations = this.getLoadResources();
        this.mapperRegistry = this.getMapperRegistry();
    }

    private List<String> getLoadResources() throws Exception {
        final Set<String> loadedResources = this.reflectionReadConfiguration("loadedResources");

        return this.mapperLocations = loadedResources
                .stream()
                .map(source -> {
                    Matcher matcher
                            = PATTERN.matcher(source);
                    if (matcher.matches()) {
                        return matcher.group(1);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private void reload(Configuration configuration) throws Exception {
        for (String source : mapperLocations) {
            try {
                XMLMapperBuilder xmlMapperBuilder = new XMLMapperBuilder(Files.newInputStream(Paths.get(source)), configuration, source, configuration.getSqlFragments());
                xmlMapperBuilder.parse();
                LOGGER.info("reloaded : {}", source);
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse mapping resource: '" + source + "'", e);
            } finally {
                ErrorContext.instance().reset();
            }
        }
    }

    /**
     * 重新加载所有文件.
     */
    public void reloadAll() {
        Stopwatch sw = Stopwatch.createStarted();

        Configuration configuration = this.sqlSession.getConfiguration();

        this.removeConfig(configuration);

        try {
            this.reload(configuration);
        } catch (Exception e) {
            LOGGER.error("加载mybaits xml失败", e);
        }

        sw.stop();
        LOGGER.info("重新加载mybatis映射文件完成. 耗时%sms", sw.elapsed(TimeUnit.MILLISECONDS));
    }


    private <T> T reflectionReadConfiguration(String fieldName) throws Exception {
        return this.reflectionReadField(Configuration.class, this.sqlSession.getConfiguration(), fieldName);
    }

    private <T> T reflectionReadField(Class<?> clazz, Object object, String fieldName) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (T) field.get(object);
    }

    /**
     * 删除不必要的配置项.
     *
     * @param configuration
     * @throws Exception
     */
    private void removeConfig(Configuration configuration) {
        try {
            Class<?> classConfig = configuration.getClass();
            clearMap(classConfig, configuration, "mappedStatements");
            clearMap(classConfig, configuration, "caches");
            clearMap(classConfig, configuration, "resultMaps");
            clearMap(classConfig, configuration, "parameterMaps");
            clearMap(classConfig, configuration, "keyGenerators");
            clearMap(classConfig, configuration, "sqlFragments");
            clearSet(classConfig, configuration, "loadedResources");
            ((Map<Class<?>, MapperProxyFactory<?>>) this.reflectionReadField(MapperRegistry.class, this.mapperRegistry, "knownMappers")).clear();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("rawtypes")
    private void clearMap(Class<?> classConfig, Configuration configuration, String fieldName) throws Exception {
        Field field = classConfig.getDeclaredField(fieldName);
        field.setAccessible(true);
        Map mapConfig = (Map) field.get(configuration);
        mapConfig.clear();
    }

    @SuppressWarnings("rawtypes")
    private void clearSet(Class<?> classConfig, Configuration configuration, String fieldName) throws Exception {
        Field field = classConfig.getDeclaredField(fieldName);
        field.setAccessible(true);
        Set setConfig = (Set) field.get(configuration);
        setConfig.clear();
    }

    private MapperRegistry getMapperRegistry() throws Exception {
        return this.reflectionReadConfiguration("mapperRegistry");
    }

    public List<String> getMapperLocations() {
        return mapperLocations;
    }
}
