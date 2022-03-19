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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.builder.CacheRefResolver;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.ResultMapResolver;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Discriminator;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultFlag;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 *
 * 解析Mapper映射器的 BlogMapperExt.xml
 *
 *
 <?xml version="1.0" encoding="UTF-8" ?>
 <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
 <mapper namespace="com.gupaoedu.mapper.BlogMapperExt">
      <!-- 只能继承statement，不能继承sql、resultMap等标签 -->
      <resultMap id="BaseResultMap" type="blog">
          <id column="bid" property="bid" jdbcType="INTEGER"/>
          <result column="name" property="name" jdbcType="VARCHAR"/>
          <result column="author_id" property="authorId" jdbcType="INTEGER"/>
      </resultMap>

      <!-- 在parent xml 和child xml 的 statement id相同的情况下，会使用child xml 的statement id -->
      <select id="selectBlogByName" resultMap="BaseResultMap" statementType="PREPARED">
          select * from blog where name = #{name}
      </select>
  </mapper>
 *
 */
public class XMLMapperBuilder extends BaseBuilder {

  private final XPathParser parser;
  private final MapperBuilderAssistant builderAssistant;
  private final Map<String, XNode> sqlFragments;
  private final String resource;

  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
    this(reader, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    this(new XPathParser(reader, true, configuration.getVariables(), new XMLMapperEntityResolver()),
        configuration, resource, sqlFragments);
  }

  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
    this(inputStream, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  /**
   *
   * @param inputStream
   * @param configuration
   * @param resource 解析的xml文件地址 如BlogMapper.xml的地址
   * @param sqlFragments
   */
  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    this(
      //实际上就是将文件转换为Document对象
      new XPathParser(inputStream, true, configuration.getVariables(), new XMLMapperEntityResolver()),
      configuration,
      resource,
      sqlFragments);
  }

  private XMLMapperBuilder(XPathParser parser, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    super(configuration);
    this.builderAssistant = new MapperBuilderAssistant(configuration, resource);
    this.parser = parser;
    this.sqlFragments = sqlFragments;
    this.resource = resource;
  }

  /**
   *
   * 单条mapper解析
   * 例如
   *  <mapper resource="BlogMapper.xml"/>
   *
   *  <mapper url="file:///var/mappers/PostMapper.xml"/>
   *
   *  <mapper class="org.mybatis.builder.AuthorMapper"/>
   *
   *
   */
  public void parse() {
    //<mapper resource="BlogMapper.xml"/>
    if (!configuration.isResourceLoaded(resource)) {
      /*
      首先判断是否加载过改文件，如果已经加载，不再重复加载，未加载的话，重新加载
       */
      configurationElement(parser.evalNode("/mapper"));
      configuration.addLoadedResource(resource);
      bindMapperForNamespace();
    }

    parsePendingResultMaps();

    parsePendingCacheRefs();
    parsePendingStatements();
  }

  public XNode getSqlFragment(String refid) {
    return sqlFragments.get(refid);
  }


  /**
   *
   * @param context BlogMapper.xml中的具体内容，如
   *
   * <mapper namespace="com.gupaoedu.mapper.BlogMapper">
   *     <!-- 声明这个namespace使用二级缓存 -->
   *     <cache/>
   *     ...
   *     <resultMap id="BaseResultMap" type="blog">
   *         <id column="bid" property="bid" jdbcType="INTEGER"/>
   *         <result column="name" property="name" jdbcType="VARCHAR"/>
   *         <result column="author_id" property="authorId" jdbcType="INTEGER"/>
   *     </resultMap>
   * </mapper>
   *
   */
  private void configurationElement(XNode context) {
    try {
      String namespace = context.getStringAttribute("namespace");
      if (namespace == null || namespace.equals("")) {
        throw new BuilderException("Mapper's namespace cannot be empty");
      }
      builderAssistant.setCurrentNamespace(namespace);

      //TODO
      cacheRefElement(context.evalNode("cache-ref"));
      cacheElement(context.evalNode("cache"));

      parameterMapElement(context.evalNodes("/mapper/parameterMap"));

      /**
       *  <resultMap id="BaseResultMap" type="blog">
       *         <id column="bid" property="bid" jdbcType="INTEGER"/>
       *         <result column="name" property="name" jdbcType="VARCHAR"/>
       *         <result column="author_id" property="authorId" jdbcType="INTEGER"/>
       *  </resultMap>
       *
       * <resultMap id="BlogWithAuthorResultMap" type="com.gupaoedu.domain.associate.BlogAndAuthor">
       *         <id column="bid" property="bid" jdbcType="INTEGER"/>
       *         <result column="name" property="name" jdbcType="VARCHAR"/>
       *         <!-- 联合查询，将author的属性映射到ResultMap -->
       *         <association property="author" javaType="com.gupaoedu.domain.Author">
       *             <id column="author_id" property="authorId"/>
       *             <result column="author_name" property="authorName"/>
       *         </association>
       *  </resultMap>
       *
       *  实际上是匹配的所有的resultMap
       */
      resultMapElements(context.evalNodes("/mapper/resultMap"));

      /**
       * https://mybatis.org/mybatis-3/zh/sqlmap-xml.html#select
       * 解析sql片段
       * <sql id="userColumns"> ${alias}.id,${alias}.username,${alias}.password </sql>
       * 或者
       * <sql id="sometable">
       *   ${prefix}Table
       * </sql>
       *
       * <sql id="someinclude">
       *   from
       *     <include refid="${include_target}"/>
       * </sql>
       *
       * <select id="select" resultType="map">
       *   select
       *     field1, field2, field3
       *   <include refid="someinclude">
       *     <property name="prefix" value="Some"/>
       *     <property name="include_target" value="sometable"/>
       *   </include>
       * </select>
       */
      sqlElement(context.evalNodes("/mapper/sql"));

      /**
       * https://mybatis.org/mybatis-3/zh/sqlmap-xml.html#select
       * 这个是重点了 对于增删改查的SQL解析
       *
       */
      buildStatementFromContext(context.evalNodes("select|insert|update|delete"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing Mapper XML. The XML location is '" + resource + "'. Cause: " + e, e);
    }
  }

  /**
   * 构建Statement 对象
   * @param list
   */
  private void buildStatementFromContext(List<XNode> list) {
    if (configuration.getDatabaseId() != null) {
      buildStatementFromContext(list, configuration.getDatabaseId());
    }
    buildStatementFromContext(list, null);
  }

  private void buildStatementFromContext(List<XNode> list, String requiredDatabaseId) {
    for (XNode context : list) {
      final XMLStatementBuilder xmlStatementBuilder = new XMLStatementBuilder(configuration, builderAssistant, context, requiredDatabaseId);
      try {
        xmlStatementBuilder.parseStatementNode();
      } catch (IncompleteElementException e) {
        configuration.addIncompleteStatement(xmlStatementBuilder);
      }
    }
  }

  private void parsePendingResultMaps() {
    Collection<ResultMapResolver> incompleteResultMaps = configuration.getIncompleteResultMaps();
    synchronized (incompleteResultMaps) {
      Iterator<ResultMapResolver> iter = incompleteResultMaps.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().resolve();
          iter.remove();
        } catch (IncompleteElementException e) {
          // ResultMap is still missing a resource...
        }
      }
    }
  }

  private void parsePendingCacheRefs() {
    Collection<CacheRefResolver> incompleteCacheRefs = configuration.getIncompleteCacheRefs();
    synchronized (incompleteCacheRefs) {
      Iterator<CacheRefResolver> iter = incompleteCacheRefs.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().resolveCacheRef();
          iter.remove();
        } catch (IncompleteElementException e) {
          // Cache ref is still missing a resource...
        }
      }
    }
  }

  private void parsePendingStatements() {
    Collection<XMLStatementBuilder> incompleteStatements = configuration.getIncompleteStatements();
    synchronized (incompleteStatements) {
      Iterator<XMLStatementBuilder> iter = incompleteStatements.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().parseStatementNode();
          iter.remove();
        } catch (IncompleteElementException e) {
          // Statement is still missing a resource...
        }
      }
    }
  }

  private void cacheRefElement(XNode context) {
    if (context != null) {
      configuration.addCacheRef(builderAssistant.getCurrentNamespace(), context.getStringAttribute("namespace"));
      CacheRefResolver cacheRefResolver = new CacheRefResolver(builderAssistant, context.getStringAttribute("namespace"));
      try {
        cacheRefResolver.resolveCacheRef();
      } catch (IncompleteElementException e) {
        configuration.addIncompleteCacheRef(cacheRefResolver);
      }
    }
  }

  /**
   *
   * 这个是在Blog.xml文件中的对这个Namespace的一个全局配置
   * <!-- 声明这个 namespace 使用二级缓存 -->
   *     <cache
   *          type="org.apache.ibatis.cache.impl.PerpetualCache"
   *          size="1024"                             <!—最多缓存对象个数，默认 1024-->
   *          eviction="LRU"                          <!—回收策略-->
   *          flushInterval="120000"                  <!—自动刷新时间 ms，未配置时只有调用时刷新-->
   *          readOnly="false"/>                      <!—默认是 false（安全），改为 true 可读写时，对象必须支持序列 化 -->
   *
   * @param context
   */
  private void cacheElement(XNode context) {
    if (context != null) {
      String type = context.getStringAttribute("type", "PERPETUAL");
      Class<? extends Cache> typeClass = typeAliasRegistry.resolveAlias(type);
      String eviction = context.getStringAttribute("eviction", "LRU");
      Class<? extends Cache> evictionClass = typeAliasRegistry.resolveAlias(eviction);
      Long flushInterval = context.getLongAttribute("flushInterval");
      Integer size = context.getIntAttribute("size");
      boolean readWrite = !context.getBooleanAttribute("readOnly", false);
      boolean blocking = context.getBooleanAttribute("blocking", false);
      Properties props = context.getChildrenAsProperties();
      builderAssistant.useNewCache(typeClass, evictionClass, flushInterval, size, readWrite, blocking, props);
    }
  }

  /**
   *
   * <parameterMap type="Book.dao.Book" id="BookParameterMap">
   *   <parameter property="bookName" resultMap="BookResultMap" />
   *   <parameter property="bookPrice" resultMap="BookResultMap" />
   *  </parameterMap>
   *
   * @param list
   */
  private void parameterMapElement(List<XNode> list) {
    for (XNode parameterMapNode : list) {

      String id = parameterMapNode.getStringAttribute("id");
      String type = parameterMapNode.getStringAttribute("type");
      Class<?> parameterClass = resolveClass(type);

      List<XNode> parameterNodes = parameterMapNode.evalNodes("parameter");
      List<ParameterMapping> parameterMappings = new ArrayList<>();
      for (XNode parameterNode : parameterNodes) {
        String property = parameterNode.getStringAttribute("property");
        String javaType = parameterNode.getStringAttribute("javaType");
        String jdbcType = parameterNode.getStringAttribute("jdbcType");
        String resultMap = parameterNode.getStringAttribute("resultMap");
        String mode = parameterNode.getStringAttribute("mode");
        String typeHandler = parameterNode.getStringAttribute("typeHandler");
        Integer numericScale = parameterNode.getIntAttribute("numericScale");
        ParameterMode modeEnum = resolveParameterMode(mode);
        Class<?> javaTypeClass = resolveClass(javaType);
        JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
        Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
        ParameterMapping parameterMapping = builderAssistant.buildParameterMapping(parameterClass, property, javaTypeClass, jdbcTypeEnum, resultMap, modeEnum, typeHandlerClass, numericScale);
        parameterMappings.add(parameterMapping);
      }
      builderAssistant.addParameterMap(id, parameterClass, parameterMappings);
    }
  }

  /**
   *  <resultMap id="BaseResultMap" type="blog">
   *         <id column="bid" property="bid" jdbcType="INTEGER"/>
   *         <result column="name" property="name" jdbcType="VARCHAR"/>
   *         <result column="author_id" property="authorId" jdbcType="INTEGER"/>
   *  </resultMap>
   *
   * <resultMap id="BlogWithAuthorResultMap" type="com.gupaoedu.domain.associate.BlogAndAuthor">
   *         <id column="bid" property="bid" jdbcType="INTEGER"/>
   *         <result column="name" property="name" jdbcType="VARCHAR"/>
   *         <!-- 联合查询，将author的属性映射到ResultMap -->
   *         <association property="author" javaType="com.gupaoedu.domain.Author">
   *             <id column="author_id" property="authorId"/>
   *             <result column="author_name" property="authorName"/>
   *         </association>
   *  </resultMap>
   *
   *  实际上是匹配的所有的resultMap
   */
  private void resultMapElements(List<XNode> list) throws Exception {
    for (XNode resultMapNode : list) {
      try {

        /**
         *
         * <resultMap id="BaseResultMap" type="blog">
         *         <id column="bid" property="bid" jdbcType="INTEGER"/>
         *         <result column="name" property="name" jdbcType="VARCHAR"/>
         *         <result column="author_id" property="authorId" jdbcType="INTEGER"/>
         * </resultMap>
         *
         * resultMapNode如上一个
         *
         */

        resultMapElement(resultMapNode);
      } catch (IncompleteElementException e) {
        // ignore, it will be retried
      }
    }
  }

  /**
   *
   * <resultMap id="BaseResultMap" type="blog">
   *         <id column="bid" property="bid" jdbcType="INTEGER"/>
   *         <result column="name" property="name" jdbcType="VARCHAR"/>
   *         <result column="author_id" property="authorId" jdbcType="INTEGER"/>
   * </resultMap>
   *
   * resultMapNode如上一个
   *
   */

  private ResultMap resultMapElement(XNode resultMapNode) throws Exception {
    return resultMapElement(resultMapNode, Collections.emptyList(), null);
  }

  private ResultMap resultMapElement(XNode resultMapNode, List<ResultMapping> additionalResultMappings, Class<?> enclosingType) throws Exception {
    ErrorContext.instance().activity("processing " + resultMapNode.getValueBasedIdentifier());

    //首先用type，type为空接下来去判断ofType，以此类推，实际上是全局获取beanClass
    String type = resultMapNode.getStringAttribute(
      "type",
      resultMapNode.getStringAttribute(
          "ofType",
          resultMapNode.getStringAttribute(
              "resultType",
              resultMapNode.getStringAttribute("javaType")
          )
      )
    );
    Class<?> typeClass = resolveClass(type);
    if (typeClass == null) {
      //TODO
      typeClass = inheritEnclosingType(resultMapNode, enclosingType);
    }
    Discriminator discriminator = null;
    List<ResultMapping> resultMappings = new ArrayList<>();
    resultMappings.addAll(additionalResultMappings);
    List<XNode> resultChildren = resultMapNode.getChildren();
    for (XNode resultChild : resultChildren) {

      /**
       *
       * resultChild 未resultMap中的子节点
       *
      <!-- 非常复杂的结果映射 -->
      <resultMap id="detailedBlogResultMap" type="Blog">
        <constructor>
          <idArg column="blog_id" javaType="int"/>
        </constructor>
        <result property="title" column="blog_title"/>
        <association property="author" javaType="Author">
          <id property="id" column="author_id"/>
          <result property="username" column="author_username"/>
          <result property="password" column="author_password"/>
          <result property="email" column="author_email"/>
          <result property="bio" column="author_bio"/>
          <result property="favouriteSection" column="author_favourite_section"/>
        </association>
        <collection property="posts" ofType="Post">
          <id property="id" column="post_id"/>
          <result property="subject" column="post_subject"/>
          <association property="author" javaType="Author"/>
          <collection property="comments" ofType="Comment">
            <id property="id" column="comment_id"/>
          </collection>
          <collection property="tags" ofType="Tag" >
            <id property="id" column="tag_id"/>
          </collection>
          <discriminator javaType="int" column="draft">
            <case value="1" resultType="DraftPost"/>
          </discriminator>
        </collection>
      </resultMap>
     */

      if ("constructor".equals(resultChild.getName())) {
        processConstructorElement(resultChild, typeClass, resultMappings);
      } else if ("discriminator".equals(resultChild.getName())) {
        discriminator = processDiscriminatorElement(resultChild, typeClass, resultMappings);
      } else {
        List<ResultFlag> flags = new ArrayList<>();
        if ("id".equals(resultChild.getName())) {
          flags.add(ResultFlag.ID);
        }
        resultMappings.add(buildResultMappingFromContext(resultChild, typeClass, flags));
      }
    }
    String id = resultMapNode.getStringAttribute("id",
            resultMapNode.getValueBasedIdentifier());
    String extend = resultMapNode.getStringAttribute("extends");
    Boolean autoMapping = resultMapNode.getBooleanAttribute("autoMapping");
    ResultMapResolver resultMapResolver = new ResultMapResolver(builderAssistant, id, typeClass, extend, discriminator, resultMappings, autoMapping);
    try {
      return resultMapResolver.resolve();
    } catch (IncompleteElementException  e) {
      configuration.addIncompleteResultMap(resultMapResolver);
      throw e;
    }
  }


  /**
   * https://mybatis.org/mybatis-3/zh/sqlmap-xml.html#Result_Maps
   * <!-- 非常复杂的结果映射 -->
   * <resultMap id="detailedBlogResultMap" type="Blog">
   *   <constructor>
   *     <idArg column="blog_id" javaType="int"/>
   *   </constructor>
   *   <result property="title" column="blog_title"/>
   *   <association property="author" javaType="Author">
   *     <id property="id" column="author_id"/>
   *     <result property="username" column="author_username"/>
   *     <result property="password" column="author_password"/>
   *     <result property="email" column="author_email"/>
   *     <result property="bio" column="author_bio"/>
   *     <result property="favouriteSection" column="author_favourite_section"/>
   *   </association>
   *   <collection property="posts" ofType="Post">
   *     <id property="id" column="post_id"/>
   *     <result property="subject" column="post_subject"/>
   *     <association property="author" javaType="Author"/>
   *     <collection property="comments" ofType="Comment">
   *       <id property="id" column="comment_id"/>
   *     </collection>
   *     <collection property="tags" ofType="Tag" >
   *       <id property="id" column="tag_id"/>
   *     </collection>
   *     <discriminator javaType="int" column="draft">
   *       <case value="1" resultType="DraftPost"/>
   *     </discriminator>
   *   </collection>
   * </resultMap>
   *
   * @param resultMapNode
   * @param enclosingType
   * @return
   */
  protected Class<?> inheritEnclosingType(XNode resultMapNode, Class<?> enclosingType) {
    if ("association".equals(resultMapNode.getName()) && resultMapNode.getStringAttribute("resultMap") == null) {
      String property = resultMapNode.getStringAttribute("property");
      if (property != null && enclosingType != null) {
        MetaClass metaResultType = MetaClass.forClass(enclosingType, configuration.getReflectorFactory());
        return metaResultType.getSetterType(property);
      }
    } else if ("case".equals(resultMapNode.getName()) && resultMapNode.getStringAttribute("resultMap") == null) {
      return enclosingType;
    }
    return null;
  }

  /**
   *
   *  <constructor>
   *    <idArg column="blog_id" javaType="int"/>
   *  </constructor>
   *
   * @param resultChild
   * @param resultType
   * @param resultMappings
   * @throws Exception
   */
  private void processConstructorElement(XNode resultChild, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {
    List<XNode> argChildren = resultChild.getChildren();
    for (XNode argChild : argChildren) {
      /*
      argChild 为constructor的子节点，如idArg
       */
      List<ResultFlag> flags = new ArrayList<>();
      flags.add(ResultFlag.CONSTRUCTOR);
      if ("idArg".equals(argChild.getName())) {
        flags.add(ResultFlag.ID);
      }
      resultMappings.add(buildResultMappingFromContext(argChild, resultType, flags));
    }
  }

  /**
   *
   *   <discriminator javaType="int" column="draft">
   *             <case value="1" resultType="DraftPost"/>
   *   </discriminator>
   *
   * @param context
   * @param resultType
   * @param resultMappings
   * @return
   * @throws Exception
   */
  private Discriminator processDiscriminatorElement(XNode context, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {
    String column = context.getStringAttribute("column");
    String javaType = context.getStringAttribute("javaType");
    String jdbcType = context.getStringAttribute("jdbcType");
    String typeHandler = context.getStringAttribute("typeHandler");
    Class<?> javaTypeClass = resolveClass(javaType);
    Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
    Map<String, String> discriminatorMap = new HashMap<>();
    for (XNode caseChild : context.getChildren()) {
      /*
      caseChild为 discriminator的子节点 ，如case
       */
      String value = caseChild.getStringAttribute("value");
      String resultMap = caseChild.getStringAttribute("resultMap", processNestedResultMappings(caseChild, resultMappings, resultType));
      discriminatorMap.put(value, resultMap);
    }
    return builderAssistant.buildDiscriminator(resultType, column, javaTypeClass, jdbcTypeEnum, typeHandlerClass, discriminatorMap);
  }

  private void sqlElement(List<XNode> list) {
    if (configuration.getDatabaseId() != null) {
      sqlElement(list, configuration.getDatabaseId());
    }
    sqlElement(list, null);
  }

  /**
   *
   * @param list
   * @param requiredDatabaseId
   */
  private void sqlElement(List<XNode> list, String requiredDatabaseId) {
    for (XNode context : list) {
      /**
       *  <sql id="Example_Where_Clause" >
       *     <where >
       *       <foreach collection="oredCriteria" item="criteria" separator="or" >
       *         <if test="criteria.valid" >
       *           <trim prefix="(" suffix=")" prefixOverrides="and" >
       *             <foreach collection="criteria.criteria" item="criterion" >
       *               <choose >
       *                 <when test="criterion.noValue" >
       *                   and ${criterion.condition}
       *                 </when>
       *                 <when test="criterion.singleValue" >
       *                   and ${criterion.condition} #{criterion.value}
       *                 </when>
       *                 <when test="criterion.betweenValue" >
       *                   and ${criterion.condition} #{criterion.value} and #{criterion.secondValue}
       *                 </when>
       *                 <when test="criterion.listValue" >
       *                   and ${criterion.condition}
       *                   <foreach collection="criterion.value" item="listItem" open="(" close=")" separator="," >
       *                     #{listItem}
       *                   </foreach>
       *                 </when>
       *               </choose>
       *             </foreach>
       *           </trim>
       *         </if>
       *       </foreach>
       *     </where>
       *   </sql>
       */
      String databaseId = context.getStringAttribute("databaseId");
      String id = context.getStringAttribute("id");
      //实际上就是把当前的id(Example_Where_Clause) 给加上namespace:如 com.gaoxinfu.demo.open.source.mybatis.db.mapper.BlogMapper
      id = builderAssistant.applyCurrentNamespace(id, false);
      if (databaseIdMatchesCurrent(id, databaseId, requiredDatabaseId)) {
        sqlFragments.put(id, context);
      }
    }
  }

  private boolean databaseIdMatchesCurrent(String id, String databaseId, String requiredDatabaseId) {
    if (requiredDatabaseId != null) {
      if (!requiredDatabaseId.equals(databaseId)) {
        return false;
      }
    } else {
      if (databaseId != null) {
        return false;
      }
      // skip this fragment if there is a previous one with a not null databaseId
      if (this.sqlFragments.containsKey(id)) {
        XNode context = this.sqlFragments.get(id);
        if (context.getStringAttribute("databaseId") != null) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   *
   * <resultMap id="BlogWithAuthorResultMap" type="com.gupaoedu.domain.associate.BlogAndAuthor">
   *         <id column="bid" property="bid" jdbcType="INTEGER"/>
   *         <result column="name" property="name" jdbcType="VARCHAR"/>
   * </resultMap>
   *
   *
   * @param context  为上面resultMap中的child元素
   * @param resultType
   * @param flags 为resultMap中的child元素的name 如id或者result
   * @return
   * @throws Exception
   */
  private ResultMapping buildResultMappingFromContext(XNode context, Class<?> resultType, List<ResultFlag> flags) throws Exception {
    String property;
    if (flags.contains(ResultFlag.CONSTRUCTOR)) {
      /*
      <constructor>
        <idArg column="blog_id" javaType="int"/>
      </constructor>
       */
      property = context.getStringAttribute("name");
    } else {
      property = context.getStringAttribute("property");
    }
    String column = context.getStringAttribute("column");
    String javaType = context.getStringAttribute("javaType");
    String jdbcType = context.getStringAttribute("jdbcType");
    String nestedSelect = context.getStringAttribute("select");
    String nestedResultMap = context.getStringAttribute("resultMap",
        processNestedResultMappings(context, Collections.emptyList(), resultType));
    String notNullColumn = context.getStringAttribute("notNullColumn");
    String columnPrefix = context.getStringAttribute("columnPrefix");
    String typeHandler = context.getStringAttribute("typeHandler");
    String resultSet = context.getStringAttribute("resultSet");
    String foreignColumn = context.getStringAttribute("foreignColumn");
    boolean lazy = "lazy".equals(context.getStringAttribute("fetchType", configuration.isLazyLoadingEnabled() ? "lazy" : "eager"));
    Class<?> javaTypeClass = resolveClass(javaType);
    Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
    return builderAssistant.buildResultMapping(resultType, property, column, javaTypeClass, jdbcTypeEnum, nestedSelect, nestedResultMap, notNullColumn, columnPrefix, typeHandlerClass, flags, resultSet, foreignColumn, lazy);
  }

  private String processNestedResultMappings(XNode context, List<ResultMapping> resultMappings, Class<?> enclosingType) throws Exception {
    if ("association".equals(context.getName())
        || "collection".equals(context.getName())
        || "case".equals(context.getName())) {
      if (context.getStringAttribute("select") == null) {
        validateCollection(context, enclosingType);
        ResultMap resultMap = resultMapElement(context, resultMappings, enclosingType);
        return resultMap.getId();
      }
    }
    return null;
  }

  protected void validateCollection(XNode context, Class<?> enclosingType) {
    if ("collection".equals(context.getName()) && context.getStringAttribute("resultMap") == null
        && context.getStringAttribute("javaType") == null) {
      MetaClass metaResultType = MetaClass.forClass(enclosingType, configuration.getReflectorFactory());
      String property = context.getStringAttribute("property");
      if (!metaResultType.hasSetter(property)) {
        throw new BuilderException(
          "Ambiguous collection type for property '" + property + "'. You must specify 'javaType' or 'resultMap'.");
      }
    }
  }

  /**
   * 绑定Mapper接口和Mapper.xml
   *例如:BlogMapper.java 和BlogMapper.xml
   *
   */
  private void bindMapperForNamespace() {
    String namespace = builderAssistant.getCurrentNamespace();
    if (namespace != null) {
      //boundType 就是我们的接口的类型,这里就是BlogMapper这个类的class
      //@BlogMapper
      Class<?> boundType = null;
      try {
        boundType = Resources.classForName(namespace);
      } catch (ClassNotFoundException e) {
        //ignore, bound type is not required
      }
      if (boundType != null) {
        //判断 MapperRegistry 是否已经存在
        //最终都放在  Map<Class<?>, MapperProxyFactory<?>> knownMappers = new HashMap<>();中
        if (!configuration.hasMapper(boundType)) {
          // Spring may not know the real resource name so we set a flag
          // to prevent loading again this resource from the mapper interface
          // look at MapperAnnotationBuilder#loadXmlResource
          configuration.addLoadedResource("namespace:" + namespace);
          configuration.addMapper(boundType);
        }
      }
    }
  }

}
