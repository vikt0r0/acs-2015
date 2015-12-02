/**
 *
 */
package com.acertainbookstore.business;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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
	private Map<Integer, ReadWriteLock> lockMap;

	public ConcurrentCertainBookStore() {
		// Constructors are not synchronized
		bookMap = new HashMap<Integer, BookStoreBook>();
		lockMap = new HashMap<Integer, ReadWriteLock>();
	}
	
	private void acquireReadLocks(List<Integer> isbnSet) throws BookStoreException {
		List<Integer> acquiredList = new ArrayList<Integer>();
		java.util.Collections.sort(isbnSet);
		for (int ISBN : isbnSet) {
			if (!lockMap.containsKey(ISBN)) {
				releaseReadLocks(acquiredList);
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.NOT_AVAILABLE);
			}
			lockMap.get(ISBN).readLock().lock();
			acquiredList.add(ISBN);
		}
	}
	private void acquireWriteLocks(List<Integer> isbnSet) throws BookStoreException {
		List<Integer> acquiredList = new ArrayList<Integer>();
		java.util.Collections.sort(isbnSet);
		for (int ISBN : isbnSet) {
			if (!lockMap.containsKey(ISBN)) {
				releaseReadLocks(acquiredList);
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.NOT_AVAILABLE);
			}
			lockMap.get(ISBN).writeLock().lock();
			acquiredList.add(ISBN);
		}
	}
	private void releaseReadLocks(List<Integer> isbnSet) {
		for (int ISBN : isbnSet) {
			if (lockMap.containsKey(ISBN)) {
				lockMap.get(ISBN).readLock().unlock();
			}
		}
	}
	private void releaseWriteLocks(List<Integer> isbnSet) {
		for (int ISBN : isbnSet) {
			if (lockMap.containsKey(ISBN)) {
				lockMap.get(ISBN).writeLock().unlock();
			}
		}
	}

	public void addBooks(Set<StockBook> bookSet)
			throws BookStoreException {

		if (bookSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}
		
		List<Integer> isbnList = new ArrayList<Integer>();
		for (StockBook book : bookSet) {
			isbnList.add(book.getISBN());
		}
		Collections.sort(isbnList);
		
		for (int ISBN : isbnList) {
			if (lockMap.containsKey(ISBN)) {
				lockMap.get(ISBN).writeLock().lock();
			}
			else {
				ReentrantReadWriteLock rrwl = new ReentrantReadWriteLock();
				rrwl.writeLock().lock();
				lockMap.put(ISBN, rrwl);
			}
		}
		
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
				releaseWriteLocks(isbnList);
				throw new BookStoreException(BookStoreConstants.BOOK
						+ book.toString() + BookStoreConstants.INVALID);
			} else if (bookMap.containsKey(ISBN)) {
				releaseWriteLocks(isbnList);
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN
						+ BookStoreConstants.DUPLICATED);
			}
		}

		for (StockBook book : bookSet) {
			int ISBN = book.getISBN();
			bookMap.put(ISBN, new BookStoreBook(book));
		}
		releaseWriteLocks(isbnList);
		return;
	}

	public void addCopies(Set<BookCopy> bookCopiesSet)
			throws BookStoreException {
		int ISBN, numCopies;

		if (bookCopiesSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}
		
		List<Integer> isbnList = new ArrayList<Integer>();
		for (BookCopy book : bookCopiesSet) {
			isbnList.add(book.getISBN());
		}
		acquireWriteLocks(isbnList);

		for (BookCopy bookCopy : bookCopiesSet) {
			ISBN = bookCopy.getISBN();
			numCopies = bookCopy.getNumCopies();
			if (BookStoreUtility.isInvalidISBN(ISBN)) {
				releaseWriteLocks(isbnList);
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN
						+ BookStoreConstants.INVALID);
			}
			if (!bookMap.containsKey(ISBN)) {
				releaseWriteLocks(isbnList);
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN
						+ BookStoreConstants.NOT_AVAILABLE);
			}
			if (BookStoreUtility.isInvalidNoCopies(numCopies)) {
				releaseWriteLocks(isbnList);
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
		
		releaseWriteLocks(isbnList);	
	}

	public List<StockBook> getBooks() {
		List<StockBook> listBooks = new ArrayList<StockBook>();
		List<Integer> isbnList = new ArrayList<Integer>(lockMap.keySet());
		
		try {
			acquireReadLocks(isbnList);
		} catch (BookStoreException e) {
			;
		}
		
		// Get books
		Collection<BookStoreBook> bookMapValues = bookMap.values();
		for (BookStoreBook book : bookMapValues) {
			listBooks.add(book.immutableStockBook());
		}
		
		releaseReadLocks(isbnList);
		
		return listBooks;
	}

	public void updateEditorPicks(Set<BookEditorPick> editorPicks)
			throws BookStoreException {
		// Check that all ISBNs that we add/remove are there first.
		if (editorPicks == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}
		List<Integer> isbnList = new ArrayList<Integer>();
		for (BookEditorPick editorPickArg : editorPicks) {
			isbnList.add(editorPickArg.getISBN());
		}
		
		acquireWriteLocks(isbnList);
		
		int ISBNVal;
		for (BookEditorPick editorPickArg : editorPicks) {
			ISBNVal = editorPickArg.getISBN();
			if (BookStoreUtility.isInvalidISBN(ISBNVal)) {
				releaseWriteLocks(isbnList);
				throw new BookStoreException(BookStoreConstants.ISBN + ISBNVal
						+ BookStoreConstants.INVALID);
			}
			if (!bookMap.containsKey(ISBNVal)) {
				releaseWriteLocks(isbnList);
				throw new BookStoreException(BookStoreConstants.ISBN + ISBNVal
						+ BookStoreConstants.NOT_AVAILABLE);
			}
		}
		
		// Update
		for (BookEditorPick editorPickArg : editorPicks) {
			bookMap.get(editorPickArg.getISBN()).setEditorPick(
					editorPickArg.isEditorPick());
		}
		
		releaseWriteLocks(isbnList);
		return;
	}

	public void buyBooks(Set<BookCopy> bookCopiesToBuy)
			throws BookStoreException {
		if (bookCopiesToBuy == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}
		int ISBN;
		BookStoreBook book;
		Boolean saleMiss = false;
		List<Integer> isbnSet = new ArrayList<Integer>();
		for (BookCopy bookCopyToBuy : bookCopiesToBuy) {
			isbnSet.add(bookCopyToBuy.getISBN());
		}
		
		// Acquire locks
		acquireWriteLocks(isbnSet);

		// Check that all ISBNs that we buy are there first.
		for (BookCopy bookCopyToBuy : bookCopiesToBuy) {
			ISBN = bookCopyToBuy.getISBN();
			if (bookCopyToBuy.getNumCopies() < 0) {
				releaseReadLocks(isbnSet);
				throw new BookStoreException(BookStoreConstants.NUM_COPIES
						+ bookCopyToBuy.getNumCopies()
						+ BookStoreConstants.INVALID);
			}
			if (BookStoreUtility.isInvalidISBN(ISBN)) {
				releaseWriteLocks(isbnSet);
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN
						+ BookStoreConstants.INVALID);
			}
			if (!bookMap.containsKey(ISBN)) {
				releaseWriteLocks(isbnSet);
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
			releaseWriteLocks(isbnSet);
			throw new BookStoreException(BookStoreConstants.BOOK
					+ BookStoreConstants.NOT_AVAILABLE);
		}

		// Then make purchase
		for (BookCopy bookCopyToBuy : bookCopiesToBuy) {
			book = bookMap.get(bookCopyToBuy.getISBN());
			book.buyCopies(bookCopyToBuy.getNumCopies());
		}
		
		releaseWriteLocks(isbnSet);
		
		return;
	}


	public List<StockBook> getBooksByISBN(Set<Integer> isbnSet)
			throws BookStoreException {
		if (isbnSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}
		List<Integer> isbnList = new ArrayList<Integer>(isbnSet);
		acquireReadLocks(isbnList);
		
		for (Integer ISBN : isbnSet) {
			if (BookStoreUtility.isInvalidISBN(ISBN)) {
				releaseReadLocks(isbnList);
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN
						+ BookStoreConstants.INVALID);
			}
			if (!bookMap.containsKey(ISBN)) {
				releaseReadLocks(isbnList);
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN
						+ BookStoreConstants.NOT_AVAILABLE);
			}
		}

		List<StockBook> listBooks = new ArrayList<StockBook>();

		for (Integer ISBN : isbnSet) {
			listBooks.add(bookMap.get(ISBN).immutableStockBook());
		}
		releaseReadLocks(isbnList);
		return listBooks;
	}

	public List<Book> getBooks(Set<Integer> isbnSet)
			throws BookStoreException {
		if (isbnSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}
		List<Integer> isbnList = new ArrayList<Integer>(isbnSet);
		acquireReadLocks(isbnList);
		
		// Check that all ISBNs that we rate are there first.
		for (Integer ISBN : isbnSet) {
			if (BookStoreUtility.isInvalidISBN(ISBN)) {
				releaseReadLocks(isbnList);
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN
						+ BookStoreConstants.INVALID);
			}
			if (!bookMap.containsKey(ISBN)) {
				releaseReadLocks(isbnList);
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN
						+ BookStoreConstants.NOT_AVAILABLE);
			}
		}

		List<Book> listBooks = new ArrayList<Book>();

		// Get the books
		for (Integer ISBN : isbnSet) {
			listBooks.add(bookMap.get(ISBN).immutableBook());
		}
		releaseReadLocks(isbnList);
		return listBooks;
	}

	public List<Book> getEditorPicks(int numBooks)
			throws BookStoreException {
		if (numBooks < 0) {
			throw new BookStoreException("numBooks = " + numBooks
					+ ", but it must be positive");
		}
		List<Integer> isbnList = new ArrayList<Integer>(lockMap.keySet());
		acquireReadLocks(isbnList);
		
		List<BookStoreBook> listAllEditorPicks = new ArrayList<BookStoreBook>();
		List<Book> listEditorPicks = new ArrayList<Book>();
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
		releaseReadLocks(isbnList);
		
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
		List<Integer> isbnList = new ArrayList<Integer>(lockMap.keySet());
		acquireWriteLocks(isbnList);
		bookMap.clear();
		releaseWriteLocks(isbnList);
	}

	public void removeBooks(Set<Integer> isbnSet)
			throws BookStoreException {

		if (isbnSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}
		
		List<Integer> isbnList = new ArrayList<Integer>(isbnSet);
		acquireWriteLocks(isbnList);
		
		for (Integer ISBN : isbnSet) {
			if (BookStoreUtility.isInvalidISBN(ISBN)) {
				releaseWriteLocks(isbnList);
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN
						+ BookStoreConstants.INVALID);
			}
			if (!bookMap.containsKey(ISBN)) {
				releaseWriteLocks(isbnList);
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN
						+ BookStoreConstants.NOT_AVAILABLE);
			}
		}

		for (int isbn : isbnSet) {
			bookMap.remove(isbn);
		}
		
		releaseWriteLocks(isbnList);
	}
}
