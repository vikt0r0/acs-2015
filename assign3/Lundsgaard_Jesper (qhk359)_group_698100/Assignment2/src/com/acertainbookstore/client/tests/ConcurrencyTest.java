package com.acertainbookstore.client.tests;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.acertainbookstore.business.Book;
import com.acertainbookstore.business.BookCopy;
import com.acertainbookstore.business.BookEditorPick;
import com.acertainbookstore.business.ConcurrentCertainBookStore;
import com.acertainbookstore.business.ImmutableStockBook;
import com.acertainbookstore.business.StockBook;
import com.acertainbookstore.client.BookStoreHTTPProxy;
import com.acertainbookstore.client.StockManagerHTTPProxy;
import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreConstants;
import com.acertainbookstore.utils.BookStoreException;

/**
 * Test class to test concurrency
 * 
 */
public class ConcurrencyTest {

	private static final int TEST_ISBN = 3044560;
	private static final int NUM_COPIES = 5;
	private static boolean localTest = true;
	private static StockManager storeManager;
	private static BookStore bookStore;
	private static Thread client_1;
	private static Thread client_2;
	private static final int NUMBER_OF_OPERATIONS = 1000000;
	
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
				bookStore = store;
			} else {
				storeManager = new StockManagerHTTPProxy(
						"http://localhost:8081/stock");
				bookStore = new BookStoreHTTPProxy("http://localhost:8081");
			}
			storeManager.removeAllBooks();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private class AddCopiesRunnable implements Runnable {
		private Set<BookCopy> bookSet;
		
		AddCopiesRunnable(Set<BookCopy> bookSet) {
			this.bookSet = bookSet;
		}
		
		@Override
		public void run() {
			try {
				for(int i = 0; i < NUMBER_OF_OPERATIONS; i++) {
					storeManager.addCopies(this.bookSet);
				}
			} catch (BookStoreException e) {
				e.printStackTrace();
			}
		}
	}
	private class BuyBooksRunnable implements Runnable {
		private Set<BookCopy> bookSet;
		
		BuyBooksRunnable(Set<BookCopy> bookSet) {
			this.bookSet = bookSet;
		}
		
		@Override
		public void run() {
			try {
				for(int i = 0; i < NUMBER_OF_OPERATIONS; i++) {
					bookStore.buyBooks(this.bookSet);
				}
			} catch (BookStoreException e) {
				e.printStackTrace();
			}
		}
	}
	private class AddAndBuyBooksRunnable implements Runnable {
		private Set<BookCopy> bookSet;
		
		AddAndBuyBooksRunnable(Set<BookCopy> bookSet) {
			this.bookSet = bookSet;
		}
		
		@Override
		public void run() {
			try {
				for(int i = 0; i < NUMBER_OF_OPERATIONS; i++) {
					bookStore.buyBooks(this.bookSet);
					storeManager.addCopies(this.bookSet);
				}
			} catch (BookStoreException e) {
				e.printStackTrace();
			}
		}
	}
	private class UpdateEditorPicksRunnable implements Runnable {
		private Set<BookEditorPick> bookSet;
		
		UpdateEditorPicksRunnable(Set<BookEditorPick> bookSet) {
			this.bookSet = bookSet;
		}
		
		@Override
		public void run() {
			try {
				for(int i = 0; i < NUMBER_OF_OPERATIONS; i++) {
					storeManager.updateEditorPicks(this.bookSet);
				}
			} catch (BookStoreException e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Helper method to get the default book used by initializeBooks
	 */
	public StockBook getDefaultBook() {
		return new ImmutableStockBook(TEST_ISBN, "Harry Potter and JUnit",
				"JK Unit", (float) 10, NUM_COPIES, 0, 0, 0, false);
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
	 * Test 1: Tests buyBooks and addBooks simultaneously
	 */
	@Test
	public void testBuyBooksAndAddBooks() throws BookStoreException {
		Set<BookCopy> bc = new HashSet<BookCopy>();
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		StockBook bookToTest = new ImmutableStockBook(TEST_ISBN + 1, "BOOO! ..k - A Spooky Scary Ghost Story",
				"Lucas Georgeson", (float) 10, NUM_COPIES * NUMBER_OF_OPERATIONS, 0, 0, 0, false);
		booksToAdd.add(bookToTest);
		storeManager.addBooks(booksToAdd);
		
		bc.add(new BookCopy(TEST_ISBN + 1, NUM_COPIES));
		
		client_1 = new Thread(new AddCopiesRunnable(bc));
		client_2 = new Thread(new BuyBooksRunnable(bc));
		
		client_1.start();
		client_2.start();
		
		try {
			client_1.join();
			client_2.join();
		} catch (InterruptedException e) {
			fail();
		}
		
		int num_copies_after = storeManager.getBooks().get(1).getNumCopies();
		
		assertTrue(num_copies_after == bookToTest.getNumCopies());
	}
	
	/**
	 * Test 2: Tests addCopies and getBooks simultaneously
	 */
	@Test
	public void testAddCopiesAndGetBooks() throws BookStoreException {
		int number_to_buy = 2;
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		Set<BookCopy> bookCopies = new HashSet<BookCopy>();
		Set<Integer> isbnSet = new HashSet<Integer>();
		
		StockBook book1 = new ImmutableStockBook(TEST_ISBN + 1, "Star Wars VII: The New Book",
				"Lucas Georgeson'", (float) 10, NUM_COPIES, 0, 0, 0, false);
		StockBook book2 = new ImmutableStockBook(TEST_ISBN + 2, "Star Wars VIII: The NEW New Book",
				"Lucas Georgeson'", (float) 10, NUM_COPIES, 0, 0, 0, false);
		StockBook book3 = new ImmutableStockBook(TEST_ISBN + 3, "Star Wars IX: Christian Skywalker",
				"Lucas Georgeson'", (float) 10, NUM_COPIES, 0, 0, 0, false);
		booksToAdd.add(book1);
		booksToAdd.add(book2);
		booksToAdd.add(book3);
		
		for (StockBook b : booksToAdd) {
			bookCopies.add(new BookCopy(b.getISBN(), number_to_buy));
		}
		for (StockBook b : booksToAdd) {
			isbnSet.add(b.getISBN());
		}
		
		storeManager.addBooks(booksToAdd);
		
		client_1 = new Thread(new AddAndBuyBooksRunnable(bookCopies));
		client_1.start();
		
		while (client_1.isAlive()) {
			List<StockBook> books = storeManager.getBooksByISBN(isbnSet);
			int number_copies = books.get(0).getNumCopies();
			assertTrue(number_copies == NUM_COPIES || number_copies == NUM_COPIES - number_to_buy);
			for (StockBook sb : books) {
				assertTrue(sb.getNumCopies() == number_copies);
			}
		}		
		try {
			client_1.join();
		} catch (InterruptedException e) {
			fail();
		}
	}

	/**
	 * Test 3: Test adding and removing books, specifically that books can be 
	 *         re-added and that locks are released properly.
	 */
	@Test
	public void testAddAndRemoveBooks() throws BookStoreException {
		int number_to_buy = 2;
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		Set<BookCopy> bookCopies = new HashSet<BookCopy>();
		Set<Integer> isbnSet = new HashSet<Integer>();
		
		StockBook book1 = new ImmutableStockBook(TEST_ISBN + 1, "Star Wars VII: The New Book",
				"Lucas Georgeson'", (float) 10, NUM_COPIES, 0, 0, 0, false);
		StockBook book2 = new ImmutableStockBook(TEST_ISBN + 2, "Star Wars VIII: The NEW New Book",
				"Lucas Georgeson'", (float) 10, NUM_COPIES, 0, 0, 0, false);
		StockBook book3 = new ImmutableStockBook(TEST_ISBN + 3, "Star Wars IX: Christian Skywalker",
				"Lucas Georgeson'", (float) 10, NUM_COPIES, 0, 0, 0, false);
		
		booksToAdd.add(book1);
		booksToAdd.add(book2);
		booksToAdd.add(book3);
		
		for (StockBook b : booksToAdd) {
			bookCopies.add(new BookCopy(b.getISBN(), number_to_buy));
		}
		for (StockBook b : booksToAdd) {
			isbnSet.add(b.getISBN());
		}
		
		storeManager.addBooks(booksToAdd);
		storeManager.removeAllBooks();
		storeManager.addBooks(booksToAdd);
		storeManager.removeBooks(isbnSet);
		assertTrue(storeManager.getBooks().isEmpty());
		
	}
	
	/**
	 * Test 4: Tests that the editor pick methods work concurrently in a similar fashion to test 2
	 */
	@Test
	public void testGetAndUpdateEditorPicks() throws BookStoreException {
		int number_to_buy = 2;
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		Set<BookEditorPick> bookEditorPicksTrue = new HashSet<BookEditorPick>();
		Set<BookEditorPick> bookEditorPicksFalse = new HashSet<BookEditorPick>();
		Set<Integer> isbnSet = new HashSet<Integer>();
		
		StockBook book1 = new ImmutableStockBook(TEST_ISBN + 1, "Star Wars VII: The New Book",
				"Lucas Georgeson'", (float) 10, NUM_COPIES, 0, 0, 0, false);
		StockBook book2 = new ImmutableStockBook(TEST_ISBN + 2, "Star Wars VIII: The NEW New Book",
				"Lucas Georgeson'", (float) 10, NUM_COPIES, 0, 0, 0, false);
		StockBook book3 = new ImmutableStockBook(TEST_ISBN + 3, "Star Wars IX: Christian Skywalker",
				"Lucas Georgeson'", (float) 10, NUM_COPIES, 0, 0, 0, false);
		
		booksToAdd.add(book1);
		booksToAdd.add(book2);
		booksToAdd.add(book3);
		
		storeManager.addBooks(booksToAdd);
		
		for (StockBook b : booksToAdd) {
			bookEditorPicksTrue.add(new BookEditorPick(b.getISBN(), true));
			bookEditorPicksFalse.add(new BookEditorPick(b.getISBN(), false));
		}
		for (StockBook b : booksToAdd) {
			isbnSet.add(b.getISBN());
		}
		
		client_1 = new Thread(new UpdateEditorPicksRunnable(bookEditorPicksTrue));
		client_2 = new Thread(new UpdateEditorPicksRunnable(bookEditorPicksFalse));
		
		client_1.start();
		client_2.start();
		
		while (client_1.isAlive()) {
			List<Book> books = bookStore.getEditorPicks(3);
			assertTrue(books.isEmpty() || books.size() == 3);

		}		
		try {
			client_1.join();
			client_2.join();
		} catch (InterruptedException e) {
			fail();
		}
	}
	
	@AfterClass
	public static void tearDownAfterClass() throws BookStoreException {
		storeManager.removeAllBooks();
		if (!localTest) {
			((BookStoreHTTPProxy) bookStore).stop();
			((StockManagerHTTPProxy) storeManager).stop();
		}
	}

}
