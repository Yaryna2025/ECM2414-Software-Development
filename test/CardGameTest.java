import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CardGameTest {

    @TempDir
    Path tempDir;

    // ---------- helpers ----------

    private static List<Card> cards(int... values) {
        List<Card> list = new ArrayList<>();
        for (int v : values) {
            list.add(new Card(v));
        }
        return list;
    }

    private Path writePack(String name, List<String> lines) throws IOException {
        Path file = tempDir.resolve(name);
        Files.write(file, lines);
        return file;
    }

    private static List<String> eightLines(String value) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            lines.add(value);
        }
        return lines;
    }

    private static int countOccurrences(String text, String part) {
        int count = 0;
        int from = 0;
        while ((from = text.indexOf(part, from)) >= 0) {
            count++;
            from += part.length();
        }
        return count;
    }

    private List<Integer> deckValuesFromFile(int deckNumber) throws IOException {
        List<String> lines = Files.readAllLines(
                tempDir.resolve("deck" + deckNumber + "_output.txt"));
        assertEquals(1, lines.size(), "a deck file holds a single line");
        String prefix = "deck" + deckNumber + " contents:";
        assertTrue(lines.get(0).startsWith(prefix));
        String rest = lines.get(0).substring(prefix.length()).trim();
        List<Integer> values = new ArrayList<>();
        if (!rest.isEmpty()) {
            for (String s : rest.split(" ")) {
                values.add(Integer.parseInt(s));
            }
        }
        return values;
    }

    // ---------- parsing the number of players ----------

    @Test
    void parseNumberOfPlayersAcceptsPositiveWholeNumbers() {
        assertEquals(1, CardGame.parseNumberOfPlayers("1"));
        assertEquals(4, CardGame.parseNumberOfPlayers("4"));
        assertEquals(12, CardGame.parseNumberOfPlayers("  12  "));
    }

    @Test
    void parseNumberOfPlayersRejectsEverythingElse() {
        String[] bad = {"0", "-1", "abc", "2.5", "", " ", "4 5", "+3", "99999999999"};
        for (String text : bad) {
            assertThrows(IllegalArgumentException.class,
                    () -> CardGame.parseNumberOfPlayers(text), "should reject '" + text + "'");
        }
        assertThrows(IllegalArgumentException.class,
                () -> CardGame.parseNumberOfPlayers(null));
    }

    // ---------- loading and validating a pack ----------

    @Test
    void loadPackReadsOneCardPerLineInFileOrder() throws Exception {
        Path file = writePack("ok.txt",
                List.of("0", "7", "1000000", "3", "3", "3", "3", "5"));
        List<Card> pack = CardGame.loadPack(file, 1);
        List<Integer> values = new ArrayList<>();
        for (Card c : pack) {
            values.add(c.getValue());
        }
        assertEquals(List.of(0, 7, 1000000, 3, 3, 3, 3, 5), values);
    }

    @Test
    void loadPackRejectsWrongNumberOfLines() throws Exception {
        Path tooShort = writePack("short.txt", eightLines("1").subList(0, 7));
        List<String> longer = eightLines("1");
        longer.add("1");
        Path tooLong = writePack("long.txt", longer);
        Path empty = writePack("empty.txt", List.of());
        Path forTwoPlayers = writePack("eight.txt", eightLines("1"));

        assertThrows(IllegalArgumentException.class, () -> CardGame.loadPack(tooShort, 1));
        assertThrows(IllegalArgumentException.class, () -> CardGame.loadPack(tooLong, 1));
        assertThrows(IllegalArgumentException.class, () -> CardGame.loadPack(empty, 1));
        assertThrows(IllegalArgumentException.class,
                () -> CardGame.loadPack(forTwoPlayers, 2), "8 lines is wrong for 2 players");
    }

    @Test
    void loadPackRejectsNegativeNonIntegerBlankAndOversizedLines() throws Exception {
        String[] bad = {"-1", "abc", "1.5", "", " ", "+1", "1 2", "99999999999"};
        for (String badLine : bad) {
            List<String> lines = eightLines("1");
            lines.set(3, badLine);
            Path file = writePack("bad.txt", lines);
            assertThrows(IllegalArgumentException.class,
                    () -> CardGame.loadPack(file, 1), "should reject line '" + badLine + "'");
        }
    }

    @Test
    void loadPackReportsAMissingFileAsAnIOException() {
        assertThrows(IOException.class,
                () -> CardGame.loadPack(tempDir.resolve("nope.txt"), 1));
    }

    // ---------- prompting ----------

    @Test
    void promptForPlayersAsksAgainUntilTheAnswerIsValid() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        Scanner in = new Scanner("abc\n0\n-3\n\n4\n");

        int n = CardGame.promptForPlayers(in, out);

        assertEquals(4, n);
        String printed = bytes.toString(StandardCharsets.UTF_8);
        assertEquals(5, countOccurrences(printed, "Please enter the number of players:"));
        assertEquals(4, countOccurrences(printed, "Invalid number of players"));
    }

    @Test
    void promptForPackAsksAgainUntilAValidPackIsGiven() throws Exception {
        Path bad = writePack("bad.txt", List.of("1", "2", "3"));
        Path good = writePack("good.txt", eightLines("2"));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        Scanner in = new Scanner(tempDir.resolve("missing.txt") + "\n" + bad + "\n" + good + "\n");

        List<Card> pack = CardGame.promptForPack(in, out, 1);

        assertEquals(8, pack.size());
        String printed = bytes.toString(StandardCharsets.UTF_8);
        assertEquals(3, countOccurrences(printed, "Please enter location of pack to load:"));
        assertEquals(1, countOccurrences(printed, "Could not read that pack file"));
        assertEquals(1, countOccurrences(printed, "Invalid pack:"));
    }

    // ---------- setting up a game ----------

    @Test
    void constructorDealsHandsRoundRobinThenFillsDecksRoundRobin() {
        CardGame game = new CardGame(2,
                cards(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16), tempDir);

        assertEquals("1 3 5 7", game.getPlayers().get(0).getHandString());
        assertEquals("2 4 6 8", game.getPlayers().get(1).getHandString());
        assertEquals("9 11 13 15", game.getDecks().get(0).getContents());
        assertEquals("10 12 14 16", game.getDecks().get(1).getContents());
    }

    @Test
    void constructorRejectsAPackOfTheWrongSize() {
        assertThrows(IllegalArgumentException.class,
                () -> new CardGame(2, cards(1, 2, 3, 4, 5, 6, 7, 8), tempDir));
    }

    @Test
    void constructorRejectsANonPositivePlayerCount() {
        assertThrows(IllegalArgumentException.class,
                () -> new CardGame(0, cards(), tempDir));
    }

    // ---------- playing whole games ----------

    @Test
    void immediateWinWritesEveryFileAndLeavesTheDecksUntouched() throws Exception {
        // Player 1 is dealt 1 1 1 1 (pack positions 0, 2, 4, 6).
        CardGame game = new CardGame(2,
                cards(1, 2, 1, 3, 1, 4, 1, 5, 5, 6, 7, 8, 5, 6, 7, 8), tempDir);

        int winner = game.play();

        assertEquals(1, winner);
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
        assertEquals(List.of("deck1 contents: 5 7 5 7"),
                Files.readAllLines(tempDir.resolve("deck1_output.txt")));
        assertEquals(List.of("deck2 contents: 6 8 6 8"),
                Files.readAllLines(tempDir.resolve("deck2_output.txt")));
    }

    @Test
    void fullGameEndsWithOneWinnerAndConservesEveryCard() throws Exception {
        // Player 1 wins on its first turn (the top of deck 1 is a 1).
        // Player 2 keeps its two 2s forever and only three 2s exist, so it can
        // never win. Hence the outcome is deterministic.
        int[] packValues = {1, 2, 1, 2, 1, 3, 2, 3, 1, 4, 3, 4, 3, 4, 3, 4};
        CardGame game = new CardGame(2, cards(packValues), tempDir);

        int winner = game.play();

        assertEquals(1, winner);
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
        assertEquals("player 2 initial hand 2 2 3 3", log2.get(0));
        assertEquals("player 1 has informed player 2 that player 1 has won",
                log2.get(log2.size() - 3));
        assertEquals("player 2 exits", log2.get(log2.size() - 2));

        // Every player holds four cards, and no card was created or lost.
        List<Integer> everything = new ArrayList<>();
        for (Player p : game.getPlayers()) {
            assertEquals(4, p.getHand().size());
            for (Card c : p.getHand()) {
                everything.add(c.getValue());
            }
        }
        everything.addAll(deckValuesFromFile(1));
        everything.addAll(deckValuesFromFile(2));
        List<Integer> expected = new ArrayList<>();
        for (int v : packValues) {
            expected.add(v);
        }
        Collections.sort(everything);
        Collections.sort(expected);
        assertEquals(expected, everything);
    }

    @Test
    void singlePlayerGameWorks() throws Exception {
        CardGame game = new CardGame(1, cards(1, 1, 1, 1, 2, 2, 2, 2), tempDir);

        assertEquals(1, game.play());

        assertEquals(List.of(
                "player 1 initial hand 1 1 1 1",
                "player 1 wins",
                "player 1 exits",
                "player 1 final hand: 1 1 1 1"),
                Files.readAllLines(tempDir.resolve("player1_output.txt")));
        assertEquals(List.of("deck1 contents: 2 2 2 2"),
                Files.readAllLines(tempDir.resolve("deck1_output.txt")));
    }

    @Test
    void aGameCanOnlyBePlayedOnce() throws Exception {
        CardGame game = new CardGame(1, cards(1, 1, 1, 1, 2, 2, 2, 2), tempDir);
        game.play();
        assertThrows(IllegalStateException.class, game::play);
    }
}
