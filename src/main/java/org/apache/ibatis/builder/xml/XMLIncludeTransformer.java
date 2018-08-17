/**
 * Copyright 2009-2017 the original author or authors.
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

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.parsing.PropertyParser;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.session.Configuration;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * XML引入转换器
 * <ul>
 * <li>1. 在mapper文件配置中，引入sql片段信息</li>
 * </ul>
 *
 * @author Frank D. Martinez [mnesarco]
 */
public class XMLIncludeTransformer {

    private final Configuration configuration;
    private final MapperBuilderAssistant builderAssistant;

    public XMLIncludeTransformer(Configuration configuration, MapperBuilderAssistant builderAssistant) {
        this.configuration = configuration;
        this.builderAssistant = builderAssistant;
    }

    /**
     * 执行引入操作
     *
     * @param source 需要引入的节点
     */
    public void applyIncludes(Node source) {
        Properties variablesContext = new Properties();
        // 获取mybatis中配置的所有变量
        Properties configurationVariables = configuration.getVariables();
        // 获取一份变量的拷贝信息
        if (configurationVariables != null) {
            variablesContext.putAll(configurationVariables);
        }
        applyIncludes(source, variablesContext, false);
    }

    /**
     * Recursively apply includes through all SQL fragments.
     *
     * @param source           Include node in DOM tree
     * @param variablesContext Current context for static variables with values
     */
    private void applyIncludes(Node source, final Properties variablesContext, boolean included) {

        // 判断节点的名称是否include
        // 因为在进行sql片段引入的时候，会通过<include/>标签来进行
        if (source.getNodeName().equals("include")) {
            // 解析并获取sql片段节点信息
            Node toInclude = findSqlFragment(getStringAttribute(source, "refid"), variablesContext);

            // 获取所有的配置信息, 是通过节点的name与value属性进行配置的
            Properties toIncludeContext = getVariablesContext(source, variablesContext);

            // 解析对应的节点信息
            applyIncludes(toInclude, toIncludeContext, true);
            if (toInclude.getOwnerDocument() != source.getOwnerDocument()) {
                toInclude = source.getOwnerDocument().importNode(toInclude, true);
            }

            // 使用toInclude替换到source的节点信息
            source.getParentNode().replaceChild(toInclude, source);
            while (toInclude.hasChildNodes()) {
                toInclude.getParentNode().insertBefore(toInclude.getFirstChild(), toInclude);
            }
            toInclude.getParentNode().removeChild(toInclude);
        }
        else if (source.getNodeType() == Node.ELEMENT_NODE) {
            if (included && !variablesContext.isEmpty()) {
                // replace variables in attribute values
                NamedNodeMap attributes = source.getAttributes();
                for (int i = 0; i < attributes.getLength(); i++) {
                    Node attr = attributes.item(i);
                    attr.setNodeValue(PropertyParser.parse(attr.getNodeValue(), variablesContext));
                }
            }
            NodeList children = source.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                applyIncludes(children.item(i), variablesContext, included);
            }
        }
        else if (included && source.getNodeType() == Node.TEXT_NODE
                && !variablesContext.isEmpty()) {
            // replace variables in text node
            source.setNodeValue(PropertyParser.parse(source.getNodeValue(), variablesContext));
        }
    }

    /**
     * 查询sql判断节点
     *
     * @param refid     指向的sql片段ID值
     * @param variables 所有的变量信息
     * @return {@link Node} sql节点信息
     */
    private Node findSqlFragment(String refid, Properties variables) {
        // 变量解析操作, 相当于refid是可以使用configuration中的变量列表的
        refid = PropertyParser.parse(refid, variables);
        refid = builderAssistant.applyCurrentNamespace(refid, true);
        try {
            XNode nodeToInclude = configuration.getSqlFragments().get(refid);
            return nodeToInclude.getNode().cloneNode(true);
        } catch (IllegalArgumentException e) {
            throw new IncompleteElementException("Could not find SQL statement to include with refid '" + refid + "'", e);
        }
    }

    /**
     * 获取节点属性值，以String的方式返回
     *
     * @param node 当前操作的节点信息
     * @param name 需要查询的属性名称
     * @return {@link String} 属性值的配置
     */
    private String getStringAttribute(Node node, String name) {
        return node.getAttributes().getNamedItem(name).getNodeValue();
    }

    /**
     * Read placeholders and their values from include node definition.
     *
     * @param node                      Include node instance
     * @param inheritedVariablesContext Current context used for replace variables in new variables values
     * @return variables context from include instance (no inherited values)
     */
    private Properties getVariablesContext(Node node, Properties inheritedVariablesContext) {
        Map<String, String> declaredProperties = null;
        // 获取当前节点下的所有子节点
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                // 获取当前节点的name属性
                String name = getStringAttribute(n, "name");

                // Replace variables inside
                // 进行占位符替换操作
                String value = PropertyParser.parse(getStringAttribute(n, "value"), inheritedVariablesContext);
                if (declaredProperties == null) {
                    declaredProperties = new HashMap<String, String>();
                }
                // 如果当前的配置项中，包含了对应的配置，则直接抛出异常
                if (declaredProperties.put(name, value) != null) {
                    throw new BuilderException("Variable " + name + " defined twice in the same include definition");
                }
            }
        }
        if (declaredProperties == null) {
            return inheritedVariablesContext;
        }
        else {
            // 将当前的节点内的配置添加到Properties配置项中
            Properties newProperties = new Properties();
            newProperties.putAll(inheritedVariablesContext);
            newProperties.putAll(declaredProperties);
            return newProperties;
        }
    }
}
