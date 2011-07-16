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
package com.google.appengine.datanucleus.scostore;

import org.datanucleus.ClassLoaderResolver;
import org.datanucleus.store.ObjectProvider;
import org.datanucleus.store.connection.ManagedConnection;
import org.datanucleus.store.mapped.exceptions.MappedDatastoreException;
import org.datanucleus.store.mapped.scostore.AbstractArrayStore;
import org.datanucleus.store.mapped.scostore.AbstractArrayStoreSpecialization;
import org.datanucleus.store.mapped.scostore.ElementContainerStore;
import org.datanucleus.util.Localiser;

import com.google.appengine.datanucleus.DatastoreManager;

/**
 * Datastore-specific implementation of {@link AbstractArrayStoreSpecialization}.
 *
 * @author Max Ross <maxr@google.com>
 */
public class DatastoreAbstractArrayStoreSpecialization extends DatastoreAbstractCollectionStoreSpecialization
  implements AbstractArrayStoreSpecialization {

  public DatastoreAbstractArrayStoreSpecialization(Localiser localiser, ClassLoaderResolver clr,
      DatastoreManager storeMgr) {
    super(localiser, clr, storeMgr);
  }

  public void clear(ObjectProvider ownerOP, ElementContainerStore ecs) {
    // The purpose of this method is to null out the fk on the child table,
    // but the only the fks the datastore supports (currently) are parent
    // keys that are part of the child key.  There is no way to modify
    // a key once it has been written to the datastore, so nulling out
    // the parent key isn't possible.  We need to treat this method as
    // a no-op because datanucleus persists modifications to arrays by
    // clearing and then rewriting all the fks.
  }

  public int[] internalAdd(ObjectProvider ownerOP, AbstractArrayStore aas, Object element,
      ManagedConnection mconn, boolean batched, int orderId,
      boolean executeNow) throws MappedDatastoreException {
    // TODO(maxr) Figure out when this gets called.
    throw new UnsupportedOperationException();
  }

  public void processBatchedWrites(ManagedConnection mconn) throws MappedDatastoreException {
    throw new UnsupportedOperationException("Google App Engine does not support batched writes.");
  }
}
