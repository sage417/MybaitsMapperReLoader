# mybatis xml文件自动加载

### 切莫用于生产环境（后果自负）！

### 使用方式

    // Java Config 方式
    @Bean
    @Profile("development")
    public MybatisXmlMapperAutoReloader mybatisXmlMapperAutoReloader(){
        MybatisXmlMapperAutoReloader reloader = new MybatisXmlMapperAutoReloader();
        return reloader;
    }
    
    // xml config 方式
	<!-- mybatis自动热加载 -->
	<bean class="com.thomas.mybatis.MybatisXmlMapperAutoReloader" >
		<!-- 设置是否启用: 默认启用 -->
		<property name="enableAutoReload" value="${mybatis.enableAutoReload}" />
		<!-- 设置sqlSessionFactory -->
		<property name="sqlSessionFactory" ref="sqlSessionFactory" />
		<!-- 设置映射文件地址 -->
		<property name="mapperLocations" value="classpath*:mybatis/bean/mapper/*.xml" />
	</bean>
