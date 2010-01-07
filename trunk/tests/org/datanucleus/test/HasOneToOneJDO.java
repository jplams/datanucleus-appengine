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
package org.datanucleus.test;

import javax.jdo.annotations.Extension;
import javax.jdo.annotations.IdGeneratorStrategy;
import javax.jdo.annotations.IdentityType;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.PrimaryKey;

/**
 * @author Max Ross <maxr@google.com>
 */
@PersistenceCapable(identityType = IdentityType.APPLICATION, detachable = "true")
public class HasOneToOneJDO {

  @PrimaryKey
  @Persistent(valueStrategy = IdGeneratorStrategy.IDENTITY)
  @Extension(vendorName = "datanucleus", key = "gae.encoded-pk", value="true")
  private String id;

  @Persistent(dependent = "true")
  private Flight flight;

  @Persistent(dependent = "true")
  private HasKeyPkJDO hasKeyPK;

  @Persistent(dependent = "true")
  private HasOneToOneParentJDO hasParent;

  @Persistent(dependent = "true")
  private HasOneToOneParentKeyPkJDO hasParentKeyPK;

  @Persistent
  private HasEncodedStringPkJDO notDependent;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Flight getFlight() {
    return flight;
  }

  public void setFlight(Flight flight) {
    this.flight = flight;
  }

  public HasKeyPkJDO getHasKeyPK() {
    return hasKeyPK;
  }

  public void setHasKeyPK(HasKeyPkJDO hasKeyPK) {
    this.hasKeyPK = hasKeyPK;
  }

  public HasOneToOneParentJDO getHasParent() {
    return hasParent;
  }

  public void setHasParent(HasOneToOneParentJDO hasParent) {
    this.hasParent = hasParent;
  }

  public HasOneToOneParentKeyPkJDO getHasParentKeyPK() {
    return hasParentKeyPK;
  }

  public void setHasParentKeyPK(HasOneToOneParentKeyPkJDO hasParentKeyPK) {
    this.hasParentKeyPK = hasParentKeyPK;
  }

  public HasEncodedStringPkJDO getNotDependent() {
    return notDependent;
  }

  public void setNotDependent(HasEncodedStringPkJDO notDependent) {
    this.notDependent = notDependent;
  }
}
