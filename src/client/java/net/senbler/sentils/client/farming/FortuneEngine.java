package net.senbler.sentils.client.farming;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

/**
 * Runs the fortune script: Elite's farming-weight library, which is what eliteskyblock.com
 * calculates with, bundled as JavaScript by tools/fortune. Using the library itself rather than
 * a translation of it keeps the mod's numbers the same as the website's.
 * <p>
 * The script is slow to load and may only be used from one thread, so everything here happens
 * on a thread of its own; nothing in this class blocks the game.
 */
public final class FortuneEngine {

    private FortuneEngine() {}

    private static final String SCRIPT = "/assets/sentils/fortune/fortune.js";

    private static final ExecutorService THREAD = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Sentils fortune");
        thread.setDaemon(true);
        return thread;
    });

    // Only touched by the engine thread.
    private static Scriptable scope;
    private static Scriptable script;

    /** Runs a task on the engine thread; the task is the only place call may be used. */
    public static <T> CompletableFuture<T> submit(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        THREAD.execute(() -> {
            try {
                future.complete(task.call());
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /** Calls one of the script's functions with text arguments and returns the JSON it answers with. */
    static String call(String function, String... arguments) throws IOException {
        Context context = Context.enter();
        try {
            // Interpreted, since the script is far too large to be compiled into Java classes.
            context.setInterpretedMode(true);
            context.setLanguageVersion(Context.VERSION_ECMASCRIPT);
            if (script == null) load(context);
            Object result = ScriptableObject.callMethod(context, script, function, arguments);
            return Context.toString(result);
        } finally {
            Context.exit();
        }
    }

    private static void load(Context context) throws IOException {
        String source;
        try (InputStream in = FortuneEngine.class.getResourceAsStream(SCRIPT)) {
            if (in == null) throw new IOException("The fortune script is missing from the mod");
            source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Scriptable global = context.initSafeStandardObjects();
        context.evaluateString(global, source, "fortune.js", 1, null);
        scope = global;
        script = (Scriptable) ScriptableObject.getProperty(scope, "Sentils");
    }
}
