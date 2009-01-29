// Copyright 2008 Google Inc. All Rights Reserved.
package org.datanucleus.store.appengine;

import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;

import org.datanucleus.test.Flight;
import org.datanucleus.test.HasVersionWithFieldJDO;

import javax.jdo.JDOHelper;
import javax.jdo.JDOOptimisticVerificationException;

/**
 * @author Erick Armbrust <earmbrust@google.com>
 */
public class JDOUpdateTest extends JDOTestCase {

  private static final String DEFAULT_VERSION_PROPERTY_NAME = "OPT_VERSION";

  public void testSimpleUpdate() throws EntityNotFoundException {
    Key key = ldth.ds.put(Flight.newFlightEntity("1", "yam", "bam", 1, 2));

    String keyStr = KeyFactory.encodeKey(key);
    beginTxn();
    Flight flight = pm.getObjectById(Flight.class, keyStr);

    assertEquals(keyStr, flight.getId());
    assertEquals("yam", flight.getOrigin());
    assertEquals("bam", flight.getDest());
    assertEquals("1", flight.getName());
    assertEquals(1, flight.getYou());
    assertEquals(2, flight.getMe());

    flight.setName("2");
    commitTxn();

    Entity flightCheck = ldth.ds.get(key);
    assertEquals("yam", flightCheck.getProperty("origin"));
    assertEquals("bam", flightCheck.getProperty("dest"));
    assertEquals("2", flightCheck.getProperty("name"));
    assertEquals(1L, flightCheck.getProperty("you"));
    assertEquals(2L, flightCheck.getProperty("me"));
    // verify that the version got bumped
    assertEquals(2L,
        flightCheck.getProperty(DEFAULT_VERSION_PROPERTY_NAME));
  }

  public void testSimpleUpdateWithNamedKey() throws EntityNotFoundException {
    Key key = ldth.ds.put(Flight.newFlightEntity("named key", "1", "yam", "bam", 1, 2));

    String keyStr = KeyFactory.encodeKey(key);
    beginTxn();
    Flight flight = pm.getObjectById(Flight.class, keyStr);

    assertEquals(keyStr, flight.getId());
    assertEquals("yam", flight.getOrigin());
    assertEquals("bam", flight.getDest());
    assertEquals("1", flight.getName());
    assertEquals(1, flight.getYou());
    assertEquals(2, flight.getMe());

    flight.setName("2");
    commitTxn();

    Entity flightCheck = ldth.ds.get(key);
    assertEquals("yam", flightCheck.getProperty("origin"));
    assertEquals("bam", flightCheck.getProperty("dest"));
    assertEquals("2", flightCheck.getProperty("name"));
    assertEquals(1L, flightCheck.getProperty("you"));
    assertEquals(2L, flightCheck.getProperty("me"));
    // verify that the version got bumped
    assertEquals(2L,
        flightCheck.getProperty(DEFAULT_VERSION_PROPERTY_NAME));
    assertEquals("named key", flightCheck.getKey().getName());
  }

  public void testUpdateId()
      throws EntityNotFoundException {
    Key key = ldth.ds.put(Flight.newFlightEntity("named key", "1", "yam", "bam", 1, 2));

    String keyStr = KeyFactory.encodeKey(key);
    beginTxn();
    Flight flight = pm.getObjectById(Flight.class, keyStr);

    assertEquals(keyStr, flight.getId());
    assertEquals("yam", flight.getOrigin());
    assertEquals("bam", flight.getDest());
    assertEquals("1", flight.getName());
    assertEquals(1, flight.getYou());
    assertEquals(2, flight.getMe());

    flight.setName("2");
    flight.setId("foo");
    commitTxn();

    Entity flightCheck = ldth.ds.get(key);
    assertEquals("yam", flightCheck.getProperty("origin"));
    assertEquals("bam", flightCheck.getProperty("dest"));
    assertEquals("2", flightCheck.getProperty("name"));
    assertEquals(1L, flightCheck.getProperty("you"));
    assertEquals(2L, flightCheck.getProperty("me"));
    // verify that the version got bumped
    assertEquals(2L,
        flightCheck.getProperty(DEFAULT_VERSION_PROPERTY_NAME));
    assertEquals("named key", flightCheck.getKey().getName());
  }

  public void testOptimisticLocking_Update_NoField() {
    Entity flightEntity = Flight.newFlightEntity("1", "yam", "bam", 1, 2);
    Key key = ldth.ds.put(flightEntity);

    String keyStr = KeyFactory.encodeKey(key);
    beginTxn();
    Flight flight = pm.getObjectById(Flight.class, keyStr);

    flight.setName("2");
    flightEntity.setProperty(DEFAULT_VERSION_PROPERTY_NAME, 2L);
    // we update the flight directly in the datastore right before commit
    ldth.ds.put(flightEntity);
    try {
      commitTxn();
      fail("expected optimistic exception");
    } catch (JDOOptimisticVerificationException jove) {
      // good
    }
  }

  public void testOptimisticLocking_Delete_NoField() {
    Entity flightEntity = Flight.newFlightEntity("1", "yam", "bam", 1, 2);
    Key key = ldth.ds.put(flightEntity);

    String keyStr = KeyFactory.encodeKey(key);
    beginTxn();
    Flight flight = pm.getObjectById(Flight.class, keyStr);

    flight.setName("2");
    flightEntity.setProperty(DEFAULT_VERSION_PROPERTY_NAME, 2L);
    // we remove the flight from the datastore right before commit
    ldth.ds.delete(key);
    try {
      commitTxn();
      fail("expected optimistic exception");
    } catch (JDOOptimisticVerificationException jove) {
      // good
    }
  }

  public void testOptimisticLocking_Update_HasVersionField() {
    Entity entity = new Entity(HasVersionWithFieldJDO.class.getSimpleName());
    entity.setProperty(DEFAULT_VERSION_PROPERTY_NAME, 1L);
    Key key = ldth.ds.put(entity);

    String keyStr = KeyFactory.encodeKey(key);
    beginTxn();
    HasVersionWithFieldJDO hvwf = pm.getObjectById(HasVersionWithFieldJDO.class, keyStr);

    hvwf.setValue("value");
    commitTxn();
    beginTxn();
    hvwf = pm.getObjectById(HasVersionWithFieldJDO.class, keyStr);
    assertEquals(2L, hvwf.getVersion());
    // make sure the version gets bumped
    entity.setProperty(DEFAULT_VERSION_PROPERTY_NAME, 3L);

    hvwf.setValue("another value");
    // we update the entity directly in the datastore right before commit
    entity.setProperty(DEFAULT_VERSION_PROPERTY_NAME, 7L);
    ldth.ds.put(entity);
    try {
      commitTxn();
      fail("expected optimistic exception");
    } catch (JDOOptimisticVerificationException jove) {
      // good
    }
    // make sure the version didn't change on the model object
    assertEquals(2L, JDOHelper.getVersion(hvwf));
  }

  public void testOptimisticLocking_Delete_HasVersionField() {
    Entity entity = new Entity(HasVersionWithFieldJDO.class.getSimpleName());
    entity.setProperty(DEFAULT_VERSION_PROPERTY_NAME, 1L);
    Key key = ldth.ds.put(entity);

    String keyStr = KeyFactory.encodeKey(key);
    beginTxn();
    HasVersionWithFieldJDO hvwf = pm.getObjectById(HasVersionWithFieldJDO.class, keyStr);

    // delete the entity in the datastore right before we commit
    ldth.ds.delete(key);
    hvwf.setValue("value");
    try {
      commitTxn();
      fail("expected optimistic exception");
    } catch (JDOOptimisticVerificationException jove) {
      // good
    }
    // make sure the version didn't change on the model object
    assertEquals(1L, JDOHelper.getVersion(hvwf));
  }
}
