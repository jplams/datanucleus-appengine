/**********************************************************************
Copyright (c) 2012 Google Inc.

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
package com.google.appengine.datanucleus.test.jdo;

import javax.jdo.annotations.IdGeneratorStrategy;
import javax.jdo.annotations.IdentityType;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.PrimaryKey;

import com.google.appengine.api.datastore.Key;

/**
 * Class with an embedded array of objects.
 */
@PersistenceCapable(identityType = IdentityType.APPLICATION)
public class EmbeddedArrayOwner {
  @PrimaryKey 
  @Persistent(valueStrategy=IdGeneratorStrategy.IDENTITY)
  private Key key;

  @Persistent(embedded="true")
  private EmbeddedRelatedBase[] array = null;

  public Key getKey() {
    return key;
  }

  public EmbeddedRelatedBase[] getArray() {
    return array;
  }

  public void setArray(EmbeddedRelatedBase[] arr) {
    this.array = arr;
  }
}