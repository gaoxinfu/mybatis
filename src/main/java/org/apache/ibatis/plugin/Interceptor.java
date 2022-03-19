/**
 *    Copyright 2009-2015 the original author or authors.
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
package org.apache.ibatis.plugin;

import java.util.Properties;

/**
 * @author Clinton Begin
 *
 * 拦截器
 */
public interface Interceptor {

  /**
   *  用于覆盖被拦截对象的原有方法
   *  在调用Plugin的invoke()方法时触发
   * @param invocation
   * @return
   * @throws Throwable
   */
  Object intercept(Invocation invocation) throws Throwable;

  /**
   *
   * @descreption 这个方法时给被拦截对象生成一个代理对象 并返回给它
   * @param target 被拦截的对象
   * @return
   */
  Object plugin(Object target);


  /**
   * 设置属性
   * @param properties
   */
  void setProperties(Properties properties);

}
