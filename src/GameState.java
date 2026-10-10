import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * State shared by all player threads of one game: who has won, and a gate
 * that stops anyone moving until every player has checked its initial hand.
 *
 * <p>The gate guarantees that if a winning hand is dealt, the win is
 * declared before any other player takes a turn. The winner is claimed
 * with a compare-and-set, so at most one player can ever be the winner.
 */
public class GameState {

    private static final int NO_WINNER = 0;

    private final AtomicInteger winner = new AtomicInteger(NO_WINNER);
    private final CountDownLatch initialChecks;

    /**
     * @param numberOfPlayers how many players must check their initial hand
     *                        before play begins; must be at least 1
     * @throws IllegalArgumentException if numberOfPlayers is less than 1
     */
    public GameState(int numberOfPlayers) {
        if (numberOfPlayers < 1) {
            throw new IllegalArgumentException(
                    "Number of players must be at least 1: " + numberOfPlayers);
        }
        this.initialChecks = new CountDownLatch(numberOfPlayers);
    }

    /**
     * Attempts to claim victory for a player.
     *
     * @param playerId the claimant's number, starting at 1
     * @return true if this call made the player the winner; false if
     *         somebody else had already won
     * @throws IllegalArgumentException if playerId is less than 1
     */
    public boolean tryDeclareWinner(int playerId) {
        if (playerId < 1) {
            throw new IllegalArgumentException(
                    "Player id must be at least 1: " + playerId);
        }
        return winner.compareAndSet(NO_WINNER, playerId);
    }

    /** @return the winning player's number, or 0 if nobody has won yet */
    public int getWinner() {
        return winner.get();
    }

    /** @return true once a player has won */
    public boolean isOver() {
        return winner.get() != NO_WINNER;
    }

    /** Called once by each player after it has checked its initial hand. */
    public void markInitialCheckDone() {
        initialChecks.countDown();
    }

    /**
     * Blocks until every player has called {@link #markInitialCheckDone()}.
     *
     * @throws InterruptedException if the waiting thread is interrupted
     */
    public void awaitAllInitialChecks() throws InterruptedException {
        initialChecks.await();
    }
}
