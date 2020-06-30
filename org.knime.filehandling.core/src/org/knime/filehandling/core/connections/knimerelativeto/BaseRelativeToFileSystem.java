/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   Feb 11, 2020 (Sascha Wolke, KNIME GmbH): created
 */
package org.knime.filehandling.core.connections.knimerelativeto;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Collections;

import org.apache.commons.io.FilenameUtils;
import org.knime.core.node.workflow.WorkflowPersistor;
import org.knime.filehandling.core.connections.DefaultFSLocationSpec;
import org.knime.filehandling.core.connections.FSCategory;
import org.knime.filehandling.core.connections.FSLocationSpec;
import org.knime.filehandling.core.connections.base.BaseFileStore;
import org.knime.filehandling.core.connections.base.BaseFileSystem;
import org.knime.filehandling.core.defaultnodesettings.FileSystemChoice.Choice;
import org.knime.filehandling.core.defaultnodesettings.KNIMEConnection.Type;

/**
 * Abstract relative to file system.
 *
 * @author Sascha Wolke, KNIME GmbH
 */
public abstract class BaseRelativeToFileSystem extends BaseFileSystem<RelativeToPath> {

    static final String MOUNTPOINT_REL_SCHEME = "knime-relative-mountpoint";

    static final String WORKFLOW_REL_SCHEME = "knime-relative-workflow";

    static final String WORKFLOW_DATA_REL_SCHEME = "knime-relative-workflow-data";

    public static final FSLocationSpec CONVENIENCE_WORKFLOW_RELATIVE_FS_LOCATION_SPEC =
        new DefaultFSLocationSpec(FSCategory.RELATIVE, "knime.workflow");

    public static final FSLocationSpec CONVENIENCE_WORKFLOW_DATA_RELATIVE_FS_LOCATION_SPEC =
        new DefaultFSLocationSpec(FSCategory.RELATIVE, "knime.workflow.data");

    public static final FSLocationSpec CONVENIENCE_MOUNTPOINT_RELATIVE_FS_LOCATION_SPEC =
        new DefaultFSLocationSpec(FSCategory.RELATIVE, "knime.mountpoint");

    public static final FSLocationSpec CONNECTED_WORKFLOW_RELATIVE_FS_LOCATION_SPEC =
        new DefaultFSLocationSpec(FSCategory.CONNECTED, WORKFLOW_REL_SCHEME);

    public static final FSLocationSpec CONNECTED_MOUNTPOINT_RELATIVE_FS_LOCATION_SPEC =
        new DefaultFSLocationSpec(FSCategory.CONNECTED, MOUNTPOINT_REL_SCHEME);

    public static final FSLocationSpec CONNECTED_WORKFLOW_DATA_RELATIVE_FS_LOCATION_SPEC =
        new DefaultFSLocationSpec(FSCategory.CONNECTED, WORKFLOW_DATA_REL_SCHEME);

    /**
     * Separator used between names in paths.
     */
    protected static final String PATH_SEPARATOR = "/";

    private static final long CACHE_TTL = 0; // = disabled


    private final Type m_type;

    private final String m_scheme;

    private final String m_hostString;

    /**
     * Default constructor.
     *
     * @param fileSystemProvider Creator of this FS, holding a reference.
     * @param uri URI without a path
     * @param connectionType {@link Type#MOUNTPOINT_RELATIVE} or {@link Type#WORKFLOW_RELATIVE} connection type
     * @param isConnectedFs Whether this file system is a {@link Choice#CONNECTED_FS} or a convenience file system
     *            ({@link Choice#KNIME_FS})
     * @throws IOException
     */
    protected BaseRelativeToFileSystem(final BaseRelativeToFileSystemProvider<? extends BaseRelativeToFileSystem> fileSystemProvider,
        final URI uri,
        final Type connectionType,
        final String workingDir,
        final FSLocationSpec fsLocationSpec) {

        super(fileSystemProvider, //
            uri, //
            CACHE_TTL, //
            workingDir,
            fsLocationSpec);

        m_type = connectionType;
        if (m_type == Type.MOUNTPOINT_RELATIVE) {
            m_scheme = MOUNTPOINT_REL_SCHEME;
        } else if (m_type == Type.WORKFLOW_RELATIVE) {
            m_scheme = WORKFLOW_REL_SCHEME;
        } else if (m_type == Type.WORKFLOW_DATA_RELATIVE) {
            m_scheme = WORKFLOW_DATA_REL_SCHEME;
        } else {
            throw new IllegalArgumentException("Illegal type " + m_type);
        }
        m_hostString = uri.getHost();
    }

    @Override
    public String getSeparator() {
        return PATH_SEPARATOR;
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        return Collections.singletonList(getPath(getSeparator()));
    }

    /**
     * Get the file store of a given relative-to path.
     *
     * @param path relative-to path
     * @return file store of the given relative-to path.
     * @throws IOException
     */
    protected abstract FileStore getFileStore(final RelativeToPath path) throws IOException;

    /**
     * Utility method to create a {@link FileStore} based on the file store of a given path.
     *
     * @param path path with source file store
     * @param type type of the new file store
     * @param name name of the new file store
     * @return file store with given type, name and attributes from file store of the given path
     * @throws IOException
     */
    protected static FileStore getFileStore(final Path path, final String type, final String name) throws IOException {
        final FileStore localFileStore = Files.getFileStore(path);
        return new BaseFileStore(type, //
            name, //
            localFileStore.isReadOnly(), //
            localFileStore.getTotalSpace(), //
            localFileStore.getUsableSpace());
    }

    @Override
    public RelativeToPath getPath(final String first, final String... more) {
        return new RelativeToPath(this, first, more);
    }

    /**
     * Utility method whether the given path (from the relative-to FS) can be accessed with the relative-to
     * file system, e.g. files outside the current mountpoint are not accessible.
     *
     * @param path A path (from the relative-to FS).
     * @return true when the given path can be accessed with the relative-to file system, false otherwise.
     * @throws IOException
     */
    public boolean isPathAccessible(final RelativeToPath path) throws IOException {
        // we must not access files outside of the mountpoint
        if (!isInMountPoint(path)) {
            return false;
        }

        // we must be able to view workflows (whether we display them as files or folders)
        if (isWorkflowDirectory(path)) {
            return true;
        }

        // we must never be able to see files inside workflows
        if (isPartOfWorkflow(path)) {
            return false;
        }

        return true;
    }

    /**
     * Validate if a given relative-to path is a workflow, meta node or component directory.
     * @param path path to check
     * @return {@code true} if the path represents a workflow, meta node or component directory.
     * @throws IOException
     */
    public abstract boolean isWorkflowDirectory(final RelativeToPath path) throws IOException;

    /**
     * Validates if the given local file system path is a workflow folder.
     *
     * @param localPath a path in the local file system to validate
     * @return {@code true} when the path contains a workflow
     */
    protected boolean isLocalWorkflowDirectory(final Path localPath) {
        if (!Files.exists(localPath)) {
            return false;
        }

        if (Files.exists(localPath.resolve(WorkflowPersistor.TEMPLATE_FILE))) { // metanode
            return false;
        }

        return Files.exists(localPath.resolve(WorkflowPersistor.WORKFLOW_FILE));
    }

    /**
     * Validate recursive if given path (from the relative-to FS) or a parent is part of a workflow.
     *
     * @param path relative-to file system path to check
     * @return {@code true} if given path or a parent path is part of a workflow
     * @throws IOException
     */
    private boolean isPartOfWorkflow(final RelativeToPath path) throws IOException {
        RelativeToPath current = (RelativeToPath)path.toAbsolutePath().normalize();

        while (isInMountPoint(current)) {
            if (isWorkflowDirectory(current)) {
                return true;
            } else {
                current = (RelativeToPath)current.getParent();
            }
        }

        return false;
    }

    /**
     * Test if given path is a child of the mount point directory.
     *
     * @param path relative-to file system path to test
     * @return {@code} if given path is a child of the mount point directory
     */
    private static boolean isInMountPoint(final RelativeToPath path) {
        // Note: Java's Path.normalize converts outside of the root "/../bla" into "/bla".
        // The Apache commons filename utils return null if the path is outside of the root directory.
        return path != null && FilenameUtils.normalize(path.toAbsolutePath().toString(), true) != null;
    }

    /**
     * Validates that a given relative-to file system path is accessible and maps it to a absolute and normalized path
     * in the real file system.
     *
     * @param path a relative-to path inside relative-to file system
     * @return an absolute path in the local file system (default FS provider) that corresponds to this path.
     * @throws NoSuchFileException if given path is not accessible
     * @throws IOException on other failures
     */
    protected abstract Path toRealPathWithAccessibilityCheck(final RelativeToPath path) throws IOException;

    /**
     * Check if given relative-to path represent a regular file. Workflow directories are files, with the exception of
     * the current workflow directory and a workflow relative path.
     *
     * @param path relative-to file system path to check
     * @return {@code true} if path is a normal file or a workflow directory
     * @throws IOException
     */
    protected abstract boolean isRegularFile(final RelativeToPath path) throws IOException;

    /**
     * Check if the given relative-to path exists and is accessible.
     *
     * @param path relative-to path to check
     * @return {@code true} if path exists and is accessible
     * @throws IOException
     */
    protected abstract boolean existsWithAccessibilityCheck(final RelativeToPath path) throws IOException;


    /**
     *
     * @return the {@link Type} of this file system.
     */
    public Type getType() {
        return m_type;
    }

    /**
     * @return {@code true} if this is a workflow relative and {@code false} if this is a mount point relative file
     *         system
     */
    public boolean isWorkflowRelativeFileSystem() {
        return m_type == Type.WORKFLOW_RELATIVE;
    }

    /**
     * @return {@code true} if this is a workflow relative and {@code false} if this is a mount point relative file
     *         system
     */
    public boolean isMountpointRelativeFileSystem() {
        return m_type == Type.MOUNTPOINT_RELATIVE;
    }

    /**
     * @return {@link BaseFileStore} file system type
     */
    protected String getFileStoreType() {
        return isWorkflowRelativeFileSystem() ? WORKFLOW_REL_SCHEME : MOUNTPOINT_REL_SCHEME;
    }

    @Override
    public String getSchemeString() {
        return m_scheme;
    }

    @Override
    public String getHostString() {
        return "";
    }
}