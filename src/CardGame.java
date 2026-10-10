import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs the multi-threaded card game.
 *
 * <p>{@link #main(String[])} asks for the number of players and the location
 * of a valid pack, then deals the hands, fills the decks and starts one
 * thread per player. When the game is over every player has written
 * {@code playerN_output.txt} and every deck has written
 * {@code deckN_output.txt}.
 */
public class CardGame {

    private static final String PLAYERS_PROMPT = "Please enter the number of players:";
    private static final String PACK_PROMPT = "Please enter location of pack to load:";

    private final int numberOfPlayers;
    private final Path outputDirectory;
    private final GameState state;
    private final List<CardDeck> decks = new ArrayList<>();
    private final List<Player> players = new ArrayList<>();
    private final AtomicBoolean played = new AtomicBoolean(false);

    /**
     * Sets up a game: creates the decks and players, deals four cards to
     * each player round-robin from the top of the pack, then deals the
     * remaining cards to the decks round-robin.
     *
     * @param numberOfPlayers the number of players (and decks); at least 1
     * @param pack            exactly 8 * numberOfPlayers cards, top first
     * @param outputDirectory where the output files are written
     * @throws IllegalArgumentException if numberOfPlayers is less than 1 or
     *                                  the pack is not 8n cards long
     * @throws NullPointerException     if pack or outputDirectory is null
     */
    public CardGame(int numberOfPlayers, List<Card> pack, Path outputDirectory) {
        if (numberOfPlayers < 1) {
            throw new IllegalArgumentException(
                    "Number of players must be at least 1: " + numberOfPlayers);
        }
        Objects.requireNonNull(pack, "pack must not be null");
        Objects.requireNonNull(outputDirectory, "outputDirectory must not be null");
        if ((long) pack.size() != 8L * numberOfPlayers) {
            throw new IllegalArgumentException("A pack for " + numberOfPlayers
                    + " players must contain " + (8L * numberOfPlayers)
                    + " cards but has " + pack.size());
        }
        this.numberOfPlayers = numberOfPlayers;
        this.outputDirectory = outputDirectory;
        this.state = new GameState(numberOfPlayers);

        for (int i = 1; i <= numberOfPlayers; i++) {
            decks.add(new CardDeck(i));
        }
        for (int i = 1; i <= numberOfPlayers; i++) {
            CardDeck drawDeck = decks.get(i - 1);
            CardDeck discardDeck = decks.get(i % numberOfPlayers);
            players.add(new Player(i, drawDeck, discardDeck, state, outputDirectory));
        }

        int handCards = 4 * numberOfPlayers;
        for (int k = 0; k < handCards; k++) {
            players.get(k % numberOfPlayers).addToHand(pack.get(k));
        }
        for (int k = handCards; k < pack.size(); k++) {
            decks.get((k - handCards) % numberOfPlayers).addCard(pack.get(k));
        }
    }

    /** @return an unmodifiable view of the players, player 1 first */
    public List<Player> getPlayers() {
        return Collections.unmodifiableList(players);
    }

    /** @return an unmodifiable view of the decks, deck 1 first */
    public List<CardDeck> getDecks() {
        return Collections.unmodifiableList(decks);
    }

    /**
     * Plays the game: starts every player thread, waits for all of them to
     * finish, then writes one output file per deck. May be called once.
     *
     * @return the number of the winning player
     * @throws IOException           if a deck output file cannot be written
     * @throws InterruptedException  if interrupted while waiting for players
     * @throws IllegalStateException if the game has already been played
     */
    public int play() throws IOException, InterruptedException {
        if (!played.compareAndSet(false, true)) {
            throw new IllegalStateException("This game has already been played");
        }
        List<Thread> threads = new ArrayList<>();
        for (Player player : players) {
            threads.add(new Thread(player, "player-" + player.getId()));
        }
        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        writeDeckFiles();
        return state.getWinner();
    }

    private void writeDeckFiles() throws IOException {
        for (CardDeck deck : decks) {
            String contents = deck.getContents();
            String line = "deck" + deck.getId() + " contents:"
                    + (contents.isEmpty() ? "" : " " + contents);
            Files.writeString(
                    outputDirectory.resolve("deck" + deck.getId() + "_output.txt"),
                    line + System.lineSeparator());
        }
    }

    // ------------------------------------------------------------------
    // Input handling
    // ------------------------------------------------------------------

    /**
     * Parses a number of players.
     *
     * @param text the text typed by the user
     * @return the number of players, at least 1
     * @throws IllegalArgumentException if text is not a positive whole number
     */
    static int parseNumberOfPlayers(String text) {
        if (text == null) {
            throw new IllegalArgumentException("No number of players given");
        }
        String trimmed = text.trim();
        if (!trimmed.matches("[0-9]+")) {
            throw new IllegalArgumentException(
                    "'" + trimmed + "' is not a positive whole number");
        }
        int n;
        try {
            n = Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + trimmed + "' is too large");
        }
        if (n < 1) {
            throw new IllegalArgumentException("There must be at least 1 player");
        }
        return n;
    }

    /**
     * Reads and validates a pack file: a plain text file with exactly 8n
     * lines, each holding one non-negative integer.
     *
     * @param file the pack file
     * @param n    the number of players
     * @return the cards, in file order (top of the pack first)
     * @throws IOException              if the file cannot be read
     * @throws IllegalArgumentException if the contents are not a valid pack
     */
    static List<Card> loadPack(Path file, int n) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if ((long) lines.size() != 8L * n) {
            throw new IllegalArgumentException("a pack for " + n + " players needs "
                    + (8L * n) + " lines but this file has " + lines.size());
        }
        List<Card> pack = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (!line.matches("[0-9]+")) {
                throw new IllegalArgumentException("line " + (i + 1) + " ('" + line
                        + "') is not a non-negative integer");
            }
            try {
                pack.add(new Card(Integer.parseInt(line)));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "line " + (i + 1) + " ('" + line + "') is too large");
            }
        }
        return pack;
    }

    /** Asks for the number of players until a valid answer is given. */
    static int promptForPlayers(Scanner in, PrintStream out) {
        while (true) {
            out.println(PLAYERS_PROMPT);
            String line = in.nextLine();
            try {
                return parseNumberOfPlayers(line);
            } catch (IllegalArgumentException e) {
                out.println("Invalid number of players: " + e.getMessage() + ".");
            }
        }
    }

    /** Asks for a pack location until a valid pack file is given. */
    static List<Card> promptForPack(Scanner in, PrintStream out, int n) {
        while (true) {
            out.println(PACK_PROMPT);
            String line = in.nextLine().trim();
            try {
                return loadPack(Path.of(line), n);
            } catch (IOException e) {
                out.println("Could not read that pack file (" + e.getMessage()
                        + "). Please try again.");
            } catch (IllegalArgumentException e) {
                out.println("Invalid pack: " + e.getMessage()
                        + ". Please enter a valid pack file.");
            }
        }
    }

    /**
     * Entry point: asks for the number of players and the pack location,
     * then plays the game.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        try {
            int n = promptForPlayers(in, System.out);
            List<Card> pack = promptForPack(in, System.out, n);
            new CardGame(n, pack, Path.of(".")).play();
        } catch (NoSuchElementException e) {
            System.err.println("Input ended before a valid game could be set up.");
        } catch (IOException e) {
            System.err.println("Could not write the output files: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
