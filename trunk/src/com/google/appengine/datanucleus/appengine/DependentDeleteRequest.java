/**********************************************************************
Copyright (c) 2009 Google Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
**********************************************************************/
package com.google.appengine.datanucleus.appengine;

import com.google.appengine.api.datastore.Entity;

import org.datanucleus.ClassLoaderResolver;
import org.datanucleus.ObjectManager;
import org.datanucleus.StateManager;
import org.datanucleus.metadata.AbstractClassMetaData;
import org.datanucleus.metadata.AbstractMemberMetaData;
import org.datanucleus.metadata.Relation;
import org.datanucleus.store.mapped.DatastoreClass;
import org.datanucleus.store.mapped.DatastoreField;
import org.datanucleus.store.mapped.MappedStoreManager;
import org.datanucleus.store.mapped.mapping.JavaTypeMapping;
import org.datanucleus.store.mapped.mapping.MappingCallbacks;
import org.datanucleus.store.mapped.mapping.MappingConsumer;
import org.datanucleus.store.mapped.mapping.PersistenceCapableMapping;
import org.datanucleus.store.mapped.mapping.ReferenceMapping;

import java.util.List;

/**
 * Encapsulates logic that supports deletion of dependent objects.
 *
 * Code based pretty closely on the rdbms version of DeleteRequest.
 *
 * @author Max Ross <maxr@google.com>
 */
class DependentDeleteRequest {

  private final List<MappingCallbacks> callbacks;

  /**
   * 1-1 bidir non-owner fields that are reachable (but not updated) and have no datastore column.
   */
  private final AbstractMemberMetaData[] oneToOneNonOwnerFields;

  public DependentDeleteRequest(DatastoreClass dc, AbstractClassMetaData acmd, ClassLoaderResolver clr) {
    DependentDeleteMappingConsumer consumer = new DependentDeleteMappingConsumer(acmd, clr);
    dc.provideNonPrimaryKeyMappings(consumer); // to compute callbacks
    dc.providePrimaryKeyMappings(consumer);
    dc.provideDatastoreIdMappings(consumer);
    callbacks = consumer.getMappingCallBacks();
    oneToOneNonOwnerFields = consumer.getOneToOneNonOwnerFields();
  }

  /**
   * Actually performs the work.
   */
  public void execute(StateManager sm, Entity owningEntity) {

    // Process all related fields first
    // a). Delete any dependent objects
    // b). Null any non-dependent objects with FK at other side
    for (MappingCallbacks callback : callbacks) {
      callback.preDelete(sm);
    }

    if (oneToOneNonOwnerFields != null && oneToOneNonOwnerFields.length > 0) {
      for (AbstractMemberMetaData relatedFmd : oneToOneNonOwnerFields) {
        updateOneToOneBidirectionalOwnerObjectForField(sm, relatedFmd, owningEntity);
      }
    }
  }

  private void updateOneToOneBidirectionalOwnerObjectForField(
      StateManager sm, AbstractMemberMetaData fmd, Entity owningEntity) {
    MappedStoreManager storeMgr = (MappedStoreManager) sm.getStoreManager();
    ObjectManager om = sm.getObjectManager();
    ClassLoaderResolver clr = om.getClassLoaderResolver();
    AbstractMemberMetaData[] relatedMmds = fmd.getRelatedMemberMetaData(clr);
    String fullClassName = ((AbstractClassMetaData) relatedMmds[0].getParent()).getFullClassName();
    DatastoreClass refTable = storeMgr.getDatastoreClass(fullClassName, clr);
    JavaTypeMapping refMapping = refTable.getMemberMapping(fmd.getMappedBy());
    if (refMapping.isNullable()) {
      // Null out the relationship to the object being deleted.
      refMapping.setObject(om, owningEntity, new int[1], sm.getObject());

      // TODO(maxr): Do I need to manually request an update now?
    }
  }

  /**
   * Mapping consumer for dependent deletes.  Largely based on rdbms
   * DeleteRequest.DeleteMappingConsumer
   */
  private static class DependentDeleteMappingConsumer implements MappingConsumer {

    private int pkField;

    /**
     * Fields in a 1-1 relation with FK in the table of the other object.
     */
    private final List<AbstractMemberMetaData> oneToOneNonOwnerFields = Utils.newArrayList();

    private final List<MappingCallbacks> callbacks = Utils.newArrayList();

    private final ClassLoaderResolver clr;

    private final AbstractClassMetaData cmd;
    
    public DependentDeleteMappingConsumer(AbstractClassMetaData cmd, ClassLoaderResolver clr) {
      this.clr = clr;
      this.cmd = cmd;
    }

    public void preConsumeMapping(int highest) {
    }

    public void consumeMapping(JavaTypeMapping m, AbstractMemberMetaData fmd) {
      if (!fmd.getAbstractClassMetaData().isSameOrAncestorOf(cmd)) {
        // Make sure we only accept mappings from the correct part of any inheritance tree
        return;
      }

      if (m.includeInUpdateStatement()) {
        if (fmd.isPrimaryKey()) {
          pkField = fmd.getAbsoluteFieldNumber();
        } else if (m instanceof PersistenceCapableMapping || m instanceof ReferenceMapping) {
          if (m.getNumberOfDatastoreFields() == 0) {
            // Field storing a PC object with FK at other side
            int relationType = fmd.getRelationType(clr);
            if (relationType == Relation.ONE_TO_ONE_BI) {
              if (fmd.getMappedBy() != null) {
                // 1-1 bidirectional field without datastore column(s) (with single FK at other side)
                oneToOneNonOwnerFields.add(fmd);
              }
            } else if (relationType == Relation.MANY_TO_ONE_BI) {
              AbstractMemberMetaData[] relatedMmds = fmd.getRelatedMemberMetaData(clr);
              if (fmd.getJoinMetaData() != null || relatedMmds[0].getJoinMetaData() != null) {
                // 1-N bidirectional using join table for relation
                // TODO Anything to do here ?
              }
            }
          }
        }
      }

      // Build up list of mappings callbacks for the fields of this class.
      // The Mapping callback called delete is the preDelete
      if (m instanceof MappingCallbacks) {
        callbacks.add((MappingCallbacks) m);
      }
    }

    public int getPrimaryKeyFieldNumber() {
      return pkField;
    }

    /**
     * All 1-1 bidirectional non-owner fields, with the FK In the other object.
     *
     * @return The fields that are 1-1 bidirectional with the FK at the other side.
     */
    public AbstractMemberMetaData[] getOneToOneNonOwnerFields() {
      AbstractMemberMetaData[] fmds = new AbstractMemberMetaData[oneToOneNonOwnerFields.size()];
      for (int i = 0; i < oneToOneNonOwnerFields.size(); ++i) {
        fmds[i] = oneToOneNonOwnerFields.get(i);
      }
      return fmds;
    }

    public List<MappingCallbacks> getMappingCallBacks() {
      return callbacks;
    }

    public void consumeUnmappedDatastoreField(DatastoreField fld) {
    }

    public void consumeMapping(JavaTypeMapping m, int mappingType) {
    }
  }
}
