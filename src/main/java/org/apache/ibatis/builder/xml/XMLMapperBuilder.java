/**
 * Copyright 2009-2018 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.builder.xml;

import org.apache.ibatis.builder.*;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

import java.io.InputStream;
import java.io.Reader;
import java.util.*;

/**
 * Mapper XML文件解析类
 *
 * @author Clinton Begin
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

    /**
     * Mapper XML文档解析器
     *
     * @param inputStream   XML文件输入流
     * @param configuration MyBatis配置对象
     * @param resource      mapper XML文件的路径
     * @param sqlFragments  SQL片段信息
     * @param namespace     空间的名称
     */
    public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
        this(inputStream, configuration, resource, sqlFragments);
        this.builderAssistant.setCurrentNamespace(namespace);
    }

    /**
     * Mapper XML文档解析器
     *
     * @param inputStream   XML文件输入流
     * @param configuration 配置文件信息
     * @param resource      XML配置路径信息
     * @param sqlFragments  SQL片段信息
     */
    public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
        this(new XPathParser(inputStream, true, configuration.getVariables(), new XMLMapperEntityResolver()),
                configuration, resource, sqlFragments);
    }

    /**
     * Mapper XML文件解析器
     *
     * @param parser        XML解析器
     * @param configuration 配置对象
     * @param resource      资源路径
     * @param sqlFragments  SQL片段信息
     */
    private XMLMapperBuilder(XPathParser parser, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
        super(configuration);
        this.builderAssistant = new MapperBuilderAssistant(configuration, resource);
        // mapper的解析器
        this.parser = parser;
        this.sqlFragments = sqlFragments;
        this.resource = resource;
    }

    /**
     * 执行解析操作
     */
    public void parse() {
        // 判断资源是否被加载
        if (!configuration.isResourceLoaded(resource)) {
            configurationElement(parser.evalNode("/mapper"));
            // 添加已经解析的资源路径或名称
            configuration.addLoadedResource(resource);
            // 绑定Mapper和命名空间之间的关系
            bindMapperForNamespace();
        }

        // 解析未完成的配置信息
        parsePendingResultMaps();
        // 解析未完成的缓存信息
        parsePendingCacheRefs();
        // 解析未完成的表达式信息
        parsePendingStatements();
    }

    public XNode getSqlFragment(String refid) {
        return sqlFragments.get(refid);
    }

    /**
     * 配置元素信息, 该节点为Mapper文件的根节点信息，即&lt;mapper&gt;
     *
     * @param context 配置文件元素信息
     */
    private void configurationElement(XNode context) {
        try {
            // 获取该节点下的namespace命名空间
            String namespace = context.getStringAttribute("namespace");
            if (namespace == null || namespace.equals("")) {
                throw new BuilderException("Mapper's namespace cannot be empty");
            }

            // 设置当前的namespace信息，该处的namespace的值，必须跟Mapper的对象的名称保持一致
            builderAssistant.setCurrentNamespace(namespace);

            // 加载缓存(cache-ref)节点信息
            cacheRefElement(context.evalNode("cache-ref"));

            // 加载cache节点信息
            cacheElement(context.evalNode("cache"));
            /**
             * 加载parameterMap节点
             */
            parameterMapElement(context.evalNodes("/mapper/parameterMap"));
            /**
             * 解析resultMap节点
             */
            resultMapElements(context.evalNodes("/mapper/resultMap"));

            /**
             * 加载所有的sql片段&lt;sql&gt;节点
             */
            sqlElement(context.evalNodes("/mapper/sql"));

            /**
             * 记载所有的select,insert,update,delete操作信息
             */
            buildStatementFromContext(context.evalNodes("select|insert|update|delete"));
        } catch (Exception e) {
            throw new BuilderException("Error parsing Mapper XML. The XML location is '" + resource + "'. Cause: " + e, e);
        }
    }

    /**
     * 解析所有的select, insert, update, delete的节点的语法信息
     *
     * @param list
     */
    private void buildStatementFromContext(List<XNode> list) {
        if (configuration.getDatabaseId() != null) {
            buildStatementFromContext(list, configuration.getDatabaseId());
        }
        buildStatementFromContext(list, null);
    }

    /**
     * 构建所有的sql执行语句
     * @param list 节点列表
     * @param requiredDatabaseId 数据库ID
     */
    private void buildStatementFromContext(List<XNode> list, String requiredDatabaseId) {
        for (XNode context : list) {
            final XMLStatementBuilder statementParser = new XMLStatementBuilder(configuration, builderAssistant, context, requiredDatabaseId);
            try {
                statementParser.parseStatementNode();
            } catch (IncompleteElementException e) {
                // 如果解析发生异常, 则将当前节点放入到未完成节点之中
                configuration.addIncompleteStatement(statementParser);
            }
        }
    }

    /**
     * 解析未完成的ResultMap的配置信息
     */
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

    /**
     * 解析为完成的缓存信息
     */
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

    /**
     * 解析未完成的表达式信息
     */
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

    /**
     * 加载缓存节点信息
     *
     * @param context cache-ref 节点信息
     */
    private void cacheRefElement(XNode context) {
        if (context != null) {
            // 向Configuration添加缓存信息，Key值采用的当前的命名空间(namespace), 内容为namespace属性
            configuration.addCacheRef(builderAssistant.getCurrentNamespace(), context.getStringAttribute("namespace"));

            // 缓存cache-ref的实际解析器
            CacheRefResolver cacheRefResolver = new CacheRefResolver(builderAssistant, context.getStringAttribute("namespace"));
            try {
                cacheRefResolver.resolveCacheRef();
            } catch (IncompleteElementException e) {
                // 当依赖的namespace不存在缓存时，则将该缓存放入Configuration配置文件中
                // 依赖的cache不存在，主要是因为在Configuration中为找到对应的Cache的缓存对象，
                // 因此将当前的放入到未完成的Cache-Ref当中，方便后面使用
                configuration.addIncompleteCacheRef(cacheRefResolver);
            }
        }
    }

    /**
     * 加载cache节点信息, 对于MyBatis而言，cache默认为关闭状态，需要手工开启。除开默认Session中的缓存之外.
     * <p>
     * 当前的所有的cache项会最终配置到{@link Configuration#caches}之中, 然而在添加到{@link Configuration}
     * 之中的时候，会获取{@link Cache#getId()}, 通过该出我们会发现，该属性默认存储了{@link MapperBuilderAssistant#currentNamespace}
     * 字段进行设置.
     * </p>
     *
     * @param context cache节点封装对象
     * @throws Exception
     */
    private void cacheElement(XNode context) throws Exception {
        if (context != null) {
            // 获取type属性, 该属性主要用于自定义Cache, 必须继承自Cache类
            // 默认的Cache对象为PerpetualCache类型，采用Map实现
            String type = context.getStringAttribute("type", "PERPETUAL");
            Class<? extends Cache> typeClass = typeAliasRegistry.resolveAlias(type);

            /**
             * 获取eviction属性，该属性决定了缓存中的对象数量被删除的机制
             * <ul>
             *     <li>LRU: 最近很少使用, 移除最长时间不被使用的对象</li>
             *     <li>FIFO: 先进先出,按对象进入缓存的顺序来移除他们</li>
             *     <li>SOFT: 软引用,移除基于垃圾回收器状态和软引用股则的对象</li>
             *     <li>WEAK: 弱引用: 更积极地移除基于垃圾收集器状态和弱引用规则的对象</li>
             * </ul>
             */
            String eviction = context.getStringAttribute("eviction", "LRU");
            Class<? extends Cache> evictionClass = typeAliasRegistry.resolveAlias(eviction);

            /**
             * 加载flushInterval属性，单位为毫秒，默认情况不设置，也就是没有刷新问题，
             * 缓存仅仅调用语句时刷新
             */
            Long flushInterval = context.getLongAttribute("flushInterval");

            /**
             * 加载size属性, 用于记录缓存对象数目和你云心管径的可用内存资源数目,  默认值是1024
             */
            Integer size = context.getIntAttribute("size");

            /**
             * 加载readOnly属性，可以被设置为true/false, 只读的缓存会给所有调用者返回混村对象相同实例.因此这些对象不能被修改。
             * 这里提供了很重要的性能优势，可读写的缓存会返回缓存对象拷贝。这会慢一些，但是安全，因此默认是false,
             */
            boolean readWrite = !context.getBooleanAttribute("readOnly", false);

            /**
             * 获取blocking属性
             */
            boolean blocking = context.getBooleanAttribute("blocking", false);

            /**
             * 获取其下的所有的&lt;property&gt;配置
             */
            Properties props = context.getChildrenAsProperties();
            /**
             * 通过{@link MapperBuilderAssistant} 构建新的{@link Cache}对象
             */
            builderAssistant.useNewCache(typeClass, evictionClass, flushInterval, size, readWrite, blocking, props);
        }
    }

    /**
     * 加载parameterMap节点
     *
     * @param list parameterMap节点列表
     * @throws Exception
     */
    private void parameterMapElement(List<XNode> list) throws Exception {
        for (XNode parameterMapNode : list) {
            // 获取parameterMap 的id属性
            String id = parameterMapNode.getStringAttribute("id");
            // 获取parameterMap 类型属性
            String type = parameterMapNode.getStringAttribute("type");
            // 加载type的类型
            Class<?> parameterClass = resolveClass(type);
            // 获取parameter节点列表
            List<XNode> parameterNodes = parameterMapNode.evalNodes("parameter");
            List<ParameterMapping> parameterMappings = new ArrayList<ParameterMapping>();
            // 遍历所有的Parameter节点
            for (XNode parameterNode : parameterNodes) {
                // 获取property属性
                String property = parameterNode.getStringAttribute("property");
                // 获取JAVA类型
                String javaType = parameterNode.getStringAttribute("javaType");
                // 获取jdbc类型
                String jdbcType = parameterNode.getStringAttribute("jdbcType");
                // 获取resultMap类型配置
                String resultMap = parameterNode.getStringAttribute("resultMap");
                // 获取模式
                String mode = parameterNode.getStringAttribute("mode");
                // 获取类型处理器
                String typeHandler = parameterNode.getStringAttribute("typeHandler");
                // 获取数字范围
                Integer numericScale = parameterNode.getIntAttribute("numericScale");
                // 解析mode参数, IN, OUT, INOUT
                ParameterMode modeEnum = resolveParameterMode(mode);
                // 加载并获取解析的类型
                Class<?> javaTypeClass = resolveClass(javaType);
                // 从JdbcType中获取jdbc类型
                JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
                // 获取类型处理器，必须为TypeHandler子类
                @SuppressWarnings("unchecked")
                Class<? extends TypeHandler<?>> typeHandlerClass = (Class<? extends TypeHandler<?>>) resolveClass(typeHandler);
                ParameterMapping parameterMapping = builderAssistant.buildParameterMapping(parameterClass, property, javaTypeClass, jdbcTypeEnum, resultMap, modeEnum, typeHandlerClass, numericScale);
                parameterMappings.add(parameterMapping);
            }
            builderAssistant.addParameterMap(id, parameterClass, parameterMappings);
        }
    }

    /**
     * 解析resultMap节点
     *
     * @param list resultMap节点列表
     * @throws Exception
     */
    private void resultMapElements(List<XNode> list) throws Exception {
        for (XNode resultMapNode : list) {
            try {
                resultMapElement(resultMapNode);
            } catch (IncompleteElementException e) {
                // ignore, it will be retried
            }
        }
    }

    /**
     * 单个节点映射列表
     *
     * @param resultMapNode resultMap节点对象
     * @return {@link ResultMap} resultMap节点对象
     * @throws Exception
     */
    private ResultMap resultMapElement(XNode resultMapNode) throws Exception {
        return resultMapElement(resultMapNode, Collections.<ResultMapping>emptyList());
    }

    /**
     * resultMap节点解析
     *
     * @param resultMapNode            resultMap节点
     * @param additionalResultMappings
     * @return {@link ResultMap} 结果映射对象
     * @throws Exception
     */
    private ResultMap resultMapElement(XNode resultMapNode, List<ResultMapping> additionalResultMappings) throws Exception {
        ErrorContext.instance().activity("processing " + resultMapNode.getValueBasedIdentifier());
        /**
         * 获取该resultMap的id属性，如果id不存在，一次寻找 value/property属性
         */
        String id = resultMapNode.getStringAttribute("id",
                resultMapNode.getValueBasedIdentifier());
        /**
         * 获取该resultMap映射的java类型，通过获取type/ofType/resultType/javaType的殊勋进行确定
         */
        String type = resultMapNode.getStringAttribute("type",
                resultMapNode.getStringAttribute("ofType",
                        resultMapNode.getStringAttribute("resultType",
                                resultMapNode.getStringAttribute("javaType"))));
        /**
         * 获取extends属性
         */
        String extend = resultMapNode.getStringAttribute("extends");
        /**
         * 获取autoMapping属性
         */
        Boolean autoMapping = resultMapNode.getBooleanAttribute("autoMapping");
        Class<?> typeClass = resolveClass(type);

        Discriminator discriminator = null;
        List<ResultMapping> resultMappings = new ArrayList<ResultMapping>();
        resultMappings.addAll(additionalResultMappings);
        /**
         * 获取resultMap节点下的所有子节点
         */
        List<XNode> resultChildren = resultMapNode.getChildren();
        for (XNode resultChild : resultChildren) {
            // 获取并解析constructor节点
            if ("constructor".equals(resultChild.getName())) {
                processConstructorElement(resultChild, typeClass, resultMappings);
            }
            else if ("discriminator".equals(resultChild.getName())) {
                discriminator = processDiscriminatorElement(resultChild, typeClass, resultMappings);
            }
            else {
                List<ResultFlag> flags = new ArrayList<ResultFlag>();
                if ("id".equals(resultChild.getName())) {
                    flags.add(ResultFlag.ID);
                }
                resultMappings.add(buildResultMappingFromContext(resultChild, typeClass, flags));
            }
        }
        ResultMapResolver resultMapResolver = new ResultMapResolver(builderAssistant, id, typeClass, extend, discriminator, resultMappings, autoMapping);
        try {
            return resultMapResolver.resolve();
        } catch (IncompleteElementException e) {
            configuration.addIncompleteResultMap(resultMapResolver);
            throw e;
        }
    }

    /**
     * 执行类型constructor节点
     *
     * @param resultChild    constructor节点对象
     * @param resultType     需要解析的类型
     * @param resultMappings 结果集映射列表
     * @throws Exception
     */
    private void processConstructorElement(XNode resultChild, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {
        List<XNode> argChildren = resultChild.getChildren();
        for (XNode argChild : argChildren) {
            List<ResultFlag> flags = new ArrayList<ResultFlag>();
            flags.add(ResultFlag.CONSTRUCTOR);
            if ("idArg".equals(argChild.getName())) {
                flags.add(ResultFlag.ID);
            }
            resultMappings.add(buildResultMappingFromContext(argChild, resultType, flags));
        }
    }

    private Discriminator processDiscriminatorElement(XNode context, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {
        String column = context.getStringAttribute("column");
        String javaType = context.getStringAttribute("javaType");
        String jdbcType = context.getStringAttribute("jdbcType");
        String typeHandler = context.getStringAttribute("typeHandler");
        Class<?> javaTypeClass = resolveClass(javaType);
        @SuppressWarnings("unchecked")
        Class<? extends TypeHandler<?>> typeHandlerClass = (Class<? extends TypeHandler<?>>) resolveClass(typeHandler);
        JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
        Map<String, String> discriminatorMap = new HashMap<String, String>();
        for (XNode caseChild : context.getChildren()) {
            String value = caseChild.getStringAttribute("value");
            String resultMap = caseChild.getStringAttribute("resultMap", processNestedResultMappings(caseChild, resultMappings));
            discriminatorMap.put(value, resultMap);
        }
        return builderAssistant.buildDiscriminator(resultType, column, javaTypeClass, jdbcTypeEnum, typeHandlerClass, discriminatorMap);
    }

    /**
     * &lt;sql&gt;节点信息
     *
     * @param list sql节点列表
     * @throws Exception
     */
    private void sqlElement(List<XNode> list) throws Exception {
        if (configuration.getDatabaseId() != null) {
            sqlElement(list, configuration.getDatabaseId());
        }
        sqlElement(list, null);
    }

    /**
     * 解析sql节点
     *
     * @param list               sql节点列表
     * @param requiredDatabaseId databaseId配置,该值在configuration节点有体现
     * @throws Exception
     */
    private void sqlElement(List<XNode> list, String requiredDatabaseId) throws Exception {
        for (XNode context : list) {
            // 获取databaseId信息
            String databaseId = context.getStringAttribute("databaseId");
            // 获取该节点的id属性
            String id = context.getStringAttribute("id");
            id = builderAssistant.applyCurrentNamespace(id, false);
            if (databaseIdMatchesCurrent(id, databaseId, requiredDatabaseId)) {
                // 将sql片段节点保存在缓存内存之中
                sqlFragments.put(id, context);
            }
        }
    }

    /**
     * 判断当前的databaseId是否一致. 总之一条规矩: 当在mybatis中的配置了databaseId时，在任何时候都需要保证
     * databaseId的一致性。要么设置为一样，要么都不设置
     *
     * @param id                 id属性, 用于从sql片段中获取已经存在的sql片段节点
     * @param databaseId         数据库id
     * @param requiredDatabaseId 需要的数据库ID
     * @return false - 如果{@code requiredDatabaseId}不为空，并且不与{@code databaseId}相同时
     */
    private boolean databaseIdMatchesCurrent(String id, String databaseId, String requiredDatabaseId) {
        if (requiredDatabaseId != null) {
            if (!requiredDatabaseId.equals(databaseId)) {
                return false;
            }
        }
        else {
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
     * 从resultMap子节点中获取相关配置
     *
     * @param context    resultMapping 配置节点
     * @param resultType 结果类型
     * @param flags      类型
     * @return {@link ResultMapping} 结果映射对象
     * @throws Exception
     */
    private ResultMapping buildResultMappingFromContext(XNode context, Class<?> resultType, List<ResultFlag> flags) throws Exception {
        String property;
        if (flags.contains(ResultFlag.CONSTRUCTOR)) {
            property = context.getStringAttribute("name");
        }
        else {
            property = context.getStringAttribute("property");
        }
        String column = context.getStringAttribute("column");
        String javaType = context.getStringAttribute("javaType");
        String jdbcType = context.getStringAttribute("jdbcType");
        String nestedSelect = context.getStringAttribute("select");
        String nestedResultMap = context.getStringAttribute("resultMap",
                processNestedResultMappings(context, Collections.<ResultMapping>emptyList()));
        String notNullColumn = context.getStringAttribute("notNullColumn");
        String columnPrefix = context.getStringAttribute("columnPrefix");
        String typeHandler = context.getStringAttribute("typeHandler");
        String resultSet = context.getStringAttribute("resultSet");
        String foreignColumn = context.getStringAttribute("foreignColumn");
        boolean lazy = "lazy".equals(context.getStringAttribute("fetchType", configuration.isLazyLoadingEnabled() ? "lazy" : "eager"));
        Class<?> javaTypeClass = resolveClass(javaType);
        @SuppressWarnings("unchecked")
        Class<? extends TypeHandler<?>> typeHandlerClass = (Class<? extends TypeHandler<?>>) resolveClass(typeHandler);
        JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
        return builderAssistant.buildResultMapping(resultType, property, column, javaTypeClass, jdbcTypeEnum, nestedSelect, nestedResultMap, notNullColumn, columnPrefix, typeHandlerClass, flags, resultSet, foreignColumn, lazy);
    }

    private String processNestedResultMappings(XNode context, List<ResultMapping> resultMappings) throws Exception {
        if ("association".equals(context.getName())
                || "collection".equals(context.getName())
                || "case".equals(context.getName())) {
            if (context.getStringAttribute("select") == null) {
                ResultMap resultMap = resultMapElement(context, resultMappings);
                return resultMap.getId();
            }
        }
        return null;
    }

    /**
     * 绑定Mapper对象到命名空间
     */
    private void bindMapperForNamespace() {
        String namespace = builderAssistant.getCurrentNamespace();
        if (namespace != null) {
            Class<?> boundType = null;
            try {
                boundType = Resources.classForName(namespace);
            } catch (ClassNotFoundException e) {
                //ignore, bound type is not required
            }
            if (boundType != null) {
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
