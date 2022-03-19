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
package org.apache.ibatis.session;

import java.sql.Connection;

/**
 * Creates an {@link SqlSession} out of a connection or a DataSource
 *
 * @author Clinton Begin
 */

/**
 *
 * SqlSessionFactory 是用来创建 SqlSession 的，每次应用程序访问数据库，都需要
 * 创建一个会话。因为我们一直有创建会话的需要，所以 SqlSessionFactory 应该存在于
 * 应用的整个生命周期中（作用域是应用作用域）。创建 SqlSession 只需要一个实例来做
 * 这件事就行了，否则会产生很多的混乱，和浪费资源。所以我们要采用单例模式。
 *
 * 应用样例 {@link com.gaoxinfu.demo.open.source.mybatis.session.SqlSessionDemo}
 *
 * SqlSessionFactory 单例 全局应用，一个就完了
 *
 */
public interface SqlSessionFactory {

  SqlSession openSession();

  SqlSession openSession(boolean autoCommit);

  SqlSession openSession(Connection connection);

  SqlSession openSession(TransactionIsolationLevel level);

  SqlSession openSession(ExecutorType execType);

  SqlSession openSession(ExecutorType execType, boolean autoCommit);

  SqlSession openSession(ExecutorType execType, TransactionIsolationLevel level);

  SqlSession openSession(ExecutorType execType, Connection connection);

  Configuration getConfiguration();

}
