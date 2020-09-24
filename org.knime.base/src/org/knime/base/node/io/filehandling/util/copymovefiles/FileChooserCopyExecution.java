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
 *   Sep 29, 2020 (lars.schweikardt): created
 */
package org.knime.base.node.io.filehandling.util.copymovefiles;

import java.io.IOException;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.knime.base.node.io.filehandling.util.PathRelativizer;
import org.knime.base.node.io.filehandling.util.PathRelativizerNonTableInput;
import org.knime.core.data.DataRow;
import org.knime.core.data.filestore.FileStoreFactory;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.util.CheckUtils;
import org.knime.filehandling.core.connections.FSFiles;
import org.knime.filehandling.core.connections.FSLocation;
import org.knime.filehandling.core.connections.FSPath;
import org.knime.filehandling.core.defaultnodesettings.filechooser.reader.ReadPathAccessor;
import org.knime.filehandling.core.defaultnodesettings.filechooser.writer.WritePathAccessor;
import org.knime.filehandling.core.defaultnodesettings.filtermode.SettingsModelFilterMode.FilterMode;
import org.knime.filehandling.core.defaultnodesettings.status.StatusMessage;

/**
 *
 * @author Lars Schweikardt, KNIME GmbH, Konstanz, Germany
 */
final class FileChooserCopyExecution implements CopyExecution {

    private final CopyMoveFilesNodeConfig m_config;

    FileChooserCopyExecution(final CopyMoveFilesNodeConfig config) {
        m_config = config;
    }

    @Override
    public void fillRowOutput(final Consumer<DataRow> rowConsumer, final Consumer<StatusMessage> statusConsumer,
        final ExecutionContext exec) throws IOException, InvalidSettingsException, CanceledExecutionException {

        final FSLocation sourceLocation = m_config.getSourceFileChooserModel().getLocation();
        final FSLocation destinationLocation = m_config.getDestinationFileChooserModel().getLocation();

        final FileCopier fileCopier =
            new FileCopier(rowConsumer, m_config, FileStoreFactory.createFileStoreFactory(exec), sourceLocation,
                destinationLocation, m_config.getDestinationFileChooserModel().getFileOverwritePolicy());

        try (final ReadPathAccessor readPathAccessor = m_config.getSourceFileChooserModel().createReadPathAccessor();
                final WritePathAccessor writePathAccessor =
                    m_config.getDestinationFileChooserModel().createWritePathAccessor()) {

            final FSPath rootPath = readPathAccessor.getRootPath(statusConsumer);
            final FSPath destinationDir = writePathAccessor.getOutputPath(statusConsumer);
            final FilterMode filterMode = m_config.getSourceFileChooserModel().getFilterModeModel().getFilterMode();
            final List<FSPath> sourcePaths = getSourcePaths(readPathAccessor, filterMode, statusConsumer);
            //                            m_statusConsumer.setWarningsIfRequired(this::setWarningMessage);

            //Creates output directories if necessary
            if (m_config.getDestinationFileChooserModel().isCreateMissingFolders()) {
                FileCopier.createOutputDirectories(destinationDir);
            }

            final PathRelativizer pathRelativizer = new PathRelativizerNonTableInput(rootPath,
                m_config.getSettingsModelIncludeParentFolder().getBooleanValue(), filterMode);

            long rowIdx = 0;
            final long noOfFiles = sourcePaths.size();

            for (FSPath sourceFilePath : sourcePaths) {
                fileCopier.copy(sourceFilePath, destinationDir.resolve(pathRelativizer.apply(sourceFilePath)), rowIdx);
                final long copiedFiles = rowIdx + 1;
                exec.setProgress(copiedFiles / (double)noOfFiles, () -> ("Copied files :" + copiedFiles));
                exec.checkCanceled();
                rowIdx++;
            }
        }
    }

    //TODO DESC
    private static List<FSPath> getSourcePaths(final ReadPathAccessor readPathAccessor, final FilterMode filterMode,
        final Consumer<StatusMessage> statusConsumer) throws IOException, InvalidSettingsException {
        List<FSPath> sourcePaths = readPathAccessor.getFSPaths(statusConsumer);
        CheckUtils.checkSetting(!sourcePaths.isEmpty(),
            "No files available please select a folder which contains files");
        if (filterMode == FilterMode.FOLDER) {
            final List<FSPath> pathsFromFolder = FSFiles.getFilePathsFromFolder(sourcePaths.get(0));
            sourcePaths = pathsFromFolder;
        }
        return sourcePaths;
    }


    @Override
    public void pushFlowVariables(final BiConsumer<String, FSLocation> variableConsumer) {
        variableConsumer.accept("source_path", m_config.getSourceFileChooserModel().getLocation());
        variableConsumer.accept("destination_path", m_config.getDestinationFileChooserModel().getLocation());
    }

}
