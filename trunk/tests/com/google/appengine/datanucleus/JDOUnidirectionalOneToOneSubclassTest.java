/*
 * Copyright (C) 2010 Google Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.appengine.datanucleus;

import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.datanucleus.test.UnidirectionalOneToOneSubclassesJDO.SubChild;
import com.google.appengine.datanucleus.test.UnidirectionalOneToOneSubclassesJDO.SubParentWithSubChild;
import com.google.appengine.datanucleus.test.UnidirectionalOneToOneSubclassesJDO.SubParentWithSuperChild;
import com.google.appengine.datanucleus.test.UnidirectionalOneToOneSubclassesJDO.SuperChild;
import com.google.appengine.datanucleus.test.UnidirectionalOneToOneSubclassesJDO.SuperParentWithSubChild;
import com.google.appengine.datanucleus.test.UnidirectionalOneToOneSubclassesJDO.SuperParentWithSuperChild;


/**
 * @author Max Ross <max.ross@gmail.com>
 */
public class JDOUnidirectionalOneToOneSubclassTest extends JDOTestCase {

  public void testSubParentWithSubChild() throws EntityNotFoundException {
    // insertion
    SubParentWithSubChild parent = new SubParentWithSubChild();
    parent.setSuperParentString("super parent string");
    parent.setSubParentString("sub parent string");
    SubChild subChild = new SubChild();
    subChild.setAString("a string");
    subChild.setBString("b string");
    parent.setSuperParentSubChild(subChild);

    beginTxn();
    pm.makePersistent(parent);
    commitTxn();
    Entity parentEntity =
        ds.get(KeyFactory.createKey(kindForClass(parent.getClass()), parent.getId()));
    Entity superParentSubChildEntity = ds.get(subChild.getId());
    assertEquals(3, parentEntity.getProperties().size());
    assertEquals("super parent string", parentEntity.getProperty("superParentString"));
    assertEquals("sub parent string", parentEntity.getProperty("subParentString"));
    assertEquals(superParentSubChildEntity.getKey(), parentEntity.getProperty("subChild_id_OID"));

    assertEquals(2, superParentSubChildEntity.getProperties().size());
    assertEquals("a string", superParentSubChildEntity.getProperty("aString"));
    assertEquals("b string", superParentSubChildEntity.getProperty("bString"));

    // lookup
    beginTxn();
    parent = pm.getObjectById(parent.getClass(), parent.getId());
    assertEquals("super parent string", parent.getSuperParentString());
    assertEquals("sub parent string", parent.getSubParentString());
    assertEquals(subChild.getId(), parent.getSuperParentSubChild().getId());
    commitTxn();

    beginTxn();
    subChild = pm.getObjectById(subChild.getClass(), subChild.getId());
    assertEquals("a string", subChild.getAString());
    assertEquals("b string", subChild.getBString());
    commitTxn();

    // cascade delete
    beginTxn();
    pm.deletePersistent(parent);
    commitTxn();

    assertEquals(0, countForClass(parent.getClass()));
    assertEquals(0, countForClass(subChild.getClass()));
  }

  public void testSubParentWithSuperChild() throws EntityNotFoundException {
    // insertion
    SubParentWithSuperChild parent = new SubParentWithSuperChild();
    parent.setSuperParentString("super parent string");
    parent.setSubParentString("sub parent string");

    SuperChild superChild = new SuperChild();
    superChild.setAString("a string");
    parent.setSuperParentSuperChild(superChild);

    beginTxn();
    pm.makePersistent(parent);
    commitTxn();
    Entity parentEntity =
        ds.get(KeyFactory.createKey(kindForClass(parent.getClass()), parent.getId()));
    Entity superParentSuperChildEntity = ds.get(superChild.getId());
    assertEquals(3, parentEntity.getProperties().size());
    assertEquals("super parent string", parentEntity.getProperty("superParentString"));
    assertEquals("sub parent string", parentEntity.getProperty("subParentString"));
    assertEquals(superParentSuperChildEntity.getKey(), parentEntity.getProperty("superChild_id_OID"));

    assertEquals(1, superParentSuperChildEntity.getProperties().size());
    assertEquals("a string", superParentSuperChildEntity.getProperty("aString"));

    // lookup
    beginTxn();
    parent = pm.getObjectById(parent.getClass(), parent.getId());
    assertEquals("super parent string", parent.getSuperParentString());
    assertEquals("sub parent string", parent.getSubParentString());
    assertEquals(superChild.getId(), parent.getSuperParentSuperChild().getId());
    commitTxn();

    beginTxn();
    superChild = pm.getObjectById(superChild.getClass(), superChild.getId());
    assertEquals("a string", superChild.getAString());
    commitTxn();

    // cascade delete
    beginTxn();
    pm.deletePersistent(parent);
    commitTxn();

    assertEquals(0, countForClass(parent.getClass()));
    assertEquals(0, countForClass(superChild.getClass()));
  }

  public void testSuperParentWithSuperChild() throws EntityNotFoundException {
    // insertion
    SuperParentWithSuperChild parent = new SuperParentWithSuperChild();
    parent.setSuperParentString("super parent string");

    SuperChild superChild = new SuperChild();
    superChild.setAString("a string");
    parent.setSuperParentSuperChild(superChild);

    beginTxn();
    pm.makePersistent(parent);
    commitTxn();
    Entity parentEntity =
        ds.get(KeyFactory.createKey(kindForClass(parent.getClass()), parent.getId()));
    Entity superParentSuperChildEntity = ds.get(superChild.getId());
    assertEquals(2, parentEntity.getProperties().size());
    assertEquals("super parent string", parentEntity.getProperty("superParentString"));
    assertEquals(superParentSuperChildEntity.getKey(), parentEntity.getProperty("superChild_id_OID"));
    assertEquals(1, superParentSuperChildEntity.getProperties().size());
    assertEquals("a string", superParentSuperChildEntity.getProperty("aString"));

    // lookup
    beginTxn();
    parent = pm.getObjectById(parent.getClass(), parent.getId());
    assertEquals("super parent string", parent.getSuperParentString());
    assertEquals(superChild.getId(), parent.getSuperParentSuperChild().getId());
    commitTxn();

    beginTxn();
    superChild = pm.getObjectById(superChild.getClass(), superChild.getId());
    assertEquals("a string", superChild.getAString());
    commitTxn();

    // cascade delete
    beginTxn();
    pm.deletePersistent(parent);
    commitTxn();

    assertEquals(0, countForClass(parent.getClass()));
    assertEquals(0, countForClass(superChild.getClass()));
  }

  public void testSuperParentWithSubChild() throws EntityNotFoundException {
    // insertion
    SuperParentWithSubChild parent = new SuperParentWithSubChild();
    parent.setSuperParentString("super parent string");

    SubChild subChild = new SubChild();
    subChild.setAString("a string");
    subChild.setBString("b string");
    parent.setSuperParentSubChild(subChild);

    beginTxn();
    pm.makePersistent(parent);
    commitTxn();
    Entity parentEntity =
        ds.get(KeyFactory.createKey(kindForClass(parent.getClass()), parent.getId()));
    Entity superParentSubChildEntity = ds.get(subChild.getId());
    assertEquals(2, parentEntity.getProperties().size());
    assertEquals("super parent string", parentEntity.getProperty("superParentString"));
    assertEquals(superParentSubChildEntity.getKey(), parentEntity.getProperty("subChild_id_OID"));

    assertEquals(2, superParentSubChildEntity.getProperties().size());
    assertEquals("a string", superParentSubChildEntity.getProperty("aString"));
    assertEquals("b string", superParentSubChildEntity.getProperty("bString"));

    // lookup
    beginTxn();
    parent = pm.getObjectById(parent.getClass(), parent.getId());
    assertEquals("super parent string", parent.getSuperParentString());
    assertEquals(subChild.getId(), parent.getSuperParentSubChild().getId());
    commitTxn();

    beginTxn();
    subChild = pm.getObjectById(subChild.getClass(), subChild.getId());
    assertEquals("a string", subChild.getAString());
    assertEquals("b string", subChild.getBString());
    commitTxn();

    // cascade delete
    beginTxn();
    pm.deletePersistent(parent);
    commitTxn();

    assertEquals(0, countForClass(parent.getClass()));
    assertEquals(0, countForClass(subChild.getClass()));
  }

  public void testWrongChildType() throws IllegalAccessException, InstantiationException {
    SuperParentWithSuperChild parent = new SuperParentWithSuperChild();
    parent.setSuperParentString("a string");
    // working around more runtime enhancer madness
    Object child = SubChild.class.newInstance();
    parent.setSuperParentSuperChild((SuperChild) child);

    beginTxn();
    try {
      pm.makePersistent(parent);
      fail("expected exception");
    } catch (UnsupportedOperationException uoe) {
      // good
    }
    rollbackTxn();
  }

  public void testWrongChildType_Update() throws IllegalAccessException, InstantiationException {
    SuperParentWithSuperChild parent = new SuperParentWithSuperChild();
    parent.setSuperParentString("a string");
    beginTxn();
    pm.makePersistent(parent);
    commitTxn();

    beginTxn();
    parent = pm.getObjectById(parent.getClass(), parent.getId());
    // working around more runtime enhancer madness
    Object child = SubChild.class.newInstance();
    parent.setSuperParentSuperChild((SuperChild) child);

    try {
      commitTxn();
      fail("expected exception");
    } catch (UnsupportedOperationException uoe) {
      // good
    }
  }

}