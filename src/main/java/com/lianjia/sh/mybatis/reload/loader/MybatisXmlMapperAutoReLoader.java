package com.lianjia.sh.mybatis.reload.loader;

import com.lianjia.sh.mybatis.reload.filesystem.DirectoryWatchService;
import com.lianjia.sh.mybatis.reload.scanner.AutoReloadScanner;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

public class MybatisXmlMapperAutoReLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(MybatisXmlMapperAutoReLoader.class);

    private DirectoryWatchService watchService;

    // 是否启用热加载.
    private boolean enableAutoReload = true;

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
        final AutoReloadScanner scanner = new AutoReloadScanner(this.sqlSession);

        String root = StringUtils.getCommonPrefix(scanner.getMapperLocations().toArray(new String[]{}));

        try {
            watchService = new DirectoryWatchService(true, scanner, Paths.get(root));
        } catch (IOException e) {
            LOGGER.error("fail to start mybatis mapper reload");
            throw e;
        }
        Executors.newSingleThreadExecutor().submit(() -> watchService.processEvents());
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


    public void setSqlSession(SqlSession sqlSession) {
        this.sqlSession = sqlSession;
    }
}