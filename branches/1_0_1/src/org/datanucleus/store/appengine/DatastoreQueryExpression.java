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

import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.appengine.api.datastore.Query.SortPredicate;

import org.datanucleus.ClassLoaderResolver;
import org.datanucleus.store.mapped.DatastoreContainerObject;
import org.datanucleus.store.mapped.DatastoreField;
import org.datanucleus.store.mapped.DatastoreIdentifier;
import org.datanucleus.store.mapped.MappedStoreManager;
import org.datanucleus.store.mapped.expression.BooleanExpression;
import org.datanucleus.store.mapped.expression.IntegerLiteral;
import org.datanucleus.store.mapped.expression.LogicSetExpression;
import org.datanucleus.store.mapped.expression.NumericExpression;
import org.datanucleus.store.mapped.expression.ObjectLiteral;
import org.datanucleus.store.mapped.expression.QueryExpression;
import org.datanucleus.store.mapped.expression.ScalarExpression;
import org.datanucleus.store.mapped.expression.StatementText;
import org.datanucleus.store.mapped.expression.StringLiteral;
import org.datanucleus.store.mapped.mapping.JavaTypeMapping;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Datastore specific implementation of a {@link QueryExpression}.
 * Most functionality is unsupported.  It's currently unclear how much of this
 * actually makes sense for the datastore, since {@link QueryExpression}, even
 * though it doesn't have any rdbms dependencies, still assumes a String-based
 * query mechanism, which the datastore does not have.
 *
 * @author Max Ross <maxr@google.com>
 */
class DatastoreQueryExpression implements QueryExpression {

  private static final Map<String, Query.FilterOperator> FILTER_OPERATOR_MAP = buildFilterOperatorMap();

  private final List<BooleanExpression> andConditions = Utils.newArrayList();
  private final List<SortPredicate> sortPredicates = Utils.newArrayList();
  private final DatastoreTable mainTable;
  private final ClassLoaderResolver clr;
  /**
   * We only set this member if the first sort order is on the pk of the table.
   * If no other sort order is set, we never add it to the list of sort
   * predicates.  If another sort order _is_ set we add this one first.
   */
  private boolean ignoreSubsequentSorts;

  DatastoreQueryExpression(DatastoreTable table, ClassLoaderResolver clr) {
    this.mainTable = table;
    this.clr = clr;
  }

  /**
   * Some sillyness to get around the fact that the {@link ScalarExpression}
   * constants are of a type that is protected.
   */
  private static String getSymbol(Object op) {
    return op.toString().trim();
  }

  private static Map<String, Query.FilterOperator> buildFilterOperatorMap() {
    Map<String, Query.FilterOperator> map = Utils.newHashMap();
    map.put(getSymbol(ScalarExpression.OP_EQ), Query.FilterOperator.EQUAL);
    map.put(getSymbol(ScalarExpression.OP_GT), Query.FilterOperator.GREATER_THAN);
    map.put(getSymbol(ScalarExpression.OP_GTEQ), Query.FilterOperator.GREATER_THAN_OR_EQUAL);
    map.put(getSymbol(ScalarExpression.OP_LT), Query.FilterOperator.LESS_THAN);
    map.put(getSymbol(ScalarExpression.OP_LTEQ), Query.FilterOperator.LESS_THAN_OR_EQUAL);
    return map;
  }

  private static final Field APPENDED_FIELD =
      getDeclaredFieldQuietly(StatementText.class, "appended");

  private static final Field DATASTORE_FIELD_FIELD =
      getDeclaredFieldQuietly(ScalarExpression.DatastoreFieldExpression.class, "field");

  // TODO(maxr) Add an accessor to StatementText.
  private static Field getDeclaredFieldQuietly(Class<?> cls, String fieldName) {
    Field field;
    try {
      field = cls.getDeclaredField(fieldName);
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
    field.setAccessible(true);
    return field;
  }

  // TODO(maxr) Add accessor for StatementText.appended (datanuc change)
  private List<?> getAppended(StatementText st) {
    try {
      return (List<?>) APPENDED_FIELD.get(st);
    } catch (IllegalAccessException e) {
      return null;
    }
  }

  private DatastoreProperty getDatastoreProperty(ScalarExpression.DatastoreFieldExpression dfe) {
    try {
      return (DatastoreProperty) DATASTORE_FIELD_FIELD.get(dfe);
    } catch (IllegalAccessException e) {
      return null;
    }
  }

  /**
   * Extract the parent key from the expression.  This is totally fragile and
   * needs to be rewritten.
   *
   * TODO(maxr): Give callers of this method access to the ObjectManager of the
   * owning object (datanuc change).
   *
   * @return The key of the parent in the query.
   */
  Key getParentKey() {
    // We are relying on the query having at least one 'and' condition where
    // the first element of the first condition is the parent key.
    // Like I said, totally fragile.
    if (andConditions.size() < 1) {
      return null;
    }
    List<?> appended =
        getAppended(andConditions.get(0).toStatementText(ScalarExpression.PROJECTION));
    if (appended.size() != 3) {
      return null;
    }
    if (appended.get(0) instanceof StringLiteral) {
      StringLiteral stringLiteral = (StringLiteral) appended.get(0);
      try {
        return KeyFactory.stringToKey(stringLiteral.getValue().toString());
      } catch (IllegalArgumentException iae) {
        // treat it as an unencoded String
        String kind = getKind((ScalarExpression.DatastoreFieldExpression) appended.get(2));
        return KeyFactory.createKey(kind, stringLiteral.getValue().toString());
      }
    } else if (appended.get(0) instanceof ObjectLiteral) {
      ObjectLiteral objectLiteral = (ObjectLiteral) appended.get(0);
      return (Key) objectLiteral.getValue();
    } else if (appended.get(0) instanceof IntegerLiteral) {
      IntegerLiteral integerLiteral = (IntegerLiteral) appended.get(0);
      String kind = getKind((ScalarExpression.DatastoreFieldExpression) appended.get(2));
      return KeyFactory.createKey(kind, (Long) integerLiteral.getValue());
    }
    return null;
  }

  private String getKind(ScalarExpression.DatastoreFieldExpression dfe) {
    DatastoreProperty prop = getDatastoreProperty(dfe);
    return EntityUtils.determineKind(
        prop.getOwningClassMetaData(), mainTable.getStoreManager().getIdentifierFactory());
  }

  Collection<SortPredicate> getSortPredicates() {
    return sortPredicates;
  }

  public void setOrdering(ScalarExpression[] exprs, boolean[] descending) {
    if (exprs.length != descending.length) {
      throw new IllegalArgumentException(
          "Expression array and descending array are not the same size.");
    }
    if (ignoreSubsequentSorts) {
      return;
    }
    for (int i = 0; i < exprs.length && !ignoreSubsequentSorts; i++) {
      if (!(exprs[i] instanceof ScalarExpression.DatastoreFieldExpression)) {
        throw new UnsupportedOperationException(
            "Expression of type " + exprs[i].getClass().getName() + " not supported.");
      }
      ScalarExpression.DatastoreFieldExpression dfe = (ScalarExpression.DatastoreFieldExpression) exprs[i];
      String propertyName = dfe.toString();
      boolean isPrimaryKey = isPrimaryKey(propertyName);
      if (isPrimaryKey) {
        // sorting by id requires us to use a reserved property name
        propertyName = Entity.KEY_RESERVED_PROPERTY;
      }
      SortPredicate sortPredicate = new SortPredicate(
          propertyName, descending[i] ? SortDirection.DESCENDING : SortDirection.ASCENDING);
      boolean addPredicate = true;
      if (isPrimaryKey) {
        // User wants to sort by pk.  Since pk is guaranteed to be unique, set a
        // flag so we know there's no point in adding any more sort predicates
        ignoreSubsequentSorts = true;
        // Don't even bother adding if the first sort is id ASC (this is the
        // default sort so there's no point in making the datastore figure this
        // out).
        if (sortPredicate.getDirection() == SortDirection.ASCENDING && sortPredicates.isEmpty()) {
          addPredicate = false;
        }
      }
      if (addPredicate) {
        sortPredicates.add(sortPredicate);
      }
    }
  }

  boolean isPrimaryKey(String propertyName) {
    return mainTable.getDatastoreField(propertyName).isPrimaryKey();
  }

  List<Query.FilterPredicate> getFilterPredicates() {
    if (andConditions.size() == 1) {
      return Collections.emptyList();
    }
    List<Query.FilterPredicate> filters = Utils.newArrayList();
    // We assume the first and condition is the fk so we start
    // with the second.
    for (BooleanExpression expr : andConditions.subList(1, andConditions.size())) {
      filters.add(getFilterPredicate(expr));
    }
    return filters;
  }

  private Query.FilterPredicate getFilterPredicate(BooleanExpression expr) {
    StatementText st = expr.toStatementText(ScalarExpression.PROJECTION);
    List<?> appended =
        getAppended(expr.toStatementText(ScalarExpression.PROJECTION));

    if (!(appended.get(0) instanceof NumericExpression)) {
      throw new UnsupportedOperationException(
          "Cannot transform " + st.toString() + " into a Filter Predicate.");
    }
    NumericExpression numExpr = (NumericExpression) appended.get(0);
    List<?> innerAppended = getAppended(numExpr.toStatementText(ScalarExpression.PROJECTION));
    if (innerAppended.size() != 1 || !(innerAppended.get(0) instanceof String)) {
      throw new UnsupportedOperationException(
          "Cannot transform " + st.toString() + " into a Filter Predicate.");
    }

    String propertyName = (String) innerAppended.get(0);

    String opString = (String) appended.get(1);

    if (!(appended.get(2) instanceof IntegerLiteral)) {
      throw new UnsupportedOperationException(
          "Cannot transform " + st.toString() + " into a Filter Predicate.");
    }
    Object value = ((IntegerLiteral) appended.get(2)).getValue();
    Query.FilterOperator op = FILTER_OPERATOR_MAP.get(opString.trim());
    if (op == null) {
      throw new UnsupportedOperationException("Unsupported operator in expression " + st.toString());
    }
    return new Query.FilterPredicate(propertyName, op, value);
  }


  public void andCondition(BooleanExpression condition) {
    andConditions.add(condition);
  }

  public void andCondition(BooleanExpression condition, boolean unionQueries) {
    // we don't support union so just ignore the unionQueries param
    andConditions.add(condition);
  }

  public void setParent(QueryExpression parentQueryExpr) {
    throw new UnsupportedOperationException();
  }

  public QueryExpression getParent() {
    throw new UnsupportedOperationException();
  }

  public void setCandidateInformation(Class cls, String alias) {
    throw new UnsupportedOperationException();
  }

  public Class getCandidateClass() {
    throw new UnsupportedOperationException();
  }

  public String getCandidateAlias() {
    throw new UnsupportedOperationException();
  }

  public LogicSetExpression getMainTableExpression() {
    return new LogicSetExpression(this, mainTable, null) {

      public String referenceColumn(DatastoreField col) {
        return col.getIdentifier().getIdentifierName();
      }

      public String toString() {
        return null;
      }
    };
  }

  public DatastoreIdentifier getMainTableAlias() {
    throw new UnsupportedOperationException();
  }

  public LogicSetExpression getTableExpression(DatastoreIdentifier alias) {
    return null;
  }

  public LogicSetExpression newTableExpression(DatastoreContainerObject mainTable,
                                               DatastoreIdentifier alias) {
    throw new UnsupportedOperationException();
  }

  public LogicSetExpression[] newTableExpression(DatastoreContainerObject mainTable,
                                                 DatastoreIdentifier alias, boolean unionQueries) {
    throw new UnsupportedOperationException();
  }

  public MappedStoreManager getStoreManager() {
    return mainTable.getStoreManager();
  }

  public ClassLoaderResolver getClassLoaderResolver() {
    return clr;
  }

  public void setDistinctResults(boolean distinctResults) {
    throw new UnsupportedOperationException();
  }

  public void addExtension(String key, Object value) {
    throw new UnsupportedOperationException();
  }

  public Object getValueForExtension(String key) {
    throw new UnsupportedOperationException();
  }

  public HashMap getExtensions() {
    throw new UnsupportedOperationException();
  }

  public boolean hasMetaDataExpression() {
    return false;
  }

  public int[] selectDatastoreIdentity(String alias, boolean unionQueries) {
    throw new UnsupportedOperationException();
  }

  public int[] selectVersion(String alias, boolean unionQueries) {
    throw new UnsupportedOperationException();
  }

  public int[] selectField(String fieldName, String alias, boolean unionQueries) {
    throw new UnsupportedOperationException();
  }

  public int[] select(JavaTypeMapping mapping) {
    throw new UnsupportedOperationException();
  }

  public int[] select(JavaTypeMapping mapping, boolean unionQueries) {
    return null;
  }

  public int selectScalarExpression(ScalarExpression expr) {
    throw new UnsupportedOperationException();
  }

  public int selectScalarExpression(ScalarExpression expr, boolean unionQueries) {
    throw new UnsupportedOperationException();
  }

  public int[] select(DatastoreIdentifier alias, JavaTypeMapping mapping) {
    throw new UnsupportedOperationException();
  }

  public int[] select(DatastoreIdentifier alias, JavaTypeMapping mapping, boolean unionQueries) {
    throw new UnsupportedOperationException();
  }

  public void crossJoin(LogicSetExpression tableExpr, boolean unionQueries) {
    throw new UnsupportedOperationException();
  }

  public boolean hasCrossJoin(LogicSetExpression tableExpr) {
    throw new UnsupportedOperationException();
  }

  public void innerJoin(ScalarExpression expr, ScalarExpression expr2, LogicSetExpression tblExpr,
                        boolean equals, boolean unionQueries) {
    throw new UnsupportedOperationException();
  }

  public void innerJoin(ScalarExpression expr, ScalarExpression expr2, LogicSetExpression tblExpr,
                        boolean equals) {
    throw new UnsupportedOperationException();
  }

  public void leftOuterJoin(ScalarExpression expr, ScalarExpression expr2,
                            LogicSetExpression tblExpr, boolean equals, boolean unionQueries) {
    throw new UnsupportedOperationException();
  }

  public void leftOuterJoin(ScalarExpression expr, ScalarExpression expr2,
                            LogicSetExpression tblExpr, boolean equals) {
    throw new UnsupportedOperationException();
  }

  public void rightOuterJoin(ScalarExpression expr, ScalarExpression expr2,
                             LogicSetExpression tblExpr, boolean equals, boolean unionQueries) {
    throw new UnsupportedOperationException();
  }

  public void rightOuterJoin(ScalarExpression expr, ScalarExpression expr2,
                             LogicSetExpression tblExpr, boolean equals) {
    throw new UnsupportedOperationException();
  }

  public void addGroupingExpression(ScalarExpression expr) {
    throw new UnsupportedOperationException();
  }

  public void setHaving(BooleanExpression expr) {
    throw new UnsupportedOperationException();
  }

  public void setUpdates(ScalarExpression[] exprs) {
    throw new UnsupportedOperationException();
  }

  public void union(QueryExpression qe) {
    throw new UnsupportedOperationException();
  }

  public void iorCondition(BooleanExpression condition) {
    throw new UnsupportedOperationException();
  }

  public void iorCondition(BooleanExpression condition, boolean unionQueries) {
    throw new UnsupportedOperationException();
  }

  public void setRangeConstraint(long offset, long count) {
    throw new UnsupportedOperationException();
  }

  public void setExistsSubQuery(boolean isExistsSubQuery) {
    throw new UnsupportedOperationException();
  }

  public int getNumberOfScalarExpressions() {
    throw new UnsupportedOperationException();
  }

  public StatementText toDeleteStatementText() {
    throw new UnsupportedOperationException();
  }

  public StatementText toUpdateStatementText() {
    throw new UnsupportedOperationException();
  }

  public StatementText toStatementText(boolean lock) {
    throw new UnsupportedOperationException();
  }

  public void reset() {
    throw new UnsupportedOperationException();
  }

  public boolean hasNucleusTypeExpression() {
    return false;
  }
}
