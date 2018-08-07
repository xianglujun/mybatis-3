/**
 * Copyright 2009-2015 the original author or authors.
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
package org.apache.ibatis.reflection.wrapper;

import java.util.List;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * 对象包装器，用于提供一些公共的操作
 *
 * @author Clinton Begin
 */
public interface ObjectWrapper {

    Object get(PropertyTokenizer prop);

    void set(PropertyTokenizer prop, Object value);

    /**
     * 获取属性
     * @param name 需要获取的属性名称
     * @param useCamelCaseMapping 是否使用驼峰表达式
     * @return {@link String} 属性的名称
     */
    String findProperty(String name, boolean useCamelCaseMapping);

    /**
     * 获取所有的getter名称列表
     * @return {@link String[]} getter名称列表
     */
    String[] getGetterNames();

    /**
     * 获取setter的所有名称列表
     * @return {@link String[]} 所有的setter名称列表
     */
    String[] getSetterNames();

    Class<?> getSetterType(String name);

    Class<?> getGetterType(String name);

    /**
     * 是否包含setter方法
     * @param name 属性名称
     * @return
     */
    boolean hasSetter(String name);

    /**
     * 是否包含了getter方法，
     * @param name 属性名称
     * @return
     */
    boolean hasGetter(String name);

    MetaObject instantiatePropertyValue(String name, PropertyTokenizer prop, ObjectFactory objectFactory);

    /**
     * 判断当前类型是否为集合类型
     * @return
     */
    boolean isCollection();

    void add(Object element);

    <E> void addAll(List<E> element);

}
