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

import com.gaoxinfu.demo.open.source.mybatis.db.mapper.BlogMapper;
import com.gaoxinfu.demo.open.source.mybatis.db.mapper.BlogSpecMapper;
import com.gaoxinfu.demo.open.source.mybatis.db.model.Blog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 拦截器
 */
public class  Example01PageInterceptorTest {

  public static void main(String[] args) throws IOException {
    String resource = "mybatis-config-example01.xml";
    InputStream inputStream = Resources.getResourceAsStream(resource);
    SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);

    SqlSession session = sqlSessionFactory.openSession(); // ExecutorType.BATCH
    try {
      int start = 0; // offset
      int pageSize = 5; // limit
      RowBounds rb = new RowBounds(start, pageSize);

      //这里注意BlogSpecMapper 是一个代理类MapperProxy<这里注意BlogSpecMapper>
      BlogSpecMapper BlogSpecMapper = session.getMapper(BlogSpecMapper.class);
      //首先代理到MapperProxy<BlogSpecMapper>到invoke方法
      List<Blog> blogList = BlogSpecMapper.selectBlogList(rb);
      for (Blog blog:blogList){
        System.out.println(blog);
      }
    } finally {
      session.close();
    }
  }


  public void testSelect() throws IOException {

  }
}
