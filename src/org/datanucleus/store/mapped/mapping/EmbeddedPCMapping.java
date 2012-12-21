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

import org.datanucleus.ClassLoaderResolver;
import org.datanucleus.ExecutionContext;
import org.datanucleus.metadata.AbstractClassMetaData;
import org.datanucleus.metadata.AbstractMemberMetaData;
import org.datanucleus.metadata.EmbeddedMetaData;
import org.datanucleus.state.ObjectProvider;
import org.datanucleus.store.mapped.DatastoreContainerObject;

/**
 * Mapping for a PC object embedded within another PC object (1-1 relation).
 * Provides mapping for a single Java type (the PC type) to multiple datastore columns.
 * Allows for nested embedded fields.
 * Implements MappingCallbacks since if we are embedding a MappingCallbacks field (e.g a BLOB on Oracle)
 * then we need in turn to call the underlying MappingCallbacks methods.
 **/
public class EmbeddedPCMapping extends EmbeddedMapping implements MappingCallbacks
{
    /**
     * Initialize this JavaTypeMapping with the given DatastoreAdapter for the given metadata.
     * @param mmd FieldMetaData for the field to be mapped (if any)
     * @param container The datastore container storing this mapping (if any)
     * @param clr the ClassLoaderResolver
     */
    public void initialize(AbstractMemberMetaData mmd, DatastoreContainerObject container, ClassLoaderResolver clr)
    {
        initialize(mmd, container, clr, mmd.getEmbeddedMetaData(), mmd.getTypeName(),
        		ObjectProvider.EMBEDDED_PC);
    }

    public void insertPostProcessing(ObjectProvider op)
    {
    }

    /**
     * MappingCallback called when the owning object is being fetched.
     * @param sm StateManager of the owning object
     */
    public void postFetch(ObjectProvider sm)
    {
        if (mmd.getAbsoluteFieldNumber() < 0)
        {
            return;
        }

        // Find the SM for the embedded PC object
        ObjectProvider thisSM = getStateManagerForEmbeddedObject(sm);
        if (thisSM == null)
        {
            return;
        }

        for (int i=0;i<getNumberOfJavaTypeMappings();i++)
        {
            JavaTypeMapping m = getJavaTypeMapping(i);
            if (m instanceof MappingCallbacks)
            {
                ((MappingCallbacks)m).postFetch(thisSM);
            }
        }
    }

    /**
     * MappingCallback called when the owning object has just being inserted.
     * @param sm StateManager of the owning object
     */
    public void postInsert(ObjectProvider sm)
    {
        if (mmd.getAbsoluteFieldNumber() < 0)
        {
            return;
        }

        // Find the SM for the embedded PC object
        ObjectProvider thisSM = getStateManagerForEmbeddedObject(sm);
        if (thisSM == null)
        {
            return;
        }

        // Call postInsert on any MappingCallbacks components
        for (int i=0;i<getNumberOfJavaTypeMappings();i++)
        {
            JavaTypeMapping m = getJavaTypeMapping(i);
            if (m instanceof MappingCallbacks)
            {
                ((MappingCallbacks)m).postInsert(thisSM);
            }
        }
    }

    /**
     * MappingCallback called when the owning object has just being udpated.
     * @param sm StateManager of the owning object
     */
    public void postUpdate(ObjectProvider sm)
    {
        if (mmd.getAbsoluteFieldNumber() < 0)
        {
            return;
        }

        // Find the SM for the embedded PC object
        ObjectProvider thisSM = getStateManagerForEmbeddedObject(sm);
        if (thisSM == null)
        {
            return;
        }

        // Call postUpdate on any MappingCallbacks components
        for (int i=0;i<getNumberOfJavaTypeMappings();i++)
        {
            JavaTypeMapping m = getJavaTypeMapping(i);
            if (m instanceof MappingCallbacks)
            {
                ((MappingCallbacks)m).postUpdate(thisSM);
            }
        }
    }

    /**
     * MappingCallback called when the owning object is about to be deleted.
     * @param sm StateManager of the owning object
     */
    public void preDelete(ObjectProvider sm)
    {
        if (mmd.getAbsoluteFieldNumber() < 0)
        {
            return;
        }

        // Find the SM for the embedded PC object
        ObjectProvider thisSM = getStateManagerForEmbeddedObject(sm);
        if (thisSM == null)
        {
            return;
        }

        // Call preDelete on any MappingCallbacks components
        for (int i=0;i<getNumberOfJavaTypeMappings();i++)
        {
            JavaTypeMapping m = getJavaTypeMapping(i);
            if (m instanceof MappingCallbacks)
            {
                ((MappingCallbacks)m).preDelete(thisSM);
            }
        }
    }

    /**
     * Accessor for the StateManager of the embedded PC object when provided with the owner object.
     * @param ownerSM StateManager of the owner
     * @return StateManager of the embedded object
     */
    private ObjectProvider getStateManagerForEmbeddedObject(ObjectProvider ownerSM)
    {
        ExecutionContext ec = ownerSM.getExecutionContext();
        AbstractMemberMetaData theMmd = mmd;
        if (mmd.getParent() instanceof EmbeddedMetaData)
        {
            // Get the real owner classMetaData (when embedded the cmd is often the embedded)
            AbstractClassMetaData cmd = ec.getMetaDataManager().getMetaDataForClass(mmd.getClassName(), clr);
            theMmd = cmd.getMetaDataForMember(mmd.getName());
        }

        Object value = ownerSM.provideField(theMmd.getAbsoluteFieldNumber()); // Owner (non-embedded) PC
        if (value == null)
        {
            return null;
        }

        ObjectProvider thisSM = ec.findObjectProvider(value);
        if (thisSM == null)
        {
            // Assign a StateManager to manage our embedded object
            thisSM = ec.newObjectProviderForEmbedded(value, false, ownerSM, theMmd.getAbsoluteFieldNumber());
            thisSM.setPcObjectType(objectType);
        }

        return thisSM;
    }
}