/**********************************************************************
Copyright (c) 2003 Mike Martin and others. All rights reserved.
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
    Andy Jefferson - coding standards
    ...
**********************************************************************/
package org.datanucleus.store.mapped.exceptions;

import org.datanucleus.exceptions.NucleusDataStoreException;
import org.datanucleus.store.mapped.DatastoreAdapter;
import org.datanucleus.util.Localiser;

/**
 * A <tt>TooManyIndicesException</tt> is thrown when trying to add an index
 * to a table and the table already has the maximum allowed number of indices.
 */
public class TooManyIndicesException extends NucleusDataStoreException
{
    protected static final Localiser LOCALISER=Localiser.getInstance(
        "org.datanucleus.Localisation", org.datanucleus.ClassConstants.NUCLEUS_CONTEXT_LOADER);

    /**
     * Constructs a too-many-indices exception.
     * @param dba the database adapter
     * @param tableName Name of the table with too many indices
     */
    public TooManyIndicesException(DatastoreAdapter dba, String tableName)
    {
        super(LOCALISER.msg("020016","" + dba.getMaxIndexes(), tableName));
        setFatal();
    }
}