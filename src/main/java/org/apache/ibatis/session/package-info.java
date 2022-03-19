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
/**
 * Base package. Contains the SqlSession.
 *
 *
 * 接口层：对外提供数据库增删改查操作的API入口
 *
 * 首先接口层是我们打交道最多的。核心对象是 SqlSession，它是上层应用和 MyBatis 打交道的桥梁，
 * SqlSession 上定义了非常多的对数据库的操作方法。接口层在接收到调 用请求的时候，
 * 会调用核心处理层的相应模块来完成具体的数据库操作。
 *
 */
package org.apache.ibatis.session;
