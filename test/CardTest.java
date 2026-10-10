import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CardTest {

    @Test
    void storesTheValueItWasGiven() {
        assertEquals(7, new Card(7).getValue());
    }

    @Test
    void zeroIsAValidDenomination() {
        assertEquals(0, new Card(0).getValue());
    }

    @Test
    void valueMayExceedNumberOfPlayers() {
        assertEquals(1_000_000, new Card(1_000_000).getValue());
    }

    @Test
    void negativeValueIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Card(-1));
    }

    @Test
    void toStringIsTheFaceValue() {
        assertEquals("12", new Card(12).toString());
    }
}
