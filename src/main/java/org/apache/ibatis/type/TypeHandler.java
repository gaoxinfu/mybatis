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
package org.apache.ibatis.type;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 *
 * 由于 Java 类型和数据库的 JDBC 类型不是一一对应的（比如 String 与 varchar），
 * 所以我们把 Java 对象转换为数据库的值，和把数据库的值转换成 Java 对象，需要经过 一定的转换，
 * 这两个方向的转换就要用到 TypeHandler。
 *
 * 类型处理器
 * MyBatis 在设置预处理语句（PreparedStatement）中的参数或从结果集中取出一个值时，
 * 都会用类型处理器将获取到的值以合适的方式转换成 Java类型。下表描述了一些默认的类型处理器。
 *
 * 简单讲：实现java类型与数据库类型的转换
 *
 * 对TypeHandler相关具体实现子类的管理TypeHandlerRegistry管理
 * @see TypeHandlerRegistry
 * @author Clinton Begin
 */
public interface TypeHandler<T> {

  /**
   * 从 Java 类型到 JDBC 类型：查询数据库之前的操作：设置参数
   * @param ps
   * @param i
   * @param parameter
   * @param jdbcType
   * @throws SQLException
   */
  void setParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException;

  /**
   * 根据列明获取结果
   * @param rs
   * @param columnName
   * @return
   * @throws SQLException
   */
  T getResult(ResultSet rs, String columnName) throws SQLException;

  /**
   * 根据列的下标index获取结果
   * @param rs
   * @param columnIndex
   * @return
   * @throws SQLException
   */
  T getResult(ResultSet rs, int columnIndex) throws SQLException;

  /**
   *  支持调用存储过程,提供了对输出和输入/输出参数(INOUT)的支持; 通过下标获取
   * @param cs
   * @param columnIndex
   * @return
   * @throws SQLException
   */
  T getResult(CallableStatement cs, int columnIndex) throws SQLException;

}
