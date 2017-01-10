package com.lianjia.sh.mybatis.reload.spring.autoconfigure;

import com.lianjia.sh.mybatis.reload.MybatisXmlMapperAutoReLoader;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Package: com.lianjia.sh.mybatis.reload.autoconfigure
 * Author: mac
 * Date: 2017/1/10
 */
@Configuration
@ConditionalOnClass({SqlSessionFactory.class, SqlSessionFactoryBean.class})
@ConditionalOnBean(DataSource.class)
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
public class MyBatisXmlMapperAutoReloaderAutoConfiger {

    @Bean
    @ConditionalOnMissingBean
    public MybatisXmlMapperAutoReLoader sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
        MybatisXmlMapperAutoReLoader reloader = new MybatisXmlMapperAutoReLoader();
        reloader.setEnableAutoReload(true);
        reloader.setSqlSessionFactory(sqlSessionFactory);
        return reloader;
    }

}
