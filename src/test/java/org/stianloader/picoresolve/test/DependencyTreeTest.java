package org.stianloader.picoresolve.test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.stianloader.picoresolve.DependencyLayer;
import org.stianloader.picoresolve.DependencyLayer.DependencyEdge;
import org.stianloader.picoresolve.DependencyLayer.DependencyLayerElement;
import org.stianloader.picoresolve.GAV;
import org.stianloader.picoresolve.MavenResolver;
import org.stianloader.picoresolve.repo.URIMavenRepository;
import org.stianloader.picoresolve.version.MavenVersion;

public class DependencyTreeTest {

    private static class GA {
        @NotNull
        private final String artifact;
        @NotNull
        private final String group;

        public GA(@NotNull String group, @NotNull String artifact) {
            this.group = group;
            this.artifact = artifact;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof GA) {
                GA other = (GA) obj;

                return other.group.equals(this.group) && other.artifact.equals(this.artifact);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.group, this.artifact);
        }
    }

    private static void assertTreeEquals(@NotNull GAV gav, @NotNull String tree) {
        assertDoesNotThrow(() -> {
            try {
                MavenResolver resolver = new MavenResolver(Paths.get("testmvnlocal"))
                        .addRepository(new TestResourceRepository())
                        .addRepository(new URIMavenRepository("stianloader-all", URI.create("https://stianloader.org/maven2/all")));

                resolver.setLogger(new NOPLogger());

                DependencyLayer layer = DependencyTreeTest.createVirtualLayerFor(gav);

                resolver.resolveAllChildren(layer, Runnable::run);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DependencyTreeTest.printtree(new PrintStream(baos, true, StandardCharsets.UTF_8.name()), layer.elements.get(0));

                assertEquals(tree.trim(), new String(baos.toByteArray(), StandardCharsets.UTF_8).trim());
            } catch (Throwable t) {
                t.printStackTrace();
                throw t;
            }
        });
    }

    @NotNull
    private static DependencyLayer createVirtualLayerFor(@NotNull GAV @NotNull... dependencies) {
        return DependencyLayer.createLayerFor(new GAV("virtual-node", "virtual-node", MavenVersion.parse("")), dependencies);
    }

    static void printtree(PrintStream out, DependencyLayerElement tree) {
        DependencyTreeTest.printtree(out, tree, new BitSet(256), 0, new HashSet<>());
    }

    private static void printtree(PrintStream out, DependencyLayerElement tree, BitSet indent, int indentDepth, Set<GA> duplicateGuard) {
        if (indentDepth != 0) {
            for (int i = 1; i < indentDepth; i++) {
                if (indent.get(i)) {
                    out.print("|  ");
                } else {
                    out.print("   ");
                }
            }

            if (indent.get(0)) {
                out.print("+- ");
            } else {
                out.print("\\- ");
            }
        }

        out.println(tree.gav.group() + ":" + tree.gav.artifact() + ":" + tree.classifier + ":" + tree.type + ":" + tree.gav.version().getOriginText());
        int childCount = tree.outgoingEdges.size();
        int i = 0;

        if (indent.size() == indentDepth) {
            BitSet b2 = new BitSet(indent.size() * 2);
            b2.or(indent);
            indent = b2;
        }

        indent.set(indentDepth, indent.get(0));
        GA ga = new GA(tree.gav.group(), tree.gav.artifact());

        if (duplicateGuard.add(ga)) {
            for (DependencyEdge child : tree.outgoingEdges) {
                if (++i == childCount) {
                    indent.clear(0);
                } else {
                    indent.set(0);
                }

                DependencyTreeTest.printtree(out, child.getResolved(), indent, indentDepth + 1, duplicateGuard);
            }
        }
    }

    @Test
    public void testOrderInsensitive() {
        // Resolution trees whose contents don't really differ even if the dependencies are reordered in the <dependencies> block.

        assertTreeEquals(new GAV("org.stianloader.picoresolve-tests", "test-project-a", MavenVersion.parse("1.1.0")),
                "virtual-node:virtual-node:null:jar:\n"
                + "\\- org.stianloader.picoresolve-tests:test-project-a:null:jar:1.1.0\n"
                + "   +- org.ow2.asm:asm-util:null:jar:9.10.1\n"
                + "   |  +- org.ow2.asm:asm:null:jar:9.10\n"
                + "   |  +- org.ow2.asm:asm-tree:null:jar:9.10.1\n"
                + "   |  |  \\- org.ow2.asm:asm:null:jar:9.10\n"
                + "   |  \\- org.ow2.asm:asm-analysis:null:jar:9.10.1\n"
                + "   |     \\- org.ow2.asm:asm-tree:null:jar:9.10.1\n"
                + "   \\- org.ow2.asm:asm:null:jar:9.10");

        assertTreeEquals(new GAV("org.stianloader.picoresolve-tests", "test-project-a", MavenVersion.parse("1.1.1")),
                "virtual-node:virtual-node:null:jar:\n"
                + "\\- org.stianloader.picoresolve-tests:test-project-a:null:jar:1.1.1\n"
                + "   +- org.ow2.asm:asm:null:jar:9.10\n"
                + "   \\- org.ow2.asm:asm-util:null:jar:9.10.1\n"
                + "      +- org.ow2.asm:asm:null:jar:9.10\n"
                + "      +- org.ow2.asm:asm-tree:null:jar:9.10.1\n"
                + "      |  \\- org.ow2.asm:asm:null:jar:9.10\n"
                + "      \\- org.ow2.asm:asm-analysis:null:jar:9.10.1\n"
                + "         \\- org.ow2.asm:asm-tree:null:jar:9.10.1");

        assertTreeEquals(new GAV("org.stianloader.picoresolve-tests", "test-project-a", MavenVersion.parse("1.2.0")),
                "virtual-node:virtual-node:null:jar:\n"
                + "\\- org.stianloader.picoresolve-tests:test-project-a:null:jar:1.2.0\n"
                + "   +- org.ow2.asm:asm:null:jar:9.10.1\n"
                + "   \\- org.ow2.asm:asm-util:null:jar:9.10.1\n"
                + "      +- org.ow2.asm:asm:null:jar:9.10.1\n"
                + "      +- org.ow2.asm:asm-tree:null:jar:9.10.1\n"
                + "      |  \\- org.ow2.asm:asm:null:jar:9.10.1\n"
                + "      \\- org.ow2.asm:asm-analysis:null:jar:9.10.1\n"
                + "         \\- org.ow2.asm:asm-tree:null:jar:9.10.1");

        assertTreeEquals(new GAV("org.stianloader.picoresolve-tests", "test-project-a", MavenVersion.parse("1.2.1")),
                "virtual-node:virtual-node:null:jar:\n"
                + "\\- org.stianloader.picoresolve-tests:test-project-a:null:jar:1.2.1\n"
                + "   +- org.ow2.asm:asm-util:null:jar:9.10.1\n"
                + "   |  +- org.ow2.asm:asm:null:jar:9.10.1\n"
                + "   |  +- org.ow2.asm:asm-tree:null:jar:9.10.1\n"
                + "   |  |  \\- org.ow2.asm:asm:null:jar:9.10.1\n"
                + "   |  \\- org.ow2.asm:asm-analysis:null:jar:9.10.1\n"
                + "   |     \\- org.ow2.asm:asm-tree:null:jar:9.10.1\n"
                + "   \\- org.ow2.asm:asm:null:jar:9.10.1");

        assertTreeEquals(new GAV("org.stianloader.picoresolve-tests", "test-project-a", MavenVersion.parse("1.2.2")),
                "virtual-node:virtual-node:null:jar:\n"
                + "\\- org.stianloader.picoresolve-tests:test-project-a:null:jar:1.2.2\n"
                + "   +- org.ow2.asm:asm-util:null:jar:9.9\n"
                + "   |  +- org.ow2.asm:asm:null:jar:9.10.1\n"
                + "   |  +- org.ow2.asm:asm-tree:null:jar:9.9\n"
                + "   |  |  \\- org.ow2.asm:asm:null:jar:9.10.1\n"
                + "   |  \\- org.ow2.asm:asm-analysis:null:jar:9.9\n"
                + "   |     \\- org.ow2.asm:asm-tree:null:jar:9.9\n"
                + "   \\- org.ow2.asm:asm:null:jar:9.10.1");

        assertTreeEquals(new GAV("org.stianloader.picoresolve-tests", "test-project-a", MavenVersion.parse("1.2.3")),
                "virtual-node:virtual-node:null:jar:\n"
                + "\\- org.stianloader.picoresolve-tests:test-project-a:null:jar:1.2.3\n"
                + "   +- org.ow2.asm:asm:null:jar:9.10.1\n"
                + "   \\- org.ow2.asm:asm-util:null:jar:9.9\n"
                + "      +- org.ow2.asm:asm:null:jar:9.10.1\n"
                + "      +- org.ow2.asm:asm-tree:null:jar:9.9\n"
                + "      |  \\- org.ow2.asm:asm:null:jar:9.10.1\n"
                + "      \\- org.ow2.asm:asm-analysis:null:jar:9.9\n"
                + "         \\- org.ow2.asm:asm-tree:null:jar:9.9");
        
    }

    @Test
    public void testOrderSensitive() {
        // Resolution trees whose contents differ significantly if the dependencies are reordered in the <dependencies> block.

        assertTreeEquals(new GAV("org.stianloader.picoresolve-tests", "test-project-a", MavenVersion.parse("1.3.0")),
                "virtual-node:virtual-node:null:jar:\n"
                + "\\- org.stianloader.picoresolve-tests:test-project-a:null:jar:1.3.0\n"
                + "   +- org.ow2.asm:asm-util:null:jar:9.9\n"
                + "   |  +- org.ow2.asm:asm:null:jar:9.9\n"
                + "   |  +- org.ow2.asm:asm-tree:null:jar:9.9\n"
                + "   |  |  \\- org.ow2.asm:asm:null:jar:9.9\n"
                + "   |  \\- org.ow2.asm:asm-analysis:null:jar:9.10.1\n"
                + "   |     \\- org.ow2.asm:asm-tree:null:jar:9.9\n"
                + "   \\- org.ow2.asm:asm-analysis:null:jar:9.10.1");

        assertTreeEquals(new GAV("org.stianloader.picoresolve-tests", "test-project-a", MavenVersion.parse("1.3.1")),
                "virtual-node:virtual-node:null:jar:\n"
                + "\\- org.stianloader.picoresolve-tests:test-project-a:null:jar:1.3.1\n"
                + "   +- org.ow2.asm:asm-analysis:null:jar:9.10.1\n"
                + "   |  \\- org.ow2.asm:asm-tree:null:jar:9.10.1\n"
                + "   |     \\- org.ow2.asm:asm:null:jar:9.9\n"
                + "   \\- org.ow2.asm:asm-util:null:jar:9.9\n"
                + "      +- org.ow2.asm:asm:null:jar:9.9\n"
                + "      +- org.ow2.asm:asm-tree:null:jar:9.10.1\n"
                + "      \\- org.ow2.asm:asm-analysis:null:jar:9.10.1");
    }
}
