/**
 *
 */
package com.acertainbookstore.business;

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

import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreConstants;
import com.acertainbookstore.utils.BookStoreException;
import com.acertainbookstore.utils.BookStoreUtility;

/**
 * ConcurrentCertainBookStore implements the bookstore and its functionality which is
 * defined in the BookStore
 */
public class ConcurrentCertainBookStore implements BookStore, StockManager {
	private Map<Integer, BookStoreBook> bookMap;
    private boolean writeLock;
    private int     readLock;
    private ArrayList<Thread> threads;

	public ConcurrentCertainBookStore() {
		// Constructors are not synchronized
		bookMap   = new HashMap<Integer, BookStoreBook>();
		writeLock = false;
        readLock  = 0;
        threads   = new ArrayList<>();
	}

	public void addBooks(Set<StockBook> bookSet)
			throws BookStoreException {

		if (bookSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}
        getWriteLock();
        // Check if all are there
		for (StockBook book : bookSet) {
			int ISBN = book.getISBN();
			String bookTitle = book.getTitle();
			String bookAuthor = book.getAuthor();
			int noCopies = book.getNumCopies();
			float bookPrice = book.getPrice();
            if (BookStoreUtility.isInvalidISBN(ISBN)
					|| BookStoreUtility.isEmpty(bookTitle)
					|| BookStoreUtility.isEmpty(bookAuthor)
					|| BookStoreUtility.isInvalidNoCopies(noCopies)
					|| bookPrice < 0.0) {
                releaseWriteLock();
				throw new BookStoreException(BookStoreConstants.BOOK
						+ book.toString() + BookStoreConstants.INVALID);
			} else if (bookMap.containsKey(ISBN)) {
                releaseWriteLock();
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN
						+ BookStoreConstants.DUPLICATED);
			}
		}
		for (StockBook book : bookSet) {
			int ISBN = book.getISBN();
			bookMap.put(ISBN, new BookStoreBook(book));
		}
        releaseWriteLock();
		return;
	}

	public void addCopies(Set<BookCopy> bookCopiesSet)
			throws BookStoreException {
		int ISBN, numCopies;

		if (bookCopiesSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}
        getWriteLock();
		for (BookCopy bookCopy : bookCopiesSet) {
			ISBN = bookCopy.getISBN();
			numCopies = bookCopy.getNumCopies();
			if (BookStoreUtility.isInvalidISBN(ISBN)) {
                releaseWriteLock();
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN
						+ BookStoreConstants.INVALID);
            }
			if (!bookMap.containsKey(ISBN)) {
                releaseWriteLock();
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN
						+ BookStoreConstants.NOT_AVAILABLE);
            }
			if (BookStoreUtility.isInvalidNoCopies(numCopies)) {
                releaseWriteLock();
				throw new BookStoreException(BookStoreConstants.NUM_COPIES
						+ numCopies + BookStoreConstants.INVALID);
            }
		}
		BookStoreBook book;
		// Update the number of copies
		for (BookCopy bookCopy : bookCopiesSet) {
			ISBN = bookCopy.getISBN();
			numCopies = bookCopy.getNumCopies();
			book = bookMap.get(ISBN);
			book.addCopies(numCopies);
		}
        releaseWriteLock();
	}

	public List<StockBook> getBooks() {
		List<StockBook> listBooks = new ArrayList<StockBook>();
        getReadLock();
		Collection<BookStoreBook> bookMapValues = bookMap.values();
		for (BookStoreBook book : bookMapValues) {
			listBooks.add(book.immutableStockBook());
		}
        releaseReadLock();
		return listBooks;
	}

	public void updateEditorPicks(Set<BookEditorPick> editorPicks)
			throws BookStoreException {
		// Check that all ISBNs that we add/remove are there first.
		if (editorPicks == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}

		int ISBNVal;
        getWriteLock();
		for (BookEditorPick editorPickArg : editorPicks) {
			ISBNVal = editorPickArg.getISBN();
			if (BookStoreUtility.isInvalidISBN(ISBNVal)) {
                releaseWriteLock();
				throw new BookStoreException(BookStoreConstants.ISBN + ISBNVal
						+ BookStoreConstants.INVALID);
            }
			if (!bookMap.containsKey(ISBNVal))  {
                releaseWriteLock();
				throw new BookStoreException(BookStoreConstants.ISBN + ISBNVal
						+ BookStoreConstants.NOT_AVAILABLE);
            }
		}
		for (BookEditorPick editorPickArg : editorPicks) {
			bookMap.get(editorPickArg.getISBN()).setEditorPick(
					editorPickArg.isEditorPick());
		}
        releaseWriteLock();
		return;
	}

	public void buyBooks(Set<BookCopy> bookCopiesToBuy)
			throws BookStoreException {
		if (bookCopiesToBuy == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}

		// Check that all ISBNs that we buy are there first.
		int ISBN;
		BookStoreBook book;
		Boolean saleMiss = false;
        getWriteLock();
		for (BookCopy bookCopyToBuy : bookCopiesToBuy) {
			ISBN = bookCopyToBuy.getISBN();
			if (bookCopyToBuy.getNumCopies() < 0) {
                releaseWriteLock();
				throw new BookStoreException(BookStoreConstants.NUM_COPIES
						+ bookCopyToBuy.getNumCopies()
						+ BookStoreConstants.INVALID);
            }
			if (BookStoreUtility.isInvalidISBN(ISBN)) {
                releaseWriteLock();
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN
						+ BookStoreConstants.INVALID);
            }
			if (!bookMap.containsKey(ISBN)) {
                releaseWriteLock();
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN
						+ BookStoreConstants.NOT_AVAILABLE);
            }
			book = bookMap.get(ISBN);
			if (!book.areCopiesInStore(bookCopyToBuy.getNumCopies())) {
				book.addSaleMiss(); // If we cannot sell the copies of the book
									// its a miss
				saleMiss = true;
			}
		}

		// We throw exception now since we want to see how many books in the
		// order incurred misses which is used by books in demand
		if (saleMiss) {
            releaseWriteLock();
            throw new BookStoreException(BookStoreConstants.BOOK
                    + BookStoreConstants.NOT_AVAILABLE);
        }
		// Then make purchase
		for (BookCopy bookCopyToBuy : bookCopiesToBuy) {
			book = bookMap.get(bookCopyToBuy.getISBN());
			book.buyCopies(bookCopyToBuy.getNumCopies());
		}
        releaseWriteLock();
		return;
	}


	public List<StockBook> getBooksByISBN(Set<Integer> isbnSet)
			throws BookStoreException {
		if (isbnSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}
        getReadLock();
		for (Integer ISBN : isbnSet) {
			if (BookStoreUtility.isInvalidISBN(ISBN)) {
                releaseReadLock();
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN
						+ BookStoreConstants.INVALID);
            }
			if (!bookMap.containsKey(ISBN)) {
                releaseReadLock();
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN
						+ BookStoreConstants.NOT_AVAILABLE);
            }
		}

		List<StockBook> listBooks = new ArrayList<StockBook>();

		for (Integer ISBN : isbnSet) {
			listBooks.add(bookMap.get(ISBN).immutableStockBook());
		}
        releaseReadLock();
		return listBooks;
	}

	public List<Book> getBooks(Set<Integer> isbnSet)
			throws BookStoreException {
		if (isbnSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}
        getReadLock();
		// Check that all ISBNs that we rate are there first.
		for (Integer ISBN : isbnSet) {
			if (BookStoreUtility.isInvalidISBN(ISBN)) {
                releaseReadLock();
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN
						+ BookStoreConstants.INVALID);
            }
			if (!bookMap.containsKey(ISBN)) {
                releaseReadLock();
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN
						+ BookStoreConstants.NOT_AVAILABLE);
            }
		}

		List<Book> listBooks = new ArrayList<Book>();

		// Get the books
		for (Integer ISBN : isbnSet) {
			listBooks.add(bookMap.get(ISBN).immutableBook());
		}
        releaseReadLock();
		return listBooks;
	}

	public List<Book> getEditorPicks(int numBooks)
			throws BookStoreException {
		if (numBooks < 0) {
			throw new BookStoreException("numBooks = " + numBooks
					+ ", but it must be positive");
		}

		List<BookStoreBook> listAllEditorPicks = new ArrayList<BookStoreBook>();
		List<Book> listEditorPicks = new ArrayList<Book>();
        getReadLock();
		Iterator<Entry<Integer, BookStoreBook>> it = bookMap.entrySet()
				.iterator();
		BookStoreBook book;

		// Get all books that are editor picks
		while (it.hasNext()) {
			Entry<Integer, BookStoreBook> pair = (Entry<Integer, BookStoreBook>) it
					.next();
			book = (BookStoreBook) pair.getValue();
			if (book.isEditorPick()) {
				listAllEditorPicks.add(book);
			}
		}
		// Find numBooks random indices of books that will be picked
		Random rand = new Random();
		Set<Integer> tobePicked = new HashSet<Integer>();
		int rangePicks = listAllEditorPicks.size();
		if (rangePicks <= numBooks) {
			// We need to add all the books
			for (int i = 0; i < listAllEditorPicks.size(); i++) {
				tobePicked.add(i);
			}
		} else {
			// We need to pick randomly the books that need to be returned
			int randNum;
			while (tobePicked.size() < numBooks) {
				randNum = rand.nextInt(rangePicks);
				tobePicked.add(randNum);
			}
		}

		// Get the numBooks random books
		for (Integer index : tobePicked) {
			book = listAllEditorPicks.get(index);
			listEditorPicks.add(book.immutableBook());
		}
        releaseReadLock();
		return listEditorPicks;

	}

	@Override
	public List<Book> getTopRatedBooks(int numBooks)
			throws BookStoreException {
		throw new BookStoreException("Not implemented");
	}

	@Override
	public List<StockBook> getBooksInDemand()
			throws BookStoreException {
		throw new BookStoreException("Not implemented");
	}

	@Override
	public void rateBooks(Set<BookRating> bookRating)
			throws BookStoreException {
		throw new BookStoreException("Not implemented");
	}

	public void removeAllBooks() throws BookStoreException {
		getWriteLock();
        bookMap.clear();
        releaseWriteLock();
	}

	public void removeBooks(Set<Integer> isbnSet)
			throws BookStoreException {

		if (isbnSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}
        getReadLock();
		for (Integer ISBN : isbnSet) {
			if (BookStoreUtility.isInvalidISBN(ISBN)) {
                releaseReadLock();
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN
						+ BookStoreConstants.INVALID);
            }
			if (!bookMap.containsKey(ISBN)) {
                releaseReadLock();
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN
						+ BookStoreConstants.NOT_AVAILABLE);
            }
		}
        releaseReadLock();
        getWriteLock();
		for (int isbn : isbnSet) {
			bookMap.remove(isbn);
		}
        releaseWriteLock();
	}

    private void getReadLock() {
        while (writeLock) {

        }
        readLock++;
        return;
    }
    
    private void releaseReadLock() {
        readLock--;
        return;
    }

    private void getWriteLock() {
        Thread currentThread = Thread.currentThread();
        threads.add(currentThread);
        while (writeLock || threads.get(0) != currentThread) {

        }
        writeLock = true;
        threads.remove(0);
        while (readLock != 0) {

        }
        return;
    }

    private void releaseWriteLock() {
        writeLock = false;
        return;
    }
}
