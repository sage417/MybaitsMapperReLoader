package com.lianjia.sh.mybatis.reload.spring.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Package: com.lianjia.sh.mybatis.reload.spring.autoconfigure
 * Author: mac
 * Date: 2017/8/4
 */
@ConfigurationProperties("mybatis.reload")
public class MybatisXmlMapperAutoReloaderProperties {

    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
