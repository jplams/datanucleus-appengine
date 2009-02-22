// Copyright 2008 Google Inc. All Rights Reserved.
package org.datanucleus.store.appengine;

import org.datanucleus.test.IllegalMappingsJPA.EncodedPkOnNonPrimaryKeyField;
import org.datanucleus.test.IllegalMappingsJPA.EncodedPkOnNonStringPrimaryKeyField;
import org.datanucleus.test.IllegalMappingsJPA.HasLongPkWithStringAncestor;
import org.datanucleus.test.IllegalMappingsJPA.HasMultiplePkIdFields;
import org.datanucleus.test.IllegalMappingsJPA.HasMultiplePkNameFields;
import org.datanucleus.test.IllegalMappingsJPA.HasUnencodedStringPkWithStringAncestor;
import org.datanucleus.test.IllegalMappingsJPA.MultipleAncestors;
import org.datanucleus.test.IllegalMappingsJPA.PkIdOnNonLongField;
import org.datanucleus.test.IllegalMappingsJPA.PkIdWithUnencodedStringPrimaryKey;
import org.datanucleus.test.IllegalMappingsJPA.PkMarkedAsAncestor;
import org.datanucleus.test.IllegalMappingsJPA.PkMarkedAsPkId;
import org.datanucleus.test.IllegalMappingsJPA.PkMarkedAsPkName;
import org.datanucleus.test.IllegalMappingsJPA.PkNameOnNonStringField;
import org.datanucleus.test.IllegalMappingsJPA.PkNameWithUnencodedStringPrimaryKey;

import javax.persistence.PersistenceException;

/**
 * @author Max Ross <maxr@google.com>
 */
public class JPAMetaDataValidatorTest extends JPATestCase {
  public void testStringAncestorPlusNameOnlyPK() {
    HasUnencodedStringPkWithStringAncestor pojo = new HasUnencodedStringPkWithStringAncestor();
    beginTxn();
    em.persist(pojo);
    try {
      commitTxn();
      fail("expected exception");
    } catch (PersistenceException e) {
      // good
      assertTrue(e.getCause() instanceof MetaDataValidator.DatastoreMetaDataException);
      rollbackTxn();
    }
  }

  public void testStringAncestorPlusLongPK() {
    HasLongPkWithStringAncestor pojo = new HasLongPkWithStringAncestor();
    beginTxn();
    em.persist(pojo);
    try {
      commitTxn();
      fail("expected exception");
    } catch (PersistenceException e) {
      // good
      assertTrue(e.getCause() instanceof MetaDataValidator.DatastoreMetaDataException);
      rollbackTxn();
    }
  }

  public void testMultiplePKNameFields() {
    HasMultiplePkNameFields pojo = new HasMultiplePkNameFields();
    beginTxn();
    em.persist(pojo);
    try {
      commitTxn();
      fail("expected exception");
    } catch (PersistenceException e) {
      // good
      assertTrue(e.getCause() instanceof MetaDataValidator.DatastoreMetaDataException);
      rollbackTxn();
    }
  }

  public void testMultiplePKIdFields() {
    HasMultiplePkIdFields pojo = new HasMultiplePkIdFields();
    beginTxn();
    em.persist(pojo);
    try {
      commitTxn();
      fail("expected exception");
    } catch (PersistenceException e) {
      // good
      assertTrue(e.getCause() instanceof MetaDataValidator.DatastoreMetaDataException);
      rollbackTxn();
    }
  }

  public void testMultipleAncestors() {
    MultipleAncestors pojo = new MultipleAncestors();
    beginTxn();
    em.persist(pojo);
    try {
      commitTxn();
      fail("expected exception");
    } catch (PersistenceException e) {
      // good
      assertTrue(e.getCause() instanceof MetaDataValidator.DatastoreMetaDataException);
      rollbackTxn();
    }
  }

  public void testEncodedPkOnNonPrimaryKeyField() {
    EncodedPkOnNonPrimaryKeyField pojo = new EncodedPkOnNonPrimaryKeyField();
    beginTxn();
    em.persist(pojo);
    try {
      commitTxn();
      fail("expected exception");
    } catch (PersistenceException e) {
      // good
      assertTrue(e.getCause() instanceof MetaDataValidator.DatastoreMetaDataException);
      rollbackTxn();
    }
  }

  public void testEncodedPkOnNonStringPrimaryKeyField() {
    EncodedPkOnNonStringPrimaryKeyField pojo = new EncodedPkOnNonStringPrimaryKeyField();
    beginTxn();
    em.persist(pojo);
    try {
      commitTxn();
      fail("expected exception");
    } catch (PersistenceException e) {
      // good
      assertTrue(e.getCause() instanceof MetaDataValidator.DatastoreMetaDataException);
      rollbackTxn();
    }
  }

  public void testPkNameOnNonStringField() {
    PkNameOnNonStringField pojo = new PkNameOnNonStringField();
    beginTxn();
    em.persist(pojo);
    try {
      commitTxn();
      fail("expected exception");
    } catch (PersistenceException e) {
      // good
      assertTrue(e.getCause() instanceof MetaDataValidator.DatastoreMetaDataException);
      rollbackTxn();
    }
  }

  public void testPkIdOnNonLongField() {
    PkIdOnNonLongField pojo = new PkIdOnNonLongField();
    beginTxn();
    em.persist(pojo);
    try {
      commitTxn();
      fail("expected exception");
    } catch (PersistenceException e) {
      // good
      assertTrue(e.getCause() instanceof MetaDataValidator.DatastoreMetaDataException);
      rollbackTxn();
    }
  }

  // Fails due to a datanuc bug.
  public void testPkMarkedAsAncestor() {
    PkMarkedAsAncestor pojo = new PkMarkedAsAncestor();
    beginTxn();
    em.persist(pojo);
    try {
      commitTxn();
      fail("expected exception");
    } catch (PersistenceException e) {
      // good
      assertTrue(e.getCause() instanceof MetaDataValidator.DatastoreMetaDataException);
      rollbackTxn();
    }
  }

  public void testPkMarkedAsPkId() {
    PkMarkedAsPkId pojo = new PkMarkedAsPkId();
    beginTxn();
    em.persist(pojo);
    try {
      commitTxn();
      fail("expected exception");
    } catch (PersistenceException e) {
      // good
      assertTrue(e.getCause() instanceof MetaDataValidator.DatastoreMetaDataException);
      rollbackTxn();
    }
  }

  public void testPkMarkedAsPkName() {
    PkMarkedAsPkName pojo = new PkMarkedAsPkName();
    beginTxn();
    em.persist(pojo);
    try {
      commitTxn();
      fail("expected exception");
    } catch (PersistenceException e) {
      // good
      assertTrue(e.getCause() instanceof MetaDataValidator.DatastoreMetaDataException);
      rollbackTxn();
    }
  }

  public void testPkIdWithUnencodedStringPrimaryKey() {
    PkIdWithUnencodedStringPrimaryKey pojo = new PkIdWithUnencodedStringPrimaryKey();
    beginTxn();
    em.persist(pojo);
    try {
      commitTxn();
      fail("expected exception");
    } catch (PersistenceException e) {
      // good
      assertTrue(e.getCause() instanceof MetaDataValidator.DatastoreMetaDataException);
      rollbackTxn();
    }
  }

  public void testPkNameWithUnencodedStringPrimaryKey() {
    PkNameWithUnencodedStringPrimaryKey pojo = new PkNameWithUnencodedStringPrimaryKey();
    beginTxn();
    em.persist(pojo);
    try {
      commitTxn();
      fail("expected exception");
    } catch (PersistenceException e) {
      // good
      assertTrue(e.getCause() instanceof MetaDataValidator.DatastoreMetaDataException);
      rollbackTxn();
    }
  }
}