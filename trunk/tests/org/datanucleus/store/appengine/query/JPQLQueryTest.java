package org.datanucleus.store.appengine.query;

import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.appengine.api.datastore.Query.SortPredicate;

import org.datanucleus.jpa.JPAQuery;
import org.datanucleus.query.expression.Expression;
import org.datanucleus.store.appengine.JPATestCase;
import org.datanucleus.store.appengine.Utils;
import org.datanucleus.test.BidirectionalChildListJPA;
import org.datanucleus.test.Book;
import org.datanucleus.test.Flight;
import org.datanucleus.test.HasAncestorJPA;
import org.datanucleus.test.HasKeyPkJPA;
import org.datanucleus.test.HasMultiValuePropsJPA;
import org.datanucleus.test.HasOneToManyListJPA;
import org.datanucleus.test.HasOneToOneJPA;
import org.datanucleus.test.Person;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.PersistenceException;
import javax.persistence.Query;

public class JPQLQueryTest extends JPATestCase {

  private static final List<SortPredicate> NO_SORTS = Collections.emptyList();
  private static final List<FilterPredicate> NO_FILTERS = Collections.emptyList();

  private static final FilterPredicate TITLE_EQ_2 =
      new FilterPredicate("title", FilterOperator.EQUAL, 2L);
  private static final FilterPredicate TITLE_EQ_2STR =
      new FilterPredicate("title", FilterOperator.EQUAL, "2");
  private static final FilterPredicate ISBN_EQ_4 =
      new FilterPredicate("isbn", FilterOperator.EQUAL, 4L);
  private static final FilterPredicate TITLE_GT_2 =
      new FilterPredicate("title", FilterOperator.GREATER_THAN, 2L);
  private static final FilterPredicate TITLE_GTE_2 =
      new FilterPredicate("title", FilterOperator.GREATER_THAN_OR_EQUAL, 2L);
  private static final FilterPredicate ISBN_LT_4 =
      new FilterPredicate("isbn", FilterOperator.LESS_THAN, 4L);
  private static final FilterPredicate ISBN_LTE_4 =
      new FilterPredicate("isbn", FilterOperator.LESS_THAN_OR_EQUAL, 4L);
  private static final SortPredicate TITLE_ASC = new SortPredicate("title", SortDirection.ASCENDING);
  private static final SortPredicate ISBN_DESC = new SortPredicate("isbn", SortDirection.DESCENDING);

  @Override
  protected EntityManagerFactoryName getEntityManagerFactoryName() {
    return EntityManagerFactoryName.nontransactional_ds_non_transactional_ops_allowed;
  }

  public void testUnsupportedFilters() {
    String baseQuery = "SELECT FROM " + Book.class.getName() + " ";

    assertQueryUnsupportedByOrm(baseQuery + "GROUP BY author", DatastoreQuery.GROUP_BY_OP);
    // Can't actually test having because the parser doesn't recognize it unless there is a
    // group by, and the group by gets seen first.
    assertQueryUnsupportedByOrm(baseQuery + "GROUP BY author HAVING title = 'foo'", DatastoreQuery.GROUP_BY_OP);

    Set<Expression.Operator> unsupportedOps =
        new HashSet<Expression.Operator>(DatastoreQuery.UNSUPPORTED_OPERATORS);
    baseQuery += "WHERE ";
    assertQueryUnsupportedByOrm(baseQuery + "title = 'foo' OR title = 'bar'", Expression.OP_OR, unsupportedOps);
    assertQueryUnsupportedByOrm(baseQuery + "NOT title = 'foo'", Expression.OP_NOT, unsupportedOps);
    assertQueryUnsupportedByOrm(baseQuery + "(title + author) = 'foo'", Expression.OP_ADD, unsupportedOps);
    assertQueryUnsupportedByOrm(baseQuery + "title + author = 'foo'", Expression.OP_ADD, unsupportedOps);
    assertQueryUnsupportedByOrm(baseQuery + "(title - author) = 'foo'", Expression.OP_SUB, unsupportedOps);
    assertQueryUnsupportedByOrm(baseQuery + "title - author = 'foo'", Expression.OP_SUB, unsupportedOps);
    assertQueryUnsupportedByOrm(baseQuery + "(title / author) = 'foo'", Expression.OP_DIV, unsupportedOps);
    assertQueryUnsupportedByOrm(baseQuery + "title / author = 'foo'", Expression.OP_DIV, unsupportedOps);
    assertQueryUnsupportedByOrm(baseQuery + "(title * author) = 'foo'", Expression.OP_MUL, unsupportedOps);
    assertQueryUnsupportedByOrm(baseQuery + "title * author = 'foo'", Expression.OP_MUL, unsupportedOps);
    assertQueryUnsupportedByOrm(baseQuery + "(title % author) = 'foo'", Expression.OP_MOD, unsupportedOps);
    assertQueryUnsupportedByOrm(baseQuery + "title % author = 'foo'", Expression.OP_MOD, unsupportedOps);
    assertQueryUnsupportedByOrm(baseQuery + "title LIKE 'foo%'", Expression.OP_LIKE, unsupportedOps);
    // multiple inequality filters
    assertQueryUnsupportedByDatastore(baseQuery + "(title > 2 AND isbn < 4)");
    // inequality filter prop is not the same as the first order by prop
    assertQueryUnsupportedByDatastore(baseQuery + "(title > 2) order by isbn");

    assertEquals(new HashSet<Expression.Operator>(Arrays.asList(Expression.OP_CONCAT, Expression.OP_COM,
        Expression.OP_NEG, Expression.OP_IS, Expression.OP_BETWEEN,
        Expression.OP_ISNOT)), unsupportedOps);
  }

  public void testSupportedFilters() {
    String baseQuery = "SELECT FROM " + Book.class.getName() + " ";

    assertQuerySupported(baseQuery, NO_FILTERS, NO_SORTS);

    baseQuery += "WHERE ";
    assertQuerySupported(baseQuery + "title = 2", Utils.newArrayList(TITLE_EQ_2), NO_SORTS);
    assertQuerySupported(baseQuery + "title = \"2\"", Utils.newArrayList(TITLE_EQ_2STR), NO_SORTS);
    assertQuerySupported(baseQuery + "(title = 2)", Utils.newArrayList(TITLE_EQ_2), NO_SORTS);
    assertQuerySupported(baseQuery + "title = 2 AND isbn = 4", Utils.newArrayList(TITLE_EQ_2,
        ISBN_EQ_4), NO_SORTS);
    assertQuerySupported(baseQuery + "(title = 2 AND isbn = 4)", Utils.newArrayList(TITLE_EQ_2,
        ISBN_EQ_4), NO_SORTS);
    assertQuerySupported(baseQuery + "(title = 2) AND (isbn = 4)", Utils.newArrayList(
        TITLE_EQ_2, ISBN_EQ_4), NO_SORTS);
    assertQuerySupported(baseQuery + "title > 2", Utils.newArrayList(TITLE_GT_2), NO_SORTS);
    assertQuerySupported(baseQuery + "title >= 2", Utils.newArrayList(TITLE_GTE_2), NO_SORTS);
    assertQuerySupported(baseQuery + "isbn < 4", Utils.newArrayList(ISBN_LT_4), NO_SORTS);
    assertQuerySupported(baseQuery + "isbn <= 4", Utils.newArrayList(ISBN_LTE_4), NO_SORTS);

    baseQuery = "SELECT FROM " + Book.class.getName() + " ";
    assertQuerySupported(baseQuery + "ORDER BY title ASC", NO_FILTERS, Utils.newArrayList(TITLE_ASC));
    assertQuerySupported(baseQuery + "ORDER BY isbn DESC", NO_FILTERS, Utils.newArrayList(ISBN_DESC));
    assertQuerySupported(baseQuery + "ORDER BY title ASC, isbn DESC", NO_FILTERS,
        Utils.newArrayList(TITLE_ASC, ISBN_DESC));

    assertQuerySupported(baseQuery + "WHERE title = 2 AND isbn = 4 ORDER BY title ASC, isbn DESC",
        Utils.newArrayList(TITLE_EQ_2, ISBN_EQ_4), Utils.newArrayList(TITLE_ASC, ISBN_DESC));
  }

  public void test2Equals2OrderBy() {
    ldth.ds.put(newBook("Bar Book", "Joe Blow", "67890"));
    ldth.ds.put(newBook("Bar Book", "Joe Blow", "11111"));
    ldth.ds.put(newBook("Foo Book", "Joe Blow", "12345"));
    ldth.ds.put(newBook("A Book", "Joe Blow", "54321"));
    ldth.ds.put(newBook("Baz Book", "Jane Blow", "13579"));

    Query q = em.createQuery("SELECT FROM " +
        Book.class.getName() +
        " WHERE author = 'Joe Blow'" +
        " ORDER BY title DESC, isbn ASC");

    @SuppressWarnings("unchecked")
    List<Book> result = (List<Book>) q.getResultList();

    assertEquals(4, result.size());
    assertEquals("12345", result.get(0).getIsbn());
    assertEquals("11111", result.get(1).getIsbn());
    assertEquals("67890", result.get(2).getIsbn());
    assertEquals("54321", result.get(3).getIsbn());
  }

  public void testDefaultOrderingIsAsc() {
    ldth.ds.put(newBook("Bar Book", "Joe Blow", "67890"));
    ldth.ds.put(newBook("Bar Book", "Joe Blow", "11111"));
    ldth.ds.put(newBook("Foo Book", "Joe Blow", "12345"));
    ldth.ds.put(newBook("A Book", "Joe Blow", "54321"));
    ldth.ds.put(newBook("Baz Book", "Jane Blow", "13579"));

    Query q = em.createQuery("SELECT FROM " +
        Book.class.getName() +
        " WHERE author = 'Joe Blow'" +
        " ORDER BY title");

    @SuppressWarnings("unchecked")
    List<Book> result = (List<Book>) q.getResultList();

    assertEquals(4, result.size());
    assertEquals("54321", result.get(0).getIsbn());
    assertEquals("67890", result.get(1).getIsbn());
    assertEquals("11111", result.get(2).getIsbn());
    assertEquals("12345", result.get(3).getIsbn());
  }

  public void testLimitQuery() {
    ldth.ds.put(newBook("Bar Book", "Joe Blow", "67890"));
    ldth.ds.put(newBook("Bar Book", "Joe Blow", "11111"));
    ldth.ds.put(newBook("Foo Book", "Joe Blow", "12345"));
    ldth.ds.put(newBook("A Book", "Joe Blow", "54321"));
    ldth.ds.put(newBook("Baz Book", "Jane Blow", "13579"));

    Query q = em.createQuery("SELECT FROM " +
        Book.class.getName() +
        " WHERE author = 'Joe Blow'" +
        " ORDER BY title DESC, isbn ASC");

    q.setMaxResults(1);
    @SuppressWarnings("unchecked")
    List<Book> result1 = (List<Book>) q.getResultList();
    assertEquals(1, result1.size());
    assertEquals("12345", result1.get(0).getIsbn());

    q.setMaxResults(0);
    @SuppressWarnings("unchecked")
    List<Book> result2 = (List<Book>) q.getResultList();
    assertEquals(0, result2.size());

    try {
      q.setMaxResults(-1);
      fail("expected iae");
    } catch (IllegalArgumentException iae) {
      // good
    }
  }

  public void testOffsetQuery() {
    ldth.ds.put(newBook("Bar Book", "Joe Blow", "67890"));
    ldth.ds.put(newBook("Bar Book", "Joe Blow", "11111"));
    ldth.ds.put(newBook("Foo Book", "Joe Blow", "12345"));
    ldth.ds.put(newBook("A Book", "Joe Blow", "54321"));
    ldth.ds.put(newBook("Baz Book", "Jane Blow", "13579"));
    Query q = em.createQuery("SELECT FROM " +
        Book.class.getName() +
        " WHERE author = 'Joe Blow'" +
        " ORDER BY title DESC, isbn ASC");

    q.setFirstResult(0);
    @SuppressWarnings("unchecked")
    List<Book> result1 = (List<Book>) q.getResultList();
    assertEquals(4, result1.size());
    assertEquals("12345", result1.get(0).getIsbn());

    q.setFirstResult(1);
    @SuppressWarnings("unchecked")
    List<Book> result2 = (List<Book>) q.getResultList();
    assertEquals(3, result2.size());
    assertEquals("11111", result2.get(0).getIsbn());

    try {
      q.setFirstResult(-1);
      fail("expected iae");
    } catch (IllegalArgumentException iae) {
      // good
    }
  }

  public void testOffsetLimitQuery() {
    ldth.ds.put(newBook("Bar Book", "Joe Blow", "67890"));
    ldth.ds.put(newBook("Bar Book", "Joe Blow", "11111"));
    ldth.ds.put(newBook("Foo Book", "Joe Blow", "12345"));
    ldth.ds.put(newBook("A Book", "Joe Blow", "54321"));
    ldth.ds.put(newBook("Baz Book", "Jane Blow", "13579"));
    Query q = em.createQuery("SELECT FROM " +
        Book.class.getName() +
        " WHERE author = 'Joe Blow'" +
        " ORDER BY title DESC, isbn ASC");

    q.setFirstResult(0);
    q.setMaxResults(0);
    @SuppressWarnings("unchecked")
    List<Book> result1 = (List<Book>) q.getResultList();
    assertEquals(0, result1.size());

    q.setFirstResult(1);
    q.setMaxResults(0);
    @SuppressWarnings("unchecked")
    List<Book> result2 = (List<Book>) q.getResultList();
    assertEquals(0, result2.size());

    q.setFirstResult(0);
    q.setMaxResults(1);
    @SuppressWarnings("unchecked")
    List<Book> result3 = (List<Book>) q.getResultList();
    assertEquals(1, result3.size());

    q.setFirstResult(0);
    q.setMaxResults(2);
    @SuppressWarnings("unchecked")
    List<Book> result4 = (List<Book>) q.getResultList();
    assertEquals(2, result4.size());
    assertEquals("12345", result4.get(0).getIsbn());

    q.setFirstResult(1);
    q.setMaxResults(1);
    @SuppressWarnings("unchecked")
    List<Book> result5 = (List<Book>) q.getResultList();
    assertEquals(1, result5.size());
    assertEquals("11111", result5.get(0).getIsbn());

    q.setFirstResult(2);
    q.setMaxResults(5);
    @SuppressWarnings("unchecked")
    List<Book> result6 = (List<Book>) q.getResultList();
    assertEquals(2, result6.size());
    assertEquals("67890", result6.get(0).getIsbn());
  }

  public void testSerialization() throws IOException {
    Query q = em.createQuery("select from " + Book.class.getName());
    q.getResultList();

    JPQLQuery innerQuery = (JPQLQuery)((JPAQuery)q).getInternalQuery();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    // the fact that this doesn't blow up is the test
    oos.writeObject(innerQuery);
  }

  public void testBindVariables() {

    assertQuerySupported("select from " + Book.class.getName() + " where title = :title",
        Utils.newArrayList(TITLE_EQ_2), NO_SORTS, "title", 2L);

    assertQuerySupported("select from " + Book.class.getName()
        + " where title = :title AND isbn = :isbn",
        Utils.newArrayList(TITLE_EQ_2, ISBN_EQ_4), NO_SORTS, "title", 2L, "isbn", 4L);

    assertQuerySupported("select from " + Book.class.getName()
        + " where title = :title AND isbn = :isbn order by title asc, isbn desc",
        Utils.newArrayList(TITLE_EQ_2, ISBN_EQ_4),
        Utils.newArrayList(TITLE_ASC, ISBN_DESC), "title", 2L, "isbn", 4L);
  }

  public void testKeyQuery() {
    Entity bookEntity = newBook("Bar Book", "Joe Blow", "67890");
    ldth.ds.put(bookEntity);

    javax.persistence.Query q = em.createQuery(
        "select from " + Book.class.getName()
            + " where id = :key");
    q.setParameter("key", KeyFactory.keyToString(bookEntity.getKey()));
    @SuppressWarnings("unchecked")
    List<Book> books = (List<Book>) q.getResultList();
    assertEquals(1, books.size());
    assertEquals(bookEntity.getKey(), KeyFactory.stringToKey(books.get(0).getId()));

    // now issue the same query, but instead of providing a String version of
    // the key, provide the Key itself.
    q.setParameter("key", bookEntity.getKey());
    @SuppressWarnings("unchecked")
    List<Book> books2 = (List<Book>) q.getResultList();
    assertEquals(1, books2.size());
    assertEquals(bookEntity.getKey(), KeyFactory.stringToKey(books2.get(0).getId()));
  }

  public void testKeyQuery_KeyPk() {
    Entity e = new Entity(HasKeyPkJPA.class.getSimpleName());
    ldth.ds.put(e);

    javax.persistence.Query q = em.createQuery(
        "select from " + HasKeyPkJPA.class.getName() + " where id = :key");
    q.setParameter("key", e.getKey());
    @SuppressWarnings("unchecked")
    List<HasKeyPkJPA> result = (List<HasKeyPkJPA>) q.getResultList();
    assertEquals(1, result.size());
    assertEquals(e.getKey(), result.get(0).getId());
  }

  public void testKeyQueryWithSorts() {
    Entity bookEntity = newBook("Bar Book", "Joe Blow", "67890");
    ldth.ds.put(bookEntity);

    javax.persistence.Query q = em.createQuery(
        "select from " + Book.class.getName()
            + " where id = :key order by isbn ASC");
    q.setParameter("key", KeyFactory.keyToString(bookEntity.getKey()));
    @SuppressWarnings("unchecked")
    List<Book> books = (List<Book>) q.getResultList();
    assertEquals(1, books.size());
    assertEquals(bookEntity.getKey(), KeyFactory.stringToKey(books.get(0).getId()));
  }

  public void testKeyQuery_MultipleFilters() {
    Entity bookEntity = newBook("Bar Book", "Joe Blow", "67890");
    ldth.ds.put(bookEntity);

    javax.persistence.Query q = em.createQuery(
        "select from " + Book.class.getName()
            + " where id = :key and isbn = \"67890\"");
    q.setParameter("key", KeyFactory.keyToString(bookEntity.getKey()));
    @SuppressWarnings("unchecked")
    List<Book> books = (List<Book>) q.getResultList();
    assertEquals(1, books.size());
    assertEquals(bookEntity.getKey(), KeyFactory.stringToKey(books.get(0).getId()));
  }

  public void testKeyQuery_NonEqualityFilter() {
    Entity bookEntity1 = newBook("Bar Book", "Joe Blow", "67890");
    ldth.ds.put(bookEntity1);

    Entity bookEntity2 = newBook("Bar Book", "Joe Blow", "67890");
    ldth.ds.put(bookEntity2);

    javax.persistence.Query q = em.createQuery(
        "select from " + Book.class.getName()
            + " where id > :key");
    q.setParameter("key", KeyFactory.keyToString(bookEntity1.getKey()));
    @SuppressWarnings("unchecked")
    List<Book> books = (List<Book>) q.getResultList();
    assertEquals(1, books.size());
    assertEquals(bookEntity2.getKey(), KeyFactory.stringToKey(books.get(0).getId()));
  }

  public void testKeyQuery_SortByKey() {
    Entity bookEntity1 = newBook("Bar Book", "Joe Blow", "67890");
    ldth.ds.put(bookEntity1);

    Entity bookEntity2 = newBook("Bar Book", "Joe Blow", "67890");
    ldth.ds.put(bookEntity2);

    javax.persistence.Query q = em.createQuery(
        "select from " + Book.class.getName()
            + " order by id DESC");
    @SuppressWarnings("unchecked")
    List<Book> books = (List<Book>) q.getResultList();
    assertEquals(2, books.size());
    assertEquals(bookEntity2.getKey(), KeyFactory.stringToKey(books.get(0).getId()));
  }

  public void testAncestorQuery() {
    Entity bookEntity = newBook("Bar Book", "Joe Blow", "67890");
    ldth.ds.put(bookEntity);
    Entity hasAncestorEntity = new Entity(HasAncestorJPA.class.getSimpleName(), bookEntity.getKey());
    ldth.ds.put(hasAncestorEntity);

    javax.persistence.Query q = em.createQuery(
        "select from " + HasAncestorJPA.class.getName() + " where ancestorId = :ancId");
    q.setParameter("ancId", KeyFactory.keyToString(bookEntity.getKey()));

    @SuppressWarnings("unchecked")
    List<HasAncestorJPA> haList = (List<HasAncestorJPA>) q.getResultList();
    assertEquals(1, haList.size());
    assertEquals(bookEntity.getKey(), KeyFactory.stringToKey(haList.get(0).getAncestorId()));

    assertEquals(
        bookEntity.getKey(), getDatastoreQuery(q).getMostRecentDatastoreQuery().getAncestor());
    assertEquals(NO_FILTERS, getFilterPredicates(q));
    assertEquals(NO_SORTS, getSortPredicates(q));
  }

  public void testIllegalAncestorQuery() {
    Entity bookEntity = newBook("Bar Book", "Joe Blow", "67890");
    ldth.ds.put(bookEntity);
    Entity hasAncestorEntity = new Entity(HasAncestorJPA.class.getName(), bookEntity.getKey());
    ldth.ds.put(hasAncestorEntity);

    javax.persistence.Query q = em.createQuery(
        "select from " + HasAncestorJPA.class.getName() + " where ancestorId > :ancId");
    q.setParameter("ancId", KeyFactory.keyToString(bookEntity.getKey()));
    try {
      q.getResultList();
      fail ("expected udfe");
    } catch (DatastoreQuery.UnsupportedDatastoreFeatureException udfe) {
      // good
    }
  }

  public void testSortByFieldWithCustomColumn() {
    ldth.ds.put(newBook("Bar Book", "Joe Blow", "67890", 2003));
    ldth.ds.put(newBook("Bar Book", "Joe Blow", "11111", 2002));
    ldth.ds.put(newBook("Foo Book", "Joe Blow", "12345", 2001));

    Query q = em.createQuery("SELECT FROM " +
        Book.class.getName() +
        " WHERE author = 'Joe Blow'" +
        " ORDER BY firstPublished ASC");

    @SuppressWarnings("unchecked")
    List<Book> result = (List<Book>) q.getResultList();

    assertEquals(3, result.size());
    assertEquals("12345", result.get(0).getIsbn());
    assertEquals("11111", result.get(1).getIsbn());
    assertEquals("67890", result.get(2).getIsbn());
  }

  public void testFilterByChildObject() {
    Entity parentEntity = new Entity(HasOneToOneJPA.class.getSimpleName());
    ldth.ds.put(parentEntity);
    Entity bookEntity = newBook(parentEntity.getKey(), "Bar Book", "Joe Blow", "11111", 1929);
    ldth.ds.put(bookEntity);

    Book book = em.find(Book.class, KeyFactory.keyToString(bookEntity.getKey()));
    Query q = em.createQuery(
        "select from " + HasOneToOneJPA.class.getName() + " where book = :b");
    q.setParameter("b", book);
    List<HasOneToOneJPA> result = (List<HasOneToOneJPA>) q.getResultList();
    assertEquals(1, result.size());
    assertEquals(parentEntity.getKey(), KeyFactory.stringToKey(result.get(0).getId()));
  }

  public void testFilterByChildObject_AdditionalFilterOnParent() {
    Entity parentEntity = new Entity(HasOneToOneJPA.class.getSimpleName());
    ldth.ds.put(parentEntity);
    Entity bookEntity = newBook(parentEntity.getKey(), "Bar Book", "Joe Blow", "11111", 1929);
    ldth.ds.put(bookEntity);

    Book book = em.find(Book.class, KeyFactory.keyToString(bookEntity.getKey()));
    Query q = em.createQuery(
        "select from " + HasOneToOneJPA.class.getName() + " where id = :parentId and book = :b");
    q.setParameter("parentId", KeyFactory.keyToString(bookEntity.getKey()));
    q.setParameter("b", book);
    List<HasOneToOneJPA> result = (List<HasOneToOneJPA>) q.getResultList();
    assertTrue(result.isEmpty());

    q.setParameter("parentId", KeyFactory.keyToString(parentEntity.getKey()));
    q.setParameter("b", book);
    result = (List<HasOneToOneJPA>) q.getResultList();
    assertEquals(1, result.size());
    assertEquals(parentEntity.getKey(), KeyFactory.stringToKey(result.get(0).getId()));
  }

  public void testFilterByChildObject_UnsupportedOperator() {
    Entity parentEntity = new Entity(HasOneToOneJPA.class.getSimpleName());
    ldth.ds.put(parentEntity);
    Entity bookEntity = newBook(parentEntity.getKey(), "Bar Book", "Joe Blow", "11111", 1929);
    ldth.ds.put(bookEntity);

    Book book = em.find(Book.class, KeyFactory.keyToString(bookEntity.getKey()));
    Query q = em.createQuery(
        "select from " + HasOneToOneJPA.class.getName() + " where book > :b");
        q.setParameter("b", book);
    try {
      q.getResultList();
      fail("expected udfe");
    } catch (DatastoreQuery.UnsupportedDatastoreFeatureException udfe) {
      // good
    }
  }

  public void testFilterByChildObject_ValueWithoutAncestor() {
    Entity parentEntity = new Entity(HasOneToOneJPA.class.getSimpleName());
    ldth.ds.put(parentEntity);
    Entity bookEntity = newBook("Bar Book", "Joe Blow", "11111", 1929);
    ldth.ds.put(bookEntity);

    Book book = em.find(Book.class, KeyFactory.keyToString(bookEntity.getKey()));
    Query q = em.createQuery(
        "select from " + HasOneToOneJPA.class.getName() + " where book = :b");
    q.setParameter("b", book);
    try {
      q.getResultList();
      fail("expected JPAException");
    } catch (PersistenceException e) {
      // good
    }
  }

  public void testFilterByChildObject_KeyIsWrongType() {
    Entity parentEntity = new Entity(HasOneToOneJPA.class.getSimpleName());
    ldth.ds.put(parentEntity);

    Query q = em.createQuery(
        "select from " + HasOneToOneJPA.class.getName() + " where book = :b");
    q.setParameter("b", parentEntity.getKey());
    try {
      q.getResultList();
      fail("expected JPAException");
    } catch (PersistenceException e) {
      // good
    }
  }

  public void testFilterByChildObject_KeyParentIsWrongType() {
    Key parent = KeyFactory.createKey("yar", 44);
    Entity bookEntity = new Entity(Book.class.getSimpleName(), parent);

    Query q = em.createQuery(
        "select from " + HasOneToOneJPA.class.getName() + " where book = :b");
    q.setParameter("b", bookEntity.getKey());
    try {
      q.getResultList();
      fail("expected JPAException");
    } catch (PersistenceException e) {
      // good
    }
  }

  public void testFilterByChildObject_ValueWithoutId() {
    Entity parentEntity = new Entity(HasOneToOneJPA.class.getSimpleName());
    ldth.ds.put(parentEntity);
    Entity bookEntity = newBook("Bar Book", "Joe Blow", "11111", 1929);
    ldth.ds.put(bookEntity);

    Book book = new Book();
    Query q = em.createQuery(
        "select from " + HasOneToOneJPA.class.getName() + " where book = :b");
    q.setParameter("b", book);
    try {
      q.getResultList();
      fail("expected JPAException");
    } catch (PersistenceException e) {
      // good
    }
  }

  public void testFilterByParentObject() {
    Entity parentEntity = new Entity(HasOneToManyListJPA.class.getSimpleName());
    ldth.ds.put(parentEntity);
    Entity bidirEntity = new Entity(BidirectionalChildListJPA.class.getSimpleName(), parentEntity.getKey());
    ldth.ds.put(bidirEntity);
    Entity bidirEntity2 = new Entity(BidirectionalChildListJPA.class.getSimpleName(), parentEntity.getKey());
    ldth.ds.put(bidirEntity2);

    HasOneToManyListJPA parent =
        em.find(HasOneToManyListJPA.class, KeyFactory.keyToString(parentEntity.getKey()));
    Query q = em.createQuery("SELECT FROM " +
        BidirectionalChildListJPA.class.getName() +
        " WHERE parent = :p");

    q.setParameter("p", parent);
    @SuppressWarnings("unchecked")
    List<BidirectionalChildListJPA> result = (List<BidirectionalChildListJPA>) q.getResultList();
    assertEquals(2, result.size());
    assertEquals(bidirEntity.getKey(), KeyFactory.stringToKey(result.get(0).getId()));
    assertEquals(bidirEntity2.getKey(), KeyFactory.stringToKey(result.get(1).getId()));
  }

  public void testFilterByParentId() {
    Entity parentEntity = new Entity(HasOneToManyListJPA.class.getSimpleName());
    ldth.ds.put(parentEntity);
    Entity bidirEntity = new Entity(BidirectionalChildListJPA.class.getSimpleName(), parentEntity.getKey());
    ldth.ds.put(bidirEntity);
    Entity bidirEntity2 = new Entity(BidirectionalChildListJPA.class.getSimpleName(), parentEntity.getKey());
    ldth.ds.put(bidirEntity2);

    HasOneToManyListJPA parent =
        em.find(HasOneToManyListJPA.class, KeyFactory.keyToString(parentEntity.getKey()));
    Query q = em.createQuery("SELECT FROM " +
        BidirectionalChildListJPA.class.getName() +
        " WHERE parent = :p");

    q.setParameter("p", parent.getId());
    @SuppressWarnings("unchecked")
    List<BidirectionalChildListJPA> result = (List<BidirectionalChildListJPA>) q.getResultList();
    assertEquals(2, result.size());
    assertEquals(bidirEntity.getKey(), KeyFactory.stringToKey(result.get(0).getId()));
    assertEquals(bidirEntity2.getKey(), KeyFactory.stringToKey(result.get(1).getId()));
  }

  public void testFilterByParentKey() {
    Entity parentEntity = new Entity(HasOneToManyListJPA.class.getSimpleName());
    ldth.ds.put(parentEntity);
    Entity bidirEntity = new Entity(BidirectionalChildListJPA.class.getSimpleName(), parentEntity.getKey());
    ldth.ds.put(bidirEntity);
    Entity bidirEntity2 = new Entity(BidirectionalChildListJPA.class.getSimpleName(), parentEntity.getKey());
    ldth.ds.put(bidirEntity2);

    Query q = em.createQuery("SELECT FROM " +
        BidirectionalChildListJPA.class.getName() +
        " WHERE parent = :p");

    q.setParameter("p", parentEntity.getKey());
    @SuppressWarnings("unchecked")
    List<BidirectionalChildListJPA> result = (List<BidirectionalChildListJPA>) q.getResultList();
    assertEquals(2, result.size());
    assertEquals(bidirEntity.getKey(), KeyFactory.stringToKey(result.get(0).getId()));
    assertEquals(bidirEntity2.getKey(), KeyFactory.stringToKey(result.get(1).getId()));
  }

  public void testFilterByMultiValueProperty() {
    Entity entity = new Entity(HasMultiValuePropsJPA.class.getSimpleName());
    entity.setProperty("strList", Utils.newArrayList("1", "2", "3"));
    entity.setProperty("keyList",
        Utils.newArrayList(KeyFactory.createKey("be", "bo"), KeyFactory.createKey("bo", "be")));
    ldth.ds.put(entity);

    Query q = em.createQuery(
        "select from " + HasMultiValuePropsJPA.class.getName()
        + " where strList = :p1 AND strList = :p2");
    q.setParameter("p1", "1");
    q.setParameter("p2", "3");
    @SuppressWarnings("unchecked")
    List<HasMultiValuePropsJPA> result = (List<HasMultiValuePropsJPA>) q.getResultList();
    assertEquals(1, result.size());
    q.setParameter("p1", "1");
    q.setParameter("p2", "4");
    @SuppressWarnings("unchecked")
    List<HasMultiValuePropsJPA> result2 = (List<HasMultiValuePropsJPA>) q.getResultList();
    assertEquals(0, result2.size());

    q = em.createQuery(
        "select from " + HasMultiValuePropsJPA.class.getName()
        + " where keyList = :p1 AND keyList = :p2");
    q.setParameter("p1", KeyFactory.createKey("be", "bo"));
    q.setParameter("p2", KeyFactory.createKey("bo", "be"));
    assertEquals(1, result.size());
    q.setParameter("p1", KeyFactory.createKey("be", "bo"));
    q.setParameter("p2", KeyFactory.createKey("bo", "be2"));
    @SuppressWarnings("unchecked")
    List<HasMultiValuePropsJPA> result3 = (List<HasMultiValuePropsJPA>) q.getResultList();
    assertEquals(0, result3.size());
  }

  public void testFilterByEmbeddedField() {
    Entity entity = new Entity(Person.class.getSimpleName());
    entity.setProperty("first", "max");
    entity.setProperty("last", "ross");
    entity.setProperty("anotherFirst", "notmax");
    entity.setProperty("anotherLast", "notross");
    ldth.ds.put(entity);

    Query q = em.createQuery(
        "select from " + Person.class.getName() + " where name.first = \"max\"");
    @SuppressWarnings("unchecked")
    List<Person> result = (List<Person>) q.getResultList();
    assertEquals(1, result.size());
  }

  public void testFilterByEmbeddedField_OverriddenColumn() {
    Entity entity = new Entity(Person.class.getSimpleName());
    entity.setProperty("first", "max");
    entity.setProperty("last", "ross");
    entity.setProperty("anotherFirst", "notmax");
    entity.setProperty("anotherLast", "notross");
    ldth.ds.put(entity);

    Query q = em.createQuery(
        "select from " + Person.class.getName()
        + " where anotherName.last = \"notross\"");
    @SuppressWarnings("unchecked")
    List<Person> result = (List<Person>) q.getResultList();
    assertEquals(1, result.size());
  }

  public void testFilterByEmbeddedField_MultipleFields() {
    Entity entity = new Entity(Person.class.getSimpleName());
    entity.setProperty("first", "max");
    entity.setProperty("last", "ross");
    entity.setProperty("anotherFirst", "notmax");
    entity.setProperty("anotherLast", "notross");
    ldth.ds.put(entity);

    Query q = em.createQuery(
        "select from " + Person.class.getName()
        + " where name.first = \"max\" && anotherName.last = \"notross\"");
    @SuppressWarnings("unchecked")
    List<Person> result = (List<Person>) q.getResultList();
    assertEquals(1, result.size());
  }

  public void testFilterBySubObject_UnknownField() {
    try {
      em.createQuery(
          "select from " + Flight.class.getName() + " where origin.first = \"max\"").getResultList();
      fail("expected exception");
    } catch (PersistenceException e) {
      // good
    }
  }

  public void testFilterBySubObject_NotEmbeddable() {
    try {
      em.createQuery(
          "select from " + HasOneToOneJPA.class.getName() + " where flight.origin = \"max\"")
          .getResultList();
      fail("expected exception");
    } catch (PersistenceException e) {
      // good
    }
  }

  private static Entity newBook(String title, String author, String isbn) {
    return newBook(title, author, isbn, 2000);
  }

  private static Entity newBook(String title, String author, String isbn, int firstPublished) {
    return newBook(null, title, author, isbn, firstPublished);
  }

  private static Entity newBook(
      Key parentKey, String title, String author, String isbn, int firstPublished) {
    Entity e;
    if (parentKey != null) {
      e = new Entity(Book.class.getSimpleName(), parentKey);
    } else {
      e = new Entity(Book.class.getSimpleName());
    }
    e.setProperty("title", title);
    e.setProperty("author", author);
    e.setProperty("isbn", isbn);
    e.setProperty("first_published", firstPublished);
    return e;
  }

  private void assertQueryUnsupportedByDatastore(String query) {
    Query q = em.createQuery(query);
    try {
      q.getResultList();
      fail("expected IllegalArgumentException for query <" + query + ">");
    } catch (IllegalArgumentException iae) {
      // good
    }
  }

  private void assertQueryUnsupportedByOrm(String query,
      Expression.Operator unsupportedOp) {
    Query q = em.createQuery(query);
    try {
      q.getResultList();
      fail("expected UnsupportedOperationException for query <" + query + ">");
    } catch (DatastoreQuery.UnsupportedDatastoreOperatorException uoe) {
      // Good.
      assertEquals(unsupportedOp, uoe.getOperation());
    }
  }

  private void assertQueryUnsupportedByOrm(String query,
      Expression.Operator unsupportedOp,
      Set<Expression.Operator> unsupportedOps) {
    assertQueryUnsupportedByOrm(query, unsupportedOp);
    unsupportedOps.remove(unsupportedOp);
  }

  private void assertQuerySupported(String query, List<FilterPredicate> addedFilters,
      List<SortPredicate> addedSorts, Object... nameVals) {
    javax.persistence.Query q = em.createQuery(query);
    String name = null;
    for (Object nameOrVal : nameVals) {
      if (name == null) {
        name = (String) nameOrVal;
      } else {
        q.setParameter(name, nameOrVal);
        name = null;
      }
    }
    q.getResultList();

    assertEquals(addedFilters, getFilterPredicates(q));
    assertEquals(addedSorts, getSortPredicates(q));
  }

  private DatastoreQuery getDatastoreQuery(javax.persistence.Query q) {
    return ((JPQLQuery)((JPAQuery)q).getInternalQuery()).getDatastoreQuery();
  }

  private List<FilterPredicate> getFilterPredicates(javax.persistence.Query q) {
    return getDatastoreQuery(q).getMostRecentDatastoreQuery().getFilterPredicates();
  }

  private List<SortPredicate> getSortPredicates(javax.persistence.Query q) {
    return getDatastoreQuery(q).getMostRecentDatastoreQuery().getSortPredicates();
  }
}
