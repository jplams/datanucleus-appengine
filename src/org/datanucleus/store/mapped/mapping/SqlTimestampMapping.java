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
2003 Andy Jefferson - coding standards and javadocs
    ...
**********************************************************************/
package org.datanucleus.store.mapped.mapping;

/**
 * SCO Mapping for an SQLTimestamp type.
 */
public class SqlTimestampMapping extends TemporalMapping
{
    public Class getJavaType()
    {
        return java.sql.Timestamp.class;
    }

    /* (non-Javadoc)
     * @see org.datanucleus.store.mapped.mapping.TemporalMapping#getDefaultLengthAsString()
     */
    protected int getDefaultLengthAsString()
    {
        // String-based storage when persisted as "YYYY-MM-DD HH:MM:SS.FFFFFFFFF"
        return 29;
    }
}