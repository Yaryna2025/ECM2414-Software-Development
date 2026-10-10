/**
 * A single playing card with a non-negative integer face value (denomination).
 *
 * <p>Instances are immutable: the only field is final and there are no
 * setters, so a Card can be shared freely between threads without
 * synchronisation (thread-safe by immutability).
 */
public final class Card {

    private final int value;

    /**
     * Creates a card.
     *
     * @param value the face value; must be non-negative (it may exceed the
     *              number of players)
     * @throws IllegalArgumentException if value is negative
     */
    public Card(int value) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    "Card value must be non-negative: " + value);
        }
        this.value = value;
    }

    /** @return the face value of this card */
    public int getValue() {
        return value;
    }

    /** @return the face value as text, e.g. "7" */
    @Override
    public String toString() {
        return Integer.toString(value);
    }
}
