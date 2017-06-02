package com.lianjia.sh.mybatis.reload.spring;

import com.lianjia.sh.mybatis.reload.MybatisXmlMapperAutoReLoader;
import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Package: com.lianjia.sh.mybatis.reload
 * Author: mac
 * Date: 2017/1/10
 */
public class MybatisXmlMapperAutoReLoaderFactoryBean implements FactoryBean<MybatisXmlMapperAutoReLoader>, InitializingBean, DisposableBean {

    // 是否启用热加载.
    private boolean enableAutoReload = true;
    // 指定映射配置文件
    private String[] mapperLocations;
    // 多数据源的场景使用
    // private SqlSessionFactory sqlSessionFactory;
    private SqlSession sqlSession;

    private MybatisXmlMapperAutoReLoader mybatisXmlMapperAutoReLoader;

    private ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();

    @Override
    public MybatisXmlMapperAutoReLoader getObject() throws Exception {
        if (this.mybatisXmlMapperAutoReLoader == null) {
            afterPropertiesSet();
        }
        return this.mybatisXmlMapperAutoReLoader;
    }

    private Resource[] resolveMapperLocations() {
        List<Resource> resources = new ArrayList<>();
        if (this.mapperLocations != null) {
            for (String mapperLocation : this.mapperLocations) {
                Resource[] mappers;
                try {
                    mappers = resourcePatternResolver.getResources(mapperLocation);
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
        if (this.mybatisXmlMapperAutoReLoader != null) {
            this.mybatisXmlMapperAutoReLoader.destroy();
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.mybatisXmlMapperAutoReLoader = new MybatisXmlMapperAutoReLoader();
        this.mybatisXmlMapperAutoReLoader.setEnableAutoReload(this.enableAutoReload);
        this.mybatisXmlMapperAutoReLoader.setSqlSession(this.sqlSession);
        this.mybatisXmlMapperAutoReLoader.setMapperResources(this.resolveMapperLocations());
        this.mybatisXmlMapperAutoReLoader.init();
    }

    @Override
    public Class<?> getObjectType() {
        return MybatisXmlMapperAutoReLoader.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public void setEnableAutoReload(boolean enableAutoReload) {
        this.enableAutoReload = enableAutoReload;
    }

    public String[] getMapperLocations() {
        return mapperLocations;
    }

    public void setMapperLocations(String[] mapperLocations) {
        this.mapperLocations = mapperLocations;
    }

    public SqlSession getSqlSession() {
        return sqlSession;
    }

    public void setSqlSession(SqlSession sqlSession) {
        this.sqlSession = sqlSession;
    }

    public void setResourcePatternResolver(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }
}
