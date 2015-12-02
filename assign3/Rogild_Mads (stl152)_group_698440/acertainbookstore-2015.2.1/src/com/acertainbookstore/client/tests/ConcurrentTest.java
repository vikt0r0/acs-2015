package com.acertainbookstore.client.tests;


import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import com.acertainbookstore.business.Book;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import com.acertainbookstore.utils.BookStoreUtility;


public class ConcurrentTest {


    private static final int TEST_ISBN = 3044560;
    private static final int NUM_COPIES = 5;
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
    }

    
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

    @Before
    public void initializeBooks() throws BookStoreException {
        Set<StockBook> booksToAdd = new HashSet<StockBook>();
        booksToAdd.add(getDefaultBook());
        storeManager.addBooks(booksToAdd);
    }

    @After
    public void cleanupBooks() throws BookStoreException {
        storeManager.removeAllBooks();
    }

    @Test
	public void testAddBuy(final int numberOfTimes) throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();
		Thread t0 = new Thread() {
			public void run() {
				for (int i = 0; i < numberOfTimes; i++) {
					Set<BookCopy> book = new HashSet<BookCopy>();
			        book.add(new BookCopy(TEST_ISBN, 1));
					try {
						storeManager.addCopies(book);
					} catch (BookStoreException e) {
						// TODO Auto-generated catch block
						;
					}
				}
			}
		};
		Thread t1 = new Thread() {
			public void run() {
				for (int i = 0; i < numberOfTimes; i++) {
					Set<BookCopy> book = new HashSet<BookCopy>();
			        book.add(new BookCopy(TEST_ISBN, 1));
					try {
						client.buyBooks(book);
					} catch (BookStoreException e) {
						// TODO Auto-generated catch block
						;
					}
				}
			}
		};
		t0.start();
		t1.start();
		List<StockBook> booksInStorePostTest = storeManager.getBooks();
        assertTrue(booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	@Test
    public void testAddBuyGet(final int numberOfTimes) throws BookStoreException {
		addBooks(300440, 1);
		final List<StockBook> booksInStorePreTest = storeManager.getBooks();
        Thread t0 = new Thread() {
            public void run() {
                for (int i = 0; i < numberOfTimes; i++) {
                    Set<BookCopy> book = new HashSet<BookCopy>();
                    book.add(new BookCopy(TEST_ISBN, 1));
					book.add(new BookCopy(300440, 1));
                    try {
						storeManager.addCopies(book);
					} catch (BookStoreException e) {
						// TODO Auto-generated catch block
						;
					}
                    try {
						client.buyBooks(book);
					} catch (BookStoreException e) {
						// TODO Auto-generated catch block
						;
					}
                }
            }
        };
        Thread t1 = new Thread() {
            public void run() {
                for (int i = 0; i < numberOfTimes; i++) {
                    List<StockBook> booksInStoreCurrentTest;
					try {
						booksInStoreCurrentTest = storeManager.getBooks();
						assertTrue(booksInStorePreTest.size() == booksInStoreCurrentTest.size() || booksInStorePreTest.size()-1 == booksInStoreCurrentTest.size());
					} catch (BookStoreException e) {
						// TODO Auto-generated catch block
						;
					}
                }
            }
        };
        t0.start();
        t1.start();
    }



	@Test
	public void testAdd() throws BookStoreException {
		addBooks(300440, 1);
		final List<StockBook> booksInStorePreTest = storeManager.getBooks();
		Thread t0 = new Thread() {
			public void run() {
					Set<BookCopy> book = new HashSet<BookCopy>();
			        book.add(new BookCopy(TEST_ISBN, 1));
			        book.add(new BookCopy(300440, 1));
					try {
						storeManager.addCopies(book);
					} catch (BookStoreException e) {
						// TODO Auto-generated catch block
						;
					}
			}
		};
		Thread t1 = new Thread() {
			public void run() {
			        List<StockBook> booksInStoreCurrentTest;
					try {
						booksInStoreCurrentTest = storeManager.getBooks();
						assertTrue(booksInStorePreTest.size()+2 == booksInStoreCurrentTest.size()
								|| booksInStorePreTest.size() == booksInStoreCurrentTest.size());
					} catch (BookStoreException e) {
						// TODO Auto-generated catch block
						;
					}
			}
		};
		t0.start();
		t1.start();
		List<StockBook> booksInStorePostTest = storeManager.getBooks();
        assertTrue(booksInStorePreTest.size()+2 == booksInStorePostTest.size());
	}
	
	
	




    @AfterClass
    public static void tearDownAfterClass() throws BookStoreException {
        storeManager.removeAllBooks();
        if (!localTest) {
            ((BookStoreHTTPProxy) client).stop();
            ((StockManagerHTTPProxy) storeManager).stop();
        }
    }
    
    
	@Test
    public void testWriteLock() throws BookStoreException {
		addBooks(300440, 1);
		final List<StockBook> booksInStorePreTest = storeManager.getBooks();
        Thread t0 = new Thread() {
            public void run() {
                for (int i = 0; i < 300; i++) {
					List<StockBook> booksInStoreCurrentTest;
                    try {
                    	booksInStoreCurrentTest = storeManager.getBooks();
                    	assertTrue(booksInStorePreTest.size() == booksInStoreCurrentTest.size()
                    			|| booksInStorePreTest.size()+2 == booksInStoreCurrentTest.size());
					} catch (BookStoreException e) {
						// TODO Auto-generated catch block
						;
					}
                }
            }
        };
        Thread t1 = new Thread() {
            public void run() {
						Set<BookCopy> book = new HashSet<BookCopy>();
	                    book.add(new BookCopy(TEST_ISBN, 1));
	                    book.add(new BookCopy(300440, 1));
	                    try {
							storeManager.addCopies(book);
						} catch (BookStoreException e) {
							// TODO Auto-generated catch block
							;
						}
            }
        };
        t0.start();
        t1.start();
        List<StockBook> booksInStorePostTest = storeManager.getBooks();
        assertTrue(booksInStorePreTest.size()+1 == booksInStorePostTest.size());
    }
    
    

}

