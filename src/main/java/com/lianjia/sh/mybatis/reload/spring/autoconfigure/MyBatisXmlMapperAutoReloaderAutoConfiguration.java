package com.lianjia.sh.mybatis.reload.spring.autoconfigure;

import com.lianjia.sh.mybatis.reload.spring.factory.MybatisXmlMapperAutoReLoaderFactoryBean;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
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
@EnableConfigurationProperties(MybatisXmlMapperAutoReloaderProperties.class)
@ConditionalOnClass({SqlSessionFactory.class, SqlSessionFactoryBean.class})
@ConditionalOnBean({DataSource.class})
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
public class MyBatisXmlMapperAutoReloaderAutoConfiguration {

    @Autowired
    private MybatisXmlMapperAutoReloaderProperties mybatisXmlMapperAutoReloaderProperties;

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({SqlSession.class})
    public MybatisXmlMapperAutoReLoaderFactoryBean mybatisXmlMapperAutoReLoaderFactoryBean(SqlSession sqlSession) {
        MybatisXmlMapperAutoReLoaderFactoryBean reloader = new MybatisXmlMapperAutoReLoaderFactoryBean();
        reloader.setEnableAutoReload(this.mybatisXmlMapperAutoReloaderProperties.isEnabled());
        reloader.setSqlSession(sqlSession);
        return reloader;
    }

}
