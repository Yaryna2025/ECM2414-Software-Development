import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;

/**
 * A player in the card game. Each player runs on its own thread, draws from
 * the top of its own deck and discards to the bottom of the next deck in the
 * ring, and writes a log of its actions to {@code playerN_output.txt}.
 *
 * <p>Thread safety: the hand is guarded by this player's monitor. A turn
 * (draw plus discard) holds the monitors of both decks involved, always
 * acquired in increasing deck-id order, so it is atomic with respect to the
 * other players and cannot deadlock. The only lock order used anywhere is
 * deck monitors first, then the player's own monitor.
 */
public class Player implements Runnable {

    /** Number of cards every player holds between turns. */
    public static final int HAND_SIZE = 4;

    private static final long RETRY_PAUSE_NANOS = 1_000_000L; // 1 ms

    /**
     * The outcome of one completed atomic action.
     *
     * @param drawn     the card taken from the top of the draw deck
     * @param discarded the card put on the bottom of the discard deck
     * @param handAfter the hand after the action, e.g. "1 1 2 4"
     */
    public record Turn(Card drawn, Card discarded, String handAfter) { }

    private final int id;
    private final CardDeck drawDeck;
    private final CardDeck discardDeck;
    private final CardDeck firstLock;
    private final CardDeck secondLock;
    private final GameState state;
    private final Path outputFile;
    private final List<Card> hand = new ArrayList<>(); // guarded by this

    /**
     * @param id              the player number, starting at 1; this is also
     *                        the player's preferred denomination
     * @param drawDeck        the deck this player draws from (deck id)
     * @param discardDeck     the deck this player discards to (deck id + 1,
     *                        wrapping round to deck 1)
     * @param state           the state shared by all players of this game
     * @param outputDirectory where playerN_output.txt is written
     * @throws IllegalArgumentException if id is less than 1
     * @throws NullPointerException     if any other argument is null
     */
    public Player(int id, CardDeck drawDeck, CardDeck discardDeck,
                  GameState state, Path outputDirectory) {
        if (id < 1) {
            throw new IllegalArgumentException(
                    "Player id must be at least 1: " + id);
        }
        this.id = id;
        this.drawDeck = Objects.requireNonNull(drawDeck, "drawDeck must not be null");
        this.discardDeck = Objects.requireNonNull(discardDeck, "discardDeck must not be null");
        this.state = Objects.requireNonNull(state, "state must not be null");
        this.outputFile = Objects.requireNonNull(outputDirectory,
                "outputDirectory must not be null")
                .resolve("player" + id + "_output.txt");

        // Consistent global lock order (lowest deck id first) prevents deadlock.
        if (this.drawDeck.getId() <= this.discardDeck.getId()) {
            this.firstLock = this.drawDeck;
            this.secondLock = this.discardDeck;
        } else {
            this.firstLock = this.discardDeck;
            this.secondLock = this.drawDeck;
        }
    }

    /** @return the player number (also the preferred denomination) */
    public int getId() {
        return id;
    }

    /**
     * Adds a card to the hand (used when the hands are dealt).
     *
     * @throws NullPointerException  if card is null
     * @throws IllegalStateException if the hand already holds four cards
     */
    public synchronized void addToHand(Card card) {
        Objects.requireNonNull(card, "card must not be null");
        if (hand.size() >= HAND_SIZE) {
            throw new IllegalStateException(
                    "Player " + id + " already holds " + HAND_SIZE + " cards");
        }
        hand.add(card);
    }

    /** @return a snapshot of the hand, oldest card first */
    public synchronized List<Card> getHand() {
        return List.copyOf(hand);
    }

    /** @return the hand as values separated by single spaces, e.g. "1 1 2 4" */
    public synchronized String getHandString() {
        StringBuilder sb = new StringBuilder();
        for (Card card : hand) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(card.getValue());
        }
        return sb.toString();
    }

    /**
     * @return true if the hand holds four cards of the same value (any
     *         value, not only the preferred denomination)
     */
    public synchronized boolean hasWinningHand() {
        if (hand.size() != HAND_SIZE) {
            return false;
        }
        int first = hand.get(0).getValue();
        for (Card card : hand) {
            if (card.getValue() != first) {
                return false;
            }
        }
        return true;
    }

    /**
     * Performs one atomic action: draw the top card of the draw deck, then
     * discard the oldest non-preferred card to the bottom of the discard
     * deck. If the draw deck is empty nothing changes.
     *
     * @return the completed turn, or empty if the draw deck was empty
     * @throws IllegalStateException if the hand does not hold exactly four
     *                               cards or is already a winning hand
     */
    public Optional<Turn> takeTurn() {
        synchronized (firstLock) {
            synchronized (secondLock) {
                requireReadyForTurn();
                Card drawn = drawDeck.drawCard();
                if (drawn == null) {
                    return Optional.empty();
                }
                Card discarded = swapIntoHand(drawn);
                discardDeck.addCard(discarded);
                return Optional.of(new Turn(drawn, discarded, getHandString()));
            }
        }
    }

    private synchronized void requireReadyForTurn() {
        if (hand.size() != HAND_SIZE) {
            throw new IllegalStateException("Player " + id
                    + " must hold exactly " + HAND_SIZE + " cards to take a turn");
        }
        if (hasWinningHand()) {
            throw new IllegalStateException(
                    "Player " + id + " already holds a winning hand");
        }
    }

    /**
     * Adds the drawn card, then removes and returns the oldest card that is
     * not of the preferred denomination. A non-winning four-card hand always
     * contains at least one such card, so one always exists here.
     */
    private synchronized Card swapIntoHand(Card drawn) {
        hand.add(drawn);
        Iterator<Card> it = hand.iterator();
        while (it.hasNext()) {
            Card candidate = it.next();
            if (candidate.getValue() != id) {
                it.remove();
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Player " + id + " has no non-preferred card to discard");
    }

    /** Plays the game on the current thread until some player has won. */
    @Override
    public void run() {
        boolean initialCheckReported = false;
        try (BufferedWriter writer = Files.newBufferedWriter(outputFile)) {
            write(writer, "player " + id + " initial hand " + getHandString());
            if (hasWinningHand()) {
                declareWinIfFirst();
            }
            state.markInitialCheckDone();
            initialCheckReported = true;
            state.awaitAllInitialChecks();

            while (!state.isOver()) {
                Optional<Turn> result = takeTurn();
                if (result.isEmpty()) {
                    LockSupport.parkNanos(RETRY_PAUSE_NANOS);
                    continue;
                }
                Turn turn = result.get();
                write(writer, "player " + id + " draws a " + turn.drawn()
                        + " from deck " + drawDeck.getId());
                write(writer, "player " + id + " discards a " + turn.discarded()
                        + " to deck " + discardDeck.getId());
                write(writer, "player " + id + " current hand is "
                        + turn.handAfter());
                if (hasWinningHand()) {
                    declareWinIfFirst();
                }
            }
            writeEnding(writer);
        } catch (IOException e) {
            System.err.println("player " + id + " could not write "
                    + outputFile + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // Never leave the other players waiting at the start gate.
            if (!initialCheckReported) {
                state.markInitialCheckDone();
            }
        }
    }

    private void declareWinIfFirst() {
        if (state.tryDeclareWinner(id)) {
            System.out.println("player " + id + " wins");
        }
    }

    private void writeEnding(BufferedWriter writer) throws IOException {
        int winner = state.getWinner();
        if (winner == id) {
            write(writer, "player " + id + " wins");
            write(writer, "player " + id + " exits");
            write(writer, "player " + id + " final hand: " + getHandString());
        } else {
            write(writer, "player " + winner + " has informed player " + id
                    + " that player " + winner + " has won");
            write(writer, "player " + id + " exits");
            write(writer, "player " + id + " hand: " + getHandString());
        }
    }

    private static void write(BufferedWriter writer, String line) throws IOException {
        writer.write(line);
        writer.newLine();
        writer.flush();
    }
}
