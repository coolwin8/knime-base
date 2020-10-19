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
 *   19 Oct 2020 (lars.schweikardt): created
 */
package org.knime.base.node.io.filehandling.util.copymovefiles;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import org.apache.commons.lang3.tuple.Pair;
import org.knime.base.node.io.filehandling.util.PathRelativizer;
import org.knime.base.node.io.filehandling.util.PathRelativizerNonTableInput;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.util.CheckUtils;
import org.knime.filehandling.core.connections.FSFiles;
import org.knime.filehandling.core.connections.FSPath;
import org.knime.filehandling.core.defaultnodesettings.filechooser.reader.ReadPathAccessor;
import org.knime.filehandling.core.defaultnodesettings.filechooser.writer.WritePathAccessor;
import org.knime.filehandling.core.defaultnodesettings.filtermode.SettingsModelFilterMode.FilterMode;
import org.knime.filehandling.core.defaultnodesettings.status.StatusMessage;

/**
 *
 * @author lars.schweikardt
 */
final class PathPairs {

    private final CopyMoveFilesNodeConfig m_config;

    private final Consumer<StatusMessage> m_statusConsumer;

    private final PathRelativizer m_pathRelativizer;

    private final Iterator<FSPath> m_srcIterator;

    private final FSPath m_destinationPath;

    private final FSPath m_sourcePath;

    private final FilterMode m_filterMode;

    PathPairs(final ReadPathAccessor readPathAccessor, final WritePathAccessor writePathAccessor,
        final CopyMoveFilesNodeConfig config, final Consumer<StatusMessage> statusMessageConsumer)
        throws InvalidSettingsException, IOException {
        m_destinationPath = writePathAccessor.getOutputPath(statusMessageConsumer);
        m_config = config;

        if (m_config.getDestinationFileChooserModel().isCreateMissingFolders()) {
            FileCopier.createOutputDirectories(m_destinationPath);
        } else {
            CheckUtils.checkSetting(FSFiles.exists(m_destinationPath),
                String.format("The specified destination folder %s does not exist.", m_destinationPath));
        }
        m_statusConsumer = statusMessageConsumer;
        m_filterMode =  m_config.getSourceFileChooserModel().getFilterMode();
        final List<FSPath> pathList =
            getSourcePaths(readPathAccessor,m_filterMode);
        m_srcIterator = pathList.iterator();
        m_sourcePath = readPathAccessor.getRootPath(statusMessageConsumer);
        m_pathRelativizer = new PathRelativizerNonTableInput(m_sourcePath,
            m_config.getSettingsModelIncludeSourceFolder().getBooleanValue());
    }

    private List<FSPath> getSourcePaths(final ReadPathAccessor readPathAccessor, final FilterMode filterMode)
        throws IOException, InvalidSettingsException {
        List<FSPath> sourcePaths = readPathAccessor.getFSPaths(m_statusConsumer);
        CheckUtils.checkSetting(!sourcePaths.isEmpty(),
            "No files available please select a folder which contains files");
        if (filterMode == FilterMode.FOLDER) {
            final List<FSPath> pathsFromFolder = FSFiles.getFilePathsFromFolder(sourcePaths.get(0), m_filterMode == FilterMode.FOLDER);
            sourcePaths = pathsFromFolder;
        }
        return sourcePaths;
    }

    /**
     * TODO
     *
     * @return
     */
    public List<Pair<FSPath, FSPath>> getPathPairList() {
        final List<Pair<FSPath, FSPath>> pathList = new ArrayList<>();

        while (m_srcIterator.hasNext()) {
            final FSPath srcPath = m_srcIterator.next();
            final FSPath destPath = m_config.getSourceFileChooserModel().getFilterMode() == FilterMode.FILE
                ? (FSPath)m_destinationPath.resolve(srcPath.getFileName())
                : (FSPath)m_destinationPath.resolve(m_pathRelativizer.apply(srcPath));

            pathList.add(Pair.of(srcPath, destPath));
        }
        return pathList;
    }
}
