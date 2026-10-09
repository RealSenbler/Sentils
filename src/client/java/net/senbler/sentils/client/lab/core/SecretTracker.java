package net.senbler.sentils.client.lab.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.senbler.sentils.client.lab.model.Counters;

/**
 * Counts the secrets found in dungeon runs. The server keeps the count itself and shows it in
 * the tab list while a run is on,
 * <pre>
 *  Secrets Found: 7
 * </pre>
 * which takes in every kind of secret alike: chests, items lying about, bats, wither essence. It
 * is the whole team's count, so alone it is the player's own, and in a party it is not.
 * <p>
 * Which of a team's secrets were the player's own the server does not say. That is estimated: a
 * secret is put down to the player if the count went up just after they did something that
 * finds one, which is opening a chest, clicking a head, picking an item up or a bat dying beside
 * them. Doing one of those without the count going up, such as clicking a chest already opened,
 * counts for nothing.
 */
public final class SecretTracker {

    private static final Pattern FOUND = Pattern.compile("^\\s*Secrets Found: (\\d+)\\s*$");
    // The tab list is a moment behind what happens, and never ahead of it.
    private static final long OWN_MILLIS = 4000;
    private static final int MOST_ACTIONS = 16;

    private int found = -1; // the count as the tab list last had it, or -1 if it has not been seen
    private final ArrayDeque<Long> actions = new ArrayDeque<>(); // when the player did something that finds a secret

    /** Forgets the count, so that secrets found while not recording are not counted. */
    public void reset() {
        this.found = -1;
        this.actions.clear();
    }

    /** Notes the player doing something that finds a secret, if there is one there to find. */
    public void action(long millis) {
        this.actions.addLast(millis);
        if (this.actions.size() > MOST_ACTIONS) this.actions.removeFirst();
    }

    /** Takes the tab list's lines, without formatting codes. */
    public List<Count> tab(List<String> lines, long millis) {
        for (String line : lines) {
            Matcher m = FOUND.matcher(line);
            if (!m.matches()) continue;
            int now = Integer.parseInt(m.group(1));
            // The first sight of it is a starting point. A fall is a new run, begun again from none.
            int more = this.found < 0 ? 0 : now >= this.found ? now - this.found : now;
            this.found = now;
            return more > 0 ? found(more, millis) : List.of();
        }
        return List.of();
    }

    private List<Count> found(int more, long millis) {
        List<Count> counts = new ArrayList<>();
        counts.add(new Count(Counters.DUNGEONS_SECRETS, more));
        while (!this.actions.isEmpty() && millis - this.actions.peekFirst() > OWN_MILLIS) this.actions.removeFirst();
        int own = Math.min(more, this.actions.size());
        for (int i = 0; i < own; i++) this.actions.removeFirst();
        if (own > 0) counts.add(new Count(Counters.DUNGEONS_OWN_SECRETS, own));
        return counts;
    }
}
