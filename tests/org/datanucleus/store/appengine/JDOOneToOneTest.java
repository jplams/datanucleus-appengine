// Copyright 2008 Google Inc. All Rights Reserved.
package org.datanucleus.store.appengine;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Transaction;

import static org.datanucleus.store.appengine.TestUtils.assertKeyParentEquals;
import static org.datanucleus.store.appengine.TestUtils.assertKeyParentNull;
import org.datanucleus.test.Flight;
import org.datanucleus.test.HasKeyPkJDO;
import org.datanucleus.test.HasOneToOneJDO;
import org.datanucleus.test.HasOneToOneParentJDO;
import org.datanucleus.test.HasOneToOneParentKeyPkJDO;
import org.easymock.EasyMock;

import java.util.List;

import javax.jdo.Query;

/**
 * @author Max Ross <maxr@google.com>
 */
public class JDOOneToOneTest extends JDOTestCase {

  public void testInsert_NewParentAndChild() throws EntityNotFoundException {
    Flight f = newFlight();
    HasKeyPkJDO hasKeyPk = new HasKeyPkJDO();
    HasOneToOneParentJDO hasParent = new HasOneToOneParentJDO();
    HasOneToOneParentKeyPkJDO hasParentKeyPk = new HasOneToOneParentKeyPkJDO();

    HasOneToOneJDO pojo = new HasOneToOneJDO();
    pojo.setFlight(f);
    pojo.setHasKeyPK(hasKeyPk);
    pojo.setHasParent(hasParent);
    hasParent.setParent(pojo);
    pojo.setHasParentKeyPK(hasParentKeyPk);
    hasParentKeyPk.setParent(pojo);

    makePersistentInTxn(pojo);

    assertNotNull(f.getId());
    assertNotNull(hasKeyPk.getKey());
    assertNotNull(hasParent.getKey());
    assertNotNull(hasParentKeyPk.getKey());
    assertNotNull(pojo.getId());

    Entity flightEntity = ldth.ds.get(KeyFactory.stringToKey(f.getId()));
    assertNotNull(flightEntity);
    assertEquals("jimmy", flightEntity.getProperty("name"));
    assertEquals("bos", flightEntity.getProperty("origin"));
    assertEquals("mia", flightEntity.getProperty("dest"));
    assertEquals(2L, flightEntity.getProperty("me"));
    assertEquals(3L, flightEntity.getProperty("you"));
    assertEquals(44L, flightEntity.getProperty("flight_number"));
    assertEquals(KeyFactory.stringToKey(f.getId()), flightEntity.getKey());
    assertKeyParentEquals(pojo.getId(), flightEntity, f.getId());

    Entity hasKeyPkEntity = ldth.ds.get(hasKeyPk.getKey());
    assertNotNull(hasKeyPkEntity);
    assertEquals(hasKeyPk.getKey(), hasKeyPkEntity.getKey());
    assertKeyParentEquals(pojo.getId(), hasKeyPkEntity, hasKeyPk.getKey());

    Entity hasParentEntity = ldth.ds.get(KeyFactory.stringToKey(hasParent.getKey()));
    assertNotNull(hasParentEntity);
    assertEquals(KeyFactory.stringToKey(hasParent.getKey()), hasParentEntity.getKey());
    assertKeyParentEquals(pojo.getId(), hasParentEntity, hasParent.getKey());

    Entity hasParentKeyPkEntity = ldth.ds.get(hasParentKeyPk.getKey());
    assertNotNull(hasParentKeyPkEntity);
    assertEquals(hasParentKeyPk.getKey(), hasParentKeyPkEntity.getKey());
    assertKeyParentEquals(pojo.getId(), hasParentKeyPkEntity, hasParentKeyPk.getKey());

    Entity pojoEntity = ldth.ds.get(KeyFactory.stringToKey(pojo.getId()));
    assertNotNull(pojoEntity);
    assertEquals(flightEntity.getKey(), pojoEntity.getProperty("flight_id"));
    assertEquals(hasKeyPkEntity.getKey(), pojoEntity.getProperty("haskeypk_id"));
    assertEquals(hasParentEntity.getKey(), pojoEntity.getProperty("hasparent_id"));
    assertEquals(hasParentKeyPkEntity.getKey(), pojoEntity.getProperty("hasparentkeypk_id"));

    assertCountsInDatastore(1, 1);
  }

  public void testInsert_NewParentExistingChild_Unidirectional() throws EntityNotFoundException {
    Flight f = newFlight();
    HasKeyPkJDO hasKeyPk = new HasKeyPkJDO();
    HasOneToOneParentJDO hasParent = new HasOneToOneParentJDO();
    HasOneToOneParentKeyPkJDO hasParentKeyPk = new HasOneToOneParentKeyPkJDO();

    persistInTxn(f);
    persistInTxn(hasKeyPk);
    persistInTxn(hasParent);
    persistInTxn(hasParentKeyPk);
    assertNotNull(f.getId());
    assertNotNull(hasKeyPk.getKey());
    assertNotNull(hasParent.getKey());
    assertNotNull(hasParentKeyPk.getKey());

    HasOneToOneJDO pojo = new HasOneToOneJDO();
    pojo.setFlight(f);
    pojo.setHasKeyPK(hasKeyPk);

    beginTxn();
    pm.makePersistent(pojo);
    commitTxn();

    assertNotNull(pojo.getId());

    Entity pojoEntity = ldth.ds.get(KeyFactory.stringToKey(pojo.getId()));
    assertNotNull(pojoEntity);
    assertEquals(KeyFactory.stringToKey(f.getId()), pojoEntity.getProperty("flight_id"));
    assertEquals(hasKeyPk.getKey(), pojoEntity.getProperty("haskeypk_id"));
    assertNull(pojoEntity.getProperty("hasparent_id"));
    assertNull(pojoEntity.getProperty("hasparentkeypk_id"));

    Entity flightEntity = ldth.ds.get(KeyFactory.stringToKey(f.getId()));
    assertNotNull(flightEntity);
    assertKeyParentNull(flightEntity, f.getId());

    Entity hasKeyPkEntity = ldth.ds.get(hasKeyPk.getKey());
    assertNotNull(hasKeyPkEntity);
    assertKeyParentNull(hasKeyPkEntity, hasKeyPk.getKey());

    Entity hasParentEntity = ldth.ds.get(KeyFactory.stringToKey(hasParent.getKey()));
    assertNotNull(hasParentEntity);
    assertKeyParentNull(hasParentEntity, hasParent.getKey());

    Entity hasParentKeyPkEntity = ldth.ds.get(hasParentKeyPk.getKey());
    assertNotNull(hasParentKeyPkEntity);
    assertKeyParentNull(hasParentKeyPkEntity, hasParentKeyPk.getKey());

    assertCountsInDatastore(1, 1);
  }

  public void testInsert_NewParentExistingChild_Bidirectional() throws EntityNotFoundException {
    Flight f = newFlight();
    HasKeyPkJDO hasKeyPk = new HasKeyPkJDO();
    HasOneToOneParentJDO hasParent = new HasOneToOneParentJDO();
    HasOneToOneParentKeyPkJDO hasParentKeyPk = new HasOneToOneParentKeyPkJDO();

    persistInTxn(f);
    persistInTxn(hasKeyPk);
    persistInTxn(hasParent);
    persistInTxn(hasParentKeyPk);
    assertNotNull(f.getId());
    assertNotNull(hasKeyPk.getKey());
    assertNotNull(hasParent.getKey());
    assertNotNull(hasParentKeyPk.getKey());

    HasOneToOneJDO pojo = new HasOneToOneJDO();
    pojo.setFlight(f);
    pojo.setHasKeyPK(hasKeyPk);
    pojo.setHasParent(hasParent);
    pojo.setHasParentKeyPK(hasParentKeyPk);

    beginTxn();
    pm.makePersistent(pojo);
    try {
      // this fails because the back ptrs on hasParent and  hasParentKeyPk
      // are automatically set, which makes them dirty, and those objects
      // belong to a different entity group
      commitTxn();
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException iae) {
      // good
    }
  }

  public void testInsert_ExistingParentNewChild() throws EntityNotFoundException {
    HasOneToOneJDO pojo = new HasOneToOneJDO();

    beginTxn();
    pm.makePersistent(pojo);
    assertNotNull(pojo.getId());
    assertNull(pojo.getFlight());
    assertNull(pojo.getHasKeyPK());
    assertNull(pojo.getHasParent());
    assertNull(pojo.getHasParentKeyPK());
    commitTxn();
    
    Entity pojoEntity = ldth.ds.get(KeyFactory.stringToKey(pojo.getId()));
    assertNotNull(pojoEntity);
    assertTrue(pojoEntity.getProperties().keySet().contains("flight_id"));
    assertNull(pojoEntity.getProperty("flight_id"));
    assertTrue(pojoEntity.getProperties().keySet().contains("haskeypk_id"));
    assertNull(pojoEntity.getProperty("haskeypk_id"));
    assertTrue(pojoEntity.getProperties().keySet().contains("hasparent_id"));
    assertNull(pojoEntity.getProperty("hasparent_id"));
    assertTrue(pojoEntity.getProperties().keySet().contains("hasparentkeypk_id"));
    assertNull(pojoEntity.getProperty("hasparentkeypk_id"));

    Flight f = newFlight();
    HasKeyPkJDO hasKeyPk = new HasKeyPkJDO();
    HasOneToOneParentJDO hasParent = new HasOneToOneParentJDO();
    HasOneToOneParentKeyPkJDO hasParentKeyPk = new HasOneToOneParentKeyPkJDO();
    beginTxn();
    pojo.setFlight(f);
    pojo.setHasKeyPK(hasKeyPk);
    pojo.setHasParent(hasParent);
    hasParent.setParent(pojo);
    pojo.setHasParentKeyPK(hasParentKeyPk);
    hasParent.setParent(pojo);
    commitTxn();

    assertNotNull(f.getId());
    assertNotNull(hasKeyPk.getKey());
    assertNotNull(hasParent.getKey());
    assertNotNull(hasParentKeyPk.getKey());
    pojoEntity = ldth.ds.get(KeyFactory.stringToKey(pojo.getId()));
    assertNotNull(pojoEntity);
    assertEquals(KeyFactory.stringToKey(f.getId()), pojoEntity.getProperty("flight_id"));
    assertEquals(hasKeyPk.getKey(), pojoEntity.getProperty("haskeypk_id"));
    assertEquals(KeyFactory.stringToKey(hasParent.getKey()), pojoEntity.getProperty("hasparent_id"));
    assertEquals(hasParentKeyPk.getKey(), pojoEntity.getProperty("hasparentkeypk_id"));

    Entity flightEntity = ldth.ds.get(KeyFactory.stringToKey(f.getId()));
    assertNotNull(flightEntity);
    assertKeyParentEquals(pojo.getId(), flightEntity, f.getId());

    Entity hasKeyPkEntity = ldth.ds.get(hasKeyPk.getKey());
    assertNotNull(hasKeyPkEntity);
    assertKeyParentEquals(pojo.getId(), hasKeyPkEntity, hasKeyPk.getKey());

    Entity hasParentEntity = ldth.ds.get(KeyFactory.stringToKey(hasParent.getKey()));
    assertNotNull(hasParentEntity);
    assertKeyParentEquals(pojo.getId(), hasParentEntity, hasParent.getKey());

    Entity hasParentKeyPkEntity = ldth.ds.get(hasParentKeyPk.getKey());
    assertNotNull(hasParentKeyPkEntity);
    assertKeyParentEquals(pojo.getId(), hasParentKeyPkEntity, hasParentKeyPk.getKey());

    assertCountsInDatastore(1, 1);
  }

  public void testUpdate_UpdateChildWithMerge() throws EntityNotFoundException {
    Flight f = newFlight();
    HasKeyPkJDO hasKeyPk = new HasKeyPkJDO();

    HasOneToOneJDO pojo = new HasOneToOneJDO();
    pojo.setFlight(f);
    pojo.setHasKeyPK(hasKeyPk);

    HasOneToOneParentJDO hasParent = new HasOneToOneParentJDO();
    pojo.setHasParent(hasParent);
    hasParent.setParent(pojo);

    HasOneToOneParentKeyPkJDO hasParentKeyPk = new HasOneToOneParentKeyPkJDO();
    pojo.setHasParentKeyPK(hasParentKeyPk);
    hasParent.setParent(pojo);

    beginTxn();
    pm.makePersistent(pojo);

    assertNotNull(f.getId());
    assertNotNull(hasKeyPk.getKey());
    assertNotNull(hasParent.getKey());
    assertNotNull(hasParentKeyPk.getKey());
    assertNotNull(pojo.getId());
    commitTxn();
    beginTxn();
    f.setOrigin("yam");
    hasKeyPk.setStr("yar");
    hasParent.setStr("yag");
    hasParentKeyPk.setStr("yap");
    commitTxn();

    Entity flightEntity = ldth.ds.get(KeyFactory.stringToKey(f.getId()));
    assertNotNull(flightEntity);
    assertEquals("yam", flightEntity.getProperty("origin"));
    assertKeyParentEquals(pojo.getId(), flightEntity, f.getId());

    Entity hasKeyPkEntity = ldth.ds.get(hasKeyPk.getKey());
    assertNotNull(hasKeyPkEntity);
    assertEquals("yar", hasKeyPkEntity.getProperty("str"));
    assertKeyParentEquals(pojo.getId(), hasKeyPkEntity, hasKeyPk.getKey());

    Entity hasParentEntity = ldth.ds.get(KeyFactory.stringToKey(hasParent.getKey()));
    assertNotNull(hasParentEntity);
    assertEquals("yag", hasParentEntity.getProperty("str"));
    assertKeyParentEquals(pojo.getId(), hasParentEntity, hasParent.getKey());

    Entity hasParentPkEntity = ldth.ds.get(hasParentKeyPk.getKey());
    assertNotNull(hasParentPkEntity);
    assertEquals("yap", hasParentPkEntity.getProperty("str"));
    assertKeyParentEquals(pojo.getId(), hasParentPkEntity, hasParentKeyPk.getKey());

    assertCountsInDatastore(1, 1);
  }

  public void testUpdate_UpdateChild() throws EntityNotFoundException {
    Flight f = newFlight();
    HasKeyPkJDO hasKeyPk = new HasKeyPkJDO();
    HasOneToOneParentJDO hasParent = new HasOneToOneParentJDO();
    HasOneToOneParentKeyPkJDO hasParentKeyPk = new HasOneToOneParentKeyPkJDO();
    HasOneToOneJDO pojo = new HasOneToOneJDO();

    pojo.setFlight(f);
    pojo.setHasKeyPK(hasKeyPk);
    pojo.setHasParent(hasParent);
    hasParent.setParent(pojo);
    pojo.setHasParentKeyPK(hasParentKeyPk);
    hasParent.setParent(pojo);

    beginTxn();
    pm.makePersistent(pojo);

    assertNotNull(f.getId());
    assertNotNull(hasKeyPk.getKey());
    assertNotNull(hasParentKeyPk.getKey());
    assertNotNull(hasParent.getKey());
    assertNotNull(pojo.getId());
    commitTxn();

    beginTxn();
    pojo = pm.getObjectById(HasOneToOneJDO.class, pojo.getId());
    pojo.getFlight().setOrigin("yam");
    pojo.getHasKeyPK().setStr("yar");
    pojo.getHasParent().setStr("yag");
    pojo.getHasParentKeyPK().setStr("yap");
    commitTxn();

    Entity flightEntity = ldth.ds.get(KeyFactory.stringToKey(f.getId()));
    assertNotNull(flightEntity);
    assertEquals("yam", flightEntity.getProperty("origin"));
    assertKeyParentEquals(pojo.getId(), flightEntity, f.getId());

    Entity hasKeyPkEntity = ldth.ds.get(hasKeyPk.getKey());
    assertNotNull(hasKeyPkEntity);
    assertEquals("yar", hasKeyPkEntity.getProperty("str"));
    assertKeyParentEquals(pojo.getId(), hasKeyPkEntity, hasKeyPk.getKey());

    Entity hasParentEntity = ldth.ds.get(KeyFactory.stringToKey(hasParent.getKey()));
    assertNotNull(hasParentEntity);
    assertEquals("yag", hasParentEntity.getProperty("str"));
    assertKeyParentEquals(pojo.getId(), hasParentEntity, hasParent.getKey());

    Entity hasParentKeyPkEntity = ldth.ds.get(hasParentKeyPk.getKey());
    assertNotNull(hasParentKeyPkEntity);
    assertEquals("yap", hasParentKeyPkEntity.getProperty("str"));
    assertKeyParentEquals(pojo.getId(), hasParentKeyPkEntity, hasParentKeyPk.getKey());

    assertCountsInDatastore(1, 1);
  }

  public void testUpdate_NullOutChild() throws EntityNotFoundException {
    Flight f = newFlight();
    HasKeyPkJDO hasKeyPk = new HasKeyPkJDO();
    HasOneToOneParentJDO hasParent = new HasOneToOneParentJDO();
    HasOneToOneParentKeyPkJDO hasParentKeyPk = new HasOneToOneParentKeyPkJDO();

    HasOneToOneJDO pojo = new HasOneToOneJDO();
    pojo.setFlight(f);
    pojo.setHasKeyPK(hasKeyPk);
    pojo.setHasParent(hasParent);
    hasParent.setParent(pojo);
    pojo.setHasParentKeyPK(hasParentKeyPk);
    hasParent.setParent(pojo);

    beginTxn();
    pm.makePersistent(pojo);
    String flightId = f.getId();
    Key hasKeyPkKey = hasKeyPk.getKey();
    String hasParentKey = hasParent.getKey();
    Key hasParentKeyPkKey = hasParentKeyPk.getKey();
    commitTxn();

    beginTxn();
    try {
      pojo.setFlight(null);
      pojo.setHasKeyPK(null);
      pojo.setHasParent(null);
      pojo.setHasParentKeyPK(null);
    } finally {
      commitTxn();
    }

    try {
      ldth.ds.get(KeyFactory.stringToKey(flightId));
      fail("expected enfe");
    } catch (EntityNotFoundException enfe) {
      // good
    }

    try {
      ldth.ds.get(hasKeyPkKey);
      fail("expected enfe");
    } catch (EntityNotFoundException enfe) {
      // good
    }

    try {
      ldth.ds.get(KeyFactory.stringToKey(hasParentKey));
      fail("expected enfe");
    } catch (EntityNotFoundException enfe) {
      // good
    }

    try {
      ldth.ds.get(hasParentKeyPkKey);
      fail("expected enfe");
    } catch (EntityNotFoundException enfe) {
      // good
    }

    Entity pojoEntity = ldth.ds.get(KeyFactory.stringToKey(pojo.getId()));
    assertTrue(pojoEntity.getProperties().keySet().contains("flight_id"));
    assertNull(pojoEntity.getProperty("flight_id"));
    assertTrue(pojoEntity.getProperties().keySet().contains("haskeypk_id"));
    assertNull(pojoEntity.getProperty("haskeypk_id"));
    assertTrue(pojoEntity.getProperties().keySet().contains("hasparent_id"));
    assertNull(pojoEntity.getProperty("hasparent_id"));
    assertTrue(pojoEntity.getProperties().keySet().contains("hasparentkeypk_id"));
    assertNull(pojoEntity.getProperty("hasparentkeypk_id"));

    assertCountsInDatastore(1, 0);
  }

  public void testFind() throws EntityNotFoundException {
    Entity flightEntity = Flight.newFlightEntity("jimmy", "bos", "mia", 5, 4, 33);
    ldth.ds.put(flightEntity);

    Entity hasKeyPkEntity = new Entity(HasKeyPkJDO.class.getSimpleName());
    hasKeyPkEntity.setProperty("str", "yar");
    ldth.ds.put(hasKeyPkEntity);

    Entity hasParentEntity = new Entity(HasOneToOneParentJDO.class.getSimpleName());
    hasParentEntity.setProperty("str", "yap");
    ldth.ds.put(hasParentEntity);

    Entity hasParentKeyPkEntity = new Entity(HasOneToOneParentKeyPkJDO.class.getSimpleName());
    hasParentKeyPkEntity.setProperty("str", "yag");
    ldth.ds.put(hasParentKeyPkEntity);

    Entity pojoEntity = new Entity(HasOneToOneJDO.class.getSimpleName());
    pojoEntity.setProperty("flight_id", flightEntity.getKey());
    pojoEntity.setProperty("haskeypk_id", hasKeyPkEntity.getKey());
    pojoEntity.setProperty("hasparent_id", hasParentEntity.getKey());
    pojoEntity.setProperty("hasparentkeypk_id", hasParentKeyPkEntity.getKey());
    ldth.ds.put(pojoEntity);

    beginTxn();
    HasOneToOneJDO pojo = pm.getObjectById(HasOneToOneJDO.class, KeyFactory.keyToString(pojoEntity.getKey()));
    assertNotNull(pojo);
    assertNotNull(pojo.getFlight());
    assertEquals("bos", pojo.getFlight().getOrigin());
    assertEquals("mia", pojo.getFlight().getDest());
    assertNotNull(pojo.getHasKeyPK());
    assertEquals("yar", pojo.getHasKeyPK().getStr());
    assertNotNull(pojo.getHasParent());
    assertEquals("yap", pojo.getHasParent().getStr());
    assertNotNull(pojo.getHasParentKeyPK());
    assertEquals("yag", pojo.getHasParentKeyPK().getStr());
    commitTxn();
  }

  public void testQuery() {
    Entity flightEntity = Flight.newFlightEntity("jimmy", "bos", "mia", 5, 4, 33);
    ldth.ds.put(flightEntity);

    Entity hasKeyPkEntity = new Entity(HasKeyPkJDO.class.getSimpleName());
    hasKeyPkEntity.setProperty("str", "yar");
    ldth.ds.put(hasKeyPkEntity);

    Entity hasParentEntity = new Entity(HasOneToOneParentJDO.class.getSimpleName());
    hasParentEntity.setProperty("str", "yap");
    ldth.ds.put(hasParentEntity);

    Entity hasParentKeyPkEntity = new Entity(HasOneToOneParentKeyPkJDO.class.getSimpleName());
    hasParentKeyPkEntity.setProperty("str", "yag");
    ldth.ds.put(hasParentKeyPkEntity);

    Entity pojoEntity = new Entity(HasOneToOneJDO.class.getSimpleName());
    pojoEntity.setProperty("flight_id", flightEntity.getKey());
    pojoEntity.setProperty("haskeypk_id", hasKeyPkEntity.getKey());
    pojoEntity.setProperty("hasparent_id", hasParentEntity.getKey());
    pojoEntity.setProperty("hasparentkeypk_id", hasParentKeyPkEntity.getKey());
    ldth.ds.put(pojoEntity);

    Query q = pm.newQuery("select from " + HasOneToOneJDO.class.getName()
        + " where id == key parameters String key");
    beginTxn();
    @SuppressWarnings("unchecked")
    List<HasOneToOneJDO> result =
        (List<HasOneToOneJDO>) q.execute(KeyFactory.keyToString(pojoEntity.getKey()));
    assertEquals(1, result.size());
    HasOneToOneJDO pojo = result.get(0);
    assertNotNull(pojo.getFlight());
    assertEquals("bos", pojo.getFlight().getOrigin());
    assertNotNull(pojo.getHasKeyPK());
    assertEquals("yar", pojo.getHasKeyPK().getStr());
    assertNotNull(pojo.getHasParent());
    assertEquals("yap", pojo.getHasParent().getStr());
    assertNotNull(pojo.getHasParentKeyPK());
    assertEquals("yag", pojo.getHasParentKeyPK().getStr());
    commitTxn();
  }

  public void testChildFetchedLazily() throws Exception {
    tearDown();
    DatastoreService ds = EasyMock.createMock(DatastoreService.class);
    DatastoreService original = DatastoreServiceFactoryInternal.getDatastoreService();
    DatastoreServiceFactoryInternal.setDatastoreService(ds);
    Transaction txn;
    try {
      setUp();

      Entity flightEntity = Flight.newFlightEntity("jimmy", "bos", "mia", 5, 4, 33);
      ldth.ds.put(flightEntity);

      Entity hasKeyPkEntity = new Entity(HasKeyPkJDO.class.getSimpleName());
      hasKeyPkEntity.setProperty("str", "yar");
      ldth.ds.put(hasKeyPkEntity);

      Entity hasParentEntity = new Entity(HasOneToOneParentJDO.class.getSimpleName());
      hasParentEntity.setProperty("str", "yap");
      ldth.ds.put(hasParentEntity);

      Entity hasParentPkEntity = new Entity(HasOneToOneParentKeyPkJDO.class.getSimpleName());
      hasParentPkEntity.setProperty("str", "yag");
      ldth.ds.put(hasParentPkEntity);

      Entity pojoEntity = new Entity(HasOneToOneJDO.class.getSimpleName());
      pojoEntity.setProperty("flight_id", flightEntity.getKey());
      pojoEntity.setProperty("haskeypk_id", hasKeyPkEntity.getKey());
      pojoEntity.setProperty("hasparent_id", hasParentEntity.getKey());
      pojoEntity.setProperty("hasparentkeypk_id", hasParentPkEntity.getKey());
      ldth.ds.put(pojoEntity);

      // the only get we're going to perform is for the pojo
      txn = EasyMock.createMock(Transaction.class);
      txn.commit();
      EasyMock.expectLastCall();
      EasyMock.replay(txn);
      EasyMock.expect(ds.beginTransaction()).andReturn(txn);
      EasyMock.expect(ds.get(txn, pojoEntity.getKey())).andReturn(pojoEntity);
      EasyMock.replay(ds);

      beginTxn();
      HasOneToOneJDO pojo = pm.getObjectById(HasOneToOneJDO.class, KeyFactory.keyToString(pojoEntity.getKey()));
      assertNotNull(pojo);
      pojo.getId();
      commitTxn();
    } finally {
      DatastoreServiceFactoryInternal.setDatastoreService(original);
    }
    EasyMock.verify(ds);
    EasyMock.verify(txn);
  }

  public void testDeleteParentDeletesChild() {
    Entity pojoEntity = new Entity(HasOneToOneJDO.class.getSimpleName());
    ldth.ds.put(pojoEntity);

    Entity flightEntity = new Entity(Flight.class.getSimpleName(), pojoEntity.getKey());
    Flight.addData(flightEntity, "jimmy", "bos", "mia", 5, 4, 33);
    ldth.ds.put(flightEntity);

    Entity hasKeyPkEntity = new Entity(HasKeyPkJDO.class.getSimpleName(), pojoEntity.getKey());
    hasKeyPkEntity.setProperty("str", "yar");
    ldth.ds.put(hasKeyPkEntity);

    Entity hasParentEntity = new Entity(HasOneToOneParentJDO.class.getSimpleName(), pojoEntity.getKey());
    hasParentEntity.setProperty("str", "yap");
    ldth.ds.put(hasParentEntity);

    Entity hasParentPkEntity = new Entity(HasOneToOneParentKeyPkJDO.class.getSimpleName(), pojoEntity.getKey());
    hasParentPkEntity.setProperty("str", "yag");
    ldth.ds.put(hasParentPkEntity);

    pojoEntity.setProperty("flight_id", flightEntity.getKey());
    pojoEntity.setProperty("haskeypk_id", hasKeyPkEntity.getKey());
    pojoEntity.setProperty("hasparent_id", hasParentEntity.getKey());
    pojoEntity.setProperty("hasparentkeypk_id", hasParentPkEntity.getKey());
    ldth.ds.put(pojoEntity);

    beginTxn();
    HasOneToOneJDO pojo = pm.getObjectById(HasOneToOneJDO.class, KeyFactory.keyToString(pojoEntity.getKey()));
    pm.deletePersistent(pojo);
    commitTxn();
    assertCountsInDatastore(0, 0);
  }

  private Flight newFlight() {
    Flight flight = new Flight();
    flight.setName("jimmy");
    flight.setOrigin("bos");
    flight.setDest("mia");
    flight.setMe(2);
    flight.setYou(3);
    flight.setFlightNumber(44);
    return flight;
  }

  private int countForClass(Class<?> clazz) {
    return ldth.ds.prepare(
        new com.google.appengine.api.datastore.Query(clazz.getSimpleName())).countEntities();
  }

  private void assertCountsInDatastore(int expectedParent, int expectedChildren) {
    assertEquals(expectedParent, countForClass(HasOneToOneJDO.class));
    assertEquals(expectedChildren, countForClass(Flight.class));
    assertEquals(expectedChildren, countForClass(HasKeyPkJDO.class));
    assertEquals(expectedChildren, countForClass(HasOneToOneParentJDO.class));
    assertEquals(expectedChildren, countForClass(HasOneToOneParentKeyPkJDO.class));
  }

  private void persistInTxn(Object obj) {
    beginTxn();
    pm.makePersistent(obj);
    commitTxn();
  }

}
