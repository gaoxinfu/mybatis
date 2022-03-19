/**
 *    Copyright 2009-2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;
import javax.sql.DataSource;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 *
 * XMLConfigBuilder 解析全局配置文件mybatis-config.xml
 */
public class XMLConfigBuilder extends BaseBuilder {

  /*
  Configuration只能解析一次，这里标示是否已经解析 mybatis-config.xml文件
   */
  private boolean parsed;
  private final XPathParser parser;
  private String environment;
  private final ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();

  public XMLConfigBuilder(Reader reader) {
    this(reader, null, null);
  }

  public XMLConfigBuilder(Reader reader, String environment) {
    this(reader, environment, null);
  }

  public XMLConfigBuilder(Reader reader, String environment, Properties props) {
    this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  public XMLConfigBuilder(InputStream inputStream) {
    this(inputStream, null, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment) {
    this(inputStream, environment, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
    this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
    super(new Configuration());
    ErrorContext.instance().resource("SQL Mapper Configuration");
    this.configuration.setVariables(props);
    /*
    上面只是将mybatis-config.xml文件转换为Document对象，具体到每个节点的内容还在后面解析 parse()方法
     */
    this.parsed = false;
    this.environment = environment;
    this.parser = parser;
  }

  public Configuration parse() {
    //判断是否已经解析过配置文件,已经解析过就会抛出异常,你可以认为,已经解析过的,不会走到这里
    if (parsed) {
      throw new BuilderException("Each XMLConfigBuilder can only be used once.");
    }
    parsed = true;
    //解析全局配置文件,从 configuration跟标签开始解析
    parseConfiguration(parser.evalNode("/configuration"));
    return configuration;
  }

  private void parseConfiguration(XNode root) {
    try {
      //一级标签开始解析
      //issue #117 read properties first
      propertiesElement(root.evalNode("properties"));


      //settings mybatis核心行为的控制，这里把setting中的配置转换为Properties key-value的形式，主要是方便下面的使用
      Properties settings = settingsAsProperties(root.evalNode("settings"));
      //自定义 远程或者本地的文件加载实现 https://mybatis.org/mybatis-3/zh/configuration.html#settings
      loadCustomVfs(settings);
      //日志的自定义实现加载 https://mybatis.org/mybatis-3/zh/configuration.html#settings
      loadCustomLogImpl(settings);

      //解析类型别名
      typeAliasesElement(root.evalNode("typeAliases"));
      //解析插件
      pluginElement(root.evalNode("plugins"));
      //TODO 创建返回的对象的时候用的
      objectFactoryElement(root.evalNode("objectFactory"));
      //TODO 创建返回的对象的时候用的
      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
      //TODO
      reflectorFactoryElement(root.evalNode("reflectorFactory"));


      //setting标签最终处理,设置到configuration中
      settingsElement(settings);

      //https://mybatis.org/mybatis-3/zh/configuration.html#environments
      // read it after objectFactory and objectWrapperFactory issue #631
      environmentsElement(root.evalNode("environments"));
      //用来支持多数据库的厂商
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));
      /**
       * 无论是 MyBatis 在预处理语句（PreparedStatement）中设置一个参数时，还是从结果集中取出一个值时，
       * 都会用类型处理器将获取的值以合适的方式转换成 Java 类型。下表描述了一些默认的类型处理器。
       * 处理java对象字段类型和数据库表字段的类型转换
       */
      typeHandlerElement(root.evalNode("typeHandlers"));

      /*
      mapper.xml文件的解析
       */
      mapperElement(root.evalNode("mappers"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }

  /**
   *
   *    实际上就是将settings配置转换为properties
   *
   *    <settings>
   *         <!-- 打印查询语句 -->
   *         <setting name="logImpl" value="STDOUT_LOGGING" />
   *
   *         <!-- 控制全局缓存（二级缓存）-->
   *         <setting name="cacheEnabled" value="true"/>
   *
   *         <!-- 延迟加载的全局开关。当开启时，所有关联对象都会延迟加载。默认 false  -->
   *         <setting name="lazyLoadingEnabled" value="true"/>
   *         <!-- 当开启时，任何方法的调用都会加载该对象的所有属性。默认 false，可通过select标签的 fetchType来覆盖-->
   *         <setting name="aggressiveLazyLoading" value="false"/>
   *         <!--  Mybatis 创建具有延迟加载能力的对象所用到的代理工具，默认JAVASSIST -->
   *         <!--<setting name="proxyFactory" value="CGLIB" />-->
   *         <!-- STATEMENT级别的缓存，使一级缓存，只针对当前执行的这一statement有效 -->
   *         <!--
   *                 <setting name="localCacheScope" value="STATEMENT"/>
   *         -->
   *         <setting name="localCacheScope" value="SESSION"/>
   *     </settings>
   *
   * @param context
   * @return
   */
  private Properties settingsAsProperties(XNode context) {
    if (context == null) {
      return new Properties();
    }
    Properties props = context.getChildrenAsProperties();
    // Check that all settings are known to the configuration class
    MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
    for (Object key : props.keySet()) {
      if (!metaConfig.hasSetter(String.valueOf(key))) {
        throw new BuilderException("The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
      }
    }
    return props;
  }

  /**
   * 自定义 远程或者本地的文件加载实现
   * 要是通过程序能够方便读取本地文件系统、FTP文件系统等系统中的文件资源。
   * Mybatis中提供了VFS这个配置，主要是通过该配置可以加载自定义的虚拟文件系统应用程序。
   * @param props
   * @throws ClassNotFoundException
   */
  private void loadCustomVfs(Properties props) throws ClassNotFoundException {
    String value = props.getProperty("vfsImpl");
    if (value != null) {
      String[] clazzes = value.split(",");
      for (String clazz : clazzes) {
        if (!clazz.isEmpty()) {
          @SuppressWarnings("unchecked")
          Class<? extends VFS> vfsImpl = (Class<? extends VFS>)Resources.classForName(clazz);
          configuration.setVfsImpl(vfsImpl);
        }
      }
    }
  }

  /**
   * 日志的自定义实现加载
   *  例如
   *   <setting name="logImpl" value="STDOUT_LOGGING" />
   *
   * @param props
   */
  private void loadCustomLogImpl(Properties props) {
    Class<? extends Log> logImpl = resolveClass(props.getProperty("logImpl"));
    configuration.setLogImpl(logImpl);
  }

  /**
   * 主要是解析类型的别名
   * 例如
   * <typeAliases>
   *         <typeAlias alias="blog" type="com.gupaoedu.domain.Blog" />
   * </typeAliases>
   * @param parent
   */
  private void typeAliasesElement(XNode parent) {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {

        /**
         * <typeAliases>
         *   <package name="domain.blog"/>
         * </typeAliases>
         *
         * 每一个在包 domain.blog 中的 Java Bean，在没有注解的情况下，会使用 Bean 的首字母小写的非限定类名来作为它的别名。
         * 比如 domain.blog.Author 的别名为 author；若有注解，则别名为其注解值。见下面的例子：
         *
         * @Alias("author")
         * public class Author {
         *     ...
         * }
         */
        if ("package".equals(child.getName())) {
          String typeAliasPackage = child.getStringAttribute("name");
          configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
        } else {

          /**
           *
           *<typeAliases>
           *   <typeAlias alias="Blog" type="domain.blog.Blog"/>
           *</typeAliases>
           *
           * 当这样配置时，Blog 可以用在任何使用 domain.blog.Blog 的地方。
           *
           */
          String alias = child.getStringAttribute("alias");
          String type = child.getStringAttribute("type");
          try {
            Class<?> clazz = Resources.classForName(type);
            if (alias == null) {
              //如果没有配置alias，那么alias就是type的clazz
              //TODO 确认 相当于typeAliasRegistry.registerAlias(clazz,clazz);
              typeAliasRegistry.registerAlias(clazz);
            } else {
              /**
               * TypeAliasRegistry Map<String, Class<?>> typeAliases = new HashMap<>(); 用HashMap接收 key为alias ,value为clazz
               */
              typeAliasRegistry.registerAlias(alias, clazz);
            }
          } catch (ClassNotFoundException e) {
            throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
          }
        }
      }
    }
  }

  /**
   *  插件的解析
   *  例如
   *     <plugins>
   *         <plugin interceptor="com.gupaoedu.interceptor.SQLInterceptor">
   *             <property name="gupao" value="betterme" />
   *         </plugin>
   *         <plugin interceptor="com.gupaoedu.interceptor.MyPageInterceptor">
   *         </plugin>
   *     </plugins>
   * @param parent
   * @throws Exception
   */
  private void pluginElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        String interceptor = child.getStringAttribute("interceptor");
        Properties properties = child.getChildrenAsProperties();
        Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).newInstance();
        interceptorInstance.setProperties(properties);
        configuration.addInterceptor(interceptorInstance);
      }
    }
  }

  /**
   *
   * <objectFactory type="com.gupaoedu.objectfactory.GPObjectFactory">
   *   <property name="gupao" value="666"/>
   * </objectFactory>
   *
   * @param context  节点 objectFactory
   * @throws Exception
   */
  private void objectFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties properties = context.getChildrenAsProperties();
      ObjectFactory factory = (ObjectFactory) resolveClass(type).newInstance();
      factory.setProperties(properties);
      configuration.setObjectFactory(factory);
    }
  }

  private void objectWrapperFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).newInstance();
      configuration.setObjectWrapperFactory(factory);
    }
  }

  private void reflectorFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      ReflectorFactory factory = (ReflectorFactory) resolveClass(type).newInstance();
      configuration.setReflectorFactory(factory);
    }
  }

  /**
   * https://mybatis.org/mybatis-3/zh/configuration.html#properties
   *
   * <properties resource="org/mybatis/example/config.properties">
   *   <property name="username" value="dev_user"/>
   *   <property name="password" value="F2Fa3!33TYyg"/>
   * </properties>
   *
   *
   */
  private void propertiesElement(XNode context) throws Exception {
    if (context != null) {
      Properties defaults = context.getChildrenAsProperties();
      String resource = context.getStringAttribute("resource");
      String url = context.getStringAttribute("url");
      /**
       *
       *     <properties url="www.baidu.com" resource="xxxx"></properties>
       *     这种是错误的写法，url和resource属性不能同时并存
       */
      if (resource != null && url != null) {
        throw new BuilderException("The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
      }
      if (resource != null) {
        defaults.putAll(Resources.getResourceAsProperties(resource));
      } else if (url != null) {
        defaults.putAll(Resources.getUrlAsProperties(url));
      }

      //将前期获取自动设置的Properties属性放进去
      Properties vars = configuration.getVariables();
      if (vars != null) {
        defaults.putAll(vars);
      }
      parser.setVariables(defaults);
      //将最新最全的属性加载到configuration
      configuration.setVariables(defaults);
    }
  }

  /**
   * configuration最终设置settingsElement的节点配置
   * 这里的默认配置 我们也可以参考文档
   * http://www.mybatis.org/mybatis-3/zh/configuration.html#settings
   * @param props
   */
  private void settingsElement(Properties props) {
    configuration.setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
    configuration.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
    //是否使用二级缓存
    configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
    configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
    configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
    configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), false));
    configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
    configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
    configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
    configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
    configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
    configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
    configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
    configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
    configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
    configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
    configuration.setLazyLoadTriggerMethods(stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
    configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
    configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
    configuration.setDefaultEnumTypeHandler(resolveClass(props.getProperty("defaultEnumTypeHandler")));
    configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
    configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
    configuration.setReturnInstanceForEmptyRow(booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
    configuration.setLogPrefix(props.getProperty("logPrefix"));
    configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
  }

  /**
   *设置数据库连接的环境变量信息
   * 举例
   *
   *     <environments default="development">
   *         <environment id="development">
   *             <transactionManager type="JDBC"/>
   *             <dataSource type="POOLED">
   *                 <property name="driver" value="${jdbc.driver}"/>
   *                 <property name="url" value="${jdbc.url}"/>
   *                 <property name="username" value="${jdbc.username}"/>
   *                 <property name="password" value="${jdbc.password}"/>
   *             </dataSource>
   *         </environment>
   *     </environments>
   *
   * https://mybatis.org/mybatis-3/zh/configuration.html#environments
   * @param context
   * @throws Exception
   */
  private void environmentsElement(XNode context) throws Exception {
    if (context != null) {
      if (environment == null) {
        environment = context.getStringAttribute("default");
      }
      for (XNode child : context.getChildren()) {
        /**
         *
         * <environment id="development">
         *                 <transactionManager type="JDBC"/>
         *                 <dataSource type="POOLED">
         *                     <property name="driver" value="${jdbc.driver}"/>
         *                     <property name="url" value="${jdbc.url}"/>
         *                     <property name="username" value="${jdbc.username}"/>
         *                     <property name="password" value="${jdbc.password}"/>
         *                 </dataSource>
         * </environment>
         *
         * child就是上面的
         *
         */
        String id = child.getStringAttribute("id");
        if (isSpecifiedEnvironment(id)) {
          //产生事务工厂
          TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
          //产生数据源工厂
          DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
          //获取数据库连接
          DataSource dataSource = dsFactory.getDataSource();
          Environment.Builder environmentBuilder = new Environment.Builder(id)
              .transactionFactory(txFactory)
              .dataSource(dataSource);
          configuration.setEnvironment(environmentBuilder.build());
        }
      }
    }
  }

  /**
   *  //用来支持多数据库的厂商
   *  <databaseIdProvider type="DB_VENDOR">
   *   <property name="SQL Server" value="sqlserver"/>
   *   <property name="DB2" value="db2"/>
   *   <property name="Oracle" value="oracle" />
   * </databaseIdProvider>
   * @param context
   * @throws Exception
   */
  private void databaseIdProviderElement(XNode context) throws Exception {
    DatabaseIdProvider databaseIdProvider = null;
    if (context != null) {
      String type = context.getStringAttribute("type");
      // awful patch to keep backward compatibility
      // awful patch 重大补丁，keep backward compatibility 后续兼容
      if ("VENDOR".equals(type)) {
        type = "DB_VENDOR";
      }
      Properties properties = context.getChildrenAsProperties();
      // 初始化new Configuration的时候设置的 typeAliasRegistry.registerAlias("DB_VENDOR", VendorDatabaseIdProvider.class);
      //如果不是 最终会通过反射 Class.forName(type)获取的反射的自定义的类
      databaseIdProvider = (DatabaseIdProvider) resolveClass(type).newInstance();
      databaseIdProvider.setProperties(properties);
    }
    Environment environment = configuration.getEnvironment();
    if (environment != null && databaseIdProvider != null) {
      String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
      configuration.setDatabaseId(databaseId);
    }
  }


  /**
   *
   *         <environment id="development">
   *             <transactionManager type="JDBC"/><!-- 单独使用时配置成MANAGED没有事务 -->
   *             <dataSource type="POOLED">
   *                 <property name="driver" value="${jdbc.driver}"/>
   *                 <property name="url" value="${jdbc.url}"/>
   *                 <property name="username" value="${jdbc.username}"/>
   *                 <property name="password" value="${jdbc.password}"/>
   *             </dataSource>
   *         </environment>
   *
   * @param context  如<transactionManager type="JDBC"/>
   * @return
   * @throws Exception
   */
  private TransactionFactory transactionManagerElement(XNode context) throws Exception {
    if (context != null) {
      //<transactionManager type="JDBC"/>
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      TransactionFactory factory = (TransactionFactory) resolveClass(type).newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a TransactionFactory.");
  }

  /**
   *
   *
   *  <environments default="development">
   *         <environment id="development">
   *             <transactionManager type="JDBC"/><!-- 单独使用时配置成MANAGED没有事务 -->
   *             <dataSource type="POOLED">
   *                 <property name="driver" value="${jdbc.driver}"/>
   *                 <property name="url" value="${jdbc.url}"/>
   *                 <property name="username" value="${jdbc.username}"/>
   *                 <property name="password" value="${jdbc.password}"/>
   *             </dataSource>
   *         </environment>
   *  </environments>
   *
   *
   * @param context
   *  context为上面的dataSource的节点
   *  <dataSource type="POOLED">
   *                 <property name="driver" value="${jdbc.driver}"/>
   *                 <property name="url" value="${jdbc.url}"/>
   *                 <property name="username" value="${jdbc.username}"/>
   *                 <property name="password" value="${jdbc.password}"/>
   *  </dataSource>
   * @return
   * @throws Exception
   */
  private DataSourceFactory dataSourceElement(XNode context) throws Exception {
    if (context != null) {
      /**
       * <dataSource type="POOLED">
       * POOLED 数据库连接池的形式
       * UNPOOLED 不带数据库连接池的形式，管理真实的，物理的连接对象
       */
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      DataSourceFactory factory = (DataSourceFactory) resolveClass(type).newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a DataSourceFactory.");
  }

  /**
   * 处理java对象字段类型和数据库表字段的类型转换
   * 例如:
   *   <typeHandlers>
   *         <typeHandler handler="com.gupaoedu.type.MyTypeHandler"></typeHandler>
   *   </typeHandlers>
   * @param parent
   */
  private void typeHandlerElement(XNode parent) {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        /**
         *
         * <!-- mybatis-config.xml -->
         * <typeHandlers>
         *   <package name="org.mybatis.example"/>
         * </typeHandlers>
         *
         */
        if ("package".equals(child.getName())) {
          String typeHandlerPackage = child.getStringAttribute("name");
          //实际上就是在这个文件夹中寻找所有的继承TypeHandler的类，然后放入typeHandlerRegistry中
          typeHandlerRegistry.register(typeHandlerPackage);
        } else {
          String javaTypeName = child.getStringAttribute("javaType");
          String jdbcTypeName = child.getStringAttribute("jdbcType");
          String handlerTypeName = child.getStringAttribute("handler");
          Class<?> javaTypeClass = resolveClass(javaTypeName);
          JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
          Class<?> typeHandlerClass = resolveClass(handlerTypeName);
          if (javaTypeClass != null) {
            if (jdbcType == null) {
              typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
            } else {
              typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
            }
          } else {
            typeHandlerRegistry.register(typeHandlerClass);
          }
        }
      }
    }
  }

  /**
   *
   * 配置样例
   *
    使用相对于类路径的资源引用
   <mappers>
       <mapper resource="BlogMapper.xml"/>
       <mapper resource="BlogMapperExt.xml"/>
   </mappers>

   使用完全限定资源定位符（URL）
   <mappers>
   <mapper url="file:///var/mappers/AuthorMapper.xml"/>
   <mapper url="file:///var/mappers/BlogMapper.xml"/>
   <mapper url="file:///var/mappers/PostMapper.xml"/>
   </mappers>

    使用映射器接口实现类的完全限定类名
   <mappers>
     <mapper class="org.mybatis.builder.AuthorMapper"/>
     <mapper class="org.mybatis.builder.BlogMapper"/>
     <mapper class="org.mybatis.builder.PostMapper"/>
   </mappers>

   将包内的映射器接口实现全部注册为映射器
   <mappers>
    <package name="org.mybatis.builder"/>
   </mappers>
   *
   * @param parent
   * @throws Exception
   */
  private void mapperElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        /**
         * <mappers>
         *    <package name="org.mybatis.builder"/>
         * </mappers>
         */
        if ("package".equals(child.getName())) {
          String mapperPackage = child.getStringAttribute("name");
          configuration.addMappers(mapperPackage);
        } else {

          String resource = child.getStringAttribute("resource");
          String url = child.getStringAttribute("url");
          String mapperClass = child.getStringAttribute("class");

          if (resource != null && url == null && mapperClass == null) {
            /**
             * <mapper resource="BlogMapper.xml"/>
             */
            ErrorContext.instance().resource(resource);
            InputStream inputStream = Resources.getResourceAsStream(resource);
            XMLMapperBuilder xmlMapperBuilder = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
            xmlMapperBuilder.parse();
          } else if (resource == null && url != null && mapperClass == null) {
            /**
             *   <mappers>
             *    <mapper url="file:///var/mappers/AuthorMapper.xml"/>
             *    <mapper url="file:///var/mappers/BlogMapper.xml"/>
             *    <mapper url="file:///var/mappers/PostMapper.xml"/>
             *   </mappers>
             */
            ErrorContext.instance().resource(url);
            InputStream inputStream = Resources.getUrlAsStream(url);
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
            mapperParser.parse();

            /**
             *   <mappers>
             *    <mapper class="org.mybatis.builder.AuthorMapper"/>
             *    <mapper class="org.mybatis.builder.BlogMapper"/>
             *    <mapper class="org.mybatis.builder.PostMapper"/>
             *   </mappers>
             *
             */
          } else if (resource == null && url == null && mapperClass != null) {
            Class<?> mapperInterface = Resources.classForName(mapperClass);
            configuration.addMapper(mapperInterface);
          } else {
            throw new BuilderException("A mapper element may only specify a url, resource or class, but not more than one.");
          }
        }
      }
    }
  }

  /**
   *
   * 是否指定了数据库的环境配置
   * @param id
   * @return
   */
  private boolean isSpecifiedEnvironment(String id) {
    if (environment == null) {
      throw new BuilderException("No environment specified.");
    } else if (id == null) {
      throw new BuilderException("Environment requires an id attribute.");
    } else if (environment.equals(id)) {
      return true;
    }
    return false;
  }

}
