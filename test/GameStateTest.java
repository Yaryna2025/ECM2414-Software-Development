import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Test;

class GameStateTest {

    @Test
    void nobodyHasWonAtTheStart() {
        GameState state = new GameState(3);
        assertEquals(0, state.getWinner());
        assertFalse(state.isOver());
    }

    @Test
    void firstClaimWinsAndLaterClaimsFail() {
        GameState state = new GameState(3);
        assertTrue(state.tryDeclareWinner(2));
        assertFalse(state.tryDeclareWinner(1));
        assertFalse(state.tryDeclareWinner(2));
        assertEquals(2, state.getWinner());
        assertTrue(state.isOver());
    }

    @Test
    void playerCountBelowOneIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new GameState(0));
    }

    @Test
    void playerIdBelowOneIsRejected() {
        GameState state = new GameState(2);
        assertThrows(IllegalArgumentException.class, () -> state.tryDeclareWinner(0));
        assertFalse(state.isOver());
    }

    @Test
    void exactlyOneOfManySimultaneousClaimantsWins() throws Exception {
        final int claimants = 16;
        GameState state = new GameState(claimants);
        CountDownLatch go = new CountDownLatch(1);
        Queue<Integer> successes = new ConcurrentLinkedQueue<>();
        List<Thread> threads = new ArrayList<>();
        for (int i = 1; i <= claimants; i++) {
            final int playerId = i;
            Thread t = new Thread(() -> {
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (state.tryDeclareWinner(playerId)) {
                    successes.add(playerId);
                }
            });
            t.setDaemon(true);
            threads.add(t);
            t.start();
        }
        go.countDown();
        for (Thread t : threads) {
            t.join(5000);
            assertFalse(t.isAlive());
        }
        assertEquals(1, successes.size());
        assertEquals(successes.peek().intValue(), state.getWinner());
    }

    @Test
    void awaitBlocksUntilEveryPlayerHasCheckedItsHand() throws Exception {
        GameState state = new GameState(2);
        Thread waiter = new Thread(() -> {
            try {
                state.awaitAllInitialChecks();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        waiter.setDaemon(true);
        waiter.start();

        state.markInitialCheckDone();
        waiter.join(200);
        assertTrue(waiter.isAlive(), "must still be waiting for the second player");

        state.markInitialCheckDone();
        waiter.join(5000);
        assertFalse(waiter.isAlive(), "must be released once both have checked");
    }
}
