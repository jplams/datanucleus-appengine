/**********************************************************************
Copyright (c) 2005 Andy Jefferson and others. All rights reserved.
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License. 


Contributors:
    ...
**********************************************************************/
package org.datanucleus.store.mapped.mapping;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import org.datanucleus.ClassLoaderResolver;
import org.datanucleus.ClassNameConstants;
import org.datanucleus.ExecutionContext;
import org.datanucleus.api.ApiAdapter;
import org.datanucleus.exceptions.NucleusException;
import org.datanucleus.exceptions.NucleusUserException;
import org.datanucleus.metadata.AbstractMemberMetaData;
import org.datanucleus.metadata.MetaDataUtils;
import org.datanucleus.state.ObjectProvider;
import org.datanucleus.store.mapped.DatastoreContainerObject;
import org.datanucleus.store.mapped.exceptions.NoDatastoreMappingException;
import org.datanucleus.store.types.SCO;
import org.datanucleus.store.types.SCOUtils;

/**
 * Mapping for a field that represents a container of objects, such as a List,
 * a Set, a Collection, a Map, or an array. Has an owner table. 
 * Can be represented in the following ways :-
 * <ul>
 * <li>Using a Join-Table, where the linkage between owner and elements/keys/values is stored in this table</li>
 * <li>Using a Foreign-Key in the element/key/value</li>
 * <li>Serialised into a single column in the owner table.</li>
 * <li>Embedded into the Join-Table</li>
 * <li>Serialised into a single-column in the Join-Table</li>
 * </ul>
 * The contents of the container are backed by a store, handling interfacing with the datastore.
 */
public abstract class AbstractContainerMapping extends SingleFieldMapping
{
    /**
     * Initialize this JavaTypeMapping for the given field/property.
     * @param mmd MetaData for the field/property to be mapped (if any)
     * @param container The datastore container storing this mapping (if any)
     * @param clr the ClassLoaderResolver
     */
    public void initialize(AbstractMemberMetaData mmd, DatastoreContainerObject container, ClassLoaderResolver clr)
    {
		super.initialize(mmd, container, clr);

        if (mmd.getContainer() == null)
        {
            throw new NucleusUserException(LOCALISER.msg("041023", mmd.getFullFieldName()));
        }

        if (!containerIsStoredInSingleColumn())
        {
            // Not serialised so we use JoinTable or ForeignKey
            storeMgr.newJoinDatastoreContainerObject(mmd, clr);
        }
    }

    /**
     * Whether the mapping has a simple (single column) datastore representation.
     * @return Whether it has a simple datastore representation (single column)
     */
    public boolean hasSimpleDatastoreRepresentation()
    {
        return false;
    }

    /**
     * Method to prepare a field mapping for use in the datastore.
     * This creates the column in the table.
     */
    protected void prepareDatastoreMapping()
    {
        if (containerIsStoredInSingleColumn())
        {
            // Serialised collections/maps/arrays should just create a (typically BLOB) column as normal in the owning table
            super.prepareDatastoreMapping();
        }
    }

    /**
     * Accessor for the name of the java-type actually used when mapping the particular datastore
     * field. This java-type must have an entry in the datastore mappings.
     * @param index requested datastore field index.
     * @return the name of java-type for the requested datastore field.
     */
    public String getJavaTypeForDatastoreMapping(int index)
    {
        if (containerIsStoredInSingleColumn())
        {
            // Serialised container so just return serialised
            return ClassNameConstants.JAVA_IO_SERIALIZABLE;
        }
        return super.getJavaTypeForDatastoreMapping(index);
    }

    /**
     * Method to set a field in the passed JDBC PreparedStatement using this mapping.
     * Only valid when the collection is serialised.
     * @param ec ExecutionContext
     * @param preparedStatement The JDBC Prepared Statement to be populated
     * @param exprIndex The parameter positions in the JDBC statement to populate.
     * @param value The value to populate into it
     */
    public void setObject(ExecutionContext ec, Object preparedStatement, int[] exprIndex, Object value)
    {
        if (mmd == null || !containerIsStoredInSingleColumn())
        {
            throw new NucleusException(failureMessage("setObject")).setFatal();
        }

        ObjectProvider[] sms = null;
        ApiAdapter api = ec.getApiAdapter();
        if (value != null)
        {
            Collection smsColl = null;
            if (value instanceof java.util.Collection)
            {
                Iterator elementsIter = ((java.util.Collection)value).iterator();
                while (elementsIter.hasNext())
                {
                    Object elem = elementsIter.next();
                    if (api.isPersistable(elem))
                    {
                        ObjectProvider sm = ec.findObjectProvider(elem);
                        if (sm != null)
                        {
                            if (smsColl == null)
                            {
                                smsColl = new HashSet();
                            }
                            smsColl.add(sm);
                        }
                    }
                }
            }
            else if (value instanceof java.util.Map)
            {
                Iterator entriesIter = ((java.util.Map)value).entrySet().iterator();
                while (entriesIter.hasNext())
                {
                    Map.Entry entry = (Map.Entry)entriesIter.next();
                    Object key = entry.getKey();
                    Object val = entry.getValue();
                    if (api.isPersistable(key))
                    {
                        ObjectProvider sm = ec.findObjectProvider(key);
                        if (sm != null)
                        {
                            if (smsColl == null)
                            {
                                smsColl = new HashSet();
                            }
                            smsColl.add(sm);
                        }
                    }
                    if (api.isPersistable(val))
                    {
                        ObjectProvider sm = ec.findObjectProvider(val);
                        if (sm != null)
                        {
                            if (smsColl == null)
                            {
                                smsColl = new HashSet();
                            }
                            smsColl.add(sm);
                        }
                    }
                }
            }
            if (smsColl != null)
            {
                sms = (ObjectProvider[])smsColl.toArray(new ObjectProvider[smsColl.size()]);
            }
        }

        if (sms != null)
        {
            // Set all PC objects as being stored (so we dont detach them in any serialisation process)
            for (int i=0;i<sms.length;i++)
            {
                sms[i].setStoringPC();
            }
        }
        getDatastoreMapping(0).setObject(preparedStatement, exprIndex[0], value);
        if (sms != null)
        {
            // Unset all PC objects now they are stored
            for (int i=0;i<sms.length;i++)
            {
                sms[i].unsetStoringPC();
            }
        }
    }

    /**
     * Method to retrieve an object from the passed JDBC ResultSet.
     * Only valid when the collection is serialised.
     * @param ec ExecutionContext
     * @param resultSet The ResultSet
     * @param exprIndex The parameter position(s) to extract the object from
     * @return The collection object
     */
    public Object getObject(ExecutionContext ec, Object resultSet, int[] exprIndex)
    {
        if (mmd == null || !containerIsStoredInSingleColumn())
        {
            throw new NucleusException(failureMessage("getObject")).setFatal();
        }
        return getDatastoreMapping(0).getObject(resultSet, exprIndex[0]);
    }

    /**
     * Accessor for the datastore class.
     * @return The datastore class
     */
    public DatastoreContainerObject getDatastoreContainer()
    {
        if (containerIsStoredInSingleColumn())
        {
            // Serialised into owner table
            return datastoreContainer;
        }
        else
        {
            return null;
        }
    }

    /**
     * Accessor for the number of datastore fields
     * @return The number of datastore fields
     */
    public int getNumberOfDatastoreMappings()
    {
        if (containerIsStoredInSingleColumn())
        {
            // Serialised into owner table
            return super.getNumberOfDatastoreMappings();
        }
        else
        {
            // By default, we have no columns as such for the container
            return 0;
        }
    }

    /**
     * Accessor for a datastore mapping
     * @param index The id of the mapping
     * @return The datastore mapping
     */
    public DatastoreMapping getDatastoreMapping(int index)
    {
        if (containerIsStoredInSingleColumn())
        {
            // Serialised into owner table
            return super.getDatastoreMapping(index);
        }
        else
        {
            throw new NoDatastoreMappingException(mmd.getName());
        }
    }

    /**
     * Accessor for the datastore mappings for this java type
     * @return The datastore mapping(s)
     */
    public DatastoreMapping[] getDatastoreMappings()
    {
        if (containerIsStoredInSingleColumn())
        {
            // Serialised into owner table
            return super.getDatastoreMappings();
        }
        else
        {
            throw new NoDatastoreMappingException(mmd.getName());
        }
    }

    /**
     * Convenience method to return if the container (collection or map) is stored
     * in the owning table as a column. The container is stored in a single column in the following situations :-
     * <UL>
     * <LI>The FieldMetaData has 'serialized="true"'</LI>
     * <LI>The collection has embedded-element="true" but no join table (and so serialised)</LI>
     * <LI>The map has embedded-key/value="true" but no join table (and so serialised)</LI>
     * <LI>The array has embedded-element="true" but no join table (and so serialised)</LI>
     * <LI>The array has no join table and non-PC elements (and so serialised)</LI>
     * </UL>
     * @return Whether it is stored in a single column in the main table.
     */
    protected boolean containerIsStoredInSingleColumn()
    {
        if (mmd != null && mmd.isSerialized())
        {
            return true;
        }
        else if (mmd != null && mmd.hasCollection() && SCOUtils.collectionHasSerialisedElements(mmd))
        {
            return true; // No join specified but serialised elements so serialise the field
        }
        else if (mmd != null && mmd.hasMap() && SCOUtils.mapHasSerialisedKeysAndValues(mmd))
        {
            return true; // No join specified but serialised keys/values so serialise the field
        }
        else if (mmd != null && mmd.hasArray() &&
            SCOUtils.arrayIsStoredInSingleColumn(mmd, storeMgr.getMetaDataManager()))
        {
            if (MetaDataUtils.getInstance().arrayStorableAsByteArrayInSingleColumn(mmd))
            {
                return false; // Storable as byte array
            }
            else
            {
                return true; // No join specified but serialised elements so serialise the field
            }
        }
        else
        {
            return false;
        }
    }

    /**
     * This mapping is included in the select statement.
     * @return Whether to include in select statement
     */
    public boolean includeInFetchStatement()
    {
        // Only include in a fetch when it is serialised into 1 column
        return containerIsStoredInSingleColumn();
    }

    /**
     * This mapping is included in the update statement.
     * @return Whether to include in update statement
     */
    public boolean includeInUpdateStatement()
    {
        // Only include in an update when it is serialised into 1 column
        return containerIsStoredInSingleColumn();
    }

    /**
     * This mapping is included in the insert statement.
     * @return Whether to include in insert statement
     */
    public boolean includeInInsertStatement()
    {
        // Only include in an insert when it is serialised into 1 column
        return containerIsStoredInSingleColumn();
    }

    /**
     * Method to replace the field that this mapping represents with a SCO wrapper.
     * The wrapper will be suitable for the passed instantiated type and if it is null will be for
     * the declared type of the field.
     * @param op ObjectProvider for the owning object
     * @param value The value to create the wrapper with
     * @param forInsert Whether to insert the SCO with this value
     * @param forUpdate Whether to update the SCO with this value
     * @return The SCO wrapper object that the field was replaced with
     */
    protected SCO replaceFieldWithWrapper(ObjectProvider op, Object value, boolean forInsert, boolean forUpdate)
    {
        Class type = mmd.getType();
        if( value!= null)
        {
            type = value.getClass();
        }
        else if (mmd.getOrderMetaData()!=null && type.isAssignableFrom(java.util.List.class))
        {
            type = java.util.List.class;
        }
        SCO sco = SCOUtils.newSCOInstance(op, mmd, mmd.getType(), type, value, forInsert, forUpdate, true);
        return sco;
    }

    // ---------------- Implementation of MappingCallbacks --------------------

    /**
     * Method to be called after any fetch of the owner class element.
     * @param sm StateManager of the owner
     */
    public void postFetch(ObjectProvider sm)
    {
        if (containerIsStoredInSingleColumn())
        {
            // Do nothing when serialised since we are handled in the main request
            return;
        }

        replaceFieldWithWrapper(sm, null, false, false);
    }
}