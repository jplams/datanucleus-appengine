// Copyright 2008 Google Inc. All Rights Reserved.
package org.datanucleus.store.appengine;

import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;

import org.datanucleus.test.Flight;
import org.datanucleus.test.HasKeyAncestorKeyStringPkJDO;
import org.datanucleus.test.HasKeyPkJDO;
import org.datanucleus.test.HasStringAncestorKeyPkJDO;
import org.datanucleus.test.KitchenSink;
import org.datanucleus.test.Person;

import javax.jdo.JDOObjectNotFoundException;
import javax.jdo.JDOUserException;

/**
 * @author Max Ross <maxr@google.com>
 */
public class JDOFetchTest extends JDOTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    beginTxn();
  }

  @Override
  protected void tearDown() throws Exception {
    commitTxn();
    super.tearDown();
  }

  public void testSimpleFetch_Id() {
    Key key = ldth.ds.put(Flight.newFlightEntity("1", "yam", "bam", 1, 2));

    String keyStr = KeyFactory.keyToString(key);
    Flight flight = pm.getObjectById(Flight.class, keyStr);
    assertNotNull(flight);
    assertEquals(keyStr, flight.getId());
    assertEquals("yam", flight.getOrigin());
    assertEquals("bam", flight.getDest());
    assertEquals("1", flight.getName());
    assertEquals(1, flight.getYou());
    assertEquals(2, flight.getMe());
  }

  public void testSimpleFetch_Id_LongIdOnly() {
    Key key = ldth.ds.put(Flight.newFlightEntity("1", "yam", "bam", 1, 2));

    Flight flight = pm.getObjectById(Flight.class, key.getId());
    assertNotNull(flight);
    String keyStr = KeyFactory.keyToString(key);
    assertEquals(keyStr, flight.getId());
    assertEquals("yam", flight.getOrigin());
    assertEquals("bam", flight.getDest());
    assertEquals("1", flight.getName());
    assertEquals(1, flight.getYou());
    assertEquals(2, flight.getMe());
  }

  public void testSimpleFetch_Id_LongIdOnly_NotFound() {
    try {
      pm.getObjectById(Flight.class, -1);
      fail("expected onfe");
    } catch (JDOObjectNotFoundException e) {
      // good
    }
  }

  public void testSimpleFetch_Id_IntIdOnly() {
    Key key = ldth.ds.put(Flight.newFlightEntity("1", "yam", "bam", 1, 2));

    Flight flight = pm.getObjectById(Flight.class, Long.valueOf(key.getId()).intValue());
    assertNotNull(flight);
    String keyStr = KeyFactory.keyToString(key);
    assertEquals(keyStr, flight.getId());
    assertEquals("yam", flight.getOrigin());
    assertEquals("bam", flight.getDest());
    assertEquals("1", flight.getName());
    assertEquals(1, flight.getYou());
    assertEquals(2, flight.getMe());
  }

  public void testSimpleFetch_NamedKey() {
    Key key = ldth.ds.put(Flight.newFlightEntity("named key", "1", "yam", "bam", 1, 2));

    String keyStr = KeyFactory.keyToString(key);
    Flight flight = pm.getObjectById(Flight.class, keyStr);
    assertNotNull(flight);
    assertEquals(keyStr, flight.getId());
    assertEquals("named key", KeyFactory.stringToKey(flight.getId()).getName());
  }

  public void testSimpleFetch_NamedKey_NameOnly() {
    Key key = ldth.ds.put(Flight.newFlightEntity("named key", "1", "yam", "bam", 1, 2));

    Flight flight = pm.getObjectById(Flight.class, "named key");
    assertNotNull(flight);
    assertEquals(KeyFactory.keyToString(key), flight.getId());
    assertEquals("named key", KeyFactory.stringToKey(flight.getId()).getName());
  }

  public void testSimpleFetch_NamedKey_NameOnly_NotFound() {
    try {
      pm.getObjectById(Flight.class, "does not exist");
      fail("expected onfe");
    } catch (JDOObjectNotFoundException e) {
      // good
    }
  }

  public void testFetchNonExistent() {
    Key key = ldth.ds.put(Flight.newFlightEntity("1", "yam", "bam", 1, 2));
    ldth.ds.delete(key);
    String keyStr = KeyFactory.keyToString(key);
    try {
      pm.getObjectById(Flight.class, keyStr);
      fail("expected onfe");
    } catch (JDOObjectNotFoundException onfe) {
      // good
    }
  }

  public void testKitchenSinkFetch() {
    Key key = ldth.ds.put(KitchenSink.newKitchenSinkEntity(null));

    String keyStr = KeyFactory.keyToString(key);
    KitchenSink ks = pm.getObjectById(KitchenSink.class, keyStr);
    assertNotNull(ks);
    assertEquals(keyStr, ks.key);
    assertEquals(KitchenSink.newKitchenSink(ks.key), ks);
  }

  public void testFetchWithKeyPk() {
    Entity e = new Entity(HasKeyPkJDO.class.getSimpleName());
    ldth.ds.put(e);
    HasKeyPkJDO hk = pm.getObjectById(HasKeyPkJDO.class, e.getKey());
    assertNotNull(hk.getKey());
    assertNull(hk.getAncestorKey());
  }

  public void testFetchWithKeyPkAndAncestor() {
    Entity parent = new Entity("yam");
    ldth.ds.put(parent);
    Entity child = new Entity(HasKeyPkJDO.class.getSimpleName(), parent.getKey());
    ldth.ds.put(child);
    HasKeyPkJDO hk = pm.getObjectById(HasKeyPkJDO.class, child.getKey());
    assertNotNull(hk.getKey());
    assertEquals(parent.getKey(), hk.getAncestorKey());
  }

  public void testFetchWithKeyPkAndStringAncestor() {
    Entity parent = new Entity("yam");
    ldth.ds.put(parent);
    Entity child = new Entity(HasStringAncestorKeyPkJDO.class.getSimpleName(), parent.getKey());
    ldth.ds.put(child);
    HasStringAncestorKeyPkJDO hk =
        pm.getObjectById(HasStringAncestorKeyPkJDO.class, child.getKey());
    assertNotNull(hk.getKey());
    assertEquals(parent.getKey(), KeyFactory.stringToKey(hk.getAncestorKey()));
  }

  public void testFetchWithStringPkAndKeyAncestor() {
    Entity parent = new Entity("yam");
    ldth.ds.put(parent);
    Entity child = new Entity(HasKeyAncestorKeyStringPkJDO.class.getSimpleName(), parent.getKey());
    ldth.ds.put(child);
    HasKeyAncestorKeyStringPkJDO hk =
        pm.getObjectById(HasKeyAncestorKeyStringPkJDO.class, KeyFactory.keyToString(child.getKey()));
    assertNotNull(hk.getKey());
    assertEquals(parent.getKey(), hk.getAncestorKey());
  }

  public void testFetchWithWrongIdType_Key() {
    Entity entity = new Entity(HasStringAncestorKeyPkJDO.class.getSimpleName());
    ldth.ds.put(entity);

    // The model object's id is of type Key but we're going to look it up using
    // a string-encoded Key
    try {
      pm.getObjectById(HasStringAncestorKeyPkJDO.class, KeyFactory.keyToString(entity.getKey()));
      fail("Expected JDOUserException");
    } catch (JDOUserException e) {
      // good
    }
  }

  public void testFetchWithWrongIdType_String() {
    Entity entity = new Entity(HasKeyAncestorKeyStringPkJDO.class.getSimpleName());
    ldth.ds.put(entity);

    // The model object's id is of type String but we're going to look it up using
    // a Key
    try {
      pm.getObjectById(HasKeyAncestorKeyStringPkJDO.class, entity.getKey());
      fail("Expected JDOObjectNotFoundException");
    } catch (JDOObjectNotFoundException e) {
      // good
    }
  }

  public void testEmbeddable() {
    Entity e = new Entity(Person.class.getSimpleName());
    e.setProperty("first", "jimmy");
    e.setProperty("last", "jam");
    e.setProperty("anotherFirst", "anotherjimmy");
    e.setProperty("anotherLast", "anotherjam");
    ldth.ds.put(e);
    Person p = pm.getObjectById(Person.class, KeyFactory.keyToString(e.getKey()));
    assertNotNull(p);
    assertNotNull(p.getName());
    assertEquals("jimmy", p.getName().getFirst());
    assertEquals("jam", p.getName().getLast());
    assertNotNull(p.getAnotherName());
    assertEquals("anotherjimmy", p.getAnotherName().getFirst());
    assertEquals("anotherjam", p.getAnotherName().getLast());
  }

  public void testEmbeddableWithNull() {
    Entity e = new Entity(Person.class.getSimpleName());
    e.setProperty("first", "jimmy");
    e.setProperty("last", "jam");
    ldth.ds.put(e);
    Person p = pm.getObjectById(Person.class, KeyFactory.keyToString(e.getKey()));
    assertNotNull(p);
    assertNotNull(p.getName());
    assertEquals("jimmy", p.getName().getFirst());
    assertEquals("jam", p.getName().getLast());
    assertNotNull(p.getAnotherName());
    assertNull(p.getAnotherName().getFirst());
    assertNull(p.getAnotherName().getLast());
  }

  public void testFetchEntityWithMissingProps() {

  }
}
