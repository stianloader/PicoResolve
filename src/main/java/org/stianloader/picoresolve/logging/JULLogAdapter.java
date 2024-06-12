package org.stianloader.picoresolve.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;
import java.util.logging.Logger;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

class JULLogAdapter extends LoggingAdapter {
    @NotNull
    @Contract(pure = false, mutates = "param1", value = "!null, _, _ -> param1")
    private static String createMessage(String message, Object... args) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            int replaceHead = message.indexOf("{}");
            if (replaceHead == -1) {
                builder.append(message);
                message = "";
                if (i == (args.length - 1) && args[i] instanceof Throwable) {
                    builder.append('\n');
                    StringWriter sw = new StringWriter();
                    ((Throwable) args[i]).printStackTrace(new PrintWriter(sw));
                    builder.append(sw.toString());
                } else {
                    builder.append(Objects.toString(args[i]));
                }
            } else {
                builder.append(message.subSequence(0, replaceHead)).append(Objects.toString(args[i]));
                message = message.substring(replaceHead + 2);
            }
        }

        builder.append(message);
        return builder.toString();
    }

    @Override
    public void debug(Class<?> clazz, String message, Object... args) {
        Logger.getLogger(clazz.getName()).fine(() -> {
            return JULLogAdapter.createMessage(message, args);
        });
    }

    @Override
    public void error(Class<?> clazz, String message, Object... args) {
        Logger.getLogger(clazz.getName()).severe(() -> {
            return JULLogAdapter.createMessage(message, args);
        });
    }

    @Override
    public void info(Class<?> clazz, String message, Object... args) {
        Logger.getLogger(clazz.getName()).info(() -> {
            return JULLogAdapter.createMessage(message, args);
        });
    }

    @Override
    public void warn(Class<?> clazz, String message, Object... args) {
        Logger.getLogger(clazz.getName()).warning(() -> {
            return JULLogAdapter.createMessage(message, args);
        });
    }
}
