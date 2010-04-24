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
package org.datanucleus.store.appengine;

import com.google.appengine.api.datastore.dev.LocalDatastoreService;
import com.google.appengine.tools.development.ApiProxyLocal;
import com.google.appengine.tools.development.ApiProxyLocalFactory;
import com.google.appengine.tools.development.LocalServerEnvironment;
import com.google.apphosting.api.ApiProxy;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;

/**
 * {@link DatastoreDelegate} implementation that integrates with the stub
 * datastore that ships with the sdk.
 *
 * @author Max Ross <maxr@google.com>
 */
class LocalDatastoreDelegate implements DatastoreDelegate {

  private static final class LocalDatastoreDelegateEnv implements ApiProxy.Environment {
    private final ConcurrentHashMap<String, Object> attributes = new ConcurrentHashMap<String, Object>();

    public String getAppId() {
      return "test";
    }

    public String getVersionId() {
      return "1.0";
    }

    public String getEmail() {
      throw new UnsupportedOperationException();
    }

    public boolean isLoggedIn() {
      throw new UnsupportedOperationException();
    }

    public boolean isAdmin() {
      throw new UnsupportedOperationException();
    }

    public String getAuthDomain() {
      throw new UnsupportedOperationException();
    }

    public String getRequestNamespace() {
      return "";
    }

    public ConcurrentMap<String, Object> getAttributes() {
      return attributes;
    }
  }

  // Ok to reuse this across tests so long as we clear out the
  // datastore in tearDown()
  private static final ApiProxyLocal localProxy = createLocalProxy();

  private static ApiProxyLocal createLocalProxy() {
    LocalServerEnvironment lse = new LocalServerEnvironment() {
      public File getAppDir() {
        return new File(".");
      }

      public String getAddress() {
        return null;
      }

      public int getPort() {
        return 0;
      }

      public void waitForServerToStart() throws InterruptedException {
      }
    };
    ApiProxyLocal proxy = new ApiProxyLocalFactory().create(lse);
    // run completely in-memory
    proxy.setProperty(LocalDatastoreService.NO_STORAGE_PROPERTY, Boolean.TRUE.toString());
    // don't expire queries - makes debugging easier
    proxy.setProperty(LocalDatastoreService.MAX_QUERY_LIFETIME_PROPERTY,
        Integer.toString(Integer.MAX_VALUE));
    // don't expire txns - makes debugging easier
    proxy.setProperty(LocalDatastoreService.MAX_TRANSACTION_LIFETIME_PROPERTY,
        Integer.toString(Integer.MAX_VALUE));
    return proxy;
  }

  public void setUp() {
    ApiProxy.setEnvironmentForCurrentThread(new LocalDatastoreDelegateEnv());
  }

  public void tearDown() throws Exception {
    ApiProxy.clearEnvironmentForCurrentThread();
    LocalDatastoreService lds = (LocalDatastoreService) localProxy.getService("datastore_v3");
    lds.clearProfiles();
  }

  public byte[] makeSyncCall(ApiProxy.Environment environment, String packageName,
      String methodName, byte[] request) throws ApiProxy.ApiProxyException {
    return localProxy.makeSyncCall(environment, packageName, methodName, request);
  }

  public Future<byte[]> makeAsyncCall(ApiProxy.Environment environment, String s, String s1,
                                             byte[] bytes, ApiProxy.ApiConfig apiConfig) {
    return localProxy.makeAsyncCall(environment, s, s1, bytes, apiConfig);
  }

  public void log(ApiProxy.Environment environment, ApiProxy.LogRecord logRecord) {
    localProxy.log(environment, logRecord);
  }
}
