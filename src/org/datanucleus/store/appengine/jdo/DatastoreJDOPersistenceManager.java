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
package org.datanucleus.store.appengine.jdo;

import com.google.appengine.api.datastore.Key;

import org.datanucleus.exceptions.NucleusUserException;
import org.datanucleus.jdo.JDOPersistenceManager;
import org.datanucleus.jdo.JDOPersistenceManagerFactory;
import org.datanucleus.store.appengine.EntityUtils;

import javax.jdo.JDOUserException;

/**
 * @author Max Ross <maxr@google.com>
 */
public class DatastoreJDOPersistenceManager extends JDOPersistenceManager {

  public DatastoreJDOPersistenceManager(JDOPersistenceManagerFactory apmf, String userName, String password) {
    super(apmf, userName, password);
  }

  /**
   * We override this method to make it easier for users to lookup objects
   * when they only have the name or the id component of the {@link Key}.
   * Given a {@link Class} and the nname or id a user can always construct a
   * {@link Key}, but that introduces app-engine specific dependencies into
   * the code and we're trying to let users keep things portable.  Furthermore,
   * if the primary key field on the object is a {@link String}, the user,
   * after constructing the {@link Key}, needs to call another app-engine
   * specific method to convert the {@link Key} into a {@link String}.  So
   * in addition to writing code that isn't portable, users have to write a lot
   * of code.  This override determines if the provided key is just an id or
   * name, and if so it constructs an appropriate {@link Key} on the user's
   * behalf and then calls the parent's implementation of this method.
   */
  @Override
  public Object getObjectById(Class cls, Object key) {
    try {
      key = EntityUtils.idToInternalKey(getObjectManager(), cls, key);
    } catch (NucleusUserException e) {
      String keyStr = key == null ? "" : key.toString();
      throw new JDOUserException("Exception converting " + keyStr + " to an internal key.", e);
    }
    return super.getObjectById(cls, key);
  }
}
