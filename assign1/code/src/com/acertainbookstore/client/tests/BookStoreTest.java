package com.acertainbookstore.client.tests;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.acertainbookstore.business.Book;
import com.acertainbookstore.business.BookCopy;
import com.acertainbookstore.business.CertainBookStore;
import com.acertainbookstore.business.ImmutableStockBook;
import com.acertainbookstore.business.StockBook;
import com.acertainbookstore.business.BookRating;
import com.acertainbookstore.client.BookStoreHTTPProxy;
import com.acertainbookstore.client.StockManagerHTTPProxy;
import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreConstants;
import com.acertainbookstore.utils.BookStoreException;

/**
 * Test class to test the BookStore interface
 *
 */
public class BookStoreTest {

    private static final int TEST_ISBN = 3044560;
    private static final int NUM_COPIES = 5;
    private static int isbn = 3044561;
    private static boolean localTest = true;
    private static StockManager storeManager;
    private static BookStore client;

    @BeforeClass
    public static void setUpBeforeClass() {
        try {
            String localTestProperty = System
                    .getProperty(BookStoreConstants.PROPERTY_KEY_LOCAL_TEST);
            localTest = (localTestProperty != null) ? Boolean
                    .parseBoolean(localTestProperty) : localTest;
            if (localTest) {
                CertainBookStore store = new CertainBookStore();
                storeManager = store;
                client = store;
            } else {
                storeManager = new StockManagerHTTPProxy(
                        "http://localhost:8081/stock");
                client = new BookStoreHTTPProxy("http://localhost:8081");
            }
            storeManager.removeAllBooks();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Helper method to add some books
     */
    public void addBooks(int isbn, int copies) throws BookStoreException {
        Set<StockBook> booksToAdd = new HashSet<StockBook>();
        StockBook book = new ImmutableStockBook(isbn, "Test of Thrones",
                "George RR Testin'", (float) 10, copies, 0, 0, 0, false);
        booksToAdd.add(book);
        storeManager.addBooks(booksToAdd);
    }

    /**
     * Helper method to get the default book used by initializeBooks
     */
    public StockBook getDefaultBook() {
        return new ImmutableStockBook(TEST_ISBN, "Harry Potter and JUnit",
                "JK Unit", (float) 10, NUM_COPIES, 0, 0, 0, false);
    }

    /**
     * Helper method to get the default book used by initializeBooks
     *
     * @return
     */

    private StockBook getBook(int isbn, int copies, double averageRating, long nRates) throws BookStoreException {
        return new ImmutableStockBook(isbn, "Test of Thrones",
                "George RR Testin'", (float) 10, copies, 0, nRates, Math.round(averageRating*nRates), false);
    }

    /*
     * Helper method to create a set of books with ratings between minRating and maxRating
     */
    private Set<StockBook> getBooks(int n, double minRating, double maxRating, long nRates) throws BookStoreException {
        Set<StockBook> books = new HashSet<>();
        Random rand = new Random();

        for (int i = 0; i < n; ++i) {
            double rating =  (maxRating - minRating) * rand.nextFloat() + minRating;
            books.add(getBook(++isbn, 10, rating, nRates));
        }
        return books;
    }

    /**
     * Method to add a book, executed before every test case is run
     */
    @Before
    public void initializeBooks() throws BookStoreException {
        Set<StockBook> booksToAdd = new HashSet<StockBook>();
        booksToAdd.add(getDefaultBook());
        storeManager.addBooks(booksToAdd);
    }

    /**
     * Method to clean up the book store, execute after every test case is run
     */
    @After
    public void cleanupBooks() throws BookStoreException {
        storeManager.removeAllBooks();
    }

    /**
     * Tests basic buyBook() functionality
     */
    @Test
    public void testBuyAllCopiesDefaultBook() throws BookStoreException {
        // Set of books to buy
        Set<BookCopy> booksToBuy = new HashSet<BookCopy>();
        booksToBuy.add(new BookCopy(TEST_ISBN, NUM_COPIES));

        // Try to buy books
        client.buyBooks(booksToBuy);

        List<StockBook> listBooks = storeManager.getBooks();
        assertTrue(listBooks.size() == 1);
        StockBook bookInList = listBooks.get(0);
        StockBook addedBook = getDefaultBook();

        assertEquals(0, bookInList.getNumCopies());
        TestUtil.assertStockBookEq(addedBook, bookInList, TestUtil.CHECK_NUMCOPIES.IGNORE_NUMCOPIES, TestUtil.CHECK_SALESMISSES.CHECK_SALESMISSES);
    }

    /**
     * Tests that books with invalid ISBNs cannot be bought
     */
    @Test
    public void testBuyInvalidISBN() throws BookStoreException {
        List<StockBook> booksInStorePreTest = storeManager.getBooks();

        // Try to buy a book with invalid isbn
        HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
        booksToBuy.add(new BookCopy(TEST_ISBN, 1)); // valid
        booksToBuy.add(new BookCopy(-1, 1)); // invalid

        // Try to buy the books
        try {
            client.buyBooks(booksToBuy);
            fail();
        } catch (BookStoreException ex) {
            ;
        }

        List<StockBook> booksInStorePostTest = storeManager.getBooks();
        // Check pre and post state are same
        assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
                && booksInStorePreTest.size() == booksInStorePostTest.size());

    }

    /**
     * Tests that books can only be bought if they are in the book store
     */
    @Test
    public void testBuyNonExistingISBN() throws BookStoreException {
        List<StockBook> booksInStorePreTest = storeManager.getBooks();

        // Try to buy a book with isbn which does not exist
        HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
        booksToBuy.add(new BookCopy(TEST_ISBN, 1)); // valid
        booksToBuy.add(new BookCopy(100000, 10)); // invalid

        // Try to buy the books
        try {
            client.buyBooks(booksToBuy);
            fail();
        } catch (BookStoreException ex) {
            ;
        }

        List<StockBook> booksInStorePostTest = storeManager.getBooks();
        // Check pre and post state are same
        assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
                && booksInStorePreTest.size() == booksInStorePostTest.size());

    }

    /**
     * Tests that you can't buy more books than there are copies
     */
    @Test
    public void testBuyTooManyBooks() throws BookStoreException {
        List<StockBook> booksInStorePreTest = storeManager.getBooks();

        // Try to buy more copies than there are in store
        HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
        booksToBuy.add(new BookCopy(TEST_ISBN, NUM_COPIES + 1));

        try {
            client.buyBooks(booksToBuy);
            fail();
        } catch (BookStoreException ex) {
            ;
        }

        List<StockBook> booksInStorePostTest = storeManager.getBooks();
        assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
                && booksInStorePreTest.size() == booksInStorePostTest.size());

    }

    /**
     * Tests that you can't buy a negative number of books
     */
    @Test
    public void testBuyNegativeNumberOfBookCopies() throws BookStoreException {
        List<StockBook> booksInStorePreTest = storeManager.getBooks();

        // Try to buy a negative number of copies
        HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
        booksToBuy.add(new BookCopy(TEST_ISBN, -1));

        try {
            client.buyBooks(booksToBuy);
            fail();
        } catch (BookStoreException ex) {
            ;
        }

        List<StockBook> booksInStorePostTest = storeManager.getBooks();
        assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
                && booksInStorePreTest.size() == booksInStorePostTest.size());

    }

    /**
     * Tests that all books can be retrieved
     */
    @Test
    public void testGetBooks() throws BookStoreException {
        Set<StockBook> booksAdded = new HashSet<StockBook>();
        booksAdded.add(getDefaultBook());

        Set<StockBook> booksToAdd = new HashSet<StockBook>();
        booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 1,
                "The Art of Computer Programming", "Donald Knuth", (float) 300,
                NUM_COPIES, 0, 0, 0, false));
        booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 2,
                "The C Programming Language",
                "Dennis Ritchie and Brian Kerninghan", (float) 50, NUM_COPIES,
                0, 0, 0, false));

        booksAdded.addAll(booksToAdd);

        storeManager.addBooks(booksToAdd);

        // Get books in store
        List<StockBook> listBooks = storeManager.getBooks();

        // Make sure the lists equal each other
        assertTrue(listBooks.containsAll(booksAdded)
                && listBooks.size() == booksAdded.size());
    }

    /**
     * Tests that a list of books with a certain feature can be retrieved
     */
    @Test
    public void testGetCertainBooks() throws BookStoreException {
        Set<StockBook> booksToAdd = new HashSet<StockBook>();
        booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 1,
                "The Art of Computer Programming", "Donald Knuth", (float) 300,
                NUM_COPIES, 0, 0, 0, false));
        booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 2,
                "The C Programming Language",
                "Dennis Ritchie and Brian Kerninghan", (float) 50, NUM_COPIES,
                0, 0, 0, false));

        storeManager.addBooks(booksToAdd);

        // Get a list of ISBNs to retrieved
        Set<Integer> isbnList = new HashSet<Integer>();
        isbnList.add(TEST_ISBN + 1);
        isbnList.add(TEST_ISBN + 2);

        // Get books with that ISBN

        List<Book> books = client.getBooks(isbnList);
        // Make sure the lists equal each other
        assertTrue(books.containsAll(booksToAdd)
                && books.size() == booksToAdd.size());

    }

    /**
     * Tests that books cannot be retrieved if ISBN is invalid
     */
    @Test
    public void testGetInvalidIsbn() throws BookStoreException {
        List<StockBook> booksInStorePreTest = storeManager.getBooks();

        // Make an invalid ISBN
        HashSet<Integer> isbnList = new HashSet<Integer>();
        isbnList.add(TEST_ISBN); // valid
        isbnList.add(-1); // invalid

        HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
        booksToBuy.add(new BookCopy(TEST_ISBN, -1));

        try {
            client.getBooks(isbnList);
            fail();
        } catch (BookStoreException ex) {
            ;
        }

        List<StockBook> booksInStorePostTest = storeManager.getBooks();
        assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
                && booksInStorePreTest.size() == booksInStorePostTest.size());

    }

    @Test
    public void testGetTopRatedBooks() throws BookStoreException {
        // Populate bookstore
        Set<StockBook> topRatedBooks = getBooks(10, 5.0, 4.0, 10);
        Set<StockBook> otherRatedBooks = getBooks(20, 3.0, 0.0, 10);
        Set<StockBook> nonRatedBooks = getBooks(20, 0.0, 0.0, 0);
        storeManager.addBooks(topRatedBooks);
        storeManager.addBooks(otherRatedBooks);
        storeManager.addBooks(nonRatedBooks);

        List<Book> booksTopRated5, booksTopRated10;

        // Both values for < k and == k should work
        booksTopRated5 = client.getTopRatedBooks(5);
        booksTopRated10 = client.getTopRatedBooks(10);

        if (!topRatedBooks.containsAll(booksTopRated5) || !topRatedBooks.containsAll(booksTopRated10))
            fail();
    }

    @Test
    public void testGetTopRatedBooksNonPositiveK() throws BookStoreException {
        try {
            client.getTopRatedBooks(0);
            fail();
        } catch (BookStoreException e) {
            ;
        }
        try {
            client.getTopRatedBooks(-1);
            fail();
        } catch (BookStoreException e) {
            ;
        }
    }


    @Test
    public void testGetTopRatedBooksNoBooks() throws BookStoreException {
        // Remove all books
        storeManager.removeAllBooks();

        assertTrue(client.getTopRatedBooks(5).isEmpty());
    }

    @Test
    public void testGetTopRatedBooksNoRatedBooks() throws BookStoreException {
        // Populate bookstore
        Set<StockBook> nonRatedBooks = getBooks(5, 0.0, 0.0, 0);
        storeManager.addBooks(nonRatedBooks);

        assertTrue(client.getTopRatedBooks(5).isEmpty());
    }


    @Test
    public void testGetTopRatedBooksLessThanKRated() throws BookStoreException {
        // Populate bookstore
        Set<StockBook> ratedBooks = getBooks(5, 4.0, 0.0, 10);
        Set<StockBook> nonRatedBooks = getBooks(30, 0.0, 0.0, 0);
        storeManager.addBooks(ratedBooks);
        storeManager.addBooks(nonRatedBooks);

        assertTrue(ratedBooks.containsAll(client.getTopRatedBooks(10)));
    }

    /**
     * Tests that books valid and existing books get their ratings
     * updated correctly
     */
    @Test
    public void testUpdateRating() throws BookStoreException {
        // Add some books before we begin
        StockBook b1 = getBook(isbn++, 10, 0, 0),
            b2 = getBook(isbn++, 10, 0, 0);

        Set<StockBook> booksToAdd = new HashSet<StockBook>();
        booksToAdd.add(b1);
        booksToAdd.add(b2);

        storeManager.addBooks(booksToAdd);

        //HashSet<BookCopy>
        HashSet<BookRating> ratings = new HashSet<BookRating>();
        ratings.add(new BookRating(b1.getISBN(), 5));
        ratings.add(new BookRating(b2.getISBN(), 3));

        client.rateBooks(ratings);


        // Check that the ratings had the intended effect
        List<StockBook> books = storeManager.getBooks();

        StockBook b1Rated = books.stream().filter(b -> b.equals(b1)).
            collect(Collectors.toList()).get(0);
        StockBook b2Rated = books.stream().filter(b -> b.equals(b2)).
            collect(Collectors.toList()).get(0);

        assertEquals(b1,b1Rated);
        assertEquals(b2,b2Rated);

    }

    /**
     * Tests that rateBooks returns an exception when called with a
     * non-existing book
     */
    @Test
    public void testRateInvalidISBN() throws BookStoreException {
        List<StockBook> preTestBooks = storeManager.getBooks();

        HashSet<BookRating> rateBooks = new HashSet<BookRating>();
        rateBooks.add(new BookRating(-1, 5));

        try {
            client.rateBooks(rateBooks);
            fail();
        } catch (BookStoreException ex) {
            ;
        }

        // Make sure that ratings were unaffected
        List<StockBook> postTestBooks = storeManager.getBooks();
        assertTrue(preTestBooks.containsAll(postTestBooks)
                   && preTestBooks.size() == postTestBooks.size());

    }


    /**
     * Tests that rateBooks returns an exception when called with a
     * non-existing book
     */
    @Test
    public void testRateNonExistingISBN() throws BookStoreException {
        List<StockBook> preTestBooks = storeManager.getBooks();

        HashSet<BookRating> rateBooks = new HashSet<BookRating>();
        rateBooks.add(new BookRating(1337, 5));

        try {
            client.rateBooks(rateBooks);
            fail();
        } catch (BookStoreException ex) {
            ;
        }

        // Make sure that ratings were unaffected
        List<StockBook> postTestBooks = storeManager.getBooks();
        assertTrue(preTestBooks.containsAll(postTestBooks)
                   && preTestBooks.size() == postTestBooks.size());

    }

    /**
     * Tests that rateBooks returns an exception when called with an
     * invalid rating
     */
    @Test
    public void testInvalidRating() throws BookStoreException {
        List<StockBook> preTestBooks = storeManager.getBooks();

        HashSet<BookRating> rateBooks = new HashSet<BookRating>();
        rateBooks.add(new BookRating(TEST_ISBN, 6));

        try {
            client.rateBooks(rateBooks);
            fail();
        } catch (BookStoreException ex) {
            ;
        }

        // Make sure that ratings were unaffected
        List<StockBook> postTestBooks = storeManager.getBooks();
        assertTrue(preTestBooks.containsAll(postTestBooks)
                   && preTestBooks.size() == postTestBooks.size());
    }

    @AfterClass
    public static void tearDownAfterClass() throws BookStoreException {
        storeManager.removeAllBooks();
        if (!localTest) {
            ((BookStoreHTTPProxy) client).stop();
            ((StockManagerHTTPProxy) storeManager).stop();
        }
    }

}
