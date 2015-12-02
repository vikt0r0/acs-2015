package com.acertainbookstore.client.tests;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import com.acertainbookstore.business.*;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreException;

/**
 * Test class to test the concurrency of the BookStore interface.
 */
public class ConcurrentTest {
	private static final int TEST_ISBN_1 = 347868;
	private static final int TEST_ISBN_2 = 238765;
	private static final int TEST_ISBN_3 = 692378;

	private static final int NUM_COPIES = 100000;

	private static StockManager storeManager;
	private static BookStore client;

	@BeforeClass
	public static void setUpBeforeClass() {
		ConcurrentCertainBookStore store = new ConcurrentCertainBookStore();
		storeManager = store;
		client = store;
		try {
			storeManager.removeAllBooks();
		} catch (BookStoreException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Helper method to add some books
	 */
	public void addBooks(int isbn, int copies) throws BookStoreException {
		Set<StockBook> booksToAdd = new HashSet<>();
		StockBook book = new ImmutableStockBook(isbn, "Test of Thrones",
				"George RR Testin'", (float) 10, copies, 0, 0, 0, false);
		booksToAdd.add(book);
		storeManager.addBooks(booksToAdd);
	}

	/**
	 * Method to add a book, executed before every test case is run
	 */
	@Before
	public void initializeBooks() throws BookStoreException {
		addBooks(TEST_ISBN_1, NUM_COPIES);
		addBooks(TEST_ISBN_2, NUM_COPIES);
		addBooks(TEST_ISBN_3, NUM_COPIES);
	}

	/**
	 * Method to clean up the book store, execute after every test case is run
	 */
	@After
	public void cleanupBooks() throws BookStoreException {
		storeManager.removeAllBooks();
	}

	/**
	 * Test of concurrently buying and adding the same number of books.
	 *
	 * @throws BookStoreException
	 */
	@Test
	public void testConcurrentAddBuy() throws BookStoreException {
		final int numOperations = NUM_COPIES;
		Set<BookCopy> bookCopies = new HashSet<>();
		bookCopies.add(new BookCopy(TEST_ISBN_1, 1));

		Thread bookBuyer = new Thread(() -> {
            try {
                for (int i = 0; i < numOperations; ++i)
                    client.buyBooks(bookCopies);
            } catch (BookStoreException e) {
                System.out.println("thread running 'BuyCopies' threw BookStoreException");
                e.printStackTrace();
            }
		});
		Thread bookAdder = new Thread(() -> {
			try {
				for (int i = 0; i < numOperations; ++i)
					storeManager.addCopies(bookCopies);
			} catch (BookStoreException e) {
				System.out.println("thread running 'BuyCopies' threw BookStoreException");
				e.printStackTrace();
			}
		});

		bookBuyer.start();
		bookAdder.start();

		try {
			bookAdder.join();
			bookBuyer.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		Set<Integer> ISBNS = new HashSet<>();
		ISBNS.add(TEST_ISBN_1);
		List<StockBook> books = storeManager.getBooksByISBN(ISBNS);

		assertTrue("number of copies should be unchanged",
				books.get(0).getNumCopies() == NUM_COPIES);
	}

	/**
	 * Test of concurrently buying and adding a single book books in many different threads.
	 *
	 * @throws BookStoreException
	 */
	@Test
	public void testManyThreadsAddBuy() throws BookStoreException {
		final int numThreads = NUM_COPIES;
		Set<BookCopy> bookCopies = new HashSet<>();
		bookCopies.add(new BookCopy(TEST_ISBN_1, 1));

		// runnable object that buys one copy of the book
		Runnable runnableBuyer = () -> {
			try {
                client.buyBooks(bookCopies);
			} catch (BookStoreException e) {
				System.out.println("thread running 'BuyCopies' threw BookStoreException");
				e.printStackTrace();
			}
		};
		// runnable object that adds one copy of the book to the stock
		Runnable runnableAdder = () -> {
			try {
                storeManager.addCopies(bookCopies);
			} catch (BookStoreException e) {
				System.out.println("thread running 'BuyCopies' threw BookStoreException");
				e.printStackTrace();
			}
		};

		// spawn 2 * numThreads threads, half of them adding one copy of
		// a book and the other half buying one copy of the same book
		List<Thread> threads = new ArrayList<>();
		for (int i = 0; i < numThreads; ++i) {
			Thread t1 = new Thread(runnableAdder);
			Thread t2 = new Thread(runnableBuyer);
			t1.start();
			t2.start();
			threads.add(t1);
			threads.add(t2);
		}

		// wait for all the spawned threads to finish
		for (Thread t : threads) {
			try {
				t.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		// check that the same number of copies remain
		Set<Integer> ISBNS = new HashSet<>();
		ISBNS.add(TEST_ISBN_1);
		List<StockBook> books = storeManager.getBooksByISBN(ISBNS);
		assertTrue("number of copies should be unchanged",
				books.get(0).getNumCopies() == NUM_COPIES);
	}

	/**
	 * Test consistency of data by continuously adding and buying a collection of book in one
	 * thread, while in another thread, testing that the number of copies in the store correspond
	 * to that either the collection has just been bought or just been added, never somewhere in
	 * between.
	 *
	 * @throws BookStoreException
     */
	@Test
	public void testConsistency() throws BookStoreException {
		final int numSnapshots = 1000;

		// runnable object that continuously adds or buys a collection of books
		Runnable runnable = () -> {
            Set<BookCopy> bookCopies = new HashSet<>();
            bookCopies.add(new BookCopy(TEST_ISBN_1, 1));
            bookCopies.add(new BookCopy(TEST_ISBN_2, 1));
            bookCopies.add(new BookCopy(TEST_ISBN_3, 1));
            while (!Thread.interrupted()) {
                try {
                    storeManager.addCopies(bookCopies);
                    client.buyBooks(bookCopies);
                } catch (BookStoreException e) {
                    e.printStackTrace();
                }
            }
		};
		Thread t = new Thread(runnable);
		t.start();

		// take numSnapshots snapshots of the books in the store
		List<StockBook> booksInStock;
		for (int i = 0; i < numSnapshots; ++i) {
			booksInStock = storeManager.getBooks();

			Boolean firstBook = true;
			int testType = 1;

			//We are checking w.r.t NUM_COPIES because that is the initial value
			//The snapshot should ideally take place after all(3) addCopies or
			//all(3) buyBooks
			for (StockBook book : booksInStock) {
				if (firstBook) {
					if (book.getNumCopies() == NUM_COPIES)
						testType = 1;
					else if (book.getNumCopies() == NUM_COPIES + 1) 					
						testType = 2;
					firstBook = false;
				}
				
				if (testType == 1) {
					assertTrue("either books have just been bought or just been added",
							book.getNumCopies() == NUM_COPIES);
				} else {
					assertTrue("either books have just been bought or just been added",
							book.getNumCopies() == NUM_COPIES + 1);
				}
			}
		}
		t.interrupt();
		try {
            t.join();
        } catch (InterruptedException e) {
			e.printStackTrace();
		}
	}


	@Test
	public void testManyThreadsConsistency() throws BookStoreException {
		final int numSnapshots = 100000;

		// creating a set of 'BookCopy' books with the 3 ISBNs having 1 copy each
		Set<BookCopy> bookCopiesSet= new HashSet<>();
		bookCopiesSet.add(new BookCopy(TEST_ISBN_1, 1));
		bookCopiesSet.add(new BookCopy(TEST_ISBN_2, 1));
		bookCopiesSet.add(new BookCopy(TEST_ISBN_3, 1));

		// each thread will add the copies of books and buy them
		Runnable runnableFunction = () -> {
            try {
                storeManager.addCopies(bookCopiesSet);
                client.buyBooks(bookCopiesSet);
            } catch (BookStoreException e) {
                System.out.println("thread running 'addCopies' threw BookStoreException");
                e.printStackTrace();
            }
		};

		List<Thread> allThreads = new ArrayList<>();

		// run 100,000 threads that call runnableFunction
		for (int i = 0; i < NUM_COPIES; i++) {
			Thread t1 = new Thread(runnableFunction);
			t1.start();
			allThreads.add(t1);
		}

        // AllBooks is a snapshot of the books
        List<StockBook> allBooks = new ArrayList<>();
        Set<Integer> variousCount = new HashSet<>();
		int totalBookCount = 0;

		// run numSnapshots snapshots
		for (int i = 0; i < numSnapshots; ++i) {
            try {
                allBooks = storeManager.getBooks();
            } catch (Exception e) {
                e.printStackTrace();
            }

            // For each of the 3 books, check whether its count is multiple of 3
            for (StockBook s : allBooks) {
				int numberOfCopies = s.getNumCopies();
                totalBookCount += numberOfCopies;

                // Add the value of each of the book to the variousCount Set
                variousCount.add(numberOfCopies);
            }
			assertTrue("numberOfCopies should be multiple of 3",
					(totalBookCount - 3*NUM_COPIES) % 3 == 0);
			totalBookCount = 0;

            //Check whether all the books (from our snapshot) have the same number of copies
            assertEquals("The number of copies of the 3 books have different values",
                    variousCount.size(), 1);
		}

		for (Thread t : allThreads) {
			try {	
				t.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	@AfterClass
	public static void tearDownAfterClass() throws BookStoreException {
		storeManager.removeAllBooks();
	}
}