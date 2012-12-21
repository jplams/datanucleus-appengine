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

import org.datanucleus.ExecutionContext;
import org.datanucleus.api.ApiAdapter;
import org.datanucleus.state.ObjectProvider;

/**
 * Mapping for a serialised reference (Interface/Object) field.
 * Extends SerialisedMapping since that provides the basic serialisation mechanism,
 * adding on the addition of StateManagers to the serialised object whenever it is required
 * (since the object is a PersistenceCapable).
 */
public class SerialisedReferenceMapping extends SerialisedMapping
{
    /**
     * Method to populate parameter positions in a PreparedStatement with this object
     * @param ec execution context
     * @param preparedStatement The Prepared Statement
     * @param exprIndex The parameter positions to populate
     * @param value The value of the PC to use in populating the parameter positions
     */
    public void setObject(ExecutionContext ec, Object preparedStatement, int[] exprIndex, Object value)
    {
        if (mmd != null)
        {
            setObject(ec, preparedStatement, exprIndex, value, null, mmd.getAbsoluteFieldNumber());
        }
        else
        {
            super.setObject(ec, preparedStatement, exprIndex, value);
        }
    }

    /**
     * Method to populate parameter positions in a PreparedStatement with this object
     * @param ec execution context
     * @param preparedStatement The Prepared Statement
     * @param exprIndex The parameter positions to populate
     * @param value The value of the PC to use in populating the parameter positions
     * @param ownerSM ObjectProvider for the owning object
     * @param fieldNumber field number of this object in the owning object
     */
    public void setObject(ExecutionContext ec, Object preparedStatement, int[] exprIndex, Object value, 
            ObjectProvider ownerSM, int fieldNumber)
    {
        ApiAdapter api = ec.getApiAdapter();
        if (api.isPersistable(value))
        {
            // Assign a StateManager to the serialised object if none present
            ObjectProvider embSM = ec.findObjectProvider(value);
            if (embSM == null || api.getExecutionContext(value) == null)
            {
                embSM = ec.newObjectProviderForEmbedded(value, false, ownerSM, fieldNumber);
            }
        }

        ObjectProvider sm = null;
        if (api.isPersistable(value))
        {
            // Find SM for serialised PC object
            sm = ec.findObjectProvider(value);
        }

        if (sm != null)
        {
            sm.setStoringPC();
        }
        getDatastoreMapping(0).setObject(preparedStatement, exprIndex[0], value);
        if (sm != null)
        {
            sm.unsetStoringPC();
        }
    }

    /**
     * Method to extract the value of the PersistenceCapable from a ResultSet.
     * @param ec execution context
     * @param resultSet The ResultSet
     * @param exprIndex The parameter positions in the result set to use.
     * @return The (deserialised) PersistenceCapable object
     */
    public Object getObject(ExecutionContext ec, Object resultSet, int[] exprIndex)
    {
        if (mmd != null)
        {
            return getObject(ec, resultSet, exprIndex, null, mmd.getAbsoluteFieldNumber());
        }
        else
        {
            return super.getObject(ec, resultSet, exprIndex);
        }
    }

    /**
     * Method to extract the value of the PersistenceCapable from a ResultSet.
     * @param ec execution context
     * @param resultSet The ResultSet
     * @param exprIndex The parameter positions in the result set to use.
     * @param ownerSM The owning object
     * @param fieldNumber Absolute number of field in owner object
     * @return The (deserialised) PersistenceCapable object
     */
    public Object getObject(ExecutionContext ec, Object resultSet, int[] exprIndex, ObjectProvider ownerSM, int fieldNumber)
    {
        Object obj = getDatastoreMapping(0).getObject(resultSet, exprIndex[0]);
        ApiAdapter api = ec.getApiAdapter();
        if (api.isPersistable(obj))
        {
            // Assign a StateManager to the serialised object if none present
            ObjectProvider embSM = ec.findObjectProvider(obj);
            if (embSM == null || api.getExecutionContext(obj) == null)
            {
                ec.newObjectProviderForEmbedded(obj, false, ownerSM, fieldNumber);
            }
        }
        return obj;
    }
}