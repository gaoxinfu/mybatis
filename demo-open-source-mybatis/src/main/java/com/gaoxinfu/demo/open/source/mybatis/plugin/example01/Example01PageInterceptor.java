/**
 *    Copyright 2009-2020 the original author or authors.
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
package com.gaoxinfu.demo.open.source.mybatis.plugin.example01;

import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.lang.reflect.Field;
import java.util.Properties;


@Intercepts({@Signature(type = Executor.class,method = "query",
  args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class Example01PageInterceptor implements Interceptor {

  @Override
  public Object intercept(Invocation invocation) throws Throwable {
    System.out.println("将逻辑分页改为物理分页");
    Object[] args = invocation.getArgs();
    MappedStatement ms = (MappedStatement) args[0]; // MappedStatement
    BoundSql boundSql = ms.getBoundSql(args[1]); // Object parameter
    RowBounds rb = (RowBounds) args[2]; // RowBounds
    // RowBounds为空，无需分页
    if (rb == RowBounds.DEFAULT) {
      return invocation.proceed();
    }

    // 将原 RowBounds 参数设为 RowBounds.DEFAULT，关闭 MyBatis 内置的分页机制
    //args[2] = RowBounds.DEFAULT;

    // 在SQL后加上limit语句
    String sql = boundSql.getSql();
    String limit = String.format("LIMIT %d,%d", rb.getOffset(), rb.getLimit());
    sql = sql + " " + limit;

    // 自定义sqlSource
    SqlSource sqlSource = new StaticSqlSource(ms.getConfiguration(), sql, boundSql.getParameterMappings());

    // 修改原来的sqlSource
    Field field = MappedStatement.class.getDeclaredField("sqlSource");
    field.setAccessible(true);
    field.set(ms, sqlSource);

    // 执行被拦截方法
    return invocation.proceed();
  }

  @Override
  public Object plugin(Object target) {
    return Plugin.wrap(target, this);
  }

  @Override
  public void setProperties(Properties properties) {
    //do nothing
  }
}
