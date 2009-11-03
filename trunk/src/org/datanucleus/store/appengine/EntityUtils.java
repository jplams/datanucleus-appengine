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

import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;

import org.datanucleus.ManagedConnection;
import org.datanucleus.ObjectManager;
import org.datanucleus.StateManager;
import org.datanucleus.api.ApiAdapter;
import org.datanucleus.exceptions.NucleusUserException;
import org.datanucleus.metadata.AbstractClassMetaData;
import org.datanucleus.metadata.AbstractMemberMetaData;
import org.datanucleus.metadata.ColumnMetaData;
import org.datanucleus.metadata.MetaDataManager;
import org.datanucleus.metadata.VersionMetaData;
import org.datanucleus.store.appengine.jdo.DatastoreJDOPersistenceManager;
import org.datanucleus.store.mapped.IdentifierFactory;
import org.datanucleus.store.mapped.MappedStoreManager;

/**
 * Utility methods for determining entity property names and kinds.
 *
 * @author Max Ross <maxr@google.com>
 */
public final class EntityUtils {

  public static String getPropertyName(
      IdentifierFactory idFactory, AbstractMemberMetaData ammd) {
    // TODO(maxr): See if there is a better way than field name comparison to
    // determine if this is a version field
    AbstractClassMetaData acmd = ammd.getAbstractClassMetaData();
    if (acmd.hasVersionStrategy() &&
        ammd.getName().equals(acmd.getVersionMetaData().getFieldName())) {
      return getVersionPropertyName(idFactory, acmd.getVersionMetaData());
    }

    // If a column name was explicitly provided, use that as the property name.
    if (ammd.getColumn() != null) {
      return ammd.getColumn();
    }

    // If we're dealing with embeddables, the column name override
    // will show up as part of the column meta data.
    if (ammd.getColumnMetaData() != null && ammd.getColumnMetaData().length > 0) {
      if (ammd.getColumnMetaData().length != 1) {
        // TODO(maxr) throw something more appropriate
        throw new UnsupportedOperationException();
      }
      return ammd.getColumnMetaData()[0].getName();
    }
    // Use the IdentifierFactory to convert from the name of the field into
    // a property name.
    return idFactory.newDatastoreFieldIdentifier(ammd.getName()).getIdentifierName();
  }

  public static Object getVersionFromEntity(
      IdentifierFactory idFactory, VersionMetaData vmd, Entity entity) {
    return entity.getProperty(getVersionPropertyName(idFactory, vmd));
  }

  public static String getVersionPropertyName(
      IdentifierFactory idFactory, VersionMetaData vmd) {
    ColumnMetaData[] columnMetaData = vmd.getColumnMetaData();
    if (columnMetaData == null || columnMetaData.length == 0) {
      return idFactory.newVersionFieldIdentifier().getIdentifierName();
    }
    if (columnMetaData.length != 1) {
      throw new IllegalArgumentException(
          "Please specify 0 or 1 column name for the version property.");
    }
    return columnMetaData[0].getName();
  }

  public static String determineKind(AbstractClassMetaData acmd, ObjectManager om) {
    MappedStoreManager storeMgr = (MappedStoreManager) om.getStoreManager();
    return determineKind(acmd, storeMgr.getIdentifierFactory());
  }

  public static String determineKind(AbstractClassMetaData acmd, IdentifierFactory idFactory) {
    if (acmd.getTable() != null) {
      // User specified a table name as part of the mapping so use that as the
      // kind.
      return acmd.getTable();
    }
    // No table name provided so use the identifier factory to convert the
    // class name into the kind.
    return idFactory.newDatastoreContainerIdentifier(acmd).getIdentifierName();
  }

  /**
   * @see DatastoreJDOPersistenceManager#getObjectById(Class, Object) for
   * an expalnation of how this is useful.
   *
   * Supported translations:
   * When the pk field is a Long you can give us a Long, an encoded key
   * string, or a Key.
   *
   * When the pk field is an unencoded String you can give us an unencoded
   * String, an encoded String, or a Key.
   *
   * When the pk field is an encoded String you can give us an unencoded
   * String, an encoded String, or a Key.
   */
  public static Object idToInternalKey(ObjectManager om, Class<?> cls, Object val, boolean allowSubclasses) {
    AbstractClassMetaData cmd = om.getMetaDataManager().getMetaDataForClass(
        cls, om.getClassLoaderResolver());
    String kind = determineKind(cmd, om);
    AbstractMemberMetaData pkMemberMetaData = getPKMemberMetaData(om, cls);
    return idToInternalKey(kind, pkMemberMetaData, cls, val, om, allowSubclasses);
  }

  // broken out for testing
  static Object idToInternalKey(
      String kind, AbstractMemberMetaData pkMemberMetaData, Class<?> cls, Object val,
      ObjectManager om, boolean allowSubclasses) {
    Object result = null;
    Class<?> pkType = pkMemberMetaData.getType();
    if (val instanceof String) {
      result = stringToInternalKey(kind, pkType, pkMemberMetaData, cls, val);
    } else if (val instanceof Long || val instanceof Integer) {
      result = intOrLongToInternalKey(kind, pkType, pkMemberMetaData, cls, val);
    } else if (val instanceof Key) {
      result = keyToInternalKey(kind, pkType, pkMemberMetaData, cls, (Key) val, om, allowSubclasses);
    }
    if (result == null && val != null) {
      // missed a case somewhere
      throw new NucleusUserException(
          "Received a request to find an object of type " + cls.getName() + " identified by "
          + val + ".  This is not a valid representation of a primary key for an instance of "
          + cls.getName() + ".").setFatal();
    }
    return result;
  }

  static Key getPkAsKey(Object pk, AbstractClassMetaData acmd, ObjectManager om) {
    if (pk == null) {
      throw new IllegalStateException(
          "Primary key for object of type " + acmd.getName() + " is null.");
    } else if (pk instanceof Key) {
      return (Key) pk;
    } else if (pk instanceof String) {
      if (DatastoreManager.hasEncodedPKField(acmd)) {
        return KeyFactory.stringToKey((String) pk);
      } else {
        String kind = EntityUtils.determineKind(acmd, om);
        return KeyFactory.createKey(kind, (String) pk);
      }
    } else if (pk instanceof Long) {
      String kind = EntityUtils.determineKind(acmd, om);
      return KeyFactory.createKey(kind, (Long) pk);
    } else {
      throw new IllegalStateException(
          "Primary key for object of type " + acmd.getName()
              + " is of unexpected type " + pk.getClass().getName()
              + " (must be String, Long, or " + Key.class.getName() + ")");
    }
  }

  private static boolean keyKindIsValid(String kind, AbstractMemberMetaData pkMemberMetaData,
                                        Class<?> cls, Key key, ObjectManager om, boolean allowSubclasses) {

    if (key.getKind().equals(kind)) {
      return true;
    }

    if (!allowSubclasses) {
      return false;
    }

    MetaDataManager mdm = pkMemberMetaData.getMetaDataManager();
    // see if the key kind is a subclass of the requested kind
    String[] subclasses = mdm.getSubclassesForClass(cls.getName(), true);
    if (subclasses != null) {
      for (String subclass : subclasses) {
        AbstractClassMetaData subAcmd = mdm.getMetaDataForClass(subclass, om.getClassLoaderResolver());
        if (key.getKind().equals(determineKind(subAcmd, om))) {
          return true;
        }
      }
    }
    return false;
  }

  // TODO(maxr): This method is generally useful.  Consider making it public
  // and refactoring the error messages so that they aren't specific to
  // object lookups.
  private static Object keyToInternalKey(String kind, Class<?> pkType,
                                         AbstractMemberMetaData pkMemberMetaData, Class<?> cls,
                                         Key key, ObjectManager om, boolean allowSubclasses) {
    Object result = null;
    if (!keyKindIsValid(kind, pkMemberMetaData, cls, key, om, allowSubclasses)) {
      throw new NucleusUserException(
          "Received a request to find an object of kind " + kind + " but the provided "
          + "identifier is a Key for kind " + key.getKind()).setFatal();
    }
    if (!key.isComplete()) {
      throw new NucleusUserException(
          "Received a request to find an object of kind " + kind + " but the provided "
          + "identifier is is an incomplete Key").setFatal();
    }
    if (pkType.equals(String.class)) {
      if (pkMemberMetaData.hasExtension(DatastoreManager.ENCODED_PK)) {
        result = KeyFactory.keyToString(key);
      } else {
        if (key.getParent() != null) {
          // By definition, classes with unencoded string pks
          // do not have parents.  Since this key has a parent
          // this isn't valid input.
          throw new NucleusUserException(
              "Received a request to find an object of type " + cls.getName() + ".  The primary "
              + "key for this type is an unencoded String, which means instances of this type "
              + "never have parents.  However, the Key that was provided as an argument has a "
              + "parent.").setFatal();
        }
        result = key.getName();
      }
    } else if (pkType.equals(Long.class)) {
      if (key.getParent() != null) {
        // By definition, classes with unencoded string pks
        // do not have parents.  Since this key has a parent
        // this isn't valid input.
        throw new NucleusUserException(
            "Received a request to find an object of type " + cls.getName() + ".  The primary "
            + "key for this type is a Long, which means instances of this type "
            + "never have parents.  However, the Key that was provided as an argument has a "
            + "parent.").setFatal();
      }
      if (key.getName() != null) {
        throw new NucleusUserException(
            "Received a request to find an object of type " + cls.getName() + ".  The primary "
            + "key for this type is a Long.  However, the encoded string "
            + "representation of the Key that was provided as an argument has its name field "
            + "set, not its id.  This makes it an invalid key for this class.").setFatal();
      }
      result = key.getId();
    } else if (pkType.equals(Key.class)) {
      result = key;
    }
    return result;
  }

  private static Object intOrLongToInternalKey(
      String kind, Class<?> pkType, AbstractMemberMetaData pkMemberMetaData, Class<?> cls, Object val) {
    Object result = null;
    Key keyWithId = KeyFactory.createKey(kind, ((Number) val).longValue());
    if (pkType.equals(String.class)) {
      if (pkMemberMetaData.hasExtension(DatastoreManager.ENCODED_PK)) {
        result = KeyFactory.keyToString(keyWithId);
      } else {
        throw new NucleusUserException(
            "Received a request to find an object of type " + cls.getName() + ".  The primary "
            + "key for this type is an unencoded String.  However, the provided value is of type "
            + val.getClass().getName() + ".").setFatal();
      }
    } else if (pkType.equals(Long.class)) {
      result = keyWithId.getId();
    } else if (pkType.equals(Key.class)) {
      result = keyWithId;
    }
    return result;
  }

  private static Object stringToInternalKey(
      String kind, Class<?> pkType, AbstractMemberMetaData pkMemberMetaData, Class<?> cls, Object val) {
    Key decodedKey;
    Object result = null;
    try {
      decodedKey = KeyFactory.stringToKey((String) val);
      if (!decodedKey.isComplete()) {
        throw new NucleusUserException(
            "Received a request to find an object of kind " + kind + " but the provided "
            + "identifier is the String representation of an incomplete Key for kind "
            + decodedKey.getKind()).setFatal();
      }
    } catch (IllegalArgumentException iae) {
      if (pkType.equals(Long.class)) {
        // We were given an unencoded String and the pk type is Long.
        // There's no way that can be valid
        throw new NucleusUserException(
            "Received a request to find an object of type " + cls.getName() + " identified by the String "
            + val + ", but the primary key of " + cls.getName() + " is of type Long.").setFatal();
      }
      // this is ok, it just means we were only given the name
      decodedKey = KeyFactory.createKey(kind, (String) val);
    }
    if (!decodedKey.getKind().equals(kind)) {
      throw new NucleusUserException(
          "Received a request to find an object of kind " + kind + " but the provided "
          + "identifier is the String representation of a Key for kind "
          + decodedKey.getKind()).setFatal();
    }
    if (pkType.equals(String.class)) {
      if (pkMemberMetaData.hasExtension(DatastoreManager.ENCODED_PK)) {
        // Need to make sure we pass on an encoded pk
        result = KeyFactory.keyToString(decodedKey);
      } else {
        if (decodedKey.getParent() != null) {
          throw new NucleusUserException(
              "Received a request to find an object of type " + cls.getName() + ".  The primary "
              + "key for this type is an unencoded String, which means instances of this type "
              + "never have parents.  However, the encoded string representation of the Key that "
              + "was provided as an argument has a parent.").setFatal();
        }
        // Pk is an unencoded string so need to pass on just the name
        // component.  However, we need to make sure the provided key actually
        // contains a name component.
        if (decodedKey.getName() == null) {
          throw new NucleusUserException(
              "Received a request to find an object of type " + cls.getName() + ".  The primary "
              + "key for this type is an unencoded String.  However, the encoded string "
              + "representation of the Key that was provided as an argument has its id field "
              + "set, not its name.  This makes it an invalid key for this class.").setFatal();
        }
        result = decodedKey.getName();
      }
    } else if (pkType.equals(Long.class)) {
      if (decodedKey.getParent() != null) {
        throw new NucleusUserException(
            "Received a request to find an object of type " + cls.getName() + ".  The primary "
            + "key for this type is a Long, which means instances of this type "
            + "never have parents.  However, the encoded string representation of the Key that "
            + "was provided as an argument has a parent.").setFatal();
      }

      if (decodedKey.getName() != null) {
        throw new NucleusUserException(
            "Received a request to find an object of type " + cls.getName() + " identified by the "
            + "encoded String representation of "
            + decodedKey + ", but the primary key of " + cls.getName() + " is of type Long and the "
            + "encoded String has its name component set.  It must have its id component set "
            + "instead in order to be legal.").setFatal();
      }
      // pk is a long so just pass on the id component
      result = decodedKey.getId();
    } else if (pkType.equals(Key.class)) {
      result = decodedKey;
    }
    return result;
  }

  private static AbstractMemberMetaData getPKMemberMetaData(ObjectManager om, Class<?> cls) {
    AbstractClassMetaData cmd = om.getMetaDataManager().getMetaDataForClass(
            cls, om.getClassLoaderResolver());
    return cmd.getMetaDataForManagedMemberAtAbsolutePosition(cmd.getPKMemberPositions()[0]);
  }

  /**
   * Get the active transaction.  Depending on the connection factory
   * associated with the store manager, this may establish a transaction if one
   * is not currently active.  This method will return null if the connection
   * factory associated with the store manager is nontransactional
   */
  public static DatastoreTransaction getCurrentTransaction(ObjectManager om) {
    ManagedConnection mconn = om.getStoreManager().getConnection(om);
    return ((EmulatedXAResource) mconn.getXAResource()).getCurrentTransaction();
  }

  public static Key getPrimaryKeyAsKey(ApiAdapter apiAdapter, StateManager sm) {
    Object primaryKey = apiAdapter.getTargetKeyForSingleFieldIdentity(sm.getInternalObjectId());

    String kind =
        EntityUtils.determineKind(sm.getClassMetaData(), sm.getObjectManager());
    if (primaryKey instanceof Key) {
      return (Key) primaryKey;
    } else if (primaryKey instanceof Long) {
      return KeyFactory.createKey(kind, (Long) primaryKey);
    }
    try {
      return KeyFactory.stringToKey((String) primaryKey);
    } catch (IllegalArgumentException iae) {
      return KeyFactory.createKey(kind, (String) primaryKey);
    }
  }


}
