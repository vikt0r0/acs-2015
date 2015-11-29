package com.acertainbookstore.client.tests;

import static org.junit.Assert.*;

import com.acertainbookstore.business.BookCopy;
import com.acertainbookstore.business.ConcurrentCertainBookStore;
import com.acertainbookstore.business.ImmutableStockBook;
import com.acertainbookstore.business.StockBook;
import com.acertainbookstore.client.BookStoreHTTPProxy;
import com.acertainbookstore.client.StockManagerHTTPProxy;
import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreConstants;
import com.acertainbookstore.utils.BookStoreException;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

public class ConcurrencyTest {

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
                ConcurrentCertainBookStore store = new ConcurrentCertainBookStore();
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

        toAdd = new HashSet<>();
        toBuy = new HashSet<>();

        int copies; int isbn;

        isbn = 1; copies = 8;
        toAdd.add(new ImmutableStockBook(isbn, "Star Wars 1", "Georg Lucas", .42f, copies, 0, 0, 0, false));
        toBuy.add(new BookCopy(isbn, copies));

        isbn++; copies = 4;
        toAdd.add(new ImmutableStockBook(isbn, "Star Wars 2", "Georg Lucas", .43f, copies, 0, 0, 0, false));
        toBuy.add(new BookCopy(isbn, copies));

        isbn++; copies = 3;
        toAdd.add(new ImmutableStockBook(isbn, "Star Wars 2", "Georg Lucas", .44f, copies, 0, 0, 0, false));
        toBuy.add(new BookCopy(isbn, copies));
    }

    private static Set<StockBook> toAdd;
    private static Set<BookCopy> toBuy;

    /**
     * executed before every test case is run
     */
    @Before
    public void initializeBooks() throws BookStoreException {
    }

    /**
     * execute after every test case is run
     */
    @After
    public void cleanupBooks() throws BookStoreException {
        storeManager.removeAllBooks();
    }

    @Test
    public void fixedIterationTest() throws BookStoreException {
        fixedIterations(100000);
    }

    /**
     * Executes 'iterations' buyBooks and addCopies concurrently
     * end results should be the same as initial
     */
    private void fixedIterations(final int iterations) throws BookStoreException {
        storeManager.addBooks(toAdd);

        // if all "buys" happens first, they should never get into a state where they cannot buy books
        for(int i=0; i<iterations; i++) {
            storeManager.addCopies(toBuy);
        }

        List<StockBook> initialBooks = storeManager.getBooks();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<Boolean> buyFuture = executor.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                for (int i = 0; i < iterations; i++) {
                    client.buyBooks(toBuy);
                }
                return true;
            }
        });

        Future<Boolean> addFuture = executor.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                for (int i = 0; i < iterations; i++) {
                    storeManager.addCopies(toBuy);
                }
                return true;
            }
        });

        try{
            buyFuture.get();
            addFuture.get();
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }

        List<StockBook> finalBooks = storeManager.getBooks();

        assertEquals(initialBooks.size(), finalBooks.size());
        for(StockBook finalBook : finalBooks) {
            assertThat(initialBooks, CoreMatchers.hasItem(finalBook));
            StockBook initialBook = initialBooks.get(initialBooks.indexOf(finalBook));
            assertEquals(initialBook.getNumCopies(), finalBook.getNumCopies());
        }

        storeManager.removeAllBooks();
    }

    @Test
    public void fixedIterationsTest2() throws BookStoreException {
        fixedIterations2(10000);
    }

    private void fixedIterations2(final int iterations) throws BookStoreException {
        storeManager.addBooks(toAdd);

        final int copies = 8 + 4 + 3;

        for(int i = 0; i < iterations; i++) {
            storeManager.addCopies(toBuy);
        }

        List<StockBook> initialBooks = storeManager.getBooks();
        final int preTestCount = initialBooks.size();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<Boolean> buySellFuture = executor.submit(new Callable<Boolean>() {
                public Boolean call() throws Exception {
                    for (int i = 0; i < iterations; i++) {
                        // Buy first and them immediatley add books
                        client.buyBooks(toBuy);
                        storeManager.addCopies(toBuy);
                    }
                    return true;
                }
            });

        // Future for verifying that the number of books, at any time, is equal
        // to either before - copies or before
        Future<Boolean> checkFuture = executor.submit(new Callable<Boolean>() {
                public Boolean call() throws Exception {
                    Boolean res = true;
                    for (int i = 0; i < iterations; i++) {
                        int curCount = storeManager.getBooks().size();
                        res &= (curCount == preTestCount ||
                                curCount == preTestCount - copies);
                    }
                    return res;
                }
            });

        try {
            buySellFuture.get();
            assertTrue(checkFuture.get());
        } catch(Exception e) {
            e.printStackTrace();
            fail();
        }
    }

}
