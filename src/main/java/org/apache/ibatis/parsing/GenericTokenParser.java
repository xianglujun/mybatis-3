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
package org.apache.ibatis.parsing;

/**
 * 一般占位符解析类，用于处理字符串中的占位符信息
 *
 * @author Clinton Begin
 */
public class GenericTokenParser {

    private final String openToken;
    private final String closeToken;
    private final TokenHandler handler;

    /**
     * 构造器，用于处理占位符的字符串信息
     *
     * @param openToken  占位符开始标志
     * @param closeToken 占位符结束标志
     * @param handler    占位符处理对象
     */
    public GenericTokenParser(String openToken, String closeToken, TokenHandler handler) {
        this.openToken = openToken;
        this.closeToken = closeToken;
        this.handler = handler;
    }

    /**
     * 解析字符串中的占位符信息
     *
     * @param text 需要解析占位符的字符串
     * @return
     */
    public String parse(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        // search open token
        // 搜索字符串中的占位符开始部分，如果没有找到，则认为
        // 该字符串不需要处理占位符，则直接返回
        int start = text.indexOf(openToken, 0);
        if (start == -1) {
            return text;
        }

        char[] src = text.toCharArray();
        int offset = 0;
        final StringBuilder builder = new StringBuilder();
        StringBuilder expression = null;
        while (start > -1) {
            // 对于反斜杠的处理
            if (start > 0 && src[start - 1] == '\\') {
                // this open token is escaped. remove the backslash and continue.
                // 如果当前的占位符被转义，则直接去除反斜杠并继续
                // 该出为什么要-1? 因为我们获取到的openToken开始的位置，并不代表转义符\的位置
                // 因此该地方会出现一个-1的操作
                builder.append(src, offset, start - offset - 1).append(openToken);
                offset = start + openToken.length();
            }
            else {
                // found open token. let's search close token.
                // 找到了占位符的开始部分数据,并且不具备反斜杠转义符的相关操作
                if (expression == null) {
                    expression = new StringBuilder();
                }
                else {
                    expression.setLength(0);
                }

                // 加入从offset开始，长度为start-offset长度的字符串
                builder.append(src, offset, start - offset);
                // 将占位符的位置向后移动
                offset = start + openToken.length();

                // 找到占位符结束部分所在的地方
                int end = text.indexOf(closeToken, offset);

                while (end > -1) {
                    // 判断当前的占位符是否被转义符修饰，如果被转义符修饰，则移除占位符
                    // 并继续下一次占位符结束的操作
                    if (end > offset && src[end - 1] == '\\') {
                        // this close token is escaped. remove the backslash and continue.
                        // 如果占位符结束部分包含了反斜杠转义符, 则需要去除斜杠，并加入到表达式中
                        expression.append(src, offset, end - offset - 1).append(closeToken);
                        offset = end + closeToken.length();

                        // 重新计算下一个结束占位符的位置
                        end = text.indexOf(closeToken, offset);
                    }
                    else {
                        // 如果当前的占位符结束部分没有被占位符修饰，则直接放入到表达式之中
                        expression.append(src, offset, end - offset);
                        offset = end + closeToken.length();
                        break;
                    }
                }

                if (end == -1) {
                    // close token was not found.
                    builder.append(src, start, src.length - start);
                    offset = src.length;
                }
                else {
                    // 如果不是占位符的情况下，则直接通过获取到的expression取获取对应的值
                    // 然后拼接到结果当中
                    builder.append(handler.handleToken(expression.toString()));
                    offset = end + closeToken.length();
                }
            }
            // 从offset开始的占位符开始位置
            start = text.indexOf(openToken, offset);
        }

        // 当占位符搜索工作结束之后，判断是否添加完成所有的字符串
        // 如果没有，则添加完成所有的字符串
        if (offset < src.length) {
            builder.append(src, offset, src.length - offset);
        }
        return builder.toString();
    }
}
