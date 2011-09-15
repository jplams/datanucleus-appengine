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

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceConfig;
import com.google.appengine.api.datastore.TransactionOptions;

import org.datanucleus.ConnectionFactory;
import org.datanucleus.ManagedConnection;
import org.datanucleus.ManagedConnectionResourceListener;
import org.datanucleus.OMFContext;
import org.datanucleus.ObjectManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.transaction.xa.XAResource;

/**
 * Factory for connections to the datastore.
 *
 * @author Max Ross <maxr@google.com>
 */
public class DatastoreConnectionFactoryImpl implements ConnectionFactory {

  public static final String AUTO_CREATE_TXNS_PROPERTY =
      "datanucleus.appengine.autoCreateDatastoreTxns";

  private final OMFContext omfContext;
  private final boolean isTransactional;

  /**
   * Constructs a connection factory for the datastore.
   * This connection factory either creates connections that are all
   * transactional or connections that are all nontransactional.
   * Transactional connections manage an underlying datastore transaction.
   * Nontransactional connections do not manage an underlying datastore
   * transaction.   The type of connection that this factory provides
   * is controlled via the {@link #AUTO_CREATE_TXNS_PROPERTY} property, which
   * can be specified in jdoconfig.xml (for JDO) or persistence.xml (for JPA).
   * Note that the default value for this property is {@code true}.
   *
   * JDO Example:
   * <pre>
   * <jdoconfig xmlns="http://java.sun.com/xml/ns/jdo/jdoconfig"
   * xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
   * xsi:noNamespaceSchemaLocation="http://java.sun.com/xml/ns/jdo/jdoconfig">
   *
   *  <persistence-manager-factory name="transactional">
   *     <property name="javax.jdo.PersistenceManagerFactoryClass" value="org.datanucleus.jdo.JDOPersistenceManagerFactory"/>
   *     <property name="javax.jdo.option.ConnectionURL" value="appengine"/>
   *  </persistence-manager-factory>
   *
   *  <persistence-manager-factory name="nontransactional">
   *     <property name="javax.jdo.PersistenceManagerFactoryClass" value="org.datanucleus.jdo.JDOPersistenceManagerFactory"/>
   *     <property name="javax.jdo.option.ConnectionURL" value="appengine"/>
   *     <property name="datanucleus.appengine.autoCreateDatastoreTxns" value="false"/>
   *  </persistence-manager-factory>
   *
   * </jdoconfig>
   * </pre>
   *
   * JPA Example:
   * <pre>
   * <persistence-unit name="transactional">
   *     <properties>
   *         <property name="datanucleus.ConnectionURL" value="appengine"/>
   *     </properties>
   * </persistence-unit>
   *
   * <persistence-unit name="nontransactional">
   *     <properties>
   *         <property name="datanucleus.ConnectionURL" value="appengine"/>
   *         <property name="datanucleus.appengine.autoCreateDatastoreTxns" value="false"/>
   *     </properties>
   * </persistence-unit>
   * </pre>
   *
   * @param omfContext The OMFContext
   * @param resourceType The resource type of the connection, either tx or
   * notx.  We ignore this parameter because it isn't a valid indication of
   * whether or not this connection factory creates transactional connections.
   */
  public DatastoreConnectionFactoryImpl(OMFContext omfContext, String resourceType) {
    this.omfContext = omfContext;
    this.isTransactional = omfContext.getPersistenceConfiguration().getBooleanProperty(AUTO_CREATE_TXNS_PROPERTY);
  }

  /**
   * {@inheritDoc}
   */
  public ManagedConnection getConnection(ObjectManager om, Map options) {
    return omfContext.getConnectionManager().allocateConnection(this, om, options);
  }

  /**
   * {@inheritDoc}
   */
  public ManagedConnection createManagedConnection(ObjectManager om, Map transactionOptions) {
    return new DatastoreManagedConnection(newXAResource());
  }

  boolean isTransactional() {
    return isTransactional;
  }

  private XAResource newXAResource() {
    if (isTransactional()) {
      DatastoreManager datastoreManager = (DatastoreManager) omfContext.getStoreManager();
      DatastoreServiceConfig config = datastoreManager.getDefaultDatastoreServiceConfigForWrites();
      TransactionOptions txnOpts = datastoreManager.getDefaultDatastoreTransactionOptions();
      DatastoreService datastoreService = DatastoreServiceFactoryInternal.getDatastoreService(config);
      return new DatastoreXAResource(datastoreService, txnOpts);
    } else {
      return new EmulatedXAResource();
    }
  }

  static class DatastoreManagedConnection implements ManagedConnection {
    private boolean managed = false;
    private boolean locked = false;
    private final List<ManagedConnectionResourceListener> listeners =
        new ArrayList<ManagedConnectionResourceListener>();
    private final XAResource datastoreXAResource;

    DatastoreManagedConnection(XAResource datastoreXAResource) {
      this.datastoreXAResource = datastoreXAResource;
    }

    public Object getConnection() {
      return null;
    }

    public XAResource getXAResource() {
      return datastoreXAResource;
    }

    public void release() {
      if (!managed) {        
        close();
      }
    }

    public void close() {
      // nothing to close
      for (ManagedConnectionResourceListener listener : listeners) {
        listener.managedConnectionPreClose();
      }
      for (ManagedConnectionResourceListener listener : listeners) {
        listener.managedConnectionPostClose();
      }
    }

    public void setManagedResource() {
      managed = true;
    }

    public boolean isLocked() {
      return locked;
    }

    public void lock() {
      locked = true;
    }

    public void unlock() {
      locked = false;
    }

    public void flush() {
      // Nothing to flush
      for (ManagedConnectionResourceListener listener : listeners) {
        listener.managedConnectionFlushed();
      }
    }

    public void addListener(ManagedConnectionResourceListener listener) {
      listeners.add(listener);
    }

    public void removeListener(ManagedConnectionResourceListener listener) {
      listeners.remove(listener);
    }
  }
}
