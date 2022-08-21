package de.geolykt.mavenresolver.misc;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class SinkMultiplexer<T> {

    private final AtomicInteger remainingSinks = new AtomicInteger();
    private final ObjectSink<T> childSink;

    public SinkMultiplexer(ObjectSink<T> childSink) {
        this.childSink = childSink;
    }

    public ObjectSink<T> newDelegateParent() {
        remainingSinks.incrementAndGet();
        return new ObjectSink<>() {

            private AtomicBoolean completed = new AtomicBoolean();

            @Override
            public void onError(Throwable error) {
                if (!completed.compareAndExchange(false, true)) {
                    if (remainingSinks.decrementAndGet() == 0) {
                        childSink.onComplete();
                    }
                }
            }

            @Override
            public void nextItem(T item) {
                if (!completed.get()) {
                    childSink.nextItem(item);
                }
            }

            @Override
            public void onComplete() {
                if (!completed.compareAndExchange(false, true)) {
                    if (remainingSinks.decrementAndGet() == 0) {
                        childSink.onComplete();
                    }
                }
            }
        };
    }
}
