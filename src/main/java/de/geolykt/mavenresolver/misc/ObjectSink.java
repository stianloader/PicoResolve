package de.geolykt.mavenresolver.misc;

import java.util.concurrent.Flow.Subscriber;

/**
 * An adaptation of {@link java.util.concurrent.Flow.Subscriber} without {@link Subscriber#onSubscribe(java.util.concurrent.Flow.Subscription)}
 * as that method and the entire Flow structure was deemed to be hard to implement.
 *
 * <p>Never the less, the methods declared by this interface follow the same principles and behaviour as the methods declared by
 * {@link Subscriber}.
 */
public interface ObjectSink<T> {

    void onError(Throwable error);

    void nextItem(T item);

    void onComplete();
}
