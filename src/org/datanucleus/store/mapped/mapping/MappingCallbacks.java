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
2007 Andy Jefferson - moved to be RelationMappingCallbacks
    ...
**********************************************************************/
package org.datanucleus.store.mapped.mapping;

import org.datanucleus.state.ObjectProvider;

/**
 * Interface defining a series of callbacks that are called when this mapping goes through
 * certain lifecycle events. This interface would be implemented by any type of mapping that handles
 * a relation and so may need to perform action just before or just after a lifecycle event.
 */
public interface MappingCallbacks
{
    /**
     * Hook so that we can handle any post-processing on the insert of a particular field.
     * For example with Oracle we need to insert "EMPTY_CLOB" and then immediately after do a SELECT of it
     * and update the contents of the CLOB/BLOB.
     * @param op ObjectProvider
     */
    void insertPostProcessing(ObjectProvider op);

    /**
     * Method called after the insert of the object so that additional operations can be performed if necessary.
     * @param op ObjectProvider of the owner
     */
    void postInsert(ObjectProvider op);

    /**
     * Method called after the retrieval of the object, so that additional operations can be performed if necessary.
     * @param op ObjectProvider of the owner
     */
    void postFetch(ObjectProvider op);

    /**
     * Method called after the update of the object, so that additional operations can be performed if necessary.
     * @param op ObjectProvider of the owner
     */
    void postUpdate(ObjectProvider op);

    /**
     * Method called before the delete of objects, so that additional operations can be performed if necessary.
     * @param op ObjectProvider of the owner
     */
    void preDelete(ObjectProvider op);
}