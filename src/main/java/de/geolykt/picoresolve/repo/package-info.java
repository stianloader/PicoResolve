/**
 * Package storing repository, downloading and installation related classes.
 * Other capabilities of this package is checking for updates if needed, ensuring high-level
 * thread safety by locking the filesystem and providing filesystem-level interop with the official
 * maven resolver implementation.
 *
 * <p>Providing a structured layout of repositories is not the task of this package and is considered
 * as internal API.
 */
package de.geolykt.picoresolve.repo;
