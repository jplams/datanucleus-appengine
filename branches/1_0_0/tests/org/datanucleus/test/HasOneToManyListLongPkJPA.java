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
package org.datanucleus.test;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;

/**
 * @author Max Ross <maxr@google.com>
 */
@Entity
public class HasOneToManyListLongPkJPA {

  @Id
  @GeneratedValue(strategy=GenerationType.IDENTITY)
  private Long id;

  private String val;

  @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
  private List<BidirectionalChildListLongPkJPA> bidirChildren = new ArrayList<BidirectionalChildListLongPkJPA>();

  @OneToMany(cascade = CascadeType.ALL)
  private List<Book> books = new ArrayList<Book>();

  @OneToMany(cascade = CascadeType.ALL)
  private List<HasKeyPkJPA> hasKeyPks = new ArrayList<HasKeyPkJPA>();

  public Long getId() {
    return id;
  }

  public List<BidirectionalChildListLongPkJPA> getBidirChildren() {
    return (List) bidirChildren;
  }

  public void nullBidirChildren() {
    this.bidirChildren = null;
  }

  public List<Book> getBooks() {
    return books;
  }

  public void nullBooks() {
    this.books = null;
  }

  public List<HasKeyPkJPA> getHasKeyPks() {
    return hasKeyPks;
  }

  public void nullHasKeyPks() {
    this.hasKeyPks = null;
  }

  public String getVal() {
    return val;
  }

  public void setVal(String val) {
    this.val = val;
  }
}
