// Copyright 2008 Google Inc. All Rights Reserved.
package org.datanucleus.store.appengine;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.datanucleus.ClassLoaderResolver;
import org.datanucleus.exceptions.NucleusException;
import org.datanucleus.exceptions.NucleusUserException;
import org.datanucleus.identity.OID;
import org.datanucleus.metadata.AbstractClassMetaData;
import org.datanucleus.metadata.AbstractMemberMetaData;
import org.datanucleus.metadata.ClassMetaData;
import org.datanucleus.metadata.ColumnMetaData;
import org.datanucleus.metadata.ColumnMetaDataContainer;
import org.datanucleus.metadata.DiscriminatorMetaData;
import org.datanucleus.metadata.FieldPersistenceModifier;
import org.datanucleus.metadata.FieldRole;
import org.datanucleus.metadata.IdentityStrategy;
import org.datanucleus.metadata.IdentityType;
import org.datanucleus.metadata.InheritanceStrategy;
import org.datanucleus.metadata.MetaData;
import org.datanucleus.metadata.OrderMetaData;
import org.datanucleus.metadata.Relation;
import org.datanucleus.metadata.VersionMetaData;
import org.datanucleus.plugin.ConfigurationElement;
import org.datanucleus.sco.SCOUtils;
import org.datanucleus.store.exceptions.NoSuchPersistentFieldException;
import org.datanucleus.store.exceptions.NoTableManagedException;
import org.datanucleus.store.mapped.DatastoreAdapter;
import org.datanucleus.store.mapped.DatastoreClass;
import org.datanucleus.store.mapped.DatastoreField;
import org.datanucleus.store.mapped.DatastoreIdentifier;
import org.datanucleus.store.mapped.IdentifierFactory;
import org.datanucleus.store.mapped.IdentifierType;
import org.datanucleus.store.mapped.MappedStoreManager;
import org.datanucleus.store.mapped.mapping.CorrespondentColumnsMapper;
import org.datanucleus.store.mapped.mapping.DatastoreMapping;
import org.datanucleus.store.mapped.mapping.IndexMapping;
import org.datanucleus.store.mapped.mapping.IntegerMapping;
import org.datanucleus.store.mapped.mapping.JavaTypeMapping;
import org.datanucleus.store.mapped.mapping.LongMapping;
import org.datanucleus.store.mapped.mapping.MappingConsumer;
import org.datanucleus.store.mapped.mapping.OIDMapping;
import org.datanucleus.store.mapped.mapping.PersistenceCapableMapping;
import org.datanucleus.util.Localiser;
import org.datanucleus.util.MultiMap;
import org.datanucleus.util.NucleusLogger;
import org.datanucleus.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Describes a 'table' in the datastore.  We don't actually have tables, but
 * we need to represent this logically in order to take care of all the nice
 * mapping logic that datanucleus does for us.
 *
 * This code is largely copied AbstractClassTable, ClassTable, TableImpl,
 * and AbstractTable
 *
 * TODO(maxr): Refactor the RDBMS classes into the mapped package so we can
 * refactor this.
 *
 * @author Max Ross <maxr@google.com>
 */
class DatastoreTable implements DatastoreClass {

  /** Localiser for messages. */
  protected static final Localiser LOCALISER =
      Localiser.getInstance("org.datanucleus.store.appengine.Localisation",
      DatastoreManager.class.getClassLoader());

  /** All callbacks for class tables waiting to be performed. */
  private static final MultiMap callbacks = new MultiMap();

  private final MappedStoreManager storeMgr;
  private final AbstractClassMetaData cmd;
  private final ClassLoaderResolver clr;
  private final DatastoreAdapter dba;
  protected final DatastoreIdentifier identifier;

  /**
   * Mappings for fields mapped to this table, keyed by the FieldMetaData.
   */
  private final Map<AbstractMemberMetaData, JavaTypeMapping> fieldMappingsMap = Maps.newHashMap();

  /**
   * All the properties in the table.  Even though the datastore is schemaless,
   * the mappings provided by the ORM effectively impose a schema.  This allows
   * us to know, up front, what properties we can expect.
   */
  private final List<DatastoreProperty> datastoreProperties = Lists.newArrayList();

  /**
   * Index to the props, keyed by name.
   */
  protected Map<String, DatastoreProperty> datastorePropertiesByName = Maps.newHashMap();

  /**
   * Mapping for datastore identity (optional).
   *
   */
  private JavaTypeMapping datastoreIDMapping;

  /**
   * Mappings for application identity (optional).
   */
  private JavaTypeMapping[] pkMappings;

  /**
   * Mapping for the id of the table.
   */
  private JavaTypeMapping idMapping;

  /**
   * Highest absolute field number managed by this table
   */
  private int highestFieldNumber = 0;

  /**
   * Dependent fields.  Unlike pretty much the rest of this class, this member and the
   * code that populates is specific to the appengine plugin.
   */
  private final List<AbstractMemberMetaData> dependentMemberMetaData = Lists.newArrayList();
  private final Map<AbstractMemberMetaData, JavaTypeMapping> externalFkMappings = Maps.newHashMap();
  private final Map<AbstractMemberMetaData, JavaTypeMapping> externalOrderMappings = Maps.newHashMap();

  DatastoreTable(MappedStoreManager storeMgr, AbstractClassMetaData cmd,
      ClassLoaderResolver clr, DatastoreAdapter dba) {
    this.storeMgr = storeMgr;
    this.cmd = cmd;
    this.clr = clr;
    this.dba = dba;
    this.identifier = new DatastoreKind(cmd);
  }

  public String getType() {
    return cmd.getFullClassName();
  }

  public IdentityType getIdentityType() {
    return cmd.getIdentityType();
  }

  public boolean isObjectIDDatastoreAttributed() {
    return true;
  }

  public boolean isBaseDatastoreClass() {
    return true;
  }

  public DatastoreClass getBaseDatastoreClassWithMember(AbstractMemberMetaData fmd) {
    if (fieldMappingsMap.get(fmd) != null) {
      return this;
    }
    return null;
  }

  public DatastoreClass getSuperDatastoreClass() {
    return null;
  }

  public Collection getSecondaryDatastoreClasses() {
    return null;
  }

  public boolean managesClass(String className) {
    return cmd.getFullClassName().equals(className);
  }

  public JavaTypeMapping getDataStoreObjectIdMapping() {
    return datastoreIDMapping;
  }

  public JavaTypeMapping getMemberMapping(String fieldName) {
    AbstractMemberMetaData fmd = getFieldMetaData(fieldName);
    JavaTypeMapping m = getMemberMapping(fmd);
    if (m == null) {
        throw new NoSuchPersistentFieldException(cmd.getFullClassName(), fieldName);
    }
    return m;
  }

  private AbstractMemberMetaData getFieldMetaData(String fieldName) {
    return cmd.getMetaDataForMember(fieldName);
  }

  public JavaTypeMapping getMemberMapping(AbstractMemberMetaData mmd) {
    if (mmd == null) {
      return null;
    }

    return fieldMappingsMap.get(mmd);
  }

  public JavaTypeMapping getMemberMappingInDatastoreClass(AbstractMemberMetaData mmd) {
    return getMemberMapping(mmd);
  }

  // Mostly copied from AbstractTable.addDatastoreField
  public DatastoreField addDatastoreField(String storedJavaType, DatastoreIdentifier name,
      JavaTypeMapping mapping, MetaData colmd) {

    if (hasColumnName(name)) {
      throw new NucleusException("Duplicate property name: " + name);
    }

    // Create the column
    DatastoreProperty prop =
        new DatastoreProperty(this, mapping.getJavaType().getName(), name, (ColumnMetaData) colmd);

    DatastoreIdentifier colName = prop.getIdentifier();

    datastoreProperties.add(prop);
    datastorePropertiesByName.put(colName.getIdentifierName(), prop);
    return prop;
  }

  protected boolean hasColumnName(DatastoreIdentifier colName) {
    return getDatastoreField(colName) != null;
  }


  public boolean hasDatastoreField(DatastoreIdentifier identifier) {
    return (hasColumnName(identifier));
  }

  DatastoreField getDatastoreField(String colName) {
    return datastorePropertiesByName.get(colName);
  }
  public DatastoreField getDatastoreField(DatastoreIdentifier identifier) {
    return getDatastoreField(identifier.getIdentifierName());
  }

  public JavaTypeMapping getIDMapping() {
    return idMapping;
  }

  public MappedStoreManager getStoreManager() {
    return storeMgr;
  }

  public DatastoreIdentifier getIdentifier() {
    return identifier;
  }

  public boolean isSuperDatastoreClass(DatastoreClass datastoreClass) {
    return false;
  }

  public boolean managesMapping(JavaTypeMapping javaTypeMapping) {
    return true;
  }

  public DatastoreField[] getDatastoreFields() {
    return datastoreProperties.toArray(new DatastoreField[datastoreProperties.size()]);
  }

  public void buildMapping() {
    initializePK();
    initializeNonPK();
    runCallBacks();
  }

  private void initializeNonPK() {
    AbstractMemberMetaData[] fields = cmd.getManagedMembers();
    // Go through the fields for this class and add columns for them
    for (AbstractMemberMetaData fmd : fields) {
      // Primary key fields are added by the initialisePK method
      if (!fmd.isPrimaryKey()) {
        if (managesField(fmd.getFullFieldName())) {
          if (!fmd.getClassName(true).equals(cmd.getFullClassName())) {
            throw new UnsupportedOperationException("Overrides not currently supported.");
          }
        } else {
          // Manage the field if not already managed (may already exist if overriding a superclass field)
          if (fmd.getPersistenceModifier() == FieldPersistenceModifier.PERSISTENT) {
            boolean isPrimary = true;
            if (fmd.getTable() != null && fmd.getJoinMetaData() == null) {
              // Field has a table specified and is not a 1-N with join table
              // so is mapped to a secondary table
              isPrimary = false;
            }
            if (isPrimary) {
              // Add the field to this table
              JavaTypeMapping mapping = dba.getMappingManager(storeMgr).getMapping(
                  this, fmd, dba, clr, FieldRole.ROLE_FIELD);
              addFieldMapping(mapping);
            } else {
              throw new UnsupportedOperationException("No support for secondary tables.");
            }
          } else if (fmd.getPersistenceModifier() != FieldPersistenceModifier.TRANSACTIONAL) {
//            throw new NucleusException(LOCALISER.msg("057006", fmd.getName())).setFatal();
          }

          // Calculate if we need a FK adding due to a 1-N (FK) relationship
          boolean needsFKToContainerOwner = false;
          int relationType = fmd.getRelationType(clr);
          if (relationType == Relation.ONE_TO_MANY_BI) {
            AbstractMemberMetaData[] relatedMmds = fmd.getRelatedMemberMetaData(clr);
            if (fmd.getJoinMetaData() == null && relatedMmds[0].getJoinMetaData() == null) {
              needsFKToContainerOwner = true;
            }
          } else if (relationType == Relation.ONE_TO_MANY_UNI) {
            if (fmd.getJoinMetaData() == null) {
              needsFKToContainerOwner = true;
            }
          }

          if (needsFKToContainerOwner) {
            // 1-N uni/bidirectional using FK, so update the element side with a FK
            if ((fmd.getCollection() != null && !SCOUtils.collectionHasSerialisedElements(fmd)) ||
                (fmd.getArray() != null && !SCOUtils.arrayIsStoredInSingleColumn(fmd))) {
              // 1-N ForeignKey collection/array, so add FK to element table
              AbstractClassMetaData elementCmd;
              if (fmd.hasCollection()) {
                // Collection
                elementCmd = storeMgr.getOMFContext().getMetaDataManager()
                    .getMetaDataForClass(fmd.getCollection().getElementType(), clr);
              } else {
                // Array
                elementCmd = storeMgr.getOMFContext().getMetaDataManager()
                    .getMetaDataForClass(fmd.getType().getComponentType(), clr);
              }
              if (elementCmd == null) {
                // Elements that are reference types or non-PC will come through here
              } else {
                AbstractClassMetaData[] elementCmds;
                // TODO : Cater for interface elements, and get the metadata for the implementation classes here
                if (elementCmd.getInheritanceMetaData().getStrategy() == InheritanceStrategy.SUBCLASS_TABLE) {
                  elementCmds = storeMgr.getClassesManagingTableForClass(elementCmd, clr);
                } else {
                  elementCmds = new ClassMetaData[1];
                  elementCmds[0] = elementCmd;
                }

                // Run callbacks for each of the element classes.
                for (AbstractClassMetaData elementCmd1 : elementCmds) {
                  callbacks.put(elementCmd1.getFullClassName(), new CallBack(fmd));
                  DatastoreTable dt =
                      (DatastoreTable) storeMgr.getDatastoreClass(elementCmd1.getFullClassName(), clr);
                  dt.runCallBacks();
                }
              }
            } else if (fmd.getMap() != null && !SCOUtils.mapHasSerialisedKeysAndValues(fmd)) {
              // 1-N ForeignKey map, so add FK to value table
              if (fmd.getKeyMetaData() != null && fmd.getKeyMetaData().getMappedBy() != null) {
                // Key is stored in the value table so add the FK to the value table
                AbstractClassMetaData valueCmd = storeMgr.getOMFContext().getMetaDataManager()
                    .getMetaDataForClass(fmd.getMap().getValueType(), clr);
                if (valueCmd == null) {
                  // Interface elements will come through here and java.lang.String and others as well
                } else {
                }
              } else if (fmd.getValueMetaData() != null && fmd.getValueMetaData().getMappedBy() != null) {
                // Value is stored in the key table so add the FK to the key table
                AbstractClassMetaData keyCmd = storeMgr.getOMFContext().getMetaDataManager()
                    .getMetaDataForClass(fmd.getMap().getKeyType(), clr);
                if (keyCmd == null) {
                  // Interface elements will come through here and java.lang.String and others as well
                } else {
                }
              }
            }
          }
        }
      }
    }
  }

  protected void addFieldMapping(JavaTypeMapping fieldMapping) {
    AbstractMemberMetaData fmd = fieldMapping.getMemberMetaData();
    fieldMappingsMap.put(fmd, fieldMapping);
    // Update highest field number if this is higher
    int absoluteFieldNumber = fmd.getAbsoluteFieldNumber();
    if (absoluteFieldNumber > highestFieldNumber) {
      highestFieldNumber = absoluteFieldNumber;
    }

    // isDependent() returns false for one-to-many even with cascade delete.
    // Not sure why but that's why we check both isDependent and isCascadeDelete
    if (fmd.isDependent() || fmd.isCascadeDelete()) {
      dependentMemberMetaData.add(fmd);
    }
  }

  public boolean managesField(String fieldName) {
    return fieldName != null && getMappingForFieldName(fieldName) != null;

  }

  /**
   * Accessor for the JavaTypeMapping that is handling the field of the specified name. Returns the
   * first one that matches.
   *
   * @param fieldName Name of the field
   * @return The java type mapping
   */
  protected JavaTypeMapping getMappingForFieldName(String fieldName) {
    Set fields = fieldMappingsMap.keySet();
    for (Object field : fields) {
      AbstractMemberMetaData fmd = (AbstractMemberMetaData) field;
      if (fmd.getFullFieldName().equals(fieldName)) {
        return fieldMappingsMap.get(fmd);
      }
    }
    return null;
  }

  void addDatastoreId(ColumnMetaDataContainer columnContainer, DatastoreClass refTable,
      AbstractClassMetaData cmd) {
    datastoreIDMapping = new OIDMapping();
    datastoreIDMapping.initialize(dba, cmd.getFullClassName());

    // Create a ColumnMetaData in the container if none is defined
    ColumnMetaData colmd;
    if (columnContainer == null) {
      colmd = new ColumnMetaData();
    } else if (columnContainer.getColumnMetaData().length < 1) {
      colmd = new ColumnMetaData();
    } else {
      colmd = columnContainer.getColumnMetaData()[0];
    }
    if (colmd.getName() == null) {
      // Provide default column naming if none is defined
      if (refTable != null) {
        colmd.setName(storeMgr.getIdentifierFactory()
            .newDatastoreFieldIdentifier(refTable.getIdentifier().getIdentifierName(),
                this.storeMgr.getOMFContext().getTypeManager().isDefaultEmbeddedType(OID.class),
                FieldRole.ROLE_OWNER).getIdentifierName());
      } else {
        colmd.setName(
            storeMgr.getIdentifierFactory().newDatastoreFieldIdentifier(identifier.getIdentifierName(),
                this.storeMgr.getOMFContext().getTypeManager().isDefaultEmbeddedType(OID.class),
                FieldRole.ROLE_NONE).getIdentifierName());
      }
    }

    // Add the datastore identity column as the PK
    DatastoreField idColumn = addDatastoreField(OID.class.getName(),
        storeMgr.getIdentifierFactory().newIdentifier(IdentifierType.COLUMN, colmd.getName()),
        datastoreIDMapping, colmd);
    idColumn.setAsPrimaryKey();

    // Set the identity column type based on the IdentityStrategy
    String strategyName = cmd.getIdentityMetaData().getValueStrategy().toString();
    if (cmd.getIdentityMetaData().getValueStrategy().equals(IdentityStrategy.CUSTOM)) {
      strategyName = cmd.getIdentityMetaData().getValueStrategy().getCustomName();
    }

    // Check the POID type being stored
    Class poidClass = Long.class;
    ConfigurationElement elem =
        storeMgr.getOMFContext().getPluginManager().getConfigurationElementForExtension(
            "org.datanucleus.store_valuegenerator",
            new String[]{"name", "unique"}, new String[]{strategyName, "true"});
    if (elem == null) {
      // Not datastore-independent, so try for this datastore
      elem = storeMgr.getOMFContext().getPluginManager().getConfigurationElementForExtension(
          "org.datanucleus.store_valuegenerator",
          new String[]{"name", "datastore"},
          new String[]{strategyName, storeMgr.getStoreManagerKey()});
    }
    if (elem != null) {
      // Set the generator name (for use by the PoidManager)
      String generatorClassName = elem.getAttribute("class-name");
      Class generatorClass =
          getStoreManager().getOMFContext().getClassLoaderResolver(null)
              .classForName(generatorClassName);
      try {
        poidClass = (Class) generatorClass.getMethod("getStorageClass").invoke(null);
      }
      catch (Exception e) {
        // Unable to get the storage class from the PoidGenerator class
        NucleusLogger.VALUEGENERATION
            .warn("Error retrieving storage class for POID generator " + generatorClassName +
                " " + e.getMessage());
      }
    }

    dba.getMappingManager(storeMgr)
        .createDatastoreMapping(datastoreIDMapping, idColumn, poidClass.getName());

    // Handle any auto-increment requirement
    if (isObjectIDDatastoreAttributed()) {
      // Only the base class can be autoincremented
//      idColumn.setAutoIncrement(true);
    }

    // Check if auto-increment and that it is supported by this RDBMS
//    if (idColumn.isAutoIncrement() && !dba
//        .supportsOption(org.datanucleus.store.mapped.DatastoreAdapter.IDENTITY_COLUMNS)) {
//      throw new NucleusException(LOCALISER.msg("057020",
//          cmd.getFullClassName(), "datastore-identity")).setFatal();
//    }
  }

  private void initializePK() {
    AbstractMemberMetaData[] fieldsToAdd = new AbstractMemberMetaData[cmd
        .getNoOfPrimaryKeyMembers()];

    // Initialise Primary Key mappings for application id with PK fields in this class
    int pkFieldNum = 0;
    int fieldCount = cmd.getNoOfManagedMembers();
    boolean hasPrimaryKeyInThisClass = false;
    if (cmd.getNoOfPrimaryKeyMembers() > 0) {
      pkMappings = new JavaTypeMapping[cmd.getNoOfPrimaryKeyMembers()];
      if (cmd.getInheritanceMetaData().getStrategy() == InheritanceStrategy.COMPLETE_TABLE) {
        // COMPLETE-TABLE so use root class metadata and add PK members
        // TODO Does this allow for overridden PK field info ?
        AbstractClassMetaData baseCmd = cmd.getBaseAbstractClassMetaData();
        fieldCount = baseCmd.getNoOfManagedMembers();
        for (int relFieldNum = 0; relFieldNum < fieldCount; ++relFieldNum) {
          AbstractMemberMetaData mmd = baseCmd.getMetaDataForManagedMemberAtPosition(relFieldNum);
          if (mmd.isPrimaryKey()) {
            if (mmd.getPersistenceModifier() == FieldPersistenceModifier.PERSISTENT) {
              fieldsToAdd[pkFieldNum++] = mmd;
              hasPrimaryKeyInThisClass = true;
            } else if (mmd.getPersistenceModifier() != FieldPersistenceModifier.TRANSACTIONAL) {
              throw new NucleusException(LOCALISER.msg("057006", mmd.getName())).setFatal();
            }

            // Check if auto-increment and that it is supported by this datastore
            if ((mmd.getValueStrategy() == IdentityStrategy.IDENTITY) &&
                !dba.supportsOption(DatastoreAdapter.IDENTITY_COLUMNS)) {
              throw new NucleusException(LOCALISER.msg("057020",
                  cmd.getFullClassName(), mmd.getName())).setFatal();
            }
          }
        }
      } else {
        for (int relFieldNum = 0; relFieldNum < fieldCount; ++relFieldNum) {
          AbstractMemberMetaData fmd = cmd.getMetaDataForManagedMemberAtPosition(relFieldNum);
          if (fmd.isPrimaryKey()) {
            if (fmd.getPersistenceModifier() == FieldPersistenceModifier.PERSISTENT) {
              fieldsToAdd[pkFieldNum++] = fmd;
              hasPrimaryKeyInThisClass = true;
            } else if (fmd.getPersistenceModifier() != FieldPersistenceModifier.TRANSACTIONAL) {
              throw new NucleusException(LOCALISER.msg("057006", fmd.getName())).setFatal();
            }

            // Check if auto-increment and that it is supported by this datastore
            if ((fmd.getValueStrategy() == IdentityStrategy.IDENTITY) &&
                !dba.supportsOption(DatastoreAdapter.IDENTITY_COLUMNS)) {
              throw new NucleusException(LOCALISER.msg("057020",
                  cmd.getFullClassName(), fmd.getName())).setFatal();
            }
          }
        }
      }
    }

    // No Primary Key defined, so search for superclass or handle datastore id
    if (!hasPrimaryKeyInThisClass) {
      if (cmd.getIdentityType() == IdentityType.APPLICATION) {
        // application-identity
        DatastoreClass elementCT =
            storeMgr.getDatastoreClass(cmd.getPersistenceCapableSuperclass(), clr);
        if (elementCT != null) {
          // Superclass has a table so copy its PK mappings
          ColumnMetaDataContainer colContainer = null;
          if (cmd.getInheritanceMetaData() != null) {
            // Try via <inheritance><join>...</join></inheritance>
            colContainer = cmd.getInheritanceMetaData().getJoinMetaData();
          }
          if (colContainer == null) {
            // Try via <primary-key>...</primary-key>
            colContainer = cmd.getPrimaryKeyMetaData();
          }

          addApplicationIdUsingClassTableId(colContainer, elementCT, clr, cmd);
        } else {
          // Superclass has no table so create new mappings and columns
          AbstractClassMetaData pkCmd =
              storeMgr.getClassWithPrimaryKeyForClass(cmd.getSuperAbstractClassMetaData(), clr);
          if (pkCmd != null) {
            pkMappings = new JavaTypeMapping[pkCmd.getNoOfPrimaryKeyMembers()];
            pkFieldNum = 0;
            fieldCount = pkCmd.getNoOfInheritedManagedMembers() + pkCmd.getNoOfManagedMembers();
            for (int absFieldNum = 0; absFieldNum < fieldCount; ++absFieldNum) {
              AbstractMemberMetaData fmd = pkCmd
                  .getMetaDataForManagedMemberAtAbsolutePosition(absFieldNum);
              if (fmd.isPrimaryKey()) {
                AbstractMemberMetaData overriddenFmd = cmd.getOverriddenMember(fmd.getName());
                if (overriddenFmd != null) {
                  // PK field is overridden so use the overriding definition
                  fmd = overriddenFmd;
                }

                if (fmd.getPersistenceModifier() == FieldPersistenceModifier.PERSISTENT) {
                  fieldsToAdd[pkFieldNum++] = fmd;
                } else if (fmd.getPersistenceModifier() != FieldPersistenceModifier.TRANSACTIONAL) {
                  throw new NucleusException(LOCALISER.msg("057006", fmd.getName())).setFatal();
                }
              }
            }
          }
        }
      } else if (cmd.getIdentityType() == IdentityType.DATASTORE) {
        // datastore-identity
        ColumnMetaDataContainer colContainer = null;
        if (cmd.getIdentityMetaData() != null
            && cmd.getIdentityMetaData().getColumnMetaData() != null &&
            cmd.getIdentityMetaData().getColumnMetaData().length > 0) {
          // Try via <datastore-identity>...</datastore-identity>
          colContainer = cmd.getIdentityMetaData();
        }
        if (colContainer == null) {
          // Try via <primary-key>...</primary-key>
          colContainer = cmd.getPrimaryKeyMetaData();
        }
        addDatastoreId(colContainer, null, cmd);
      } else if (cmd.getIdentityType() == IdentityType.NONDURABLE) {
        // Do nothing since no identity!
      }
    }

    //add field mappings in the end, so we compute all columns after the post initialize
    for (int i = 0; i < fieldsToAdd.length; i++) {
      if (fieldsToAdd[i] != null) {
        try {
          DatastoreClass datastoreClass = getStoreManager()
              .getDatastoreClass(fieldsToAdd[i].getType().getName(), clr);
          if (datastoreClass.getIDMapping() == null) {
            throw new NucleusException(
                "Unsupported relationship with field " + fieldsToAdd[i].getFullFieldName())
                .setFatal();
          }
        }
        catch (NoTableManagedException ex) {
          //do nothing
        }
        JavaTypeMapping fieldMapping = dba.getMappingManager(storeMgr)
            .getMapping(this, fieldsToAdd[i], dba, clr, FieldRole.ROLE_FIELD);
        addFieldMapping(fieldMapping);
        pkMappings[i] = fieldMapping;
      }
    }
    initializeIDMapping();
  }

  /**
   * Initialize the ID Mapping
   */
  private void initializeIDMapping() {
    if (idMapping != null) {
      return;
    }

    final PersistenceCapableMapping mapping = new PersistenceCapableMapping();
    mapping.initialize(getStoreManager().getDatastoreAdapter(), cmd.getFullClassName());
    if (getIdentityType() == IdentityType.DATASTORE) {
      mapping.addJavaTypeMapping(datastoreIDMapping);
    } else if (getIdentityType() == IdentityType.APPLICATION) {
      for (JavaTypeMapping pkMapping : pkMappings) {
        mapping.addJavaTypeMapping(pkMapping);
      }
    } else {
      // Nothing to do for nondurable since no identity
    }

    idMapping = mapping;
  }

  final void addApplicationIdUsingClassTableId(ColumnMetaDataContainer columnContainer,
      DatastoreClass refTable, ClassLoaderResolver clr, AbstractClassMetaData cmd) {
    ColumnMetaData[] userdefinedCols = null;
    int nextUserdefinedCol = 0;
    if (columnContainer != null) {
      userdefinedCols = columnContainer.getColumnMetaData();
    }

    pkMappings = new JavaTypeMapping[cmd.getPKMemberPositions().length];
    for (int i = 0; i < cmd.getPKMemberPositions().length; i++) {
      AbstractMemberMetaData fmd = cmd
          .getMetaDataForManagedMemberAtAbsolutePosition(cmd.getPKMemberPositions()[i]);
      JavaTypeMapping mapping = refTable.getMemberMapping(fmd);
      if (mapping == null) {
        //probably due to invalid metadata defined by the user
        throw new NucleusUserException("Cannot find mapping for field " + fmd.getFullFieldName() +
            " in table " + refTable.toString() + " " +
            StringUtils.objectArrayToString(refTable.getDatastoreFields()));
      }

      JavaTypeMapping masterMapping = storeMgr.getMappingManager()
          .getMapping(clr.classForName(mapping.getType()));
      masterMapping.setMemberInformation(fmd, this); // Update field info in mapping
      pkMappings[i] = masterMapping;

      // Loop through each id column in the reference table and add the same here
      // applying the required names from the columnContainer
      for (int j = 0; j < mapping.getNumberOfDatastoreFields(); j++) {
        JavaTypeMapping m = masterMapping;
        DatastoreField refColumn = mapping.getDataStoreMapping(j).getDatastoreField();
        if (mapping instanceof PersistenceCapableMapping) {
          m = storeMgr.getMappingManager()
              .getMapping(clr.classForName(refColumn.getJavaTypeMapping().getType()));
          ((PersistenceCapableMapping) masterMapping).addJavaTypeMapping(m);
        }

        ColumnMetaData userdefinedColumn = null;
        if (userdefinedCols != null) {
          for (ColumnMetaData userdefinedCol : userdefinedCols) {
            if (refColumn.getIdentifier().toString().equals(userdefinedCol.getTarget())) {
              userdefinedColumn = userdefinedCol;
              break;
            }
          }
          if (userdefinedColumn == null && nextUserdefinedCol < userdefinedCols.length) {
            userdefinedColumn = userdefinedCols[nextUserdefinedCol++];
          }
        }

        // Add this application identity column
        DatastoreField idColumn;
        if (userdefinedColumn != null) {
          // User has provided a name for this column
          // Currently we only use the column namings from the users definition but we could easily
          // take more of their details.
          idColumn = addDatastoreField(refColumn.getStoredJavaType(),
              storeMgr.getIdentifierFactory().newIdentifier(IdentifierType.COLUMN,
                  userdefinedColumn.getName()),
              m, refColumn.getColumnMetaData());
        } else {
          // No name provided so take same as superclass
          idColumn = addDatastoreField(refColumn.getStoredJavaType(), refColumn.getIdentifier(),
              m, refColumn.getColumnMetaData());
        }
        if (mapping != null
            && mapping.getDataStoreMapping(j).getDatastoreField().getColumnMetaData() != null) {
          refColumn.copyConfigurationTo(idColumn);
        }
        idColumn.setAsPrimaryKey();

        // Set the column type based on the field.getType()
        getStoreManager().getMappingManager()
            .createDatastoreMapping(m, idColumn, refColumn.getJavaTypeMapping().getType());
      }

      // Update highest field number if this is higher
      int absoluteFieldNumber = fmd.getAbsoluteFieldNumber();
      if (absoluteFieldNumber > highestFieldNumber) {
        highestFieldNumber = absoluteFieldNumber;
      }
    }
  }

  public void provideDatastoreIdMappings(MappingConsumer consumer) {
    consumer.preConsumeMapping(highestFieldNumber + 1);

    if (getIdentityType() == IdentityType.DATASTORE) {
      consumer.consumeMapping(getDataStoreObjectIdMapping(), MappingConsumer.MAPPING_TYPE_DATASTORE_ID);
    }
  }

  public void providePrimaryKeyMappings(MappingConsumer consumer) {
    consumer.preConsumeMapping(highestFieldNumber + 1);

    if (pkMappings != null) {
      // Application identity
      int[] primaryKeyFieldNumbers = cmd.getPKMemberPositions();
      for (int i = 0; i < pkMappings.length; i++) {
        // Make the assumption that the pkMappings are in the same order as the absolute field numbers
        AbstractMemberMetaData fmd = cmd
            .getMetaDataForManagedMemberAtAbsolutePosition(primaryKeyFieldNumbers[i]);
        consumer.consumeMapping(pkMappings[i], fmd);
      }
    } else {
      // Datastore identity
      int[] primaryKeyFieldNumbers = cmd.getPKMemberPositions();
      int countPkFields = cmd.getNoOfPrimaryKeyMembers();
      for (int i = 0; i < countPkFields; i++) {
        AbstractMemberMetaData pkfmd = cmd
            .getMetaDataForManagedMemberAtAbsolutePosition(primaryKeyFieldNumbers[i]);
        consumer.consumeMapping(getMemberMapping(pkfmd), pkfmd);
      }
    }
  }

  public void provideNonPrimaryKeyMappings(MappingConsumer consumer) {
    consumer.preConsumeMapping(highestFieldNumber + 1);

    Set fieldNumbersSet = fieldMappingsMap.keySet();
    for (Object aFieldNumbersSet : fieldNumbersSet) {
      AbstractMemberMetaData fmd = (AbstractMemberMetaData) aFieldNumbersSet;
      JavaTypeMapping fieldMapping = fieldMappingsMap.get(fmd);
      if (fieldMapping != null) {
        if (!fmd.isPrimaryKey()) {
          consumer.consumeMapping(fieldMapping, fmd);
        }
      }
    }
  }

  public void provideMappingsForMembers(MappingConsumer consumer, AbstractMemberMetaData[] mmds,
      boolean includeSecondaryTables) {
    for (AbstractMemberMetaData aFieldMetaData : mmds) {
      JavaTypeMapping fieldMapping = fieldMappingsMap.get(aFieldMetaData);
      if (fieldMapping != null) {
        if (!aFieldMetaData.isPrimaryKey()) {
          consumer.consumeMapping(fieldMapping, aFieldMetaData);
        }
      }
    }
  }

  public void provideVersionMappings(MappingConsumer consumer) {
  }

  public void provideDiscriminatorMappings(MappingConsumer consumer) {
  }

  public void provideUnmappedDatastoreFields(MappingConsumer consumer) {
  }

  public void provideExternalMappings(MappingConsumer consumer, int mappingType) {
  }

  public JavaTypeMapping getExternalMapping(AbstractMemberMetaData fmd, int mappingType) {
    if (mappingType == MappingConsumer.MAPPING_TYPE_EXTERNAL_FK) {
      return getExternalFkMappings().get(fmd);
    } else if (mappingType == MappingConsumer.MAPPING_TYPE_EXTERNAL_FK_DISCRIM) {
      return null; //getExternalFkDiscriminatorMappings().get(fmd);
    } else if (mappingType == MappingConsumer.MAPPING_TYPE_EXTERNAL_INDEX) {
      return null; //getExternalOrderMappings().get(fmd);
    } else {
      return null;
    }
  }

  public AbstractMemberMetaData getMetaDataForExternalMapping(JavaTypeMapping mapping,
      int mappingType) {
    return null;
  }

  public DiscriminatorMetaData getDiscriminatorMetaData() {
    return null;
  }

  public JavaTypeMapping getDiscriminatorMapping(boolean allowSuperclasses) {
    return null;
  }

  public VersionMetaData getVersionMetaData() {
    return null;
  }

  public JavaTypeMapping getVersionMapping(boolean allowSuperclasses) {
    return null;
  }

  public List<AbstractMemberMetaData> getDependentMemberMetaData() {
    return dependentMemberMetaData;
  }

  private Map<AbstractMemberMetaData, JavaTypeMapping> getExternalFkMappings() {
    return externalFkMappings;
  }

  /**
   * Accessor for all of the order mappings (used by FK Lists, Collections, Arrays)
   * @return The mappings for the order columns.
   **/
  private Map<AbstractMemberMetaData, JavaTypeMapping> getExternalOrderMappings() {
    return externalOrderMappings;
  }

  /**
   * Execute the callbacks for the classes that this table maps to.
   */
  private void runCallBacks() {
    Collection c = (Collection) callbacks.remove(cmd.getFullClassName());
    if (c == null) {
      return;
    }
    for (Object aC : c) {
      CallBack callback = (CallBack) aC;

      if (callback.fmd.getJoinMetaData() == null) {
        // 1-N FK relationship
        AbstractMemberMetaData ownerFmd = callback.fmd;
        if (ownerFmd.getMappedBy() != null) {
          // Bidirectional (element has a PC mapping to the owner)
          // Check that the "mapped-by" field in the other class actually exists
          AbstractMemberMetaData fmd = cmd.getMetaDataForMember(ownerFmd.getMappedBy());
          if (fmd == null) {
            throw new NucleusUserException(LOCALISER.msg("057036",
                                                         ownerFmd.getMappedBy(),
                                                         cmd.getFullClassName(),
                                                         ownerFmd.getFullFieldName()));
          }

          JavaTypeMapping orderMapping = null;

          // Add the order mapping as necessary
          addOrderMapping(ownerFmd, orderMapping);
        } else {
          // Unidirectional (element knows nothing about the owner)
          String ownerClassName = ownerFmd.getAbstractClassMetaData().getFullClassName();
          JavaTypeMapping fkMapping = new PersistenceCapableMapping();
          fkMapping.initialize(dba, ownerClassName);
          JavaTypeMapping orderMapping = null;

          // Get the owner id mapping of the "1" end
          JavaTypeMapping ownerIdMapping =
              storeMgr.getDatastoreClass(ownerClassName, clr).getIDMapping();
          ColumnMetaDataContainer colmdContainer = null;
          if (ownerFmd.hasCollection() || ownerFmd.hasArray()) {
            // 1-N Collection/array
            colmdContainer = ownerFmd.getElementMetaData();
          } else if (ownerFmd.hasMap() && ownerFmd.getKeyMetaData() != null
                     && ownerFmd.getKeyMetaData().getMappedBy() != null) {
            // 1-N Map with key stored in the value
            colmdContainer = ownerFmd.getValueMetaData();
          } else if (ownerFmd.hasMap() && ownerFmd.getValueMetaData() != null
                     && ownerFmd.getValueMetaData().getMappedBy() != null) {
            // 1-N Map with value stored in the key
            colmdContainer = ownerFmd.getKeyMetaData();
          }
          CorrespondentColumnsMapper correspondentColumnsMapping =
              new CorrespondentColumnsMapper(colmdContainer, ownerIdMapping, true);
          int countIdFields = ownerIdMapping.getNumberOfDatastoreFields();
          for (int i = 0; i < countIdFields; i++) {
            DatastoreMapping refDatastoreMapping = ownerIdMapping.getDataStoreMapping(i);
            JavaTypeMapping mapping = storeMgr.getMappingManager()
                    .getMapping(refDatastoreMapping.getJavaTypeMapping().getJavaType());
            ColumnMetaData colmd = correspondentColumnsMapping.getColumnMetaDataByIdentifier(
                    refDatastoreMapping.getDatastoreField().getIdentifier());
            if (colmd == null) {
              throw new NucleusUserException(LOCALISER.msg(
                  "057035",
                  refDatastoreMapping.getDatastoreField().getIdentifier(),
                  toString())).setFatal();
            }

            DatastoreIdentifier identifier;
            IdentifierFactory idFactory = storeMgr.getIdentifierFactory();
            if (colmd.getName() == null || colmd.getName().length() < 1) {
              // No user provided name so generate one
              identifier = idFactory.newForeignKeyFieldIdentifier(
                  ownerFmd, null, refDatastoreMapping.getDatastoreField().getIdentifier(),
                  storeMgr.getOMFContext().getTypeManager().isDefaultEmbeddedType(mapping.getJavaType()),
                  FieldRole.ROLE_OWNER);
            } else {
              // User-defined name
              identifier = idFactory.newDatastoreFieldIdentifier(colmd.getName());
            }
            DatastoreField refColumn =
                addDatastoreField(mapping.getJavaType().getName(), identifier, mapping, colmd);
            refDatastoreMapping.getDatastoreField().copyConfigurationTo(refColumn);

            if ((colmd.getAllowsNull() == null) ||
                (colmd.getAllowsNull() != null && colmd.isAllowsNull())) {
              // User either wants it nullable, or havent specified anything, so make it nullable
              refColumn.setNullable();
            }

            fkMapping.addDataStoreMapping(getStoreManager().getMappingManager()
                .createDatastoreMapping(mapping, refColumn,
                                        refDatastoreMapping.getJavaTypeMapping().getJavaType().getName()));
            ((PersistenceCapableMapping) fkMapping).addJavaTypeMapping(mapping);
          }

          // Save the external FK
          getExternalFkMappings().put(ownerFmd, fkMapping);

          // Add the order mapping as necessary
          addOrderMapping(ownerFmd, orderMapping);
        }
      }
    }
  }

  private JavaTypeMapping addOrderMapping(AbstractMemberMetaData fmd, JavaTypeMapping orderMapping) {
    boolean needsOrderMapping = false;
    OrderMetaData omd = fmd.getOrderMetaData();
    if (fmd.hasArray()) {
      // Array field always has the index mapping
      needsOrderMapping = true;
    } else if (List.class.isAssignableFrom(fmd.getType())) {
      // List field
      needsOrderMapping = !(omd != null && !omd.isIndexedList());
    } else if (java.util.Collection.class.isAssignableFrom(fmd.getType()) &&
               omd != null && omd.isIndexedList() && omd.getMappedBy() == null) {
      // Collection field with <order> and is indexed list so needs order mapping
      needsOrderMapping = true;
    }

    if (needsOrderMapping) {
      // if the field is list or array type, add index column
      if (orderMapping == null) {
        // Create new order mapping since we need one and we aren't using a shared FK
        orderMapping = this.addOrderColumn(fmd);
      }
      getExternalOrderMappings().put(fmd, orderMapping);
    }

    return orderMapping;
  }

  /**
   * Adds an ordering column to the element table (this) in inverse list relationships. Used to
   * store the position of the element in the List. If the &lt;order&gt; provides a mapped-by, this
   * will return the existing column mapping.
   *
   * @param fmd The MetaData for the column to map to
   * @return The Mapping for the order column
   */
  private JavaTypeMapping addOrderColumn(AbstractMemberMetaData fmd) {
    Class indexType = Integer.class;
    JavaTypeMapping indexMapping = new IndexMapping();
    indexMapping.initialize(dba, indexType.getName());
    IdentifierFactory idFactory = storeMgr.getIdentifierFactory();
    DatastoreIdentifier indexColumnName = null;
    ColumnMetaData colmd = null;

    // Allow for any user definition in OrderMetaData
    OrderMetaData omd = fmd.getOrderMetaData();
    if (omd != null) {
      colmd =
          (omd.getColumnMetaData() != null && omd.getColumnMetaData().length > 0 ? omd
              .getColumnMetaData()[0] : null);
      if (omd.getMappedBy() != null) {
        // User has defined ordering using the column(s) of an existing field.
        JavaTypeMapping orderMapping = getMemberMapping(omd.getMappedBy());
        if (orderMapping == null) {
          throw new NucleusUserException(LOCALISER.msg("057021",
                                                       fmd.getFullFieldName(), omd.getMappedBy()));
        }
        if (!(orderMapping instanceof IntegerMapping) && !(orderMapping instanceof LongMapping)) {
          throw new NucleusUserException(LOCALISER.msg("057022",
                                                       fmd.getFullFieldName(), omd.getMappedBy()));
        }
        return orderMapping;
      }

      String colName;
      if (omd.getColumnMetaData() != null && omd.getColumnMetaData().length > 0
          && omd.getColumnMetaData()[0].getName() != null) {
        // User-defined name so create an identifier using it
        colName = omd.getColumnMetaData()[0].getName();
        indexColumnName = idFactory.newDatastoreFieldIdentifier(colName);
      }
    }
    if (indexColumnName == null) {
      // No name defined so generate one
      indexColumnName = idFactory.newForeignKeyFieldIdentifier(fmd, null, null,
                                                               storeMgr.getOMFContext()
                                                                   .getTypeManager().isDefaultEmbeddedType(
                                                                   indexType),
                                                               FieldRole.ROLE_INDEX);
    }

    DatastoreField column =
        addDatastoreField(indexType.getName(), indexColumnName, indexMapping, colmd);
    if (colmd == null || (colmd.getAllowsNull() == null) ||
        (colmd.getAllowsNull() != null && colmd.isAllowsNull())) {
      // User either wants it nullable, or havent specified anything, so make it nullable
      column.setNullable();
    }

    storeMgr.getMappingManager().createDatastoreMapping(indexMapping, column, indexType.getName());

    return indexMapping;
  }

  /**
   * Callbacks is used for inverse relationships to run some operation in the target table. The
   * operation is creation of columns, indexes, or whatever needed.
   */
  private static class CallBack {

    final AbstractMemberMetaData fmd;

    /**
     * Default constructor
     *
     * @param fmd The FieldMetaData
     */
    public CallBack(AbstractMemberMetaData fmd) {
      this.fmd = fmd;
    }
  }
}