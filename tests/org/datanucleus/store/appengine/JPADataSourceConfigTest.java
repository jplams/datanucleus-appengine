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
package org.datanucleus.store.appengine;

import junit.framework.TestCase;

import org.datanucleus.jpa.EntityManagerImpl;
import org.datanucleus.store.appengine.jpa.DatastoreEntityManagerFactory;

import javax.persistence.Persistence;

/**
 * @author Max Ross <maxr@google.com>
 */
public class JPADataSourceConfigTest extends TestCase {

  public void testTransactionalEMF() {
    DatastoreEntityManagerFactory emf =
        (DatastoreEntityManagerFactory) Persistence.createEntityManagerFactory(
            JPATestCase.EntityManagerFactoryName.transactional_ds_non_transactional_ops_not_allowed.name());
    EntityManagerImpl em = (EntityManagerImpl) emf.createEntityManager();
    DatastoreManager storeMgr = (DatastoreManager) em.getObjectManager().getStoreManager();
    assertTrue(storeMgr.connectionFactoryIsTransactional());
    em.close();
    emf.close();
  }

  public void testNonTransactionalEMF() {
    DatastoreEntityManagerFactory emf =
        (DatastoreEntityManagerFactory) Persistence.createEntityManagerFactory(
            JPATestCase.EntityManagerFactoryName.nontransactional_ds_non_transactional_ops_not_allowed.name());
    EntityManagerImpl em = (EntityManagerImpl) emf.createEntityManager();
    DatastoreManager storeMgr = (DatastoreManager) em.getObjectManager().getStoreManager();
    assertFalse(storeMgr.connectionFactoryIsTransactional());
    em.close();
    emf.close();
  }



}
