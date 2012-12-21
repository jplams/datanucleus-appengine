/**********************************************************************
Copyright (c) 2002 Mike Martin (TJDO) and others. All rights reserved. 
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
2003 Andy Jefferson - coding standards
2005 Andy Jefferson - added "value" field for cases where a parameter is put in a query "result"
2006 Andy Jefferson - remove typeInfo
    ...
**********************************************************************/
package org.datanucleus.store.mapped.mapping;

import org.datanucleus.ClassLoaderResolver;
import org.datanucleus.ExecutionContext;
import org.datanucleus.metadata.AbstractMemberMetaData;
import org.datanucleus.store.mapped.DatastoreContainerObject;
import org.datanucleus.store.mapped.DatastoreField;

/**
 * Simple mapping for a java field mapping to a single datastore field.
 */
public abstract class SingleFieldMapping extends JavaTypeMapping
{
    /**
     * Initialize this JavaTypeMapping with the given DatastoreAdapter for the given FieldMetaData.
     * @param container The datastore container storing this mapping (if any)
     * @param clr the ClassLoaderResolver
     * @param fmd FieldMetaData for the field to be mapped (if any)
     */
    public void initialize(AbstractMemberMetaData fmd, DatastoreContainerObject container, ClassLoaderResolver clr)
    {
		super.initialize(fmd, container, clr);
		prepareDatastoreMapping();
    }

    /**
     * Method to prepare a field mapping for use in the datastore.
     * This creates the column in the table.
     */
    protected void prepareDatastoreMapping()
    {
        MappingManager mmgr = storeMgr.getMappingManager();
        DatastoreField col = mmgr.createDatastoreField(this, getJavaTypeForDatastoreMapping(0), 0);
        mmgr.createDatastoreMapping(this, mmd, 0, col);
    }

    /**
     * Accessor for the default length for this type in the datastore (if applicable).
     * @param index requested datastore field index.
     * @return Default length
     */
    public int getDefaultLength(int index)
    {
        return -1;
    }

    /**
     * Accessor for an array of valid values that this type can take.
     * This can be used at the datastore side for restricting the values to be inserted.
     * @param index requested datastore field index.
     * @return The valid values
     */
    public Object[] getValidValues(int index)
    {
        return null;
    }

    /**
     * Accessor for the name of the java-type actually used when mapping the particular datastore
     * field. This java-type must have an entry in the datastore mappings.
     * @param index requested datastore field index.
     * @return the name of java-type for the requested datastore field.
     */
    public String getJavaTypeForDatastoreMapping(int index)
    {
        if (getJavaType() == null)
        {
            return null;
        }
        return getJavaType().getName();
    }

    /**
     * Equality operator
     * @param obj The object to compare with
     * @return Whether the objects are equal
     */
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }

        if (!(obj instanceof SingleFieldMapping))
        {
            return false;
        }

        SingleFieldMapping other = (SingleFieldMapping) obj;
        return getClass().equals(other.getClass()) && storeMgr.equals(other.storeMgr);
    }

    public void setBoolean(ExecutionContext ec, Object preparedStatement, int[] exprIndex, boolean value)
    {
        getDatastoreMapping(0).setBoolean(preparedStatement, exprIndex[0], value);
    }

    public boolean getBoolean(ExecutionContext ec, Object resultSet, int[] exprIndex)
    {
        return getDatastoreMapping(0).getBoolean(resultSet, exprIndex[0]);
    }

    public void setChar(ExecutionContext ec, Object preparedStatement, int[] exprIndex, char value)
    {
        getDatastoreMapping(0).setChar(preparedStatement, exprIndex[0], value);
    }

    public char getChar(ExecutionContext ec, Object resultSet, int[] exprIndex)
    {
        return getDatastoreMapping(0).getChar(resultSet, exprIndex[0]);
    }

    public void setByte(ExecutionContext ec, Object preparedStatement, int[] exprIndex, byte value)
    {
        getDatastoreMapping(0).setByte(preparedStatement, exprIndex[0], value);
    }

    public byte getByte(ExecutionContext ec, Object resultSet, int[] exprIndex)
    {
        return getDatastoreMapping(0).getByte(resultSet, exprIndex[0]);
    }

    public void setShort(ExecutionContext ec, Object preparedStatement, int[] exprIndex, short value)
    {
        getDatastoreMapping(0).setShort(preparedStatement, exprIndex[0], value);
    }

    public short getShort(ExecutionContext ec, Object resultSet, int[] exprIndex)
    {
        return getDatastoreMapping(0).getShort(resultSet, exprIndex[0]);
    }

    public void setInt(ExecutionContext ec, Object preparedStatement, int[] exprIndex, int value)
    {
        getDatastoreMapping(0).setInt(preparedStatement, exprIndex[0], value);
    }

    public int getInt(ExecutionContext ec, Object resultSet, int[] exprIndex)
    {
        return getDatastoreMapping(0).getInt(resultSet, exprIndex[0]);
    }

    public void setLong(ExecutionContext ec, Object preparedStatement, int[] exprIndex, long value)
    {
        getDatastoreMapping(0).setLong(preparedStatement, exprIndex[0], value);
    }

    public long getLong(ExecutionContext ec, Object resultSet, int[] exprIndex)
    {
        return getDatastoreMapping(0).getLong(resultSet, exprIndex[0]);
    }

    public void setFloat(ExecutionContext ec, Object preparedStatement, int[] exprIndex, float value)
    {
        getDatastoreMapping(0).setFloat(preparedStatement, exprIndex[0], value);
    }

    public float getFloat(ExecutionContext ec, Object resultSet, int[] exprIndex)
    {
        return getDatastoreMapping(0).getFloat(resultSet, exprIndex[0]);
    }

    public void setDouble(ExecutionContext ec, Object preparedStatement, int[] exprIndex, double value)
    {
        getDatastoreMapping(0).setDouble(preparedStatement, exprIndex[0], value);
    }

    public double getDouble(ExecutionContext ec, Object resultSet, int[] exprIndex)
    {
        return getDatastoreMapping(0).getDouble(resultSet, exprIndex[0]);
    }

    public void setString(ExecutionContext ec, Object preparedStatement, int[] exprIndex, String value)
    {
        getDatastoreMapping(0).setString(preparedStatement, exprIndex[0], value);
    }

    public String getString(ExecutionContext ec, Object resultSet, int[] exprIndex)
    {
        return getDatastoreMapping(0).getString(resultSet, exprIndex[0]);
    }

    public void setObject(ExecutionContext ec, Object preparedStatement, int[] exprIndex, Object value)
    {
        getDatastoreMapping(0).setObject(preparedStatement, exprIndex[0], value);
    }

    public Object getObject(ExecutionContext ec, Object resultSet, int[] exprIndex)
    {
        if (exprIndex == null)
        {
            return null;
        }
        return getDatastoreMapping(0).getObject(resultSet, exprIndex[0]);
    }
}