package com.lianjia.sh.mybatis.reload;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.boot.autoconfigure.MybatisProperties;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.ClassUtils;
import org.springframework.util.StopWatch;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.*;

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
public class MybatisXmlMapperAutoReloader implements DisposableBean, InitializingBean, ApplicationContextAware {

    private static final Logger LOGGER = LoggerFactory.getLogger(MybatisXmlMapperAutoReloader.class);

    private ApplicationContext applicationContext;
    private ScheduledExecutorService pool;
    private DirectoryWatchService watchService;

    // 多数据源的场景使用
    private Boolean enableAutoReload = true;            // 是否启用热加载.
    private String[] mapperLocations;                        // 指定映射配置文件
    private MapperScannerConfigurer config;
    private SqlSessionFactory sqlSessionFactory;
    private MybatisProperties mybatisProperties;

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
     * 设置配置对象.
     *
     * @param config
     */
    public void setConfig(MapperScannerConfigurer config) {
        this.config = config;
    }

    /**
     * 设置数据源.
     *
     * @param sqlSessionFactory
     */
    public void setSqlSessionFactory(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterPropertiesSet() throws Exception {

        // 检查设置
        if (!enableAutoReload) {
            LOGGER.info("禁用：mybatis自动热加载");
            return;
        } else {
            LOGGER.info("启用：mybatis自动热加载");
        }

        checkProperties();        // 检查属性

        // 获取mapperLocations
        String[] mapperLocations = getMapperLocations();

        // 初始化线程池2个（避免线程来回切换）（一个用来监控全局，一个用来实时监控热点文件.）
        //pool = Executors.newScheduledThreadPool(2);

        // 配置扫描器.
        final AutoReloadScanner scanner = new AutoReloadScanner(mapperLocations);
        scanner.start();

        Resource[] mapperResources = resolveMapperLocations(this.mapperLocations);

        Path[] paths = Arrays.stream(mapperResources).map(mapperLocation -> {
            try {
                return Paths.get(mapperLocation.getURI()).getParent();
            } catch (IOException e) {
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toSet()).toArray(new Path[]{});

        // 扫描全部（2s一次）
//        pool.scheduleAtFixedRate(new Runnable() {
//            @Override
//            public void run() {
//                scanner.scanAllFileChange();
//            }
//        }, 2, 2, TimeUnit.SECONDS);

        // 扫描热点文件（100ms一次，监控更为频繁）
//        pool.scheduleAtFixedRate(new Runnable() {
//            @Override
//            public void run() {
//                scanner.scanHotspotFileChange();
//            }
//        }, 2, 100, TimeUnit.MILLISECONDS);
        try {
            watchService = new DirectoryWatchService(true, paths);
        } catch (IOException e) {
            LOGGER.error("fail to start mybatis mapper reload");
            throw e;
        }
        Executors.newSingleThreadExecutor().submit(() -> {
            watchService.processEvents(scanner);
        });
        LOGGER.info("启动mybatis自动热加载");
    }

    /**
     * 获取配置文件路径（mapperLocations）.
     *
     * @return
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     * @throws Exception
     */
    private String[] getMapperLocations() throws NoSuchFieldException, IllegalAccessException, Exception {

        // 优先使用mapperLocations
        if (mapperLocations != null) {
            return mapperLocations;
        }

        // 从MapperScannerConfigurer中获取.
        if (config != null) {
            Field field = config.getClass().getDeclaredField("basePackage");
            field.setAccessible(true);
            return StringUtils.split((String) field.get(config), ConfigurableApplicationContext.CONFIG_LOCATION_DELIMITERS);
        }

        // 根本就获取不到 org.mybatis.spring.SqlSessionFactoryBean
        // spring的org.springframework.beans.factory.FactoryBean 将其封装成为了一个 org.apache.ibatis.session.defaults.DefaultSqlSessionFactory
        // 牛逼的spring
//		if(sqlSessionFactory != null){
//			Field field = sqlSessionFactory.getClass().getDeclaredField("mapperLocations");
//			field.setAccessible(true);
//			Resource[] mapperLocations = (Resource[]) field.get(sqlSessionFactory);
//			StringBuilder sb = new StringBuilder();
//			for(Resource r : mapperLocations){
//				String n = r.getURL().toString();
//				sb.append(n).append("\n");
//			}
//			return sb.toString();
//		}
        throw new RuntimeException("获取mapperLocations失败！");
    }

    private Resource[] resolveMapperLocations(String[] mapperLocationArr) {
        List<Resource> resources = new ArrayList<Resource>();
        if (this.mapperLocations != null) {
            for (String ml : mapperLocationArr) {
                Resource[] mappers;
                try {
                    mappers = new PathMatchingResourcePatternResolver().getResources(ml);
                    resources.addAll(Arrays.asList(mappers));
                } catch (IOException e) {

                }
            }
        }

        Resource[] mapperLocations = new Resource[resources.size()];
        mapperLocations = resources.toArray(mapperLocations);
        return mapperLocations;
    }

    /**
     * 检查属性，如果没有设置，直接初始化成默认的方式.
     */
    private void checkProperties() {
        // 如果没有指定数据源，直接使用默认的方式获取数据源
        if (sqlSessionFactory == null) {
            try {
                sqlSessionFactory = applicationContext.getBean(SqlSessionFactory.class);
            } catch (BeansException e) {
                throw new RuntimeException("获取数据源失败！", e);
            }
        }

        // 如果没有指定配置文件，使用默认的方式获取配置文件
        if (config == null && mapperLocations == null) {
            try {
                config = applicationContext.getBean(MapperScannerConfigurer.class);
            } catch (BeansException e) {
                LOGGER.info("fail to load config from MapperScannerConfigurer, try to load config from mybatis-spring-boot-autoconfig");
            }
        }

        // mybatis spring boot auto config
        if (config == null && mapperLocations == null) {
            mybatisProperties = applicationContext.getBean(MybatisProperties.class);
            mapperLocations = mybatisProperties.getMapperLocations();
        }

        if (config == null && mapperLocations == null) {
            throw new RuntimeException("设置配置mapperLocations失败！，请设置好配置属性，否则自动热加载无法起作用!");
        }
    }

    @Override
    public void destroy() throws Exception {
        if (pool != null) {
            pool.shutdown(); // 是否线程池资源
        }

        if (this.watchService != null && this.watchService.watcher != null) {
            this.watchService.watcher.close();
        }
    }

    /**
     * 自动重载扫描器的具体实现
     *
     * @author thomas
     * @date Mar 31, 2016 6:59:34 PM
     */
    class AutoReloadScanner {

        static final String XML_RESOURCE_PATTERN = "**/*.xml";
        static final String CLASSPATH_ALL_URL_PREFIX = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX;

        static final int expireTimes = 600 * 2;        // 2分钟内没有继续修改，变成非热点文件.不进行实时监控.

        // 需要扫描的包
        String[] basePackages;

        ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();

        // 所有文件
        Map<String, String> files = new ConcurrentHashMap<String, String>();

        // 热点文件.
        Map<String, AtomicInteger> hotspot = new ConcurrentHashMap<String, AtomicInteger>();

        public AutoReloadScanner(String[] basePackages) {
            this.basePackages = basePackages;
        }

        /**
         * 只扫描热点文件改变.（热点文件失效：连续600个扫描周期内（1分钟）没有改变）
         */
        public void scanHotspotFileChange() {

            // 如果热点文件为空，立即返回.
            if (hotspot.isEmpty()) {
                return;
            }

            List<String> list = new ArrayList<String>();
            for (Map.Entry<String, AtomicInteger> e : hotspot.entrySet()) {
                String url = e.getKey();
                AtomicInteger counter = e.getValue();
                if (counter.incrementAndGet() >= expireTimes) {
                    // 计数器自增，判断是否超过指定的过期次数
                    list.add(url);
                }
                if (hasChange(url, files.get(url))) {
                    reload(url);        // 变化，调用重新加载方法
                    counter.set(0);        // 计数器清零
                }
            }

            // 移除过期的热点文件
            if (!list.isEmpty()) {
//				System.out.println("移除过期的热点文件：list=" + list);
                for (String s : list) {
                    hotspot.remove(s);
                }
            }
        }

        /**
         * 重新加载文件.
         *
         * @param url
         */
        private void reload(String url) {
            reloadAll();        // 必须加载所有文件，否则其它文件由于没有加载会导致找不到对应的语句异常（暂时先这样吧）
        }

        /**
         * 重新加载所有文件.
         */
        private void reloadAll() {
            StopWatch sw = new StopWatch("mybatis mapper auto reload");
            sw.start();
            Configuration configuration = getConfiguration();
            for (Map.Entry<String, String> entry : files.entrySet()) {
                String location = entry.getKey();
                Resource r = resourcePatternResolver.getResource(location);
                try {
                    XMLMapperBuilder xmlMapperBuilder = new XMLMapperBuilder(r.getInputStream(), configuration, r.toString(), configuration.getSqlFragments());
                    xmlMapperBuilder.parse();
                    LOGGER.info("reloaded : {}", r.toString());
                } catch (Exception e) {
                    throw new RuntimeException("Failed to parse mapping resource: '" + r + "'", e);
                } finally {
                    ErrorContext.instance().reset();
                }
            }
            sw.stop();
//			LOGGER.info("重新加载mybatis映射文件完成.");
            LOGGER.info(sw.shortSummary());
        }

        /**
         * 扫描所有文件改变.
         */
        public void scanAllFileChange() {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                String url = entry.getKey();
                if (hasChange(url, entry.getValue())) {
                    // 变化，判断是否在热点文件中，如果存在，直接忽略，如果不存在，触发重新加载
                    if (!hotspot.containsKey(url)) {
                        // 添加到热点文件，并且触发重新加载
                        hotspot.put(url, new AtomicInteger(0));
                        reload(url);
                    }
                }
            }
        }

        /**
         * 判断文件是否变化.
         *
         * @param url
         * @param tag
         * @return
         */
        private boolean hasChange(String url, String tag) {
            Resource r = resourcePatternResolver.getResource(url);
            String newTag = getTag(r);
            // 之前的标记和最新的标记不一致，说明文件修改了！
            if (!tag.equals(newTag)) {
                files.put(url, newTag);        // 更新标记
                return true;
            }
            return false;
        }

        /**
         * 获得文件的标记.
         *
         * @param r
         * @return
         */
        private String getTag(Resource r) {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append(r.contentLength());
                sb.append(r.lastModified());
                return sb.toString();
            } catch (IOException e) {
                throw new RuntimeException("获取文件标记信息失败！r=" + r, e);
            }
        }

        /**
         * 开启扫描服务
         */
        public void start() {
            this.files.clear();
            try {
                for (String basePackage : basePackages) {
                    Resource[] resources = getResource(basePackage);
                    if (resources != null) {
                        for (Resource r : resources) {
                            String tag = getTag(r);
                            files.put(r.getURL().toString(), tag);
                        }
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("初始化扫描服务失败！", e);
            }
        }

        /**
         * 获取xml文件资源.
         *
         * @param basePackage
         * @return
         * @throws IOException
         */
        public Resource[] getResource(String basePackage) {
            try {
                if (!basePackage.startsWith(CLASSPATH_ALL_URL_PREFIX)) {
                    basePackage = CLASSPATH_ALL_URL_PREFIX + ClassUtils.convertClassNameToResourcePath(applicationContext.getEnvironment().resolveRequiredPlaceholders(basePackage)) + "/" + XML_RESOURCE_PATTERN;
                }
                Resource[] resources = resourcePatternResolver.getResources(basePackage);
                return resources;
            } catch (Exception e) {
                throw new RuntimeException("获取xml文件资源失败！basePackage=" + basePackage, e);
            }
        }

        /**
         * 获取配置信息，必须每次都重新获取，否则重新加载xml不起作用.
         *
         * @return
         */
        private Configuration getConfiguration() {
            Configuration configuration = sqlSessionFactory.getConfiguration();
            removeConfig(configuration);
            return configuration;
        }

        /**
         * 删除不必要的配置项.
         *
         * @param configuration
         * @throws Exception
         */
        private void removeConfig(Configuration configuration) {
            try {
                Class<?> classConfig = configuration.getClass();
                clearMap(classConfig, configuration, "mappedStatements");
                clearMap(classConfig, configuration, "caches");
                clearMap(classConfig, configuration, "resultMaps");
                clearMap(classConfig, configuration, "parameterMaps");
                clearMap(classConfig, configuration, "keyGenerators");
                clearMap(classConfig, configuration, "sqlFragments");
                clearSet(classConfig, configuration, "loadedResources");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @SuppressWarnings("rawtypes")
        private void clearMap(Class<?> classConfig, Configuration configuration, String fieldName) throws Exception {
            Field field = classConfig.getDeclaredField(fieldName);
            field.setAccessible(true);
            Map mapConfig = (Map) field.get(configuration);
            mapConfig.clear();
        }

        @SuppressWarnings("rawtypes")
        private void clearSet(Class<?> classConfig, Configuration configuration, String fieldName) throws Exception {
            Field field = classConfig.getDeclaredField(fieldName);
            field.setAccessible(true);
            Set setConfig = (Set) field.get(configuration);
            setConfig.clear();
        }

    }

    static class DirectoryWatchService {

        private static final Logger LOGGER = LoggerFactory.getLogger(DirectoryWatchService.class);

        private final WatchService watcher;
        private final Map<WatchKey, Path> keys;
        private final boolean recursive;

        @SuppressWarnings("unchecked")
        static <T> WatchEvent<T> cast(WatchEvent<?> event) {
            return (WatchEvent<T>) event;
        }

        /**
         * Register the given directory with the WatchService
         */
        private void register(Path dir) throws IOException {
            WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
            Path prev = keys.get(key);
            if (prev == null) {
                LOGGER.info("register: {}", dir);
            } else {
                if (!dir.equals(prev)) {
                    LOGGER.info("update: {} -> {}", prev, dir);
                }
            }
            keys.put(key, dir);
        }

        /**
         * Register the given directory, and all its sub-directories, with the
         * WatchService.
         */
        private void registerAll(final Path start) throws IOException {
            // register directory and sub-directories
            Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                        throws IOException {
                    register(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        /**
         * Creates a WatchService and registers the given directory
         */
        DirectoryWatchService(boolean recursive, Path... paths) throws IOException {
            this.watcher = FileSystems.getDefault().newWatchService();
            this.keys = new HashMap<>();
            this.recursive = recursive;

            for (Path path : paths) {
                if (recursive) {
                    LOGGER.info("Scanning {} ...", path.toString());
                    registerAll(path);
                    LOGGER.info("Done.");
                } else {
                    register(path);
                }
            }

            // enable trace after initial registration
            //this.trace = true;
        }

        /**
         * Process all events for keys queued to the watcher
         */
        void processEvents(AutoReloadScanner scanner) {
            for (; ; ) {

                // wait for key to be signalled
                WatchKey key;
                try {
                    key = watcher.take();
                } catch (InterruptedException x) {
                    return;
                }

                Path dir = keys.get(key);
                if (dir == null) {
                    LOGGER.error("WatchKey not recognized!!");
                    continue;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind kind = event.kind();

                    // TBD - provide example of how OVERFLOW event is handled
                    if (kind == OVERFLOW) {
                        continue;
                    }

                    // Context for directory entry event is the file name of entry
                    WatchEvent<Path> ev = cast(event);
                    Path name = ev.context();
                    Path child = dir.resolve(name);

                    // print out event
                    LOGGER.info("{}: {}", event.kind().name(), child);

                    // if directory is created, and watching recursively, then
                    // register it and its sub-directories
                    if (recursive && (kind == ENTRY_CREATE)) {
                        try {
                            if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
                                registerAll(child);
                            }
                        } catch (IOException x) {
                            // ignore to keep sample readbale
                        }
                    }
                    // rescan
                    scanner.start();
                    // reload
                    scanner.reloadAll();
                }

                // reset key and remove from set if directory no longer accessible
                boolean valid = key.reset();
                if (!valid) {
                    keys.remove(key);

                    // all directories are inaccessible
                    if (keys.isEmpty()) {
                        break;
                    }
                }
            }
        }
    }
}