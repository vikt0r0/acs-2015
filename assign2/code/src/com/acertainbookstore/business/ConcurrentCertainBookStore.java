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
    ReadWriteLock rwl;

    public ConcurrentCertainBookStore() {
        // Constructors are not synchronized
        bookMap = new HashMap<Integer, BookStoreBook>();
        rwl =  new ReentrantReadWriteLock();
    }

    public void addBooks(Set<StockBook> bookSet)
            throws BookStoreException {

        if (bookSet == null) {
            throw new BookStoreException(BookStoreConstants.NULL_INPUT);
        }

        // We need writeLock here to ensure a book is not added between we check it is not there, and we add it
        rwl.writeLock().lock();

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
                rwl.writeLock().unlock();
                throw new BookStoreException(BookStoreConstants.BOOK
                        + book.toString() + BookStoreConstants.INVALID);
            } else if (bookMap.containsKey(ISBN)) {
                rwl.writeLock().unlock();
                throw new BookStoreException(BookStoreConstants.ISBN + ISBN
                        + BookStoreConstants.DUPLICATED);
            }
        }

        for (StockBook book : bookSet) {
            int ISBN = book.getISBN();
            bookMap.put(ISBN, new BookStoreBook(book));
        }
        rwl.writeLock().unlock();

        return;
    }

    public void addCopies(Set<BookCopy> bookCopiesSet)
            throws BookStoreException {
        int ISBN, numCopies;

        if (bookCopiesSet == null) {
            throw new BookStoreException(BookStoreConstants.NULL_INPUT);
        }

        rwl.writeLock().lock();

        for (BookCopy bookCopy : bookCopiesSet) {
            ISBN = bookCopy.getISBN();
            numCopies = bookCopy.getNumCopies();
            if (BookStoreUtility.isInvalidISBN(ISBN)) {
                rwl.writeLock().unlock();
                throw new BookStoreException(BookStoreConstants.ISBN + ISBN
                        + BookStoreConstants.INVALID);
            } else if (!bookMap.containsKey(ISBN)) {
                rwl.writeLock().unlock();
                throw new BookStoreException(BookStoreConstants.ISBN + ISBN
                        + BookStoreConstants.NOT_AVAILABLE);
            } else if (BookStoreUtility.isInvalidNoCopies(numCopies)) {
                rwl.writeLock().unlock();
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
        rwl.writeLock().unlock();
    }

    public List<StockBook> getBooks() {
        List<StockBook> listBooks = new ArrayList<StockBook>();

        rwl.readLock().lock();
        Collection<BookStoreBook> bookMapValues = bookMap.values();
        for (BookStoreBook book : bookMapValues) {
            listBooks.add(book.immutableStockBook());
        }
        rwl.readLock().unlock();
        return listBooks;
    }

    public void updateEditorPicks(Set<BookEditorPick> editorPicks)
            throws BookStoreException {
        // Check that all ISBNs that we add/remove are there first.
        if (editorPicks == null) {
            throw new BookStoreException(BookStoreConstants.NULL_INPUT);
        }

        rwl.writeLock().lock();

        for (BookEditorPick editorPickArg : editorPicks) {
            int ISBNVal = editorPickArg.getISBN();
            if (BookStoreUtility.isInvalidISBN(ISBNVal)) {
                rwl.writeLock().unlock();
                throw new BookStoreException(BookStoreConstants.ISBN + ISBNVal
                        + BookStoreConstants.INVALID);
            } else if (!bookMap.containsKey(ISBNVal)) {
                rwl.writeLock().unlock();
                throw new BookStoreException(BookStoreConstants.ISBN + ISBNVal
                        + BookStoreConstants.NOT_AVAILABLE);
            }
        }

        for (BookEditorPick editorPickArg : editorPicks) {
            bookMap.get(editorPickArg.getISBN()).setEditorPick(
                    editorPickArg.isEditorPick());
        }

        rwl.writeLock().unlock();

        return;
    }

    public void buyBooks(Set<BookCopy> bookCopiesToBuy)
            throws BookStoreException {
        if (bookCopiesToBuy == null) {
            throw new BookStoreException(BookStoreConstants.NULL_INPUT);
        }

        rwl.writeLock().lock();

        // Check that all ISBNs that we buy are there first.
        int ISBN;
        BookStoreBook book;
        Boolean saleMiss = false;
        for (BookCopy bookCopyToBuy : bookCopiesToBuy) {
            ISBN = bookCopyToBuy.getISBN();
            if (bookCopyToBuy.getNumCopies() < 0) {
                rwl.writeLock().unlock();
                throw new BookStoreException(BookStoreConstants.NUM_COPIES
                        + bookCopyToBuy.getNumCopies()
                        + BookStoreConstants.INVALID);
            } else if (BookStoreUtility.isInvalidISBN(ISBN)) {
                rwl.writeLock().unlock();
                throw new BookStoreException(BookStoreConstants.ISBN + ISBN
                        + BookStoreConstants.INVALID);
            } else if (!bookMap.containsKey(ISBN)) {
                rwl.writeLock().unlock();
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
            rwl.writeLock().unlock();
            throw new BookStoreException(BookStoreConstants.BOOK
                    + BookStoreConstants.NOT_AVAILABLE);
        }

        // Then make purchase
        for (BookCopy bookCopyToBuy : bookCopiesToBuy) {
            book = bookMap.get(bookCopyToBuy.getISBN());
            book.buyCopies(bookCopyToBuy.getNumCopies());
        }
        rwl.writeLock().unlock();
        return;
    }


    public List<StockBook> getBooksByISBN(Set<Integer> isbnSet)
            throws BookStoreException {
        if (isbnSet == null) {
            throw new BookStoreException(BookStoreConstants.NULL_INPUT);
        }

        rwl.readLock().lock();
        for (Integer ISBN : isbnSet) {
            if (BookStoreUtility.isInvalidISBN(ISBN))
                throw new BookStoreException(BookStoreConstants.ISBN + ISBN
                        + BookStoreConstants.INVALID);
            if (!bookMap.containsKey(ISBN))
                throw new BookStoreException(BookStoreConstants.ISBN + ISBN
                        + BookStoreConstants.NOT_AVAILABLE);
        }

        List<StockBook> listBooks = new ArrayList<StockBook>();

        for (Integer ISBN : isbnSet) {
            listBooks.add(bookMap.get(ISBN).immutableStockBook());
        }

        rwl.readLock().unlock();
        return listBooks;
    }

    public List<Book> getBooks(Set<Integer> isbnSet)
            throws BookStoreException {
        if (isbnSet == null) {
            throw new BookStoreException(BookStoreConstants.NULL_INPUT);
        }

        rwl.readLock().lock();
        // Check that all ISBNs that we rate are there first.
        for (Integer ISBN : isbnSet) {
            if (BookStoreUtility.isInvalidISBN(ISBN)) {
                rwl.readLock().unlock();
                throw new BookStoreException(BookStoreConstants.ISBN + ISBN
                        + BookStoreConstants.INVALID);
            } else if (!bookMap.containsKey(ISBN)) {
                rwl.readLock().unlock();
                throw new BookStoreException(BookStoreConstants.ISBN + ISBN
                        + BookStoreConstants.NOT_AVAILABLE);
            }
        }

        List<Book> listBooks = new ArrayList<Book>();

        // Get the books
        for (Integer ISBN : isbnSet) {
            listBooks.add(bookMap.get(ISBN).immutableBook());
        }

        rwl.readLock().unlock();

        return listBooks;
    }

    public List<Book> getEditorPicks(int numBooks)
            throws BookStoreException {
        if (numBooks < 0) {
            throw new BookStoreException("numBooks = " + numBooks
                    + ", but it must be positive");
        }

        rwl.readLock().lock();

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

        // Lock needs to be kept until here, as entrySet is linked to map, and therefore
        // the books used could be modified if we did not keep it until here
        rwl.readLock().unlock();

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
        rwl.writeLock().lock();
        bookMap.clear();
        rwl.writeLock().unlock();
    }

    public void removeBooks(Set<Integer> isbnSet)
            throws BookStoreException {

        if (isbnSet == null) {
            throw new BookStoreException(BookStoreConstants.NULL_INPUT);
        }

        rwl.writeLock().lock();
        for (Integer ISBN : isbnSet) {
            if (BookStoreUtility.isInvalidISBN(ISBN)) {
                rwl.writeLock().unlock();
                throw new BookStoreException(BookStoreConstants.ISBN + ISBN
                        + BookStoreConstants.INVALID);
            } else if (!bookMap.containsKey(ISBN)) {
                rwl.writeLock().unlock();
                throw new BookStoreException(BookStoreConstants.ISBN + ISBN
                        + BookStoreConstants.NOT_AVAILABLE);
            }
        }

        for (int isbn : isbnSet) {
            bookMap.remove(isbn);
        }
        rwl.writeLock().unlock();
    }
}
