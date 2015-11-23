package com.acertainbookstore.client.tests;

import com.acertainbookstore.business.StockBook;

import static org.junit.Assert.assertEquals;

public class TestUtil {

    public enum CHECK_NUMCOPIES {CHECK_NUMCOPIES, IGNORE_NUMCOPIES};
    public enum CHECK_SALESMISSES {CHECK_SALESMISSES, IGNORE_SALESMISSES};

    // delta used for float comparison (value inspired by https://stackoverflow.com/questions/5686755/meaning-of-epsilon-argument-of-assertequals-for-double-values)
    private static final float DELTA = 1e-7f;

    public static void assertStockBookEq(StockBook expected, StockBook actual, CHECK_NUMCOPIES checkNumCopies, CHECK_SALESMISSES checkSalesMisses)
    {
        assertEquals(expected.getISBN(), actual.getISBN());
        assertEquals(expected.getTitle(), actual.getTitle());
        assertEquals(expected.getAuthor(), actual.getAuthor());
        assertEquals(expected.getPrice(), actual.getPrice(), DELTA);
        assertEquals(expected.getAverageRating(), actual.getAverageRating(), DELTA);
        assertEquals(expected.getTimesRated(), actual.getTimesRated());
        assertEquals(expected.getTotalRating(), actual.getTotalRating());
        assertEquals(expected.isEditorPick(), actual.isEditorPick());
        if (checkNumCopies == CHECK_NUMCOPIES.CHECK_NUMCOPIES) { assertEquals(expected.getNumCopies(), actual.getNumCopies()); }
        if (checkSalesMisses == CHECK_SALESMISSES.CHECK_SALESMISSES) { assertEquals(expected.getSaleMisses(), actual.getSaleMisses()); }
    }
}
