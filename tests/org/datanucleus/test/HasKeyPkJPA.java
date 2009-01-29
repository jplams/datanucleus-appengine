// Copyright 2008 Google Inc. All Rights Reserved.
package org.datanucleus.test;

import com.google.appengine.api.datastore.Key;

import org.datanucleus.jpa.annotations.Extension;

import javax.jdo.annotations.Persistent;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

/**
 * @author Max Ross <maxr@google.com>
 */
@Entity
public class HasKeyPkJPA {

  // This doesn't actually work - JPA doesn't support non-pk fields
  // of arbitrary types.
  @Extension(vendorName="datanucleus", key="ancestor-pk", value="true")
  private Key ancestorId;

  @Id
  @GeneratedValue(strategy=GenerationType.IDENTITY)
  private Key id;

  String str;

  public HasKeyPkJPA() {
  }

  public Key getAncestorId() {
    return ancestorId;
  }

  public Key getId() {
    return id;
  }

  public void setAncestorId(Key ancestorId) {
    this.ancestorId = ancestorId;
  }

  public void setId(Key id) {
    this.id = id;
  }

  @Persistent
  public String getStr() {
    return str;
  }

  public void setStr(String str) {
    this.str = str;
  }
}
