package net.senbler.sentils.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The mod's lines in the game's log, each marked as coming from Sentils. */
public final class Log {

    private Log() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Sentils");

    public static void info(String message) {
        LOGGER.info(message);
    }

    public static void warn(String message) {
        LOGGER.warn(message);
    }
}
