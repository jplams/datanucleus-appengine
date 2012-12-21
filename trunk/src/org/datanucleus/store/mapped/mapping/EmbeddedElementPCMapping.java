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
import org.datanucleus.metadata.AbstractMemberMetaData;
import org.datanucleus.state.ObjectProvider;
import org.datanucleus.store.mapped.DatastoreContainerObject;

/**
 * Mapping for a PersistenceCapable object stored in an embedded collection within a
 * PersistenceCapable object. Provides mapping for a single Java type
 * (the element PC type) to multiple datastore columns.
 */
public class EmbeddedElementPCMapping extends EmbeddedMapping
{
    /**
     * Initialize this JavaTypeMapping with the given DatastoreAdapter for
     * the given FieldMetaData.
     * @param container The datastore container storing this mapping (if any)
     * @param clr the ClassLoaderResolver
     * @param fmd FieldMetaData for the field to be mapped (if any)
     */
    public void initialize(AbstractMemberMetaData fmd, DatastoreContainerObject container, ClassLoaderResolver clr)
    {
    	initialize(fmd, container, clr, fmd.getElementMetaData().getEmbeddedMetaData(), fmd.getCollection().getElementType(),
            ObjectProvider.EMBEDDED_COLLECTION_ELEMENT_PC);
    }
}