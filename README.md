# PicoResolve

A truly small, fast and asynchronous reimplementation of the maven artifact
resolver that yet manages to be mostly feature complete.

## Why reimplement?

The official maven artifact resolver as maintained by the Apache foundation may
appear as a black box to any outsiders, significantly discouraging developers
to directly use it. As such, developers tend to use 3rd party wrappers such as
shrinkwrap ontop of the maven-artifact-resolver. All these dependencies however
significantly increase the size of your application (the resolver api, spi and
implementation already have a combined size of more than a MB), which is why a
fair amount of developers have opted to either not make use of the maven
repository scheme at all or to not support transitive dependency resolution.

## Core design philosophies

This project has following core ideas:

- The connection latency should have as little impact as possible.
- Extending support to other transports should be easily possible from the
 developer's side.
- The compiled jar may not be larger than 150 KB.
- The resolver does not depend on any 3rd party libraries at runtime (SLF4J
 may be an optional dependency in the future and jetbrains-annoations is a
 compile-time dependency)

While readability is welcome whenever applicable, it was evaluated that
the needed multi-thread capabilities severely hamper the readability of the
application. However as in practice readability is (sadly) much less
important as fast resolution, the unfortunate compromise was done at the
design stage.

As most files the resolver downloads are relatively tiny yet numerous,
bandwidth is a much lesser concern compared to latency. If files were to
be downloaded serially the time spent waiting for the server would rise
with the amount of repositories that need to be hit. As such all requests
need to be done at the same time, even if that means that slightly more
bandwidth it makes use of in the process. The performance gains from waiting
less for the server to respond greatly overshadow the performance loss from
writing and reading the extra data from the relevant networking stack.

## Dependencies

PicoResolve depends on the following at minimum (at runtime):
 - Java 8 or above

## Building

PicoResolve can be built using [maven](https://maven.apache.org/).
To compile, simply use `mvn install`.

# Usage

## Maven

**Note: At this moment in time, picoresolve is only available on https://stianloader.org/maven/**

PicoResolve is available as "org.stianloader:picoresolve:1.0.0-a20240601". As such you can
add PicoResolve to your project the following ways:

```xml
<dependency>
  <groupId>org.stianloader</groupId>
  <artifactId>picoresolve</artifactId>
  <version>1.0.0-a20240601</version>
</dependency>
```

Under maven

```groovy
implementation("org.stianloader:picoresolve:1.0.0-a20240601")
```

Under groovy gradle


Note: Please be aware the the above version is a nightly ("indev") release. The stable release
will be whichever version hits maven central. When that happens is up in the air though.

# Architecture

## Dependency layers

The picoresolve artifact resolver resolves dependencies in batches.
These batches are known as dependency layers. The resolved artifacts or
dependencies are known as dependency layer elements. Each layer may have
one parent and a child layer. For layer L with the child layer C and the parent
layer P the following things apply:
 - All elements of C were defined by L.
 - No elements of C were defined by P.
 - All elements of L were defined by L.
 - Elements of layer L or layer C do not have an impact
    for the resolution of elements in layer L or P.

The dependencies of a layer are at first stored as primitive "edges".
These edges define the following properties:
 - The requested coordinates of the dependency artifact (group, artifact,
   classifier and type)
 - The requested scope
 - The requested version range
 - The effective exclusions

Furthermore, edges can point to a dependency layer element once they are
"resolved", that is once the required version has been negotiated. The element
will always belong to a child layer **or any layer above it**, but never a
layer below the child layer. When the resolved element does not belong to the
direct child layer, the edge had no impact on the resolution, which means that
the requested exclusions of the element will not have any effect. This may even
result in scenarios where the resolved version does not fit in the requested
version range. However, this behavior is inline with the official maven
artifact resolver.

The effective exclusions are inherited throughout the generated tree. That is
the incoming edges of a element node are AND'd together and then OR'd with
the exclusions defined in the dependency and dependency management block of the
element's POM.

Picoresolve makes no serious attempt at optimizing the exclusion containers, so
for very very large trees this may be a potential architectural issue. However
it is estimated that the dependency trees would need to be so large that the
chance of there being other issues is significantly higher.

## Exclusions

All classes which introduce exclusion-style behavior need to implement
"Excluder". The standard picoresolve implementation defines two subclasses:
Exclusion and ExclusionContainer.

Exclusion is a wrapper around the "exclusion" block you are generally prone to
see within a "dependency"-element of your maven POM. The group and artifact.
Like all subclasses of Excluder, Exclusion only filters based on artifactId
and groupId, but not on classifier, type or version. The wildcard string `*`
is directly handled within Exclusion and need not be converted beforehand.

ExclusionContainer can store any Excluder subclass, however for compile-time
sanity checking reasons the ExclusionContainer class defined generics. However,
as the generic types are not exposed outside the container it is generally safe,
albeit not recommended to ignore the generics.

## Repository negotiation

The implementations of the RepositoryNegotiator interface represent the linking
bond between the multitude of repositories added to the maven resolver and the
file system. More specifically, a RepositoryNegotiator's main purpose is the
management of the file system cache. The MavenLocalRepositoryNegotiator
implementation (which is by default the only implementation of the
RepositoryNegotiator interface) for example allows to interface with
mavenLocal-type caches.

Resolver-specific metadata that only exists within the cache is generated by the
Repository negotiator. The MavenLocalRepositoryNegotiator attempts to make use
of the same metadata as the maven resolver.

Another vital role of the RepositoryNegotiator is file locking, that is to
ensure that the cache does not blow up once two or more threads or JVM processes
request the same file at the same time. However with the
MavenLocalRepositoryNegotiator there is still a risk of inconsistent data being
committed to the resolver, but the cache should not be poisoned with it. That
being said the needed effort required for fixing these concurrency issues once
and for all severely outweigh the benefits of not having a very minor chance of
unexpected results in a very rare scenario (I don't think it will be that often
that the resolver cache will be hit twice by two separate threads or processes
and attempt to require the file twice). The main potential victim are network
file systems, where transactions are unusually delayed as-is and atomic file
system operations are unlikely to be supported.

# Limitations

As of now following design limitations apply to PicoResolve that you may want
to be aware of:

 - The maven settings.xml files are ignored.
 - Repositories of dependency artifacts are ignored completely. (though on the
   other hand this means no unexpected security vulnerabilities)
 - Ivy repositories are not supported, nor is resolution based on gradle
   metadata.
 - PicoResolve uses the same version comparison behavior as maven. This may
   result in inconsistencies with gradle.
 - PicoResolve may struggle when it is sharing maven local caches with other
   resolvers, however it attempts to make use of the same locking behavior as
   the maven resolver and will generally read and write the same metadata files
   and will not write extraneous PicoResolve-specific files.
 - PicoResolve is likely to be extremely slow when the maven local cache is a
   network file system such as OneDrive or similar. Please avoid running a cache
   outside of your computer whenever possible.
 - PicoResolve does not offer a transport registration system. The underlying
   transports are instead subclasses of MavenRepository that need to be
   instantiated directly by the caller.
