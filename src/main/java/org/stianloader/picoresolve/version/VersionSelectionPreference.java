package org.stianloader.picoresolve.version;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executor;

import org.jetbrains.annotations.ApiStatus;
import org.stianloader.picoresolve.DependencyLayer;

/**
 * When selecting between multiple requested versions,
 * it is imperative to have a defined order which requested version is being ultimately used.
 *
 * <p>More specifically, when having a version <code>V0</code> declared by artifact <code>A0</code>
 * and a version <code>V1</code> declared by artifact <code>A1</code>, it is important
 * to know whether version <code>V0</code> or version <code>V1</code> is being used.
 *
 * <p>For this metric, there are two comparison methods available:
 * <ul>
 *  <li>Compare using the version itself, ignoring the declaring artifact. This corresponds to {@link #NEWEST_FIRST}.</li>
 *  <li>Alternatively, compare using the declaring artifact. In the case of {@link #DECLARATION_ORDER} it uses the declaration
 *  order as the baseline metric. As such, the version order can be ignored.</li>
 * </ul>
 *
 * @since 1.1.0-a20260531
 */
@ApiStatus.AvailableSince("1.1.0-a20260531")
public enum VersionSelectionPreference {
    /**
     * Prefer versions defined by artifacts that have been declared first in the POM.
     *
     * <p>As picoresolve is a concurrent environment, this declaration order might be
     * dependent on the {@link Executor} being used, as well as pure chance. However,
     * such randomness is usually indicative of a bug within picoresolve. Please report
     * such issues whenever they arise.
     *
     * <p>This is the metric being used in Maven's artifact resolver, as well as the
     * default metric in picoresolve since version <code>1.1.0-a20260531</code>.
     *
     * <p>In practice, the artifact declaration order is defined using the order in which
     * {@link VersionRange} instances are intersected with each other, i.e. the
     * order of the {@link VersionRange#intersect(VersionRange)} calls.
     *
     * @since 1.1.0-a20260531
     */
    @ApiStatus.AvailableSince("1.1.0-a20260531")
    DECLARATION_ORDER,

    /**
     * Prefer versions that are newer on their own, as per {@link MavenVersion#isNewerThan(MavenVersion)}.
     *
     * <p>This is the most stable metric in a concurrent environment, as it does not depend on the
     * order in which tasks are completed. It bypasses minor bugs concerning collections that
     * have no defined order, such as {@link HashMap}.
     *
     * <p>Further, this metric is rather intuitive with picoresolve's "resolve in layer" model,
     * even though the artifact ordering semantics are indirectly exposed due to
     * {@link DependencyLayer#elements} being a {@link List}.
     *
     * <p>This was the only metric used in picoresolve before version <code>1.1.0-a20260531</code>.
     * However, it is no longer the default as the maven artifact resolve does not make use of that
     * metric.
     * 
     * @since 1.1.0-a20260531
     */
    @ApiStatus.AvailableSince("1.1.0-a20260531")
    NEWEST_FIRST;
}
