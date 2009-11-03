/*
 * Copyright (C) 2009 Max Ross.
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
package org.datanucleus.store.appengine;

import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.KeyFactory;

import org.datanucleus.test.UnidirectionalOneToManySubclassesJDO.SubChild;
import org.datanucleus.test.UnidirectionalOneToManySubclassesJDO.SubParentWithSubChild;
import org.datanucleus.test.UnidirectionalOneToManySubclassesJDO.SubParentWithSuperChild;
import org.datanucleus.test.UnidirectionalOneToManySubclassesJDO.SuperChild;
import org.datanucleus.test.UnidirectionalOneToManySubclassesJDO.SuperParentWithSubChild;
import org.datanucleus.test.UnidirectionalOneToManySubclassesJDO.SuperParentWithSuperChild;

/**
 * @author Max Ross <max.ross@gmail.com>
 */
public class JDOUnidirectionalOneToManySubclassTest extends JDOTestCase {

  public void testSubParentWithSubChild() throws EntityNotFoundException {
    // insertion
    SubParentWithSubChild parent = new SubParentWithSubChild();
    parent.setSuperParentString("super parent string");
    parent.setSubParentString("sub parent string");
    SubChild subChild = new SubChild();
    subChild.setAString("a string");
    subChild.setBString("b string");
    parent.getSuperParentSubChildren().add(subChild);

    beginTxn();
    pm.makePersistent(parent);
    commitTxn();
    Entity parentEntity =
        ldth.ds.get(KeyFactory.createKey(kindForClass(parent.getClass()), parent.getId()));
    assertEquals(2, parentEntity.getProperties().size());
    assertEquals("super parent string", parentEntity.getProperty("superParentString"));
    assertEquals("sub parent string", parentEntity.getProperty("subParentString"));

    Entity superParentSubChildEntity = ldth.ds.get(subChild.getId());
    assertEquals(2, superParentSubChildEntity.getProperties().size());
    assertEquals("a string", superParentSubChildEntity.getProperty("aString"));
    assertEquals("b string", superParentSubChildEntity.getProperty("bString"));

    // lookup
    beginTxn();
    parent = pm.getObjectById(parent.getClass(), parent.getId());
    assertEquals("super parent string", parent.getSuperParentString());
    assertEquals("sub parent string", parent.getSubParentString());
    assertEquals(1, parent.getSuperParentSubChildren().size());
    assertEquals(subChild.getId(), parent.getSuperParentSubChildren().get(0).getId());
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
    parent.getSuperParentSuperChildren().add(superChild);

    beginTxn();
    pm.makePersistent(parent);
    commitTxn();
    Entity parentEntity =
        ldth.ds.get(KeyFactory.createKey(kindForClass(parent.getClass()), parent.getId()));
    assertEquals(2, parentEntity.getProperties().size());
    assertEquals("super parent string", parentEntity.getProperty("superParentString"));
    assertEquals("sub parent string", parentEntity.getProperty("subParentString"));

    Entity superParentSuperChildEntity = ldth.ds.get(superChild.getId());
    assertEquals(1, superParentSuperChildEntity.getProperties().size());
    assertEquals("a string", superParentSuperChildEntity.getProperty("aString"));

    // lookup
    beginTxn();
    parent = pm.getObjectById(parent.getClass(), parent.getId());
    assertEquals("super parent string", parent.getSuperParentString());
    assertEquals("sub parent string", parent.getSubParentString());
    assertEquals(1, parent.getSuperParentSuperChildren().size());
    assertEquals(superChild.getId(), parent.getSuperParentSuperChildren().get(0).getId());
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
    parent.getSuperParentSuperChildren().add(superChild);

    beginTxn();
    pm.makePersistent(parent);
    commitTxn();
    Entity parentEntity =
        ldth.ds.get(KeyFactory.createKey(kindForClass(parent.getClass()), parent.getId()));
    assertEquals(1, parentEntity.getProperties().size());
    assertEquals("super parent string", parentEntity.getProperty("superParentString"));
    Entity superParentSuperChildEntity = ldth.ds.get(superChild.getId());
    assertEquals(1, superParentSuperChildEntity.getProperties().size());
    assertEquals("a string", superParentSuperChildEntity.getProperty("aString"));

    // lookup
    beginTxn();
    parent = pm.getObjectById(parent.getClass(), parent.getId());
    assertEquals("super parent string", parent.getSuperParentString());
    assertEquals(1, parent.getSuperParentSuperChildren().size());
    assertEquals(superChild.getId(), parent.getSuperParentSuperChildren().get(0).getId());
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
    parent.getSuperParentSubChildren().add(subChild);

    beginTxn();
    pm.makePersistent(parent);
    commitTxn();
    Entity parentEntity =
        ldth.ds.get(KeyFactory.createKey(kindForClass(parent.getClass()), parent.getId()));
    assertEquals(1, parentEntity.getProperties().size());
    assertEquals("super parent string", parentEntity.getProperty("superParentString"));

    Entity superParentSubChildEntity = ldth.ds.get(subChild.getId());
    assertEquals(2, superParentSubChildEntity.getProperties().size());
    assertEquals("a string", superParentSubChildEntity.getProperty("aString"));
    assertEquals("b string", superParentSubChildEntity.getProperty("bString"));

    // lookup
    beginTxn();
    parent = pm.getObjectById(parent.getClass(), parent.getId());
    assertEquals("super parent string", parent.getSuperParentString());
    assertEquals(1, parent.getSuperParentSubChildren().size());
    assertEquals(subChild.getId(), parent.getSuperParentSubChildren().get(0).getId());
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
}
