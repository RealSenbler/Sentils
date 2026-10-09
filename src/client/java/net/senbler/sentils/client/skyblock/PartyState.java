package net.senbler.sentils.client.skyblock;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Follows a Hypixel party from the lines the server writes in chat about it. Nothing is asked of
 * the server, so a party formed before the game was started is not known about until one of
 * these lines is seen: someone talking in party chat is enough, and so is the player running
 * /party list. Lines followed, with any rank in front of a name:
 * <pre>
 * You have joined [MVP+] Steve's party!
 * You'll be partying with: [VIP] Alex, Sam
 * [VIP] Alex joined the party.
 * [VIP] Alex has left the party.
 * [VIP] Alex has been removed from the party.
 * Kicked [VIP] Alex because they were offline.
 * [VIP] Alex was removed from your party because they disconnected.
 * The party was transferred to [VIP] Alex by [MVP+] Steve
 * The party was transferred to [VIP] Alex because [MVP+] Steve left
 * [MVP+] Steve has disbanded the party!
 * You have been kicked from the party by [MVP+] Steve
 * You left the party.
 * The party was disbanded because all invites expired and the party was empty.
 * The party was disbanded because the party leader disconnected.
 * You are not currently in a party.
 * Party Members (3)
 * Party Leader: [MVP+] Steve ●
 * Party Members: [VIP] Alex ● Sam ●
 * Party Finder > Alex joined the dungeon group! (Mage Level 30)
 * Party > [VIP] Alex: hello
 * </pre>
 */
public final class PartyState {

    private static final Pattern FORMATTING_CODES = Pattern.compile("§.");
    // A name is matched whole, with its rank, so that a player saying one of these lines in chat,
    // which puts a channel and their own name in front, is not taken for the server saying it.
    private static final String NAME = "(?:\\[[^\\]]+\\] )?(\\w{1,16})";

    private static final Pattern YOU_JOINED = Pattern.compile("^You have joined " + NAME + "'s? party!");
    private static final Pattern PARTYING_WITH = Pattern.compile("^You'll be partying with: (.+)$");
    private static final Pattern OTHER_JOINED = Pattern.compile("^" + NAME + " joined the party\\.");
    private static final Pattern FINDER_JOINED = Pattern.compile("^Party Finder > " + NAME + " joined the (?:dungeon )?group!");
    private static final Pattern OTHER_GONE = Pattern.compile("^(?:" + NAME + " has left the party\\."
            + "|" + NAME + " has been removed from the party\\."
            + "|Kicked " + NAME + " because they were offline\\."
            + "|" + NAME + " was removed from your party because they disconnected)");
    private static final Pattern TRANSFERRED = Pattern.compile(
            "^The party was transferred to " + NAME + " (by|because) " + NAME + "( left)?");
    private static final Pattern ENDED = Pattern.compile("^(?:" + NAME + " has disbanded the party!"
            + "|You have been kicked from the party by " + NAME
            + "|You left the party\\."
            + "|The party was disbanded because "
            + "|You are not (?:currently )?in a party)");
    private static final Pattern LIST_START = Pattern.compile("^Party Members \\(\\d+\\)");
    private static final Pattern LIST_LINE = Pattern.compile("^Party (Leader|Moderators|Members): (.+)$");
    private static final Pattern CHAT = Pattern.compile("^Party > " + NAME + ": ");
    private static final Pattern ONE_NAME = Pattern.compile("^" + NAME + "$");

    private final Set<String> members = new LinkedHashSet<>(); // everyone known to be in it but the player
    private boolean inParty;
    private String leader; // null while not known

    public boolean isInParty() {
        return this.inParty;
    }

    /** The others known to be in the party. Some may be missing if it was formed before the game started. */
    public Set<String> members() {
        return Collections.unmodifiableSet(this.members);
    }

    /** The party's leader, or null if there is no party or it is not known who leads it. */
    public String leader() {
        return this.leader;
    }

    /** Forgets the party, as when the server is left and what becomes of it cannot be seen. */
    public void reset() {
        this.members.clear();
        this.inParty = false;
        this.leader = null;
    }

    /** Takes a line of chat, with the player's own name so that they are not listed as a member. */
    public void accept(String message, String self) {
        String line = FORMATTING_CODES.matcher(message).replaceAll("").trim();
        Matcher m;

        if (ENDED.matcher(line).find()) {
            reset();
        } else if ((m = YOU_JOINED.matcher(line)).find()) {
            reset();
            this.leader = m.group(1);
            add(m.group(1), self);
        } else if ((m = PARTYING_WITH.matcher(line)).find()) {
            for (String name : m.group(1).split(", ")) add(name, self);
        } else if ((m = OTHER_JOINED.matcher(line)).find() || (m = FINDER_JOINED.matcher(line)).find()) {
            // The first to join a party the player was not in joined the player's own.
            if (!this.inParty) this.leader = self;
            add(m.group(1), self);
        } else if ((m = OTHER_GONE.matcher(line)).find()) {
            for (int group = 1; group <= m.groupCount(); group++) {
                if (m.group(group) != null) this.members.remove(m.group(group));
            }
        } else if ((m = TRANSFERRED.matcher(line)).find()) {
            this.inParty = true;
            this.leader = m.group(1);
            add(m.group(1), self);
            if (m.group(4) != null) this.members.remove(m.group(3));
            else add(m.group(3), self);
        } else if (LIST_START.matcher(line).find()) {
            // The full list follows, so anyone listed before who has since gone is dropped.
            this.members.clear();
            this.inParty = true;
        } else if ((m = LIST_LINE.matcher(line)).find()) {
            for (String name : m.group(2).split("●")) {
                String member = add(name, self);
                if (member != null && m.group(1).equals("Leader")) this.leader = member;
            }
        } else if ((m = CHAT.matcher(line)).find()) {
            add(m.group(1), self);
        }
    }

    /**
     * Notes that this player, named with or without a rank, is in the party, which there must
     * then be. Returns their name, or null if the text is not a name.
     */
    private String add(String text, String self) {
        Matcher m = ONE_NAME.matcher(text.trim());
        if (!m.find()) return null;
        this.inParty = true;
        if (!m.group(1).equalsIgnoreCase(self)) this.members.add(m.group(1));
        return m.group(1);
    }
}
