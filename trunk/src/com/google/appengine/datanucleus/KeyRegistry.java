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
package com.google.appengine.datanucleus;

import com.google.appengine.api.datastore.Key;

import org.datanucleus.store.connection.ManagedConnection;
import org.datanucleus.store.ExecutionContext;
import org.datanucleus.store.StoreManager;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A registry mechanism to aid in the identification of parent objects when inserting new (owned) objects.
 *
 * @author Max Ross <maxr@google.com>
 */
public class KeyRegistry {
  /**
   * Convenience accessor for the {@link KeyRegistry} associated with the current datasource connection.
   * @param ec ExecutionContext
   * @return The KeyRegistry
   */
  public static KeyRegistry getKeyRegistry(ExecutionContext ec) {
    StoreManager storeManager = ec.getStoreManager();
    ManagedConnection mconn = storeManager.getConnection(ec);
    return ((EmulatedXAResource) mconn.getXAResource()).getKeyRegistry();
  }

  /**
   * Map of required parent key keyed by the child object.
   * We use an IdentityHashMap here because we want reference equality, not object equality.
   */
  private final Map<Object, Key> parentKeyMap = new IdentityHashMap<Object, Key>();

  /**
   * Method to register the parent key for a child object (when it is known and we are about to persist the child).
   * @param childObj Child object
   * @param parentKey Key of parent
   */
  public void registerParentKeyForOwnedObject(Object childObj, Key parentKey) {
    parentKeyMap.put(childObj, parentKey);
  }

  /**
   * Accessor for the parent key of a child object, if already registered.
   * @param childObj Child object
   * @return parentKey Key of parent (or null if not known).
   */
  public Key getParentKeyForOwnedObject(Object object) {
    return parentKeyMap.get(object);
  }

  public void clearParentKeys() {
    parentKeyMap.clear();
  }

  // TODO Drop these methods. Nonsense to try to get related objects updated which you don't need when you 
  // write your persistence process properly

  /**
   * Set is used to pass messages between child and parent during cascades.
   * The entity uniquely identified by any {@link Key} in this set needs to have its relation fields re-persisted.
   */
  private final Set<Key> modifiedParentSet = new HashSet<Key>();

  void registerModifiedParent(Key key) {
    modifiedParentSet.add(key);
  }

  void clearModifiedParent(Key key) {
    modifiedParentSet.remove(key);
  }

  boolean parentNeedsUpdate(Key key) {
    return modifiedParentSet.contains(key);
  }
}
