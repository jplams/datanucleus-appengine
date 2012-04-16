/**********************************************************************
Copyright (c) 2009 Google Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
**********************************************************************/
package com.google.appengine.datanucleus.test;

import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;

import org.datanucleus.api.jpa.annotations.Extension;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQuery;

/**
 * @author Max Ross <maxr@google.com>
 */
@Entity(name = "bookalias")
@NamedQuery(name="namedQuery", query = "SELECT from com.google.appengine.datanucleus.test.Book b where title = 'yam'")
public class Book implements Serializable {
  @Id
  @GeneratedValue(strategy=GenerationType.IDENTITY)
  @Extension(vendorName="datanucleus", key="gae.encoded-pk", value="true")
  private String id;
  private String title;
  private String author;
  private String isbn;

  @Column(name="first_published")
  private int firstPublished;

  public Book(String namedKey) {
    this.id = namedKey == null ? null :
              KeyFactory.keyToString(KeyFactory.createKey(Book.class.getSimpleName(), namedKey));
  }

  public void setId(String id) {
    this.id = id;
  }

  public Book() {
    this(null);
  }

  public String getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getAuthor() {
    return author;
  }

  public void setAuthor(String author) {
    this.author = author;
  }

  public String getIsbn() {
    return isbn;
  }

  public void setIsbn(String isbn) {
    this.isbn = isbn;
  }

  public int getFirstPublished() {
    return firstPublished;
  }

  public void setFirstPublished(int firstPublished) {
    this.firstPublished = firstPublished;
  }

  @Override
  public String toString() {
    return "\n\nid: " + id + "\ntitle: " + title + "\nauthor: " + author + "\nisbn: " + isbn
           + "\nfirstPublished: " + firstPublished;
  }

  public static com.google.appengine.api.datastore.Entity newBookEntity(String namedKey,
      String author, String isbn, String title) {
    return newBookEntity(namedKey, author, isbn, title, 2000);
  }

  public static com.google.appengine.api.datastore.Entity newBookEntity(String namedKey,
      String author, String isbn, String title, int firstPublished) {
    return newBookEntity(null, namedKey, author, isbn, title, firstPublished);
  }

  public static com.google.appengine.api.datastore.Entity newBookEntity(Key parentKey,
      String namedKey, String author, String isbn, String title, int firstPublished) {
    com.google.appengine.api.datastore.Entity e;
    String kind = Book.class.getSimpleName();
    if (namedKey != null) {
      if (parentKey != null) {
        e = new com.google.appengine.api.datastore.Entity(kind, namedKey, parentKey);
      } else {
        e = new com.google.appengine.api.datastore.Entity(kind, namedKey);
      }
    } else {
      if (parentKey != null) {
        e = new com.google.appengine.api.datastore.Entity(kind, parentKey);
      } else {
        e = new com.google.appengine.api.datastore.Entity(kind);
      }
    }
    e.setProperty("author", author);
    e.setProperty("isbn", isbn);
    e.setProperty("title", title);
    e.setProperty("first_published", firstPublished);
    return e;
  }

  public static com.google.appengine.api.datastore.Entity newBookEntity(String author, String isbn,
      String title) {
    return newBookEntity(null, null, author, isbn, title, 2000);
  }

  public static com.google.appengine.api.datastore.Entity newBookEntity(Key parent,
      String author, String isbn, String title) {
    return newBookEntity(parent, null, author, isbn, title, 2000);
  }

  public static com.google.appengine.api.datastore.Entity newBookEntity(
      String author, String isbn, String title, int firstPublished) {
    return newBookEntity(null, null, author, isbn, title, firstPublished);
  }

  public static com.google.appengine.api.datastore.Entity newBookEntity(
      Key parent, String author, String isbn, String title, int firstPublished) {
    return newBookEntity(parent, null, author, isbn, title, firstPublished);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Book book = (Book) o;

    if (id != null ? !id.equals(book.id) : book.id != null) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return (id != null ? id.hashCode() : 0);
  }
}
