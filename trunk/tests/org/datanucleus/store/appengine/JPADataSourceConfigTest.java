package org.datanucleus.store.appengine;

import junit.framework.TestCase;

import org.datanucleus.jpa.EntityManagerFactoryImpl;
import org.datanucleus.jpa.EntityManagerImpl;

import javax.persistence.Persistence;

public class JPADataSourceConfigTest extends TestCase {

  public void testTransactionalEMF() {
    EntityManagerFactoryImpl emf =
        (EntityManagerFactoryImpl) Persistence.createEntityManagerFactory(
            JPATestCase.EntityManagerFactoryName.transactional_ds_non_transactional_ops_not_allowed.name());
    EntityManagerImpl em = (EntityManagerImpl) emf.createEntityManager();
    DatastoreManager storeMgr = (DatastoreManager) em.getObjectManager().getStoreManager();
    assertTrue(storeMgr.connectionFactoryIsTransactional());
    em.close();
    emf.close();
  }

  public void testNonTransactionalEMF() {
    EntityManagerFactoryImpl emf =
        (EntityManagerFactoryImpl) Persistence.createEntityManagerFactory(
            JPATestCase.EntityManagerFactoryName.nontransactional_ds_non_transactional_ops_not_allowed.name());
    EntityManagerImpl em = (EntityManagerImpl) emf.createEntityManager();
    DatastoreManager storeMgr = (DatastoreManager) em.getObjectManager().getStoreManager();
    assertFalse(storeMgr.connectionFactoryIsTransactional());
    em.close();
    emf.close();
  }



}