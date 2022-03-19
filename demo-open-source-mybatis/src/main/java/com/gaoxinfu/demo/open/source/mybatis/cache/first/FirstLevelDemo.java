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
package com.gaoxinfu.demo.open.source.mybatis.cache.first;

import com.gaoxinfu.demo.open.source.mybatis.db.mapper.BlogMapper;
import com.gaoxinfu.demo.open.source.mybatis.db.model.Blog;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

public class FirstLevelDemo {


  @Test
  public void firstLevelDemoSameSqlSession() throws IOException {
    String resource = "mybatis-config-session-cache-first.xml";
    InputStream inputStream = Resources.getResourceAsStream(resource);
    SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);

    SqlSession session = sqlSessionFactory.openSession(); // ExecutorType.BATCH

    try {
      BlogMapper mapper = session.getMapper(BlogMapper.class);
      Blog blog = mapper.selectByPrimaryKey(1L);
      System.out.println("blog ="+blog.toString());
      Blog blog1 = mapper.selectByPrimaryKey(1L);
      System.out.println("blog1="+blog1);

      BlogMapper mapper1 = session.getMapper(BlogMapper.class);
      Blog blog2 = mapper1.selectByPrimaryKey(1L);
      System.out.println("blog2="+blog2);

    } finally {
      session.close();
    }
  }


  @Test
  public void firstLevelDemoDifferentSqlSession() throws IOException {
    String resource = "mybatis-config-session-cache-first.xml";
    InputStream inputStream = Resources.getResourceAsStream(resource);
    SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);

    SqlSession session = sqlSessionFactory.openSession(); // ExecutorType.BATCH
    SqlSession session1 = sqlSessionFactory.openSession(); // ExecutorType.BATCH

    try {
      BlogMapper mapper = session.getMapper(BlogMapper.class);
      Blog blog = mapper.selectByPrimaryKey(1L);
      System.out.println(blog.toString());

      BlogMapper mapper1 = session1.getMapper(BlogMapper.class);
      Blog blog1 = mapper1.selectByPrimaryKey(1L);
      System.out.println(blog1);
    } finally {
      session.close();
    }
  }

  @Test
  public void firstLevelDemoSameSqlSessionQueryAndUpdate() throws IOException {
    String resource = "mybatis-config-session-cache-first.xml";
    InputStream inputStream = Resources.getResourceAsStream(resource);
    SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);

    SqlSession session = sqlSessionFactory.openSession(); // ExecutorType.BATCH

    try {
      BlogMapper mapper = session.getMapper(BlogMapper.class);
      Blog blog = mapper.selectByPrimaryKey(1L);
      System.out.println("blog ="+blog.toString());

      Blog updateRecord=new Blog();
      updateRecord.setBid(blog.getBid());
      updateRecord.setName("U"+blog.getName());
      mapper.updateByPrimaryKey(updateRecord);
      session.commit();

      Blog blog1 = mapper.selectByPrimaryKey(1L);
      System.out.println("blog1="+blog1);


    } finally {
      session.close();
    }
  }

  @Test
  public void firstLevelDemoDifferentSqlSessionUpdateAndQuery() throws IOException {
    String resource = "mybatis-config-session-cache-first.xml";
    InputStream inputStream = Resources.getResourceAsStream(resource);
    SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);

    SqlSession session = sqlSessionFactory.openSession(); // ExecutorType.BATCH

    try {
      BlogMapper mapper  = session.getMapper(BlogMapper.class);
      Blog blog = mapper.selectByPrimaryKey(1L);
      System.out.println("blog="+blog);

      SqlSession session1 = sqlSessionFactory.openSession(); // ExecutorType.BATCH
      BlogMapper mapper1 = session1.getMapper(BlogMapper.class);
      Blog updateRecord=new Blog();
      updateRecord.setBid(1L);
      updateRecord.setName("frank");
      mapper1.updateByPrimaryKey(updateRecord);
      session1.commit();

      Blog blog1 = mapper.selectByPrimaryKey(1L);
      System.out.println("blog1="+blog1);


    } finally {
      session.close();
    }
  }



}
