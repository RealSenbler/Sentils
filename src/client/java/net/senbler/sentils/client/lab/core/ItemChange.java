package net.senbler.sentils.client.lab.core;

/**
 * An amount of an item seen to come or go: positive for more of it, negative for less. The id
 * is its SkyBlock id, or null where only the name was seen, as in a sack message.
 */
public record ItemChange(String name, String id, long amount) {
}
