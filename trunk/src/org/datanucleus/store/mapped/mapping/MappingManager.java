/******************************************************************
Copyright (c) 2004 Andy Jefferson and others. All rights reserved. 
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
2004 Erik Bengtson - added datastore mapping accessors
    ...
*****************************************************************/
package org.datanucleus.store.mapped.mapping;

import org.datanucleus.ClassLoaderResolver;
import org.datanucleus.metadata.AbstractMemberMetaData;
import org.datanucleus.metadata.ColumnMetaData;
import org.datanucleus.plugin.PluginManager;
import org.datanucleus.store.mapped.DatastoreContainerObject;
import org.datanucleus.store.mapped.DatastoreField;

/**
 * Representation of a MappingManager, mapping a java mapping type to a datastore mapping type.
 * Allows a java mapping type to map to multiple datastore mapping types.
 * Allows a default datastore mapping type be assigned to each java mapping type.
 */
public interface MappingManager
{
    /**
     * Initialise the datastore mapping. 
     * @param mgr the PlyginManager
     * @param clr the ClassLoaderResolver
     * @param vendorId the datastore vendor id
     */
    void loadDatastoreMapping(PluginManager mgr, ClassLoaderResolver clr, String vendorId);

    /**
     * Method to create the datastore mapping for a java type mapping at a particular index.
     * @param mapping The java mapping
     * @param fmd MetaData for the field
     * @param index Index of the datastore field
     * @param column The column
     * @return The datastore mapping
     */
    DatastoreMapping createDatastoreMapping(JavaTypeMapping mapping, AbstractMemberMetaData fmd, int index, 
            DatastoreField column);

    /**
     * Method to create the datastore mapping for a particular column and java type.
     * @param mapping The java mapping
     * @param column The column
     * @param javaType The java type (isnt this stored in the java mapping ?)
     * @return The datastore mapping
     */
    DatastoreMapping createDatastoreMapping(JavaTypeMapping mapping, DatastoreField column, 
            String javaType);

    // --------------------------------------------- Java Types ---------------------------------------------

    /**
     * Accessor for a mapping, for a java type.
     * Same as calling "getMapping(c, false, false, (String)null);"
     * @param c The java type
     * @return The mapping
     */
    JavaTypeMapping getMapping(Class c);

    /**
     * Accessor for a mapping, for a java type.
     * @param c The java type
     * @param serialised Whether the type is serialised
     * @param embedded Whether the type is embedded
     * @param fieldName Name of the field (for logging only)
     * @return The mapping
     */
    JavaTypeMapping getMapping(Class c, boolean serialised, boolean embedded, String fieldName);

    /**
     * Accessor for a mapping, for a java type complete with the datastore mapping.
     * @param c The java type
     * @param serialised Whether the type is serialised
     * @param embedded Whether the type is embedded
     * @param clr ClassLoader resolver
     * @return The mapping
     */
    JavaTypeMapping getMappingWithDatastoreMapping(Class c, boolean serialised, boolean embedded, 
            ClassLoaderResolver clr);

    /**
     * Accessor for the mapping for the field of the specified table.
     * Can be used for fields of a class, elements of a collection of a class, elements of an array of
     * a class, keys of a map of a class, values of a map of a class. This is controlled by the final
     * argument "roleForMember".
     * @param table Table to add the mapping to
     * @param mmd MetaData for the field/property to map
     * @param clr The ClassLoaderResolver
     * @param fieldRole Role that this mapping plays for the field/property
     * @return The mapping for the field.
     */
    JavaTypeMapping getMapping(DatastoreContainerObject table, AbstractMemberMetaData mmd, 
            ClassLoaderResolver clr, int fieldRole);

    // ----------------------------------------- Datastore Types ---------------------------------------------

    /**
     * Method to create a datastore field (column) in a container (table).
     * @param mapping The java mapping
     * @param javaType The java type
     * @param datastoreFieldIndex The index of the datastore field to create
     * @return The datastore field
     */
    DatastoreField createDatastoreField(JavaTypeMapping mapping, String javaType, int datastoreFieldIndex);

    /**
     * Method to create a datastore field (column) in a container (table).
     * To be used for serialised PC element/key/value in a join table.
     * @param mapping The java mapping
     * @param javaType The java type
     * @param colmd MetaData for the column to create
     * @return The datastore field
     */
    DatastoreField createDatastoreField(JavaTypeMapping mapping, String javaType, ColumnMetaData colmd);

    /**
     * Method to create a datastore field for a PersistenceCapable mapping.
     * @param fmd MetaData for the field
     * @param datastoreContainer The container in the datastore
     * @param mapping The java mapping
     * @param colmd MetaData for the column to create
     * @param reference The field to reference
     * @param clr ClassLoader resolver
     * @return The datastore field
     */
    DatastoreField createDatastoreField(AbstractMemberMetaData fmd, DatastoreContainerObject datastoreContainer, 
            JavaTypeMapping mapping, ColumnMetaData colmd, DatastoreField reference, ClassLoaderResolver clr);
}