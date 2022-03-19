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
package com.gaoxinfu.demo.open.source.mybatis.reflection;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;


public class ParamNameResolverDemo {

  @Test
  public void paramNameResolverDemo() throws NoSuchMethodException, NoSuchFieldException, IllegalAccessException {
    Configuration config = new Configuration();
    config.setUseActualParamName(false);
    Method method = ArticleMapper.class.getMethod("select", Integer.class, String.class, RowBounds.class, Article.class);

    ParamNameResolver resolver = new ParamNameResolver(config, method);
    Field field = resolver.getClass().getDeclaredField("names");
    field.setAccessible(true);
    // 通过反射获取 ParamNameResolver 私有成员变量 names
    Object names = field.get(resolver);

    System.out.println("names: " + names);
  }
}


class ArticleMapper {
  public void select(@Param("id") Integer id, @Param("author") String author, RowBounds rb, Article article) {

  }
}

class Article {
  public final Integer version = 0;
}

