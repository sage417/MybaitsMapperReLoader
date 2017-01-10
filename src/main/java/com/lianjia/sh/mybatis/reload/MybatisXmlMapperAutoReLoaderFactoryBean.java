package com.lianjia.sh.mybatis.reload;

import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.FactoryBean;
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
public class MybatisXmlMapperAutoReLoaderFactoryBean implements FactoryBean<MybatisXmlMapperAutoReLoader> {

    // 是否启用热加载.
    private boolean enableAutoReload = true;
    // 指定映射配置文件
    private String[] mapperLocations;
    // 多数据源的场景使用
    private SqlSessionFactory sqlSessionFactory;

    private ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();

    @Override
    public MybatisXmlMapperAutoReLoader getObject() throws Exception {
        MybatisXmlMapperAutoReLoader reLoader = new MybatisXmlMapperAutoReLoader();
        reLoader.setEnableAutoReload(this.enableAutoReload);
        reLoader.setSqlSessionFactory(this.sqlSessionFactory);
        reLoader.setMapperResources(this.resolveMapperLocations());
        return reLoader;
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
    public Class<?> getObjectType() {
        return MybatisXmlMapperAutoReLoader.class;
    }

    @Override
    public boolean isSingleton() {
        return false;
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

    public SqlSessionFactory getSqlSessionFactory() {
        return sqlSessionFactory;
    }

    public void setSqlSessionFactory(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    public void setResourcePatternResolver(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }
}
