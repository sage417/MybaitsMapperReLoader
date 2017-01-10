package com.lianjia.sh.mybatis.reload;

import com.lianjia.sh.mybatis.reload.filesystem.DirectoryWatchService;
import com.lianjia.sh.mybatis.reload.scanner.AutoReloadScanner;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 切莫用于生产环境（后果自负）
 * <p>
 * mybatis映射文件热加载（发生变动后自动重新加载）.
 * <p>
 * 方便开发时使用，不用每次修改xml文件后都要去重启应用.
 * <p>
 * 特性：
 * 1.支持不同的数据源。
 * 2.双线程实时监控，一个用来监控全局，一个用来实时监控热点文件。（100ms）（热点文件2分钟内没续修改自动过期）
 * 3.对于CPU不给力和映射文件庞大的应用，有一定程度的性能问题。
 *
 * @author thomas
 */
public class MybatisXmlMapperAutoReloader implements DisposableBean, InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(MybatisXmlMapperAutoReloader.class);

    private DirectoryWatchService watchService;

    // 是否启用热加载.
    private Boolean enableAutoReload = true;
    // 指定映射配置文件
    private String[] mapperLocations;
    // 多数据源的场景使用
    private SqlSessionFactory sqlSessionFactory;

    @Override
    public void afterPropertiesSet() throws Exception {

        // 检查设置
        if (!enableAutoReload) {
            LOGGER.info("禁用：mybatis自动热加载");
            return;
        } else {
            LOGGER.info("启用：mybatis自动热加载");
        }

        Resource[] mapperResources = resolveMapperLocations();

        // 配置扫描器.
        final AutoReloadScanner scanner = new AutoReloadScanner(this.sqlSessionFactory, mapperResources);
        scanner.start();

        Path[] paths = Arrays.stream(mapperResources).map(mapperLocation -> {
            try {
                return Paths.get(mapperLocation.getURI()).getParent();
            } catch (IOException e) {
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toSet()).toArray(new Path[]{});

        try {
            watchService = new DirectoryWatchService(true, paths);
        } catch (IOException e) {
            LOGGER.error("fail to start mybatis mapper reload");
            throw e;
        }
        Executors.newSingleThreadExecutor().submit(() -> watchService.processEvents(scanner));
        LOGGER.info("启动mybatis自动热加载");
    }

    private Resource[] resolveMapperLocations() {
        List<Resource> resources = new ArrayList<>();
        if (this.mapperLocations != null) {
            for (String mapperLocation : this.mapperLocations) {
                Resource[] mappers;
                try {
                    mappers = new PathMatchingResourcePatternResolver().getResources(mapperLocation);
                    resources.addAll(Arrays.asList(mappers));
                } catch (IOException ignored) {

                }
            }
        }

        Resource[] mapperLocations = new Resource[resources.size()];
        mapperLocations = resources.toArray(mapperLocations);
        return mapperLocations;
    }

    @Override
    public void destroy() throws Exception {
        if (this.watchService != null) {
            this.watchService.close();
        }
    }

    /**
     * 是否启用热加载.
     *
     * @param enableAutoReload
     */
    public void setEnableAutoReload(Boolean enableAutoReload) {
        this.enableAutoReload = enableAutoReload;
    }

    /**
     * 指定映射配置文件.
     *
     * @param mapperLocations
     */
    public void setMapperLocations(String[] mapperLocations) {
        this.mapperLocations = mapperLocations;
    }


    /**
     * 设置数据源.
     *
     * @param sqlSessionFactory
     */
    public void setSqlSessionFactory(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }
}