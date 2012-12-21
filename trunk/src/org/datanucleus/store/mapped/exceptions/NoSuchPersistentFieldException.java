/**********************************************************************
Copyright (c) 2002 Mike Martin and others. All rights reserved.
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

import org.datanucleus.exceptions.NucleusUserException;
import org.datanucleus.util.Localiser;

/**
 * A <tt>NoSuchPersistentFieldException</tt> is thrown if a reference is made
 * somewhere, such as in a query filter string, to a field that either doesn't
 * exist or is not persistent.
 */
public class NoSuchPersistentFieldException extends NucleusUserException
{
    protected static final Localiser LOCALISER=Localiser.getInstance(
        "org.datanucleus.Localisation", org.datanucleus.ClassConstants.NUCLEUS_CONTEXT_LOADER);

    /**
     * Constructs a no such persistent field exception.
     *
     * @param className The class in which the field was not found.
     * @param fieldName The name of the field.
     */
    public NoSuchPersistentFieldException(String className, String fieldName)
    {
        super(LOCALISER.msg("018009",fieldName,className));
    }

    /**
     * Constructs a no such persistent field exception.
     *
     * @param className     The class in which the field was not found.
     * @param fieldNumber   The field number  of the field.
     */
    public NoSuchPersistentFieldException(String className, int fieldNumber)
    {
        super(LOCALISER.msg("018010","" + fieldNumber,className));
    }
}
