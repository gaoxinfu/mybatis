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
package com.gaoxinfu.demo.open.source.mybatis.session;

import com.gaoxinfu.demo.open.source.mybatis.db.mapper.BlogMapper;
import com.gaoxinfu.demo.open.source.mybatis.db.model.Blog;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.IOException;
import java.io.InputStream;

public class SqlSessionForMapperPachageDemo {

  public static void main(String[] args) throws IOException {
    String resource = "mybatis-config-session-mapper-package.xml";
    InputStream inputStream = Resources.getResourceAsStream(resource);
    SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);

    SqlSession session = sqlSessionFactory.openSession(); // ExecutorType.BATCH
    try {
      BlogMapper mapper = session.getMapper(BlogMapper.class);
      Blog blog = mapper.selectByPrimaryKey(1L);
      System.out.println(blog);
    } finally {
      session.close();
    }
  }


  public void testSelect() throws IOException {

  }
}
