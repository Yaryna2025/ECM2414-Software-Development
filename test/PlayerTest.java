import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerTest {

    @TempDir
    Path tempDir;

    // ---------- helpers ----------

    private static void deal(Player player, int... values) {
        for (int v : values) {
            player.addToHand(new Card(v));
        }
    }

    private static void stack(CardDeck deck, int... values) {
        for (int v : values) {
            deck.addCard(new Card(v));
        }
    }

    private static List<Integer> allValues(List<Player> players, List<CardDeck> decks) {
        List<Integer> values = new ArrayList<>();
        for (Player p : players) {
            for (Card c : p.getHand()) {
                values.add(c.getValue());
            }
        }
        for (CardDeck d : decks) {
            String contents = d.getContents();
            if (!contents.isEmpty()) {
                for (String s : contents.split(" ")) {
                    values.add(Integer.parseInt(s));
                }
            }
        }
        Collections.sort(values);
        return values;
    }

    private static void runToCompletion(Player... players) throws InterruptedException {
        List<Thread> threads = new ArrayList<>();
        for (Player p : players) {
            Thread t = new Thread(p, "player-" + p.getId());
            t.setDaemon(true);
            threads.add(t);
        }
        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join(10_000);
            assertFalse(t.isAlive(), t.getName() + " did not finish");
        }
    }

    // ---------- hand and winning-hand behaviour ----------

    @Test
    void handCannotHoldMoreThanFourCards() {
        Player p = new Player(1, new CardDeck(1), new CardDeck(2), new GameState(1), tempDir);
        deal(p, 1, 2, 3, 4);
        assertThrows(IllegalStateException.class, () -> p.addToHand(new Card(5)));
        assertEquals("1 2 3 4", p.getHandString());
    }

    @Test
    void addingNullToHandIsRejected() {
        Player p = new Player(1, new CardDeck(1), new CardDeck(2), new GameState(1), tempDir);
        assertThrows(NullPointerException.class, () -> p.addToHand(null));
    }

    @Test
    void fourEqualCardsWinEvenWhenNotThePreferredValue() {
        Player p = new Player(1, new CardDeck(1), new CardDeck(2), new GameState(1), tempDir);
        deal(p, 7, 7, 7, 7);
        assertTrue(p.hasWinningHand());
    }

    @Test
    void threeEqualCardsPlusADifferentOneIsNotWinning() {
        Player p = new Player(1, new CardDeck(1), new CardDeck(2), new GameState(1), tempDir);
        deal(p, 1, 1, 1, 2);
        assertFalse(p.hasWinningHand());
    }

    @Test
    void incompleteHandIsNotWinning() {
        Player p = new Player(1, new CardDeck(1), new CardDeck(2), new GameState(1), tempDir);
        deal(p, 7, 7, 7);
        assertFalse(p.hasWinningHand());
    }

    // ---------- the atomic draw + discard action ----------

    @Test
    void turnDrawsFromTopOfOwnDeckAndDiscardsToBottomOfNextDeck() {
        CardDeck deck1 = new CardDeck(1);
        CardDeck deck2 = new CardDeck(2);
        stack(deck1, 4, 5);
        stack(deck2, 9);
        Player p = new Player(1, deck1, deck2, new GameState(2), tempDir);
        deal(p, 1, 1, 2, 3);

        Player.Turn turn = p.takeTurn().orElseThrow();

        assertEquals(4, turn.drawn().getValue());
        assertEquals(2, turn.discarded().getValue());
        assertEquals("1 1 3 4", turn.handAfter());
        assertEquals("1 1 3 4", p.getHandString());
        assertEquals("5", deck1.getContents());
        assertEquals("9 2", deck2.getContents());
    }

    @Test
    void lastPlayerDiscardsToDeckOne() {
        CardDeck deck1 = new CardDeck(1);
        CardDeck deck3 = new CardDeck(3);
        stack(deck3, 8);
        Player p = new Player(3, deck3, deck1, new GameState(3), tempDir);
        deal(p, 3, 3, 5, 6);

        p.takeTurn().orElseThrow();

        assertEquals("3 3 6 8", p.getHandString());
        assertEquals("5", deck1.getContents());
        assertEquals("", deck3.getContents());
    }

    @Test
    void preferredDenominationIsNeverDiscarded() {
        CardDeck deck2 = new CardDeck(2);
        CardDeck deck3 = new CardDeck(3);
        stack(deck2, 2);
        Player p = new Player(2, deck2, deck3, new GameState(3), tempDir);
        deal(p, 2, 2, 2, 3);

        Player.Turn turn = p.takeTurn().orElseThrow();

        assertEquals(3, turn.discarded().getValue());
        assertEquals("2 2 2 2", p.getHandString());
        assertTrue(p.hasWinningHand());
        assertEquals("3", deck3.getContents());
    }

    @Test
    void oldestNonPreferredCardIsDiscardedFirst() {
        CardDeck deck1 = new CardDeck(1);
        CardDeck deck2 = new CardDeck(2);
        stack(deck1, 7, 8);
        Player p = new Player(1, deck1, deck2, new GameState(2), tempDir);
        deal(p, 5, 6, 1, 1);

        assertEquals(5, p.takeTurn().orElseThrow().discarded().getValue());
        assertEquals(6, p.takeTurn().orElseThrow().discarded().getValue());
        assertEquals("1 1 7 8", p.getHandString());
    }

    @Test
    void noNonPreferredCardIsHeldIndefinitely() {
        // One player, one deck: draws from and discards to the same deck.
        CardDeck deck = new CardDeck(1);
        stack(deck, 2, 3, 4, 5);
        Player p = new Player(1, deck, deck, new GameState(1), tempDir);
        deal(p, 6, 7, 8, 9);

        for (int i = 0; i < 4; i++) {
            p.takeTurn().orElseThrow();
        }

        assertEquals("2 3 4 5", p.getHandString(), "all original cards must have been discarded");
        assertEquals("6 7 8 9", deck.getContents());
    }

    @Test
    void handStaysAtFourCardsAndCardsAreConservedOverManyTurns() {
        CardDeck deck = new CardDeck(1);
        stack(deck, 5, 6, 7, 8);
        Player p = new Player(1, deck, deck, new GameState(1), tempDir);
        deal(p, 5, 6, 7, 8);

        for (int i = 0; i < 100; i++) {
            p.takeTurn().orElseThrow();
            assertEquals(4, p.getHand().size());
            assertEquals(4, deck.size());
        }
    }

    @Test
    void turnDoesNothingWhenDrawDeckIsEmpty() {
        CardDeck deck1 = new CardDeck(1);
        CardDeck deck2 = new CardDeck(2);
        stack(deck2, 9);
        Player p = new Player(1, deck1, deck2, new GameState(2), tempDir);
        deal(p, 1, 2, 3, 4);

        assertTrue(p.takeTurn().isEmpty());

        assertEquals("1 2 3 4", p.getHandString());
        assertEquals("", deck1.getContents());
        assertEquals("9", deck2.getContents());
    }

    @Test
    void turnIsRejectedForAWinningHandAndNoCardIsTaken() {
        CardDeck deck1 = new CardDeck(1);
        stack(deck1, 9);
        Player p = new Player(1, deck1, new CardDeck(2), new GameState(2), tempDir);
        deal(p, 1, 1, 1, 1);

        assertThrows(IllegalStateException.class, p::takeTurn);

        assertEquals("9", deck1.getContents());
        assertEquals("1 1 1 1", p.getHandString());
    }

    @Test
    void turnIsRejectedWhenHandDoesNotHoldFourCards() {
        CardDeck deck1 = new CardDeck(1);
        stack(deck1, 9);
        Player p = new Player(1, deck1, new CardDeck(2), new GameState(2), tempDir);
        deal(p, 1, 2, 3);

        assertThrows(IllegalStateException.class, p::takeTurn);

        assertEquals("9", deck1.getContents());
        assertEquals("1 2 3", p.getHandString());
    }

    // ---------- concurrency ----------

    @Test
    void ringOfConcurrentPlayersNeverDeadlocksAndConservesCards() throws Exception {
        // Each player keeps its two preferred cards forever, so nobody can
        // win; the game just churns cards round the ring until we stop it.
        final int n = 4;
        GameState state = new GameState(n);
        List<CardDeck> decks = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            CardDeck deck = new CardDeck(i);
            stack(deck, 40 + i, 50 + i, 60 + i, 70 + i);
            decks.add(deck);
        }
        List<Player> players = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            Player p = new Player(i, decks.get(i - 1), decks.get(i % n), state, tempDir);
            deal(p, i, i, 20 + i, 30 + i);
            players.add(p);
        }
        List<Integer> before = allValues(players, decks);

        AtomicBoolean stop = new AtomicBoolean(false);
        AtomicInteger completedTurns = new AtomicInteger();
        List<Thread> threads = new ArrayList<>();
        for (Player p : players) {
            Thread t = new Thread(() -> {
                while (!stop.get()) {
                    if (p.takeTurn().isPresent()) {
                        completedTurns.incrementAndGet();
                    } else {
                        Thread.yield();
                    }
                }
            });
            t.setDaemon(true);
            threads.add(t);
            t.start();
        }
        Thread.sleep(300);
        stop.set(true);
        for (Thread t : threads) {
            t.join(10_000);
            assertFalse(t.isAlive(), "player thread did not stop: possible deadlock");
        }

        assertTrue(completedTurns.get() > 0, "some turns must have been completed");
        for (Player p : players) {
            assertEquals(4, p.getHand().size());
            long preferred = p.getHand().stream()
                    .filter(c -> c.getValue() == p.getId()).count();
            assertEquals(2, preferred, "preferred cards are never discarded");
        }
        assertEquals(before, allValues(players, decks), "no card created or lost");
    }

    // ---------- whole-thread behaviour and output files ----------

    @RepeatedTest(20)
    void immediateWinIsDeclaredBeforeAnyoneMoves() throws Exception {
        GameState state = new GameState(2);
        CardDeck deck1 = new CardDeck(1);
        CardDeck deck2 = new CardDeck(2);
        stack(deck1, 5, 6, 7, 8);
        stack(deck2, 5, 6, 7, 8);
        Player p1 = new Player(1, deck1, deck2, state, tempDir);
        Player p2 = new Player(2, deck2, deck1, state, tempDir);
        deal(p1, 1, 1, 1, 1);
        deal(p2, 2, 3, 4, 5);

        runToCompletion(p1, p2);

        assertEquals(1, state.getWinner());
        assertEquals(List.of(
                "player 1 initial hand 1 1 1 1",
                "player 1 wins",
                "player 1 exits",
                "player 1 final hand: 1 1 1 1"),
                Files.readAllLines(tempDir.resolve("player1_output.txt")));
        assertEquals(List.of(
                "player 2 initial hand 2 3 4 5",
                "player 1 has informed player 2 that player 1 has won",
                "player 2 exits",
                "player 2 hand: 2 3 4 5"),
                Files.readAllLines(tempDir.resolve("player2_output.txt")));
        assertEquals("5 6 7 8", deck1.getContents(), "no moves may happen after an immediate win");
        assertEquals("5 6 7 8", deck2.getContents());
    }

    @Test
    void fullGameProducesCorrectLogsAndConservesCards() throws Exception {
        // Player 1 wins on its first turn (draws the 1 from deck 1).
        // Player 2 keeps two 2s forever and only three 2s exist, so it
        // can never win.
        GameState state = new GameState(2);
        CardDeck deck1 = new CardDeck(1);
        CardDeck deck2 = new CardDeck(2);
        stack(deck1, 1, 3, 3, 3);
        stack(deck2, 4, 4, 4, 4);
        Player p1 = new Player(1, deck1, deck2, state, tempDir);
        Player p2 = new Player(2, deck2, deck1, state, tempDir);
        deal(p1, 1, 1, 1, 2);
        deal(p2, 2, 2, 3, 3);
        List<Player> players = List.of(p1, p2);
        List<CardDeck> decks = List.of(deck1, deck2);
        List<Integer> before = allValues(players, decks);

        runToCompletion(p1, p2);

        assertEquals(1, state.getWinner());
        assertEquals(List.of(
                "player 1 initial hand 1 1 1 2",
                "player 1 draws a 1 from deck 1",
                "player 1 discards a 2 to deck 2",
                "player 1 current hand is 1 1 1 1",
                "player 1 wins",
                "player 1 exits",
                "player 1 final hand: 1 1 1 1"),
                Files.readAllLines(tempDir.resolve("player1_output.txt")));

        List<String> log2 = Files.readAllLines(tempDir.resolve("player2_output.txt"));
        int size = log2.size();
        assertEquals("player 2 initial hand 2 2 3 3", log2.get(0));
        assertEquals(0, (size - 4) % 3, "initial line + whole turns + 3 closing lines");
        for (int i = 1; i < size - 3; i += 3) {
            assertTrue(log2.get(i).startsWith("player 2 draws a "));
            assertTrue(log2.get(i + 1).startsWith("player 2 discards a "));
            assertTrue(log2.get(i + 2).startsWith("player 2 current hand is "));
        }
        assertEquals("player 1 has informed player 2 that player 1 has won", log2.get(size - 3));
        assertEquals("player 2 exits", log2.get(size - 2));
        assertEquals("player 2 hand: " + p2.getHandString(), log2.get(size - 1));

        assertEquals(4, p1.getHand().size());
        assertEquals(4, p2.getHand().size());
        assertEquals(before, allValues(players, decks), "no card created or lost");
    }
}
