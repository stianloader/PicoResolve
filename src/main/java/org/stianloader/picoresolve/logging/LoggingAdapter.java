package org.stianloader.picoresolve.logging;

import java.util.Objects;

import org.jetbrains.annotations.NotNull;

/**
 * There comes a time where any library must log something.
 * Be it for debug purposes, error handling or simple warnings
 * for things that were dealt with in a sub-standard fashion.
 *
 * <p>Ordinarily we'd use SLF4J and call it a day. However, as picoresolve
 * is developed to have the fewest dependencies possible, picoresolve
 * will abstract away the logging into the {@link LoggingAdapter} interface,
 * allowing logging to function even when SLF4J is not present on the classpath.
 * The default implementation uses SLF4J as the log sink if it exists,
 * otherwise it will fall back to using JUL as the logger, as defined by
 * {@link java.util.logging.Logger}.
 * 
 * <p>This facade should support "standard" SLF4J placeholders via "{}".
 * Not all arguments may map to a placeholder and in case they do not then they
 * should simply be appended to the end of the message. Leftover "{}"
 * placeholders need to be kept as-is. Implementations of this interface
 * are free to assume that "{}" never need to be escaped and that likewise no
 * indexing occurs (e.g. that "{2}" should never happen). If the last
 * argument is a {@link Throwable}, it's stacktrace should be logged.
 */
public abstract class LoggingAdapter {

    /**
     * The currently active default logger.
     */
    @NotNull
    static LoggingAdapter currentInstance;

    static {
        LoggingAdapter instance;
        try {
            Class.forName("org.slf4j.LoggerFactory");
            instance = new SLF4JLogAdapter();
        } catch (ClassNotFoundException | NoClassDefFoundError expected) {
            instance = new JULLogAdapter();
        }
        currentInstance = instance;
    }

    @NotNull
    public static LoggingAdapter getDefaultLogger() {
        return LoggingAdapter.currentInstance;
    }

    public static void setDefaultLogger(@NotNull LoggingAdapter instance) {
        LoggingAdapter.currentInstance = Objects.requireNonNull(instance);
    }

    public abstract void debug(Class<?> clazz, String message, Object...args);
    public abstract void error(Class<?> clazz, String message, Object... args);
    public abstract void info(Class<?> clazz, String message, Object... args);
    public abstract void warn(Class<?> clazz, String message, Object... args);
}
