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
 * Test class to test the BookStore interface
 * 
 */
public class BookStoreTest {

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

		assertTrue(bookInList.getISBN() == addedBook.getISBN()
				&& bookInList.getTitle().equals(addedBook.getTitle())
				&& bookInList.getAuthor().equals(addedBook.getAuthor())
				&& bookInList.getPrice() == addedBook.getPrice()
				&& bookInList.getSaleMisses() == addedBook.getSaleMisses()
				&& bookInList.getAverageRating() == addedBook
						.getAverageRating()
				&& bookInList.getTimesRated() == addedBook.getTimesRated()
				&& bookInList.getTotalRating() == addedBook.getTotalRating()
				&& bookInList.isEditorPick() == addedBook.isEditorPick());

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

	/**
	 * Helper class used in testThread1(). It is used to buy books.
	 */
	private class BookBuyer implements Runnable {
		private int numOperations = 0;
		
		public BookBuyer(int i) {
			numOperations = i;
		}
		
		public void run() {
			Set<BookCopy> booksToBuy = new HashSet<BookCopy>();
			booksToBuy.add(new BookCopy(TEST_ISBN, 1));
			
			for (int i = 0;i < numOperations;i++) {
				try {
					client.buyBooks(booksToBuy);
				} catch (BookStoreException e) {
					;
				}
			}
		}
	}

	/**
	 * Helper class used in testThread1(). It is used to add books.
	 */
	private class BookAdder implements Runnable {
		private int numOperations = 0;
		
		public BookAdder(int i) {
			numOperations = i;
		}
		
		public void run() {
			Set<BookCopy> booksToBuy = new HashSet<BookCopy>();
			booksToBuy.add(new BookCopy(TEST_ISBN, 1));
			
			for (int i = 0;i < numOperations;i++) {
				try {
					storeManager.addCopies(booksToBuy);
				} catch (BookStoreException e) {
					;
				}
			}
		}
	}
	
	/**
	 * Tests that the buyBooks and addCopies of the ConcurrentCertainBookStore are atomic operations.
	 */
	@Test
	public void testThread1() throws BookStoreException, InterruptedException {
		// First, remove all books.
		storeManager.removeAllBooks();
		
		// Add books to the bookstore
		int numBooks = 5000;
		addBooks(TEST_ISBN, numBooks);
		
		// Start and join a thread buying the books, and another adding books.
		Thread t1 = new Thread(new BookBuyer(numBooks));
		Thread t2 = new Thread(new BookAdder(numBooks));
		t1.start();
		t2.start();
		t1.join();
		t2.join();
		
		// Get the current number of books
		List<StockBook> listBooks = storeManager.getBooks();
		
		// Assert that the number of books is the same as before the threads.
		assertEquals(numBooks, listBooks.get(0).getNumCopies());
	}
	
	/**
	 * Helper class used in testThread2(). It is used to buy some
	 * specific books and replace them again.
	 */
	private class SpecificBookBuyerAdder implements Runnable {
		private int numOperations = 0;
		
		public SpecificBookBuyerAdder(int i) {
			numOperations = i;
		}
		
		public void run() {
			Set<BookCopy> booksToBuy = new HashSet<BookCopy>();
			booksToBuy.add(new BookCopy(TEST_ISBN + 1, 1));
			booksToBuy.add(new BookCopy(TEST_ISBN + 2, 1));
			booksToBuy.add(new BookCopy(TEST_ISBN + 3, 1));
			booksToBuy.add(new BookCopy(TEST_ISBN + 4, 1));
			booksToBuy.add(new BookCopy(TEST_ISBN + 5, 1));
			
			for (int i = 0;i < numOperations;i++) {
				try {
					client.buyBooks(booksToBuy);
					storeManager.addCopies(booksToBuy);
				} catch (BookStoreException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * Helper class used in testThread2(). Checks if SpecificBookBuyerAdder
	 * has bought either all or none of intended books.
	 */
	private class SpecificBookGetter implements Runnable {
		private int numOperations = 0;
		private boolean[] success;
		
		public SpecificBookGetter(int i, boolean[] successArg) {
			numOperations = i;
			success = successArg;
		}
		
		public void run() {
			// Get books in store
			List<StockBook> listBooks;
			for (int i = 0;i < numOperations;i++) {
				try {
					listBooks = storeManager.getBooks();
					int numCopies = 0;
					
					for (StockBook book : listBooks) {
						numCopies += book.getNumCopies();
					}

					// Make sure the lists equal each other
					if (numCopies != 20 && numCopies != 25) {
						success[0] = false;
					}
				} catch (BookStoreException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * Thread one repeatedly buys five books and adds the same five books.
	 * Thread two checks that either all five books or none are in the store.
	 * If thread two ever observes another situation, the test fails.
	 */
	@Test
	public void testThread2() throws BookStoreException, InterruptedException  {
		// First, remove all books.
		storeManager.removeAllBooks();
		
		// Add initial book collection.
		addBooks(TEST_ISBN + 1, NUM_COPIES);
		addBooks(TEST_ISBN + 2, NUM_COPIES);
		addBooks(TEST_ISBN + 3, NUM_COPIES);
		addBooks(TEST_ISBN + 4, NUM_COPIES);
		addBooks(TEST_ISBN + 5, NUM_COPIES);

		// Inelegant way to pass pointer to thread.
		// First element set to false on illegal situations.
		boolean[] success = new boolean[] {true};
		
		Thread t1 = new Thread(new SpecificBookBuyerAdder(3000));
		Thread t2 = new Thread(new SpecificBookGetter(3000, success));
		t1.start();
		t2.start();
		t1.join();
		t2.join();

		// Assert that no illegal situations occured.
		assertTrue(success[0]);
	}
	
	/**
	 * Helper class used in testThread3().
	 * Used to toggle the editor picks from to 5 and back to 0.
	 */
	private class BookPickerDepicker implements Runnable {
		private int numOperations = 0;
		
		public BookPickerDepicker(int i) {
			numOperations = i;
		}
		
		public void run() {
			for (int i = 0;i < numOperations;i++) {
				try {
					// Change number of editor picks to 5 and then back to 0.
					Set<BookEditorPick> picks = new HashSet<BookEditorPick>();
					picks.add(new BookEditorPick(TEST_ISBN + 1, true));
					picks.add(new BookEditorPick(TEST_ISBN + 2, true));
					picks.add(new BookEditorPick(TEST_ISBN + 3, true));
					picks.add(new BookEditorPick(TEST_ISBN + 4, true));
					picks.add(new BookEditorPick(TEST_ISBN + 5, true));
					storeManager.updateEditorPicks(picks);
					
					Set<BookEditorPick> depicks = new HashSet<BookEditorPick>();
					depicks.add(new BookEditorPick(TEST_ISBN + 1, false));
					depicks.add(new BookEditorPick(TEST_ISBN + 2, false));
					depicks.add(new BookEditorPick(TEST_ISBN + 3, false));
					depicks.add(new BookEditorPick(TEST_ISBN + 4, false));
					depicks.add(new BookEditorPick(TEST_ISBN + 5, false));
					storeManager.updateEditorPicks(depicks);
					
				} catch (BookStoreException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * Helper class used in testThread3().
	 * Used to check that the current number of editor picks is always only either 5 or 0.
	 */
	private class PickerChecker implements Runnable {
		private int numOperations = 0;
		private boolean[] success;
		
		public PickerChecker(int i, boolean[] successArg) {
			numOperations = i;
			success = successArg;
		}
		
		public void run() {
			for (int i = 0;i < numOperations;i++) {
				// Check that number of editor picks is always only either 5 or 0.
				try {
					List<Book> listBooks;
					listBooks = client.getEditorPicks(5);
					if (listBooks.size() != 0 && listBooks.size() != 5) {
						success[0] = false;
					}
				} catch (BookStoreException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * Thread one repeatedly toggles the number of editor picks between 0 and 5.
	 * Thread two repeatedly checks whether or not the number of editor picks is either 0 or 5. 
	 * If thread two ever observes another situation, the test fails.
	 */
	@Test
	public void testThread3() throws BookStoreException, InterruptedException {
		// First, remove all books.
		storeManager.removeAllBooks();
		
		// Add initial book collection.
		addBooks(TEST_ISBN + 1, NUM_COPIES);
		addBooks(TEST_ISBN + 2, NUM_COPIES);
		addBooks(TEST_ISBN + 3, NUM_COPIES);
		addBooks(TEST_ISBN + 4, NUM_COPIES);
		addBooks(TEST_ISBN + 5, NUM_COPIES);
		
		// Inelegant way to pass pointer to thread.
		// First element is set to false if any state checked by PickerChecker was incorrect.
		boolean[] success = new boolean[] {true};
		
		Thread t1 = new Thread(new BookPickerDepicker(5000));
		Thread t2 = new Thread(new PickerChecker(5000, success));
		t1.start();
		t2.start();
		t1.join();
		t2.join();
		
		// Assert that no state checked by the PickerChecker class was incorrect.
		assertTrue(success[0]);
	}
	
	/**
	 * Helper class used in testThread4().
	 * Used to add a single copy of a book a specific number of times.
	 */
	private class BookCopyAdder implements Runnable {
		private int numOperations = 0;
		
		public BookCopyAdder(int i) {
			numOperations = i;
		}
		
		public void run() {
			Set<BookCopy> booksToBuy;
			for (int i = 0;i < numOperations;i++) {
				try {
					booksToBuy = new HashSet<BookCopy>();
					booksToBuy.add(new BookCopy(TEST_ISBN, 1));
					
					// Add a single copy of the book to the store.
					storeManager.addCopies(booksToBuy);
				} catch (BookStoreException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * Helper class used in testThread4().
	 * Used to buy 10 copies of a book or record that the books weren't in stock.
	 */
	private class TryingBuyer implements Runnable {
		private int numOperations = 0;
		private int[] buyFailures;
		
		public TryingBuyer(int i, int[] buyFailuresArg) {
			numOperations = i;
			buyFailures = buyFailuresArg;
		}
		
		public void run() {
			Set<BookCopy> booksToBuy;
			for (int i = 0;i < numOperations/10;i++) {
				booksToBuy = new HashSet<BookCopy>();
				booksToBuy.add(new BookCopy(TEST_ISBN, 10));
				
				// Try to buy 10 books. If failed, increment buyFailures[0].
				try {
					client.buyBooks(booksToBuy);
				}
				catch (BookStoreException e) {
					buyFailures[0] += 10;
				}
			}
		}
	}
	
	/**
	 * Thread one keeps adding books one at a time.
	 * Thread two keeps buying 10 copies of this book.
	 * If the number of books is not the same as before the threads started, assertion fails.
	 */
	@Test
	public void testThread4() throws BookStoreException, InterruptedException {
		// First, remove all books.
		storeManager.removeAllBooks();
		
		// Add initial book collection.
		int numBooks = 5000;
		addBooks(TEST_ISBN, numBooks);
		int[] buyFailures = new int[] {0};
		
		Thread t1 = new Thread(new BookCopyAdder(numBooks*2));
		Thread t2 = new Thread(new TryingBuyer(numBooks*2, buyFailures));
		t1.start();
		t2.start();
		t1.join();
		t2.join();
		
		// Get the current number of books
		List<StockBook> listBooks = storeManager.getBooks();
		
		// Assert that the number of books is the correct amount
		assertEquals(numBooks, listBooks.get(0).getNumCopies() - buyFailures[0]);
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
