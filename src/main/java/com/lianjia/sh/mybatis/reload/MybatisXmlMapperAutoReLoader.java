package com.lianjia.sh.mybatis.reload;

import com.lianjia.sh.mybatis.reload.filesystem.DirectoryWatchService;
import com.lianjia.sh.mybatis.reload.scanner.AutoReloadScanner;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class MybatisXmlMapperAutoReLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(MybatisXmlMapperAutoReLoader.class);

    private DirectoryWatchService watchService;

    // 是否启用热加载.
    private boolean enableAutoReload = true;
    // 指定映射配置文件
    private Resource[] mapperResources;
    // 多数据源的场景使用
    // private SqlSessionFactory sqlSessionFactory;

    private SqlSession sqlSession;

    public void init() throws Exception {

        // 检查设置
        if (!enableAutoReload) {
            LOGGER.info("禁用：mybatis自动热加载");
            return;
        } else {
            LOGGER.info("启用：mybatis自动热加载");
        }

        // 配置扫描器.
        final AutoReloadScanner scanner = new AutoReloadScanner(this.sqlSession, mapperResources);

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
    public void setEnableAutoReload(boolean enableAutoReload) {
        this.enableAutoReload = enableAutoReload;
    }

    /**
     * 指定映射配置文件.
     *
     * @param mapperResources
     */
    public void setMapperResources(Resource[] mapperResources) {
        this.mapperResources = mapperResources;
    }


    public void setSqlSession(SqlSession sqlSession) {
        this.sqlSession = sqlSession;
    }
}