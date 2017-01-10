package com.lianjia.sh.mybatis.reload.scanner;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.util.StopWatch;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自动重载扫描器的具体实现
 *
 * @author thomas
 * @date Mar 31, 2016 6:59:34 PM
 */
public class AutoReloadScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoReloadScanner.class);

    //
    private SqlSessionFactory sqlSessionFactory;

    // 需要扫描的包
    private Resource[] mapperLocations;

    // 所有文件
    private Map<Resource, String> files = new ConcurrentHashMap<>();

    public AutoReloadScanner(SqlSessionFactory sqlSessionFactory, Resource[] mapperLocations) {
        this.sqlSessionFactory = sqlSessionFactory;
        this.mapperLocations = mapperLocations;
    }

    /**
     * 重新加载所有文件.
     */
    public void reloadAll() {
        StopWatch sw = new StopWatch("mybatis mapper auto reload");
        sw.start();
        Configuration configuration = getConfiguration();
        for (Map.Entry<Resource, String> entry : files.entrySet()) {
            Resource r = entry.getKey();
            try {
                XMLMapperBuilder xmlMapperBuilder = new XMLMapperBuilder(r.getInputStream(), configuration, r.toString(), configuration.getSqlFragments());
                xmlMapperBuilder.parse();
                LOGGER.info("reloaded : {}", r.toString());
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse mapping resource: '" + r + "'", e);
            } finally {
                ErrorContext.instance().reset();
            }
        }
        sw.stop();
//			LOGGER.info("重新加载mybatis映射文件完成.");
        LOGGER.info(sw.shortSummary());
    }

    /**
     * 获得文件的标记.
     *
     * @param r
     * @return
     */
    private String getTag(Resource r) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(r.contentLength());
            sb.append(r.lastModified());
            return sb.toString();
        } catch (IOException e) {
            throw new RuntimeException("获取文件标记信息失败！r=" + r, e);
        }
    }

    /**
     * 开启扫描服务
     */
    public void scan() {
        this.files.clear();
        try {
            if (this.mapperLocations != null) {
                for (Resource r : mapperLocations) {
                    String tag = getTag(r);
                    files.put(r, tag);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("初始化扫描服务失败！", e);
        }
    }

    /**
     * 获取配置信息，必须每次都重新获取，否则重新加载xml不起作用.
     *
     * @return
     */
    private Configuration getConfiguration() {
        Configuration configuration = this.sqlSessionFactory.getConfiguration();
        removeConfig(configuration);
        return configuration;
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
}
