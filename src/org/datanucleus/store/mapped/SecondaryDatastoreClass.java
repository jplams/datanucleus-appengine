/**********************************************************************
Copyright (c) 2007 Andy Jefferson and others. All rights reserved.
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
package org.datanucleus.store.mapped;

import org.datanucleus.metadata.JoinMetaData;

/**
 * Secondary datastore class, managing the mapping of some of the fields of the class
 * and dependent on a DatastoreClass.
 */
public interface SecondaryDatastoreClass extends DatastoreClass
{
    /**
     * Accessor for the primary datastore class that this is dependent on.
     * @return The associated primary datastore class.
     */
    DatastoreClass getPrimaryDatastoreClass();

    /**
     * Accessor for the JoinMetaData which is used to join to the primary DatastoreClass.
     * @return JoinMetaData
     */
    JoinMetaData getJoinMetaData();
}