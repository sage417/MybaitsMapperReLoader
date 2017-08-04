package com.lianjia.sh.mybatis.reload.spring.autoconfigure;

import com.lianjia.sh.mybatis.reload.spring.factory.MybatisXmlMapperAutoReLoaderFactoryBean;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Package: com.lianjia.sh.mybatis.reload.autoconfigure
 * Author: mac
 * Date: 2017/1/10
 */
@Configuration
@EnableConfigurationProperties(MybatisXmlMapperAutoReLoaderProperties.class)
@ConditionalOnClass({SqlSessionFactory.class})
@ConditionalOnBean({DataSource.class})
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
public class MyBatisXmlMapperAutoReLoaderAutoConfiguration {

    private MybatisXmlMapperAutoReLoaderProperties mybatisXmlMapperAutoReloaderProperties;

    @Autowired
    public void setMybatisXmlMapperAutoReLoaderProperties(MybatisXmlMapperAutoReLoaderProperties mybatisXmlMapperAutoReloaderProperties) {
        this.mybatisXmlMapperAutoReloaderProperties = mybatisXmlMapperAutoReloaderProperties;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({SqlSession.class})
    public MybatisXmlMapperAutoReLoaderFactoryBean mybatisXmlMapperAutoReLoaderFactoryBean(SqlSession sqlSession) {
        MybatisXmlMapperAutoReLoaderFactoryBean reLoader = new MybatisXmlMapperAutoReLoaderFactoryBean();
        reLoader.setEnableAutoReload(this.mybatisXmlMapperAutoReloaderProperties.isEnabled());
        reLoader.setSqlSession(sqlSession);
        return reLoader;
    }

}
