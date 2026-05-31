package org.stianloader.picoresolve.test;

import org.stianloader.picoresolve.logging.LoggingAdapter;

public class NOPLogger extends LoggingAdapter {
    @Override
    public void debug(Class<?> clazz, String message, Object... args) {
    }

    @Override
    public void error(Class<?> clazz, String message, Object... args) {
    }

    @Override
    public void info(Class<?> clazz, String message, Object... args) {
    }

    @Override
    public void warn(Class<?> clazz, String message, Object... args) {
    }
}
