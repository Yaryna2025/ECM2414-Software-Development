import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class CardDeckTest {

    // ---- construction and error cases ----

    @Test
    void newDeckIsEmptyAndKnowsItsId() {
        CardDeck deck = new CardDeck(3);
        assertEquals(3, deck.getId());
        assertEquals(0, deck.size());
        assertTrue(deck.isEmpty());
    }

    @Test
    void idBelowOneIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new CardDeck(0));
    }

    @Test
    void addingNullIsRejected() {
        CardDeck deck = new CardDeck(1);
        assertThrows(NullPointerException.class, () -> deck.addCard(null));
    }

    @Test
    void drawingFromEmptyDeckReturnsNullAndLeavesItEmpty() {
        CardDeck deck = new CardDeck(1);
        assertNull(deck.drawCard());
        assertEquals(0, deck.size());
    }

    // ---- FIFO behaviour ----

    @Test
    void cardsComeOutInTheOrderTheyWentIn() {
        CardDeck deck = new CardDeck(1);
        Card a = new Card(1);
        Card b = new Card(2);
        Card c = new Card(3);
        deck.addCard(a);
        deck.addCard(b);
        deck.addCard(c);
        assertSame(a, deck.drawCard());
        assertSame(b, deck.drawCard());
        assertSame(c, deck.drawCard());
        assertNull(deck.drawCard());
    }

    @Test
    void addedCardGoesToTheBottomNotTheTop() {
        CardDeck deck = new CardDeck(1);
        deck.addCard(new Card(5));
        deck.addCard(new Card(9));
        assertEquals("5 9", deck.getContents());
        deck.drawCard();
        deck.addCard(new Card(7));
        assertEquals("9 7", deck.getContents());
    }

    // ---- invariants ----

    @Test
    void sizeTracksAddsAndDraws() {
        CardDeck deck = new CardDeck(2);
        deck.addCard(new Card(1));
        deck.addCard(new Card(2));
        assertEquals(2, deck.size());
        deck.drawCard();
        assertEquals(1, deck.size());
        deck.drawCard();
        assertEquals(0, deck.size());
        assertTrue(deck.isEmpty());
    }

    @Test
    void contentsAreListedTopToBottomSeparatedBySingleSpaces() {
        CardDeck deck = new CardDeck(2);
        for (int v : new int[] {1, 3, 3, 7}) {
            deck.addCard(new Card(v));
        }
        assertEquals("1 3 3 7", deck.getContents());
    }

    @Test
    void contentsOfEmptyDeckIsEmptyString() {
        assertEquals("", new CardDeck(1).getContents());
    }

    @Test
    void readingContentsDoesNotRemoveCards() {
        CardDeck deck = new CardDeck(1);
        deck.addCard(new Card(4));
        deck.getContents();
        deck.getContents();
        assertEquals(1, deck.size());
    }

    // ---- concurrency ----

    @Test
    void concurrentAddsLoseNoCards() throws Exception {
        final int threads = 8;
        final int perThread = 1000;
        CardDeck deck = new CardDeck(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < perThread; i++) {
                    deck.addCard(new Card(i));
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertEquals(threads * perThread, deck.size());
    }

    @Test
    void concurrentDrawsGiveEachCardToExactlyOneThread() throws Exception {
        final int threads = 8;
        final int total = 4000;
        CardDeck deck = new CardDeck(1);
        for (int i = 0; i < total; i++) {
            deck.addCard(new Card(i));
        }
        Queue<Integer> drawn = new ConcurrentLinkedQueue<>();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                start.await();
                Card card;
                while ((card = deck.drawCard()) != null) {
                    drawn.add(card.getValue());
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        pool.shutdown();

        Set<Integer> distinct = new HashSet<>(drawn);
        assertEquals(total, drawn.size(), "every card drawn exactly once overall");
        assertEquals(total, distinct.size(), "no card drawn twice");
        assertTrue(deck.isEmpty());
    }
}
