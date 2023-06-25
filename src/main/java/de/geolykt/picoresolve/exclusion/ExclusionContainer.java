package de.geolykt.picoresolve.exclusion;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An {@link Excluder} that combines multiple {@link Excluder Excluders} into one.
 *
 * @param <T> The type of accepted {@link Excluder Excluders}. Mainly used for sanity checking when paired
 * with {@link ExclusionMode#ALL}, which makes no sense when used with any {@link Exclusion} as a subordinate
 * {@link Excluder}.
 */
public class ExclusionContainer<T extends Excluder> implements Excluder {

    /**
     * The {@link ExclusionMode} of a {@link ExclusionContainer} defines the behaviour to use for negotiating
     * the behaviour of multiple subordinate {@link Excluder Excluders}.
     */
    public static enum ExclusionMode {

        /**
         * Only exclude the dependency is all subordinate {@link Excluder Excluders} exclude the dependency.
         * However, there is an exception in case that there are no subordinate {@link Excluder Excluders} at which
         * point the dependency is not excluded even though theoretically all of no {@link Excluder Excluders} matched.
         * However, such overwhelmingly strict behaviour is nonsensical which is why this exception was instated.
         */
        ALL,

        /**
         * Exclude the dependency if at least one subordinate {@link Excluder} excludes the dependency.
         */
        ANY;
    }

    public static final ExclusionContainer<?> EMPTY = new ExclusionContainer<>(ExclusionMode.ALL, Collections.emptyList(), false);
    @SuppressWarnings("unchecked")
    public static <T extends Excluder> ExclusionContainer<T> empty() {
        return (ExclusionContainer<T>) EMPTY;
    }
    private final List<T> children = new CopyOnWriteArrayList<>();
    private final ExclusionMode mode;

    private final boolean mutable;

    public ExclusionContainer(ExclusionMode mode) {
        this.mutable = true;
        this.mode = Objects.requireNonNull(mode, "\"mode\" may not be null");
    }

    public ExclusionContainer(ExclusionMode mode, Collection<T> exclusions, boolean mutable) {
        this.children.addAll(exclusions);
        this.mutable = mutable;
        this.mode = Objects.requireNonNull(mode, "\"mode\" may not be null");
    }

    public ExclusionContainer<T> addChild(T child) {
        if (!this.mutable) {
            throw new IllegalStateException("Container is not mutable.");
        }
        this.children.add(child);
        return this;
    }

    public ExclusionMode getMode() {
        return this.mode;
    }

    @Override
    public boolean isExcluding(String group, String artifact) {
        if (this.mode == ExclusionMode.ALL) {
            if (this.children.isEmpty()) {
                return false;
            }
            for (Excluder child : this.children) {
                if (!child.isExcluding(group, artifact)) {
                    return false;
                }
            }
            return true;
        } else {
            for (Excluder child : this.children) {
                if (child.isExcluding(group, artifact)) {
                    return true;
                }
            }
            return false;
        }
    }

    public boolean isMutable() {
        return this.mutable;
    }
}
