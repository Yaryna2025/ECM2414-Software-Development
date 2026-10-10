import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * A thread-safe FIFO deck of cards.
 *
 * <p>Cards are drawn from the front (top) and added to the back (bottom).
 * Every public method synchronises on this deck's monitor. Callers that must
 * combine operations on two decks into one atomic action (a player's draw
 * plus discard) may hold the monitors of both decks with nested
 * {@code synchronized} blocks; because Java monitors are reentrant, the
 * methods below can still be called safely inside such a block.
 */
public class CardDeck {

    private final int id;
    private final Deque<Card> cards = new ArrayDeque<>();

    /**
     * @param id the deck number, starting at 1
     * @throws IllegalArgumentException if id is less than 1
     */
    public CardDeck(int id) {
        if (id < 1) {
            throw new IllegalArgumentException("Deck id must be at least 1: " + id);
        }
        this.id = id;
    }

    /** @return the deck number (1 to n) */
    public int getId() {
        return id;
    }

    /**
     * Adds a card to the bottom (back) of the deck.
     *
     * @throws NullPointerException if card is null
     */
    public synchronized void addCard(Card card) {
        cards.addLast(Objects.requireNonNull(card, "card must not be null"));
    }

    /**
     * Removes and returns the card at the top (front) of the deck.
     *
     * @return the top card, or null if the deck is empty
     */
    public synchronized Card drawCard() {
        return cards.pollFirst();
    }

    /** @return the number of cards currently in the deck */
    public synchronized int size() {
        return cards.size();
    }

    /** @return true if the deck holds no cards */
    public synchronized boolean isEmpty() {
        return cards.isEmpty();
    }

    /**
     * @return the card values from top to bottom, separated by single
     *         spaces, e.g. "1 3 3 7" (empty string for an empty deck)
     */
    public synchronized String getContents() {
        StringBuilder sb = new StringBuilder();
        for (Card card : cards) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(card.getValue());
        }
        return sb.toString();
    }
}
