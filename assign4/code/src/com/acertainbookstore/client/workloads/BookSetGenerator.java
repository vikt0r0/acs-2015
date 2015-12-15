package com.acertainbookstore.client.workloads;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import com.acertainbookstore.business.ImmutableStockBook;
import com.acertainbookstore.business.StockBook;

/**
 * Helper class to generate stockbooks and isbns modelled similar to Random
 * class
 */
public class BookSetGenerator {

    private static Set<StockBook> allBooks;

    public BookSetGenerator() {
    }

    private static String generateName(Random rnd, ArrayList<Character> chars, int length) {
        StringBuilder sb = new StringBuilder(length);

        for (int i=0; i<length; i++) {
            sb.append(chars.get(rnd.nextInt(chars.size())));
        }

        return sb.toString();
    }

    public static void generateAllBooks(int num) {
        allBooks = new HashSet<>(num);

        Random rnd = ThreadLocalRandom.current();

        ArrayList<Character> titleChars = new ArrayList<>();
        ArrayList<Character> authorChars = new ArrayList<>();

        for (char c='a'; c<='z'; c++) {
            titleChars.add(c);
            authorChars.add(c);
            char C = Character.toUpperCase(c);
            titleChars.add(C);
            authorChars.add(C);
        }

        for(int i=0; i<num; i++) {

            int titleLength = (int) Math.max(2, Math.round(10 + 3 * rnd.nextGaussian()));
            int authorLength = (int) Math.max(2, Math.round(15 + 3 * rnd.nextGaussian()));

            int isbn = i+1;
            String title = generateName(rnd, titleChars, titleLength);
            String author = generateName(rnd, authorChars, authorLength);
            float price = 250 + rnd.nextFloat()*100;
            int numCopies = 1 + rnd.nextInt(20);
            boolean isEditorPick = 0 == rnd.nextInt(10); // 10% chance

            StockBook book = new ImmutableStockBook(isbn, title, author, price, numCopies, 0, 0, 0, isEditorPick);
            allBooks.add(book);
        }
    }

    /**
     * Returns num randomly selected isbns from the input set
     *
     * If there are less than num elements in isbns, a copy of isbns will be returned (NO EXCEPTION)
     *
     * @param num
     * @return
     */
    public Set<Integer> sampleFromSetOfISBNs(Set<Integer> isbns, int num) {
        return sampleFromSet(isbns, num);
    }

    private static <E> Set<E> sampleFromSet(Set<E> original, int num) {
        if (num >= original.size()) {
            return original.stream().collect(Collectors.toSet());
        }

        // Using Knuth/Fisher-Yates Shuffle inspired by http://stackoverflow.com/a/1520212/538973
        Random rnd = ThreadLocalRandom.current();

        int size = original.size();
        Object[] arr = new Object[size];
        original.toArray(arr);

        HashSet<E> result = new HashSet<>(num);

        // pick the first 'iterations' elements randomly and add them to the result set
        // only pick elements from [i,size), such that we do not pick the same ISBN more than once
        for (int i=0; i<num; i++) {
            int rnd_i = rnd.nextInt(size-i)+i;
            E tmp = (E) arr[rnd_i];
            arr[rnd_i] = arr[i];
            result.add(tmp);
        }

        return result;
    }

    /**
     * Return num stock books if possible, otherwise as many books as we have
     *
     * @param num
     * @return
     */
    public static Set<StockBook> nextSetOfStockBooks(int num) {
        return sampleFromSet(allBooks, num);
    }

}
