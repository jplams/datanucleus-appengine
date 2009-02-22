// Copyright 2008 Google Inc. All Rights Reserved.
package org.datanucleus.store.appengine;

import com.google.appengine.api.datastore.Blob;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;

import org.datanucleus.ClassLoaderResolver;
import org.datanucleus.ManagedConnection;
import org.datanucleus.ObjectManager;
import org.datanucleus.StateManager;
import org.datanucleus.api.ApiAdapter;
import org.datanucleus.exceptions.NucleusException;
import org.datanucleus.exceptions.NucleusUserException;
import org.datanucleus.metadata.AbstractClassMetaData;
import org.datanucleus.metadata.AbstractMemberMetaData;
import org.datanucleus.metadata.EmbeddedMetaData;
import org.datanucleus.metadata.Relation;
import org.datanucleus.state.StateManagerFactory;
import org.datanucleus.store.fieldmanager.FieldManager;
import org.datanucleus.store.mapped.IdentifierFactory;
import org.datanucleus.store.mapped.mapping.JavaTypeMapping;
import org.datanucleus.store.mapped.mapping.MappingConsumer;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jdo.spi.JDOImplHelper;

// TODO(maxr): Make this a base class and extract 2 subclasses - one for
// reads and one for writes.
/**
 * FieldManager for converting app engine datastore entities into POJOs and
 * vice-versa.
 *
 * Most of the complexity in this class is due to the fact that the datastore
 * automatically promotes certain types:
 * It promotes short/Short, int/Integer, and byte/Byte to long.
 * It also promotes float/Float to double.
 *
 * Also, the datastore does not support char/Character.  We've made the decision
 * to promote this to long as well.
 *
 * We let the datastore handle the conversion when mapping pojos to datastore
 * {@link Entity Entities} but we handle the conversion ourselves when mapping
 * datastore {@link Entity Entities} to pojos.  For symmetry's sake we could
 * do all the pojo to datastore conversions in our code, but then the
 * conversions would be in two places (our code and the datastore service).
 * We'd rather have a bit of asymmetry and only have the logic exist in one
 * place.
 *
 * @author Max Ross <maxr@google.com>
 */
public class DatastoreFieldManager implements FieldManager {

  private static final String ILLEGAL_NULL_ASSIGNMENT_ERROR_FORMAT =
      "Datastore entity with kind %s and key %s has a null property named %s.  This property is "
          + "mapped to %s, which cannot accept null values.";

  private static final int[] NOT_USED = {0};

  private static final TypeConversionUtils TYPE_CONVERSION_UTILS = new TypeConversionUtils();

  // Stack used to maintain the current field state manager to use.  We push on
  // to this stack as we encounter embedded classes and then pop when we're
  // done.
  private final LinkedList<FieldManagerState> fieldManagerStateStack =
      new LinkedList<FieldManagerState>();

  // true if we instantiated the entity ourselves.
  private final boolean createdWithoutEntity;

  private final DatastoreManager storeManager;

  private final DatastoreRelationFieldManager relationFieldManager;

  private final SerializationManager serializationManager;

  private final InsertMappingConsumer insertMappingConsumer;

  // Not final because we will reallocate if we hit a parent pk field
  // and the key of the current value does not have a parent, or if the pk
  // gets set.
  private Entity datastoreEntity;

  // We'll assign this if we have a parent member and we store a value
  // into it.
  private AbstractMemberMetaData parentMemberMetaData;

  private boolean parentAlreadySet = false;
  private boolean keyAlreadySet = false;
  private Integer pkIdPos = null;

  private static final String PARENT_ALREADY_SET =
      "Cannot set both the primary key and a parent pk field.  If you want the datastore to "
            + "generate an id for you, set the parent pk field to be the value of your parent key "
            + "and leave the primary key field blank.  If you wish to "
            + "provide a named key, leave the parent pk field blank and set the primary key to be a "
            + "Key object made up of both the parent key and the named child.";

  private DatastoreFieldManager(StateManager sm, boolean createdWithoutEntity,
      DatastoreManager storeManager, Entity datastoreEntity, int[] fieldNumbers) {
    // We start with an ammdProvider that just gets member meta data from the class meta data.
    AbstractMemberMetaDataProvider ammdProvider = new AbstractMemberMetaDataProvider() {
      public AbstractMemberMetaData get(int fieldNumber) {
        return getClassMetaData().getMetaDataForManagedMemberAtPosition(fieldNumber);
      }
    };
    this.fieldManagerStateStack.addFirst(new FieldManagerState(sm, ammdProvider));
    this.createdWithoutEntity = createdWithoutEntity;
    this.storeManager = storeManager;
    this.datastoreEntity = datastoreEntity;
    this.relationFieldManager = new DatastoreRelationFieldManager(this);
    this.serializationManager = new SerializationManager();
    this.insertMappingConsumer = buildMappingConsumerForWrite(getClassMetaData(), fieldNumbers);

    // Sanity check
    String expectedKind = EntityUtils.determineKind(getClassMetaData(), getIdentifierFactory());
    if (!expectedKind.equals(datastoreEntity.getKind())) {
      throw new NucleusException(
          "StateManager is for <" + expectedKind + "> but key is for <" + datastoreEntity.getKind()
              + ">.  One way this can happen is if you attempt to fetch an object of one type using"
              + " a Key of a different type.");
    }
  }

  /**
   * Creates a DatastoreFieldManager using the given StateManager and Entity.
   * Only use this overload when you have been provided with an Entity object
   * that already has a well-formed Key.  This will be the case when the entity
   * has been returned by the datastore (get or query), or after the entity has
   * been put into the datastore.
   */
  DatastoreFieldManager(StateManager stateManager, DatastoreManager storeManager,
      Entity datastoreEntity, int[] fieldNumbers) {
    this(stateManager, false, storeManager, datastoreEntity, fieldNumbers);
  }

  public DatastoreFieldManager(StateManager stateManager, DatastoreManager storeManager,
      Entity datastoreEntity) {
    this(stateManager, false, storeManager, datastoreEntity, new int[0]);
  }

  DatastoreFieldManager(StateManager stateManager, String kind,
      DatastoreManager storeManager) {
    this(stateManager, true, storeManager, new Entity(kind), new int[0]);
  }

  public String fetchStringField(int fieldNumber) {
    if (isPK(fieldNumber)) {
      return fetchStringPKField(fieldNumber);
    } else if (isParentPK(fieldNumber)) {
      return fetchParentStringPKField(fieldNumber);
    } else if (isPKNameField(fieldNumber)) {
      if (!fieldIsOfTypeString(fieldNumber)) {
        throw new NucleusUserException(
            "Field with \"" + DatastoreManager.PK_NAME + "\" extension must be of type String");
      }
      return fetchPKNameField();
    }
    return (String) fetchObjectField(fieldNumber);
  }

  private String fetchPKNameField() {
    Key key = datastoreEntity.getKey();
    if (key.getName() == null) {
      throw new NucleusUserException(
          "Attempting to fetch field with \"" + DatastoreManager.PK_NAME + "\" extension but the "
          + "entity is identified by an id, not a name.");
    }
    return datastoreEntity.getKey().getName();
  }

  private long fetchPKIdField() {
    Key key = datastoreEntity.getKey();
    if (key.getName() != null) {
      throw new NucleusUserException(
          "Attempting to fetch field with \"" + DatastoreManager.PK_ID + "\" extension but the "
          + "entity is identified by a name, not an id.");
    }
    return datastoreEntity.getKey().getId();
  }

  private String fetchParentStringPKField(int fieldNumber) {
    Key parentKey = datastoreEntity.getKey().getParent();
    if (parentKey == null) {
      return null;
    }
    return KeyFactory.keyToString(parentKey);
  }

  private String fetchStringPKField(int fieldNumber) {
    if (DatastoreManager.isEncodedPKField(getClassMetaData(), fieldNumber)) {
      // If this is an encoded pk field, transform the Key into its String
      // representation.
      return KeyFactory.keyToString(datastoreEntity.getKey());
    } else {
      if (datastoreEntity.getKey().isComplete() && datastoreEntity.getKey().getName() == null) {
        // This is trouble, probably an incorrect mapping.
        throw new NucleusUserException(
            "The primary key for " + getClassMetaData().getFullClassName() + " is an unencoded "
            + "string but the key of the coresponding entity in the datastore does not have a "
            + "name.  You may want to either change the primary key to be an encoded string "
            + "(add the \"" + DatastoreManager.ENCODED_PK + "\" extension), or change the "
            + "primary key to be of type " + Key.class.getName() + ".");
      }
      return datastoreEntity.getKey().getName();
    }
  }

  public short fetchShortField(int fieldNumber) {
    return (Short) fetchObjectField(fieldNumber);
  }

  private boolean fieldIsOfTypeKey(int fieldNumber) {
    // Key is final so we don't need to worry about checking for subclasses.
    return getMetaData(fieldNumber).getType().equals(Key.class);
  }

  private boolean fieldIsOfTypeString(int fieldNumber) {
    // String is final so we don't need to worry about checking for subclasses.
    return getMetaData(fieldNumber).getType().equals(String.class);
  }

  private boolean fieldIsOfTypeLong(int fieldNumber) {
    // Integer is final so we don't need to worry about checking for subclasses.
    return getMetaData(fieldNumber).getType().equals(Long.class);
  }

  private RuntimeException exceptionForUnexpectedKeyType(String fieldType, int fieldNumber) {
    return new IllegalStateException(
        fieldType + " for type " + getClassMetaData().getName()
            + " is of unexpected type " + getMetaData(fieldNumber).getType().getName()
            + " (must be String, Long, or " + Key.class.getName() + ")");
  }

  public Object fetchObjectField(int fieldNumber) {
    AbstractMemberMetaData ammd = getMetaData(fieldNumber);
    if (ammd.getEmbeddedMetaData() != null) {
      return fetchEmbeddedField(ammd);
    } else if (ammd.getRelationType(getClassLoaderResolver()) != Relation.NONE) {
      return relationFieldManager.fetchRelationField(getClassLoaderResolver(), ammd);
    }

    if (isPK(fieldNumber)) {
      if (fieldIsOfTypeKey(fieldNumber)) {
        // If this is a pk field, transform the Key into its String
        // representation.
        return datastoreEntity.getKey();
      } else if(fieldIsOfTypeLong(fieldNumber)) {
        return datastoreEntity.getKey().getId();
      }
      throw exceptionForUnexpectedKeyType("Primary key", fieldNumber);
    } else if (isParentPK(fieldNumber)) {
      if (fieldIsOfTypeKey(fieldNumber)) {
        return datastoreEntity.getKey().getParent();
      }
      throw exceptionForUnexpectedKeyType("Parent key", fieldNumber);
    } else if (isPKIdField(fieldNumber)) {
      return fetchPKIdField();
    } else {
      Object value = datastoreEntity.getProperty(getPropertyName(fieldNumber));
      if (value != null) {
        ClassLoaderResolver clr = getClassLoaderResolver();
        // Datanucleus invokes this method for the object versions
        // of primitive types.  We need to make sure we convert
        // appropriately.
        value = getConversionUtils().datastoreValueToPojoValue(
            clr, value, getStateManager(), getMetaData(fieldNumber));
        if (ammd.isSerialized()) {
          value = deserializeFieldValue(value, clr, ammd);
        } else if (Enum.class.isAssignableFrom(ammd.getType())) {
          @SuppressWarnings("unchecked")
          Class<Enum> enumClass = ammd.getType();
          value = Enum.valueOf(enumClass, (String) value);
        }
      }
      return value;
    }
  }

  private Object deserializeFieldValue(
      Object value, ClassLoaderResolver clr, AbstractMemberMetaData ammd) {
    if (!(value instanceof Blob)) {
      throw new NucleusException(
          "Datastore value is of type " + value.getClass().getName() + " (must be Blob).");
    }
    return serializationManager.deserialize(clr, ammd, (Blob) value);
  }

  private AbstractMemberMetaDataProvider getEmbeddedAbstractMemberMetaDataProvider(
      AbstractMemberMetaData ammd) {
    final EmbeddedMetaData emd = ammd.getEmbeddedMetaData();
    // This implementation gets the meta data from the embedded meta data.
    // This is needed to ensure we see column overrides that are specific to
    // a specific embedded field.
    return new AbstractMemberMetaDataProvider() {
      public AbstractMemberMetaData get(int fieldNumber) {
        return emd.getMemberMetaData()[fieldNumber];
      }
    };
  }

  private StateManager getEmbeddedStateManager(AbstractMemberMetaData ammd, Object value) {
    if (value == null) {
      // Not positive this is the right approach, but when we read the values
      // of an embedded field out of the datastore we have no way of knowing
      // if the field should be null or it should contain an instance of the
      // embeddable whose members are all initialized to their default values
      // (the result of calling the default ctor).  Also, we can't risk
      // storing 'null' for every field of the embedded class because some of
      // the members might be base types and therefore non-nullable.  Writing
      // nulls to the datastore for these fields would cause NPEs when we read
      // the object back out.  Seems like the only safe thing to do here is
      // instantiate a fresh instance of the embeddable class using the default
      // constructor and then persist that.
      value = JDOImplHelper.getInstance().newInstance(
          ammd.getType(), (javax.jdo.spi.StateManager) getStateManager());
    }
    ObjectManager objMgr = getObjectManager();
    StateManager embeddedStateMgr = objMgr.findStateManager(value);
    if (embeddedStateMgr == null) {
      embeddedStateMgr = StateManagerFactory.newStateManagerForEmbedded(objMgr, value, false);
      embeddedStateMgr.addEmbeddedOwner(getStateManager(), ammd.getAbsoluteFieldNumber());
      embeddedStateMgr.setPcObjectType(StateManager.EMBEDDED_PC);
    }
    return embeddedStateMgr;
  }

  private Object fetchEmbeddedField(AbstractMemberMetaData ammd) {
    StateManager embeddedStateMgr = getEmbeddedStateManager(ammd, null);
    AbstractMemberMetaDataProvider ammdProvider = getEmbeddedAbstractMemberMetaDataProvider(ammd);
    fieldManagerStateStack.addFirst(new FieldManagerState(embeddedStateMgr, ammdProvider));
    AbstractClassMetaData acmd = embeddedStateMgr.getClassMetaData();
    embeddedStateMgr.replaceFields(acmd.getAllMemberPositions(), this);
    fieldManagerStateStack.removeFirst();
    return embeddedStateMgr.getObject();
  }

  /**
   * Ensures that the given value is not null.  Throws
   * {@link NullPointerException} with a helpful error message if it is.
   */
  private Object checkAssignmentToNotNullField(Object val, int fieldNumber) {
    if (val != null) {
      // not null so no problem
      return val;
    }
    // Put together a really helpful error message
    AbstractMemberMetaData ammd = getMetaData(fieldNumber);
    String propertyName = getPropertyName(fieldNumber);
    final String msg = String.format(ILLEGAL_NULL_ASSIGNMENT_ERROR_FORMAT,
        datastoreEntity.getKind(), datastoreEntity.getKey(), propertyName,
        ammd.getFullFieldName());
    throw new NullPointerException(msg);
  }

  public long fetchLongField(int fieldNumber) {
    return (Long) checkAssignmentToNotNullField(fetchObjectField(fieldNumber), fieldNumber);
  }

  public int fetchIntField(int fieldNumber) {
    return (Integer) checkAssignmentToNotNullField(fetchObjectField(fieldNumber), fieldNumber);
  }

  public float fetchFloatField(int fieldNumber) {
    return (Float) checkAssignmentToNotNullField(fetchObjectField(fieldNumber), fieldNumber);
  }

  public double fetchDoubleField(int fieldNumber) {
    return (Double) checkAssignmentToNotNullField(fetchObjectField(fieldNumber), fieldNumber);
  }

  public char fetchCharField(int fieldNumber) {
    return (Character) checkAssignmentToNotNullField(fetchObjectField(fieldNumber), fieldNumber);
  }

  public byte fetchByteField(int fieldNumber) {
    return (Byte) checkAssignmentToNotNullField(fetchObjectField(fieldNumber), fieldNumber);
  }

  public boolean fetchBooleanField(int fieldNumber) {
    return (Boolean) checkAssignmentToNotNullField(fetchObjectField(fieldNumber), fieldNumber);
  }

  public void storeStringField(int fieldNumber, String value) {
    if (isPK(fieldNumber)) {
      storeStringPKField(fieldNumber, value);
    } else if (isParentPK(fieldNumber)) {
      storeParentStringField(value);
    } else if (isPKNameField(fieldNumber)) {
      storePKNameField(fieldNumber, value);
    } else {
      storeObjectField(fieldNumber, value);
    }
  }

  private void storePKIdField(int fieldNumber, Object value) {
    if (!fieldIsOfTypeLong(fieldNumber)) {
      throw new NucleusUserException(
          "Field with \"" + DatastoreManager.PK_ID + "\" extension must be of type Long");
    }
    Key key = null;
    if (value != null) {
      key = KeyFactory.createKey(datastoreEntity.getKind(), (Long) value);
    }
    storeKeyPK(key);
    pkIdPos = fieldNumber;
  }

  private void storePKNameField(int fieldNumber, String value) {
    // TODO(maxr) make sure the pk is an encoded string
    if (!fieldIsOfTypeString(fieldNumber)) {
      throw new NucleusUserException(
          "Field with \"" + DatastoreManager.PK_ID + "\" extension must be of type String");
    }
    Key key = null;
    if (value != null) {
      key = KeyFactory.createKey(datastoreEntity.getKind(), value);
      keyAlreadySet = true;
    }
    storeKeyPK(key);
  }
  
  private void storeParentStringField(String value) {
    Key key = null;
    if (value != null) {
      try {
        key = KeyFactory.stringToKey(value);
      } catch (IllegalArgumentException iae) {
        throw new NucleusUserException(
            "Attempt was made to set parent to " + value
            + " but this cannot be converted into a Key.");
      }
    }
    storeParentKeyPK(key);
  }

  private void storeStringPKField(int fieldNumber, String value) {
    Key key = null;
    if (DatastoreManager.isEncodedPKField(getClassMetaData(), fieldNumber)) {
      if (value != null) {
        try {
          key = KeyFactory.stringToKey(value);
        } catch (IllegalArgumentException iae) {
          throw new NucleusUserException(
              "Invalid primary key for " + getClassMetaData().getFullClassName() + ".  The "
              + "primary key field is an encoded String but an unencoded value has been provided. "
              + "If you want to set an unencoded value on this field you can either change its "
              + "type to be an unencoded String (remove the \"" + DatastoreManager.ENCODED_PK
              + "\" extension), change its type to be a " + Key.class.getName() + " and then set "
              + "the Key's name field, or create a separate String field for the name component "
              + "of your primary key and add the \"" + DatastoreManager.PK_NAME
              + "\" extension.");
        }
      }
    } else {
      if (value == null) {
        throw new NucleusUserException(
            "Invalid primary key for " + getClassMetaData().getFullClassName() + ".  Cannot have "
            + "a null primary key field if the field is unencoded and of type String.  "
            + "Please provide a value or, if you want the datastore to generate an id on your "
            + "behalf, change the type of the field to Long.");
      }
      if (value != null) {
        if (datastoreEntity.getParent() != null) {
          key = new Entity(datastoreEntity.getKey().getKind(), value, datastoreEntity.getParent()).getKey();
        } else {
          key = new Entity(datastoreEntity.getKey().getKind(), value).getKey();
        }
      }
    }
    storeKeyPK(key);
  }

  /**
   * Currently all relationships are parent-child.  If a datastore entity
   * doesn't have a parent there are 3 places we can look for one.
   * 1)  It's possible that a pojo in the cascade chain registered itself as
   * the parent.
   * 2)  It's possible that the pojo has an external foreign key mapping
   * to the object that owns it, in which case we can use the key of that field
   * as the parent.
   * 3)  It's possible that the pojo has a field containing the parent that is
   * not an external foreign key mapping but is labeled as a "parent
   * provider" (this is an app engine orm term).  In this case, as with
   * #2, we can use the key of that field as the parent.
   *
   * It _should_ be possible to get rid of at least one of these
   * mechanisms, most likely the first.
   *
   * @return The parent key if the pojo class has a parent property.
   * Note that a return value of {@code null} does not mean that an entity
   * group was not established, it just means the pojo doesn't have a distinct
   * field for the parent.
   */
  Object establishEntityGroup() {
    if (getEntity().getParent() != null) {
      // Entity already has a parent so nothing to do.
      return null;
    }
    StateManager sm = getStateManager();
    // Mechanism 1
    Key parentKey = getKeyRegistry().getRegisteredKey(sm.getObject());
    if (parentKey == null) {
      // Mechanism 2
      parentKey = getParentKeyFromExternalFKMappings(sm);
    }
    if (parentKey == null) {
      // Mechanism 3
      parentKey = getParentKeyFromParentField(sm);
    }
    if (parentKey != null) {

      recreateEntityWithParent(parentKey);

      if (getParentMemberMetaData() != null) {
        return getParentMemberMetaData().getType().equals(Key.class)
            ? parentKey : KeyFactory.keyToString(parentKey);
      }
    }
    return null;
  }

  private Key getIdForObject(Object pc) {
    ApiAdapter adapter = getStoreManager().getOMFContext().getApiAdapter();
    Object keyOrString = adapter.getTargetKeyForSingleFieldIdentity(adapter.getIdForObject(pc));
    return keyOrString instanceof Key ?
           (Key) keyOrString : KeyFactory.stringToKey((String) keyOrString);
  }

  private Key getParentKeyFromParentField(StateManager sm) {
    AbstractMemberMetaData parentField = insertMappingConsumer.getParentMappingField();
    if (parentField == null) {
      return null;
    }
    Object parent = sm.provideField(parentField.getAbsoluteFieldNumber());
    return parent == null ? null : getIdForObject(parent);
  }

  private Key getParentKeyFromExternalFKMappings(StateManager sm) {
    // We don't have a registered key for the object associated with the
    // state manager but there might be one tied to the foreign key
    // mappings for this object.  If this is the Many side of a bidirectional
    // One To Many it might also be available on the parent object.
    // TODO(maxr): Unify the 2 mechanisms.  We probably want to get rid of
    // the KeyRegistry.
    Set<JavaTypeMapping> externalFKMappings = insertMappingConsumer.getExternalFKMappings();
    for (JavaTypeMapping fkMapping : externalFKMappings) {
      Object fkValue = sm.getAssociatedValue(fkMapping);
      if (fkValue != null) {
        return getIdForObject(fkValue);
      }
    }
    return null;
  }

  /**
   * Get the {@link KeyRegistry} associated with the current datasource
   * connection.  There's a little bit of fancy footwork involved here
   * because, by default, asking the storeManager for a connection will
   * allocate a transactional connection if no connection has already been
   * established.  That's acceptable behavior if the datasource has not been
   * configured to allow writes outside of transactions, but if the datsaource
   * _has_ been configured to allow writes outside of transactions,
   * establishing a transaction is not the right thing to do.  So, we set
   * a property on the currently active transaction (the datanucleus
   * transaction, not the datastore transaction) to indicate that if a
   * connection gets allocated, don't establish a datastore transaction.
   * Note that even if nontransactional writes are enabled, if there
   * is already a connection available then setting the property is a no-op.
   */
  private KeyRegistry getKeyRegistry() {
    ObjectManager om = getObjectManager();
    ManagedConnection mconn = storeManager.getConnection(om);
    return ((EmulatedXAResource) mconn.getXAResource()).getKeyRegistry();
  }

  void recreateEntityWithParent(Key parentKey) {
    Entity old = datastoreEntity;
    if (old.getKey().getName() != null) {
      datastoreEntity =
          new Entity(old.getKind(), old.getKey().getName(), parentKey);
    } else {
      datastoreEntity = new Entity(old.getKind(), parentKey);
    }
    copyProperties(old, datastoreEntity);
  }

  private void storeKeyPK(Key key) {
    if (key != null && !datastoreEntity.getKind().equals(key.getKind())) {
      throw new NucleusUserException(
          "Attempt was made to set the primray key of an entity with kind "
          + datastoreEntity.getKind() + " to a key with kind " + key.getKind());  
    }
    if (datastoreEntity.getKey().isComplete()) {
      // this modification is only okay if it's actually a no-op
      if (!datastoreEntity.getKey().equals(key)) {
        if (!keyAlreadySet) {
          // Different key provided so the update isn't allowed.
          throw new NucleusUserException(
              "Attempt was made to modify the primary key of an object of type "
              + getStateManager().getClassMetaData().getFullClassName() + " identified by "
              + "key " + datastoreEntity.getKey() + ".  Primary keys are immutable.");
        }
      }
    } else if (key != null) {
      if (key.getName() == null) {
        // This means an id was provided to an incomplete Key,
        // and that means the user is trying to set the id manually, which
        // we don't support.
        throw new NucleusUserException(
            "Attempt was made to manually set the id component of a Key primary key.  If you want "
            + "to control the value of the of the primary key, set the name component instead.");
      }
      Entity old = datastoreEntity;
      if (key.getParent() == null) {
        datastoreEntity = new Entity(old.getKind(), key.getName());
      } else {
        parentAlreadySet = true;
        datastoreEntity = new Entity(old.getKind(), key.getName(), key.getParent());
      }
      copyProperties(old, datastoreEntity);
    }
  }

  static void copyProperties(Entity src, Entity dest) {
    for (Map.Entry<String, Object> entry : src.getProperties().entrySet()) {
      dest.setProperty(entry.getKey(), entry.getValue());
    }
  }

  private boolean isPKNameField(int fieldNumber) {
    return DatastoreManager.isPKNameField(getClassMetaData(), fieldNumber);
  }

  private boolean isPKIdField(int fieldNumber) {
    return DatastoreManager.isPKIdField(getClassMetaData(), fieldNumber);
  }

  private boolean isParentPK(int fieldNumber) {
    boolean result = DatastoreManager.isParentPKField(getClassMetaData(), fieldNumber);
    if (result) {
      // ew, side effect
      parentMemberMetaData = getMetaData(fieldNumber);
    }
    return result;
  }

  public void storeShortField(int fieldNumber, short value) {
    storeObjectField(fieldNumber, value);
  }

  private void storePrimaryKey(int fieldNumber, Object value) {
    if (fieldIsOfTypeLong(fieldNumber)) {
      Key key = null;
      if (value != null) {
        key = KeyFactory.createKey(datastoreEntity.getKind(), (Long) value);
      }
      storeKeyPK(key);
    } else if (fieldIsOfTypeKey(fieldNumber)) {
      Key key = (Key) value;
      if (key != null && key.getParent() != null && parentAlreadySet) {
        throw new NucleusUserException(PARENT_ALREADY_SET);
      }
      storeKeyPK((Key) value);
    } else {
      throw exceptionForUnexpectedKeyType("Primary key", fieldNumber);
    }
  }

  private void storeParentField(int fieldNumber, Object value) {
    if (fieldIsOfTypeKey(fieldNumber)) {
      storeParentKeyPK((Key) value);
    } else {
      throw exceptionForUnexpectedKeyType("Parent primary key", fieldNumber);
    }
  }

  public void storeObjectField(int fieldNumber, Object value) {
    if (isPK(fieldNumber)) {
      storePrimaryKey(fieldNumber, value);
    } else if (isParentPK(fieldNumber)) {
      storeParentField(fieldNumber, value);
    } else if (isPKIdField(fieldNumber)) {
      storePKIdField(fieldNumber, value);
    } else {
      ClassLoaderResolver clr = getClassLoaderResolver();
      AbstractMemberMetaData ammd = getMetaData(fieldNumber);
      if (value != null ) {
        if (ammd.isSerialized()) {
          value = serializationManager.serialize(clr, ammd, value);
        } else if (Enum.class.isAssignableFrom(ammd.getType())) {
          value = ((Enum) value).name();
        }
        if (value.getClass().isArray()) {
          value = convertArrayValue(ammd, value);
        } else if (Collection.class.isAssignableFrom(value.getClass())) {
          value = convertCollectionValue(ammd, value);
        } else if (value instanceof Character) {
          // Datastore doesn't support Character so translate into a Long.
          value = TypeConversionUtils.CHARACTER_TO_LONG.apply((Character) value);
        }
      }
      if (ammd.getEmbeddedMetaData() != null) {
        storeEmbeddedField(ammd, value);
      } else if (ammd.getRelationType(clr) != Relation.NONE) {
        relationFieldManager.storeRelationField(
            getClassMetaData(), ammd, value, createdWithoutEntity, insertMappingConsumer);
      } else {
        datastoreEntity.setProperty(getPropertyName(fieldNumber), value);
      }
    }
  }

  private Object convertCollectionValue(AbstractMemberMetaData ammd, Object value) {
    Object result = value;
    if (getConversionUtils().pojoPropertyIsCharacterCollection(ammd)) {
      // Datastore doesn't support Character so translate into
      // a list of Longs.  All other Collections can pass straight
      // through.
      @SuppressWarnings("unchecked")
      List<Character> chars = (List<Character>) value;
      result = Utils.transform(chars, TypeConversionUtils.CHARACTER_TO_LONG);
    } else if (Enum.class.isAssignableFrom(
        getClassLoaderResolver().classForName(ammd.getCollection().getElementType()))) {
      @SuppressWarnings("unchecked")
      Iterable<Enum> enums = (Iterable<Enum>) value; 
      result = getConversionUtils().convertEnumsToStringList(enums);
    }
    return result;
  }

  private Object convertArrayValue(AbstractMemberMetaData ammd, Object value) {
    Object result;
    if (getConversionUtils().pojoPropertyIsByteArray(ammd)) {
      result = getConversionUtils().convertByteArrayToBlob(value);
    } else if (Enum.class.isAssignableFrom(ammd.getType().getComponentType())) {
      result = getConversionUtils().convertEnumsToStringList(Arrays.<Enum>asList((Enum[]) value));
    } else {
      // Translate all arrays to lists before storing.
      result = getConversionUtils().convertPojoArrayToDatastoreList(value);
    }
    return result;
  }

  /**
   * @see DatastoreRelationFieldManager#storeRelations
   */
  void storeRelations() {
    relationFieldManager.storeRelations(getKeyRegistry());
  }

  ClassLoaderResolver getClassLoaderResolver() {
    return getObjectManager().getClassLoaderResolver();
  }

  StateManager getStateManager() {
    return fieldManagerStateStack.getFirst().stateManager;
  }

  ObjectManager getObjectManager() {
    return getStateManager().getObjectManager();
  }

  private void storeEmbeddedField(AbstractMemberMetaData ammd, Object value) {
    StateManager embeddedStateMgr = getEmbeddedStateManager(ammd, value);
    AbstractMemberMetaDataProvider ammdProvider = getEmbeddedAbstractMemberMetaDataProvider(ammd);
    fieldManagerStateStack.addFirst(new FieldManagerState(embeddedStateMgr, ammdProvider));
    AbstractClassMetaData acmd = embeddedStateMgr.getClassMetaData();
    embeddedStateMgr.provideFields(acmd.getAllMemberPositions(), this);
    fieldManagerStateStack.removeFirst();
  }

  private void storeParentKeyPK(Key key) {
    if (key != null && parentAlreadySet) {
      throw new NucleusUserException(PARENT_ALREADY_SET);
    }
    if (datastoreEntity.getParent() != null) {
      // update is ok if it's a no-op
      if (!datastoreEntity.getParent().equals(key)) {
        if (!parentAlreadySet) {
          throw new NucleusUserException(
              "Attempt was made to modify the parent of an object of type "
              + getStateManager().getClassMetaData().getFullClassName() + " identified by "
              + "key " + datastoreEntity.getKey() + ".  Parents are immutable.");
        }
      }
    } else if (key != null) {
      if (!createdWithoutEntity) {
        // Shouldn't even happen.
        throw new NucleusUserException("You can only rely on this class to properly handle "
            + "parent pks if you instantiated the class without providing a datastore "
            + "entity to the constructor.");
      }

      // If this field is labeled as a parent PK we need to recreate the Entity, passing
      // the value of this field as an arg to the Entity constructor and then moving all
      // properties on the old entity to the new entity.
      recreateEntityWithParent(key);
    } else {
      // Null parent.  Parent is defined on a per-instance basis so
      // annotating a field as a parent is not necessarily a commitment
      // to always having a parent.  Null parent is fine.
    }
  }

  public void storeLongField(int fieldNumber, long value) {
    storeObjectField(fieldNumber, value);
  }

  public void storeIntField(int fieldNumber, int value) {
    storeObjectField(fieldNumber, value);
  }

  public void storeFloatField(int fieldNumber, float value) {
    storeObjectField(fieldNumber, value);
  }

  public void storeDoubleField(int fieldNumber, double value) {
    storeObjectField(fieldNumber, value);
  }

  public void storeCharField(int fieldNumber, char value) {
    storeLongField(fieldNumber, (long) value);
  }

  public void storeByteField(int fieldNumber, byte value) {
    storeObjectField(fieldNumber, value);
  }

  public void storeBooleanField(int fieldNumber, boolean value) {
    storeObjectField(fieldNumber, value);
  }

  private boolean isPK(int fieldNumber) {
    int[] pkPositions = getClassMetaData().getPKMemberPositions();
    // Assumes that if we have a pk we only have a single field pk
    return pkPositions != null && pkPositions[0] == fieldNumber;
  }

  private String getPropertyName(int fieldNumber) {
    AbstractMemberMetaData ammd = getMetaData(fieldNumber);
    return EntityUtils.getPropertyName(getIdentifierFactory(), ammd);
  }

  private AbstractMemberMetaData getMetaData(int fieldNumber) {
    return fieldManagerStateStack.getFirst().abstractMemberMetaDataProvider.get(fieldNumber);
  }

  AbstractClassMetaData getClassMetaData() {
    return getStateManager().getClassMetaData();
  }

  Entity getEntity() {
    return datastoreEntity;
  }

  private IdentifierFactory getIdentifierFactory() {
    return storeManager.getIdentifierFactory();
  }

  AbstractMemberMetaData getParentMemberMetaData() {
    return parentMemberMetaData;
  }

  DatastoreManager getStoreManager() {
    return storeManager;
  }

  /**
   * In JDO, 1-to-many relationsihps that are expressed using a
   * {@link List} are ordered by a column in the child
   * table that stores the position of the child in the parent's list.
   * This function is responsible for making sure the appropriate values
   * for these columns find their way into the Entity.  In certain scenarios,
   * DataNucleus does not make the index of the container element being
   * written available until later on in the workflow.  The expectation
   * is that we will insert the record without the index and then perform
   * an update later on when the value becomes available.  This is problematic
   * for the App Engine datastore because we can only write an entity once
   * per transaction.  So, to get around this, we detect the case where the
   * index is not available and instruct the caller to hold off writing.
   * Later on in the workflow, when DataNucleus calls down into our plugin
   * to request the update with the index, we perform the insert.  This will
   * break someday.  Fortunately we have tests so we should find out.
   *
   * @return {@code true} if the caller (expected to be
   * {@link DatastorePersistenceHandler#insertObject}) should delay its write
   * of this object.
   */
  boolean handleIndexFields() {
    Set<JavaTypeMapping> orderMappings = insertMappingConsumer.getExternalOrderMappings();
    boolean delayWrite = false;
    for (JavaTypeMapping orderMapping : orderMappings) {
      delayWrite = true;
      // DataNucleus hides the value in the state mamanger, keyed by the
      // mapping for the order field.
      Object orderValue = getStateManager().getAssociatedValue(orderMapping);
      if (orderValue != null) {
        // We got a value!  Set it on the entity.
        delayWrite = false;
        orderMapping.setObject(getObjectManager(), getEntity(), NOT_USED, orderValue);
      }
    }
    return delayWrite;
  }

  private InsertMappingConsumer buildMappingConsumerForWrite(AbstractClassMetaData acmd, int[] fieldNumbers) {
    DatastoreTable dc = getStoreManager().getDatastoreClass(
        acmd.getFullClassName(), getClassLoaderResolver());
    InsertMappingConsumer consumer = new InsertMappingConsumer(acmd);
    dc.provideDatastoreIdMappings(consumer);
    dc.providePrimaryKeyMappings(consumer);
    dc.provideParentMappingField(consumer);
    if (createdWithoutEntity) {
      // This is the insert case.  We want to fill the consumer with mappings
      // for everything.
      dc.provideNonPrimaryKeyMappings(consumer);
      dc.provideExternalMappings(consumer, MappingConsumer.MAPPING_TYPE_EXTERNAL_FK);
      dc.provideExternalMappings(consumer, MappingConsumer.MAPPING_TYPE_EXTERNAL_INDEX);
    } else {
      // This is the update case.  We only want to fill the consumer mappings
      // for the specific fields that were provided.
      AbstractMemberMetaData[] fmds = new AbstractMemberMetaData[fieldNumbers.length];
      if (fieldNumbers.length > 0) {
        for (int i = 0; i < fieldNumbers.length; i++) {
          fmds[i] = acmd.getMetaDataForManagedMemberAtAbsolutePosition(fieldNumbers[i]);
        }
      }
      dc.provideMappingsForMembers(consumer, fmds, false);
    }
    return consumer;
  }

  /**
   * Just exists so we can override in tests. 
   */
  TypeConversionUtils getConversionUtils() {
    return TYPE_CONVERSION_UTILS;
  }

  Integer getPkIdPos() {
    return pkIdPos;
  }

  /**
   * Translates field numbers into {@link AbstractMemberMetaData}.
   */
  private interface AbstractMemberMetaDataProvider {
    AbstractMemberMetaData get(int fieldNumber);
  }

  private static final class FieldManagerState {
    private final StateManager stateManager;
    private final AbstractMemberMetaDataProvider abstractMemberMetaDataProvider;

    private FieldManagerState(StateManager stateManager,
        AbstractMemberMetaDataProvider abstractMemberMetaDataProvider) {
      this.stateManager = stateManager;
      this.abstractMemberMetaDataProvider = abstractMemberMetaDataProvider;
    }
  }

}
