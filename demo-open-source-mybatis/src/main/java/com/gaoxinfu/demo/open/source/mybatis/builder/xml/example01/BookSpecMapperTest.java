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
package com.gaoxinfu.demo.open.source.mybatis.builder.xml.example01;

import com.gaoxinfu.demo.open.source.mybatis.db.model.Book;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.Test;

import java.io.InputStream;
import java.util.Iterator;
import java.util.List;

public class BookSpecMapperTest {

  /**
   * 准备工作，获得SqlSessionFactoryBean对象
   * @return
   * @throws Exception
   * @throws Exception
   */
  SqlSessionFactory sqlSessionFactory;

  public void init() throws Exception{
    String resource = "mybatis-config-builder-xml-demo.xml";
    InputStream in = Resources.getResourceAsStream(resource);
    sqlSessionFactory = new SqlSessionFactoryBuilder().build(in);
  }

  /**
   * 查询所有Book
   * @throws Exception
   */
  @Test
  public void selectAll() throws Exception{
    init();
    SqlSession session = sqlSessionFactory.openSession();
    List<Book> list = session.selectList("com.gaoxinfu.demo.open.source.mybatis.db.mapper.BookSpecMapper.selectAll");
    session.close();
    for (Iterator iterator = list.iterator(); iterator.hasNext();) {
      Book p = (Book) iterator.next();
      System.out.println(p);
    }
  }

  /**
   * 根据ID查询Book
   * @throws Exception
   */
  @Test
  public void selectBookByID() throws Exception{
    init();
    SqlSession session = sqlSessionFactory.openSession();
    Book book = session.selectOne("com.gaoxinfu.demo.open.source.mybatis.db.mapper.BookSpecMapper.selectBookById",3);
    session.close();
    System.out.println(book);

  }

  /**
   * 根据ID删除Book
   * @throws Exception
   */
  @Test
  public void deleteBookByID() throws Exception{
    init();
    SqlSession session = sqlSessionFactory.openSession();
    int i = session.delete("com.gaoxinfu.demo.open.source.mybatis.db.mapper.BookSpecMapper.deleteBookById",3);
    System.out.println(i);
    //对数据库数据会造成影响的，需要commit
    session.commit();
    session.close();

  }

  /**
   * 保存一个Book
   * @throws Exception
   */
  @Test
  public void saveBook() throws Exception{
    init();
    SqlSession session = sqlSessionFactory.openSession();
    Book b =new Book();
    b.setBookName("BookC");
    b.setBookPrice(15.0);
    int i = session.insert("com.gaoxinfu.demo.open.source.mybatis.db.mapper.BookSpecMapper.saveBook", b);
    System.out.println("插入了"+i+"条数据");
    session.commit();
    session.close();
  }


  /**
   * 根据Id修改一个Book
   * @throws Exception
   */
  @Test
  public void updateBookById() throws Exception{
    init();
    SqlSession session = sqlSessionFactory.openSession();
    Book b =new Book();
    b.setBookName("BookB");
    b.setBookPrice(20.0);
    b.setId(3);
    int i = session.update("my.BookManage.updatePersnById",b);
    System.out.println("修改了"+i+"条数据");
    session.commit();
    session.close();
  }
}

