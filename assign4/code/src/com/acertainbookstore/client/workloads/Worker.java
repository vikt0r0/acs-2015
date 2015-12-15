/**
 *
 */
package com.acertainbookstore.client.workloads;

import java.util.Random;
import java.util.concurrent.Callable;
import java.util.Set;
import java.util.List;
import java.util.HashSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.stream.*;

import com.acertainbookstore.utils.BookStoreException;
import com.acertainbookstore.business.Book;
import com.acertainbookstore.business.StockBook;
import com.acertainbookstore.business.BookCopy;
import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.interfaces.StockManager;


/**
 *
 * Worker represents the workload runner which runs the workloads with
 * parameters using WorkloadConfiguration and then reports the results
 *
 */
public class Worker implements Callable<WorkerRunResult> {
    private WorkloadConfiguration c = null;
    private int numSuccessfulFrequentBookStoreInteraction = 0;
    private int numTotalFrequentBookStoreInteraction = 0;

    public Worker(WorkloadConfiguration config) {
        c = config;
    }

    /**
     * Run the appropriate interaction while trying to maintain the configured
     * distributions
     *
     * Updates the counts of total runs and successful runs for customer
     * interaction
     *
     * @param chooseInteraction
     * @return
     */
    private boolean runInteraction(float chooseInteraction) {
        try {
            if (chooseInteraction < c
                    .getPercentRareStockManagerInteraction()) {
                runRareStockManagerInteraction();
            } else if (chooseInteraction < c
                    .getPercentFrequentStockManagerInteraction()) {
                runFrequentStockManagerInteraction();
            } else {
                numTotalFrequentBookStoreInteraction++;
                runFrequentBookStoreInteraction();
                numSuccessfulFrequentBookStoreInteraction++;
            }
        } catch (BookStoreException ex) {
            return false;
        }
        return true;
    }

    /**
     * Run the workloads trying to respect the distributions of the interactions
     * and return result in the end
     */
    public WorkerRunResult call() throws Exception {
        int count = 1;
        long startTimeInNanoSecs = 0;
        long endTimeInNanoSecs = 0;
        int successfulInteractions = 0;
        long timeForRunsInNanoSecs = 0;

        Random rand = new Random();
        float chooseInteraction;

        // Perform the warmup runs
        while (count++ <= c.getWarmUpRuns()) {
            chooseInteraction = rand.nextFloat() * 100f;
            runInteraction(chooseInteraction);
        }

        count = 1;
        numTotalFrequentBookStoreInteraction = 0;
        numSuccessfulFrequentBookStoreInteraction = 0;

        // Perform the actual runs
        startTimeInNanoSecs = System.nanoTime();
        while (count++ <= c.getNumActualRuns()) {
            chooseInteraction = rand.nextFloat() * 100f;
            if (runInteraction(chooseInteraction)) {
                successfulInteractions++;
            }
        }
        endTimeInNanoSecs = System.nanoTime();
        timeForRunsInNanoSecs += (endTimeInNanoSecs - startTimeInNanoSecs);
        return new WorkerRunResult(successfulInteractions,
                timeForRunsInNanoSecs, c.getNumActualRuns(),
                numSuccessfulFrequentBookStoreInteraction,
                numTotalFrequentBookStoreInteraction);
    }

    /**
     * Runs the new stock acquisition interaction 
     *
     * @throws BookStoreException
     */
    private void runRareStockManagerInteraction() throws BookStoreException {
        StockManager sm = c.getStockManager();
        Set<StockBook> books = new HashSet<StockBook>(sm.getBooks());
        Set<StockBook> genBooks = BookSetGenerator.nextSetOfStockBooks(c.getNumBooksToAdd());
        genBooks.removeAll(books);
        sm.addBooks(genBooks);
    }

    /**
     * Runs the stock replenishment interaction
     *
     * @throws BookStoreException
     */
    private void runFrequentStockManagerInteraction() throws BookStoreException {
        StockManager sm = c.getStockManager();
        List<StockBook> books = sm.getBooks();
        Comparator<StockBook> comparing = (b1, b2) ->
            b1.getNumCopies() - b2.getNumCopies();

        sm.addCopies(books
                     .stream()
                     .sorted(comparing)
                     .map((book) -> new BookCopy(book.getISBN(), c.getNumAddCopies()))
                     .limit(c.getNumBooksWithLeastCopies())
                     .collect(Collectors.toSet()));
    }

    /**
     * Runs the customer interaction
     *
     * @throws BookStoreException
     */
    private void runFrequentBookStoreInteraction() throws BookStoreException {
        BookStore bs = c.getBookStore();
        List<Book> books = bs.getEditorPicks(c.getNumEditorPicksToGet());
        Set<Integer> isbns = books
            .stream()
            .map((book) -> new Integer(book.getISBN()))
            .collect(Collectors.toSet());

        Set<BookCopy> toBuy = c.getBookSetGenerator()
            .sampleFromSetOfISBNs(isbns, c.getNumBooksToBuy())
            .stream()
            .map((isbn) -> new BookCopy(isbn, c.getNumBookCopiesToBuy()))
            .collect(Collectors.toSet());

        bs.buyBooks(toBuy);
    }

}
