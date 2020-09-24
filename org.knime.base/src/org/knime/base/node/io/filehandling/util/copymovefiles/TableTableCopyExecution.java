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
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.knime.base.node.io.filehandling.util.PathRelativizer;
import org.knime.base.node.io.filehandling.util.PathRelativizerTableInput;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.container.CloseableRowIterator;
import org.knime.core.data.filestore.FileStoreFactory;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.port.PortObject;
import org.knime.filehandling.core.connections.FSConnection;
import org.knime.filehandling.core.connections.FSFiles;
import org.knime.filehandling.core.connections.FSLocation;
import org.knime.filehandling.core.connections.FSLocationSpec;
import org.knime.filehandling.core.connections.FSPath;
import org.knime.filehandling.core.connections.location.FSPathProvider;
import org.knime.filehandling.core.connections.location.FSPathProviderFactory;
import org.knime.filehandling.core.data.location.FSLocationValueMetaData;
import org.knime.filehandling.core.data.location.cell.FSLocationCell;
import org.knime.filehandling.core.defaultnodesettings.status.StatusMessage;

/**
 *
 * @author Lars Schweikardt, KNIME GmbH, Konstanz, Germany
 */
final class TableTableCopyExecution implements CopyExecution {

    private final CopyMoveFilesNodeConfig m_config;

    private final PortObject[] m_inObjects;

    private final int m_tableIdx;

    TableTableCopyExecution(final PortObject[] inObjects, final CopyMoveFilesNodeConfig config,
        final int tableIdx /*,final int fsSourceIdx, final int fsDestIdx*/) {
        m_config = config;
        m_inObjects = inObjects;
        m_tableIdx = tableIdx;
    }

    @Override
    public void fillRowOutput(final Consumer<DataRow> rowConsumer, final Consumer<StatusMessage> statusConsumer,
        final ExecutionContext exec) throws IOException, InvalidSettingsException, CanceledExecutionException {

        final BufferedDataTable inputDataTable = (BufferedDataTable)m_inObjects[m_tableIdx];
        final DataTableSpec tableSpec = (DataTableSpec)m_inObjects[m_tableIdx].getSpec();

        final DataColumnSpec sourceColSpec =
            tableSpec.getColumnSpec(m_config.getSelectedSourceColumnModel().getStringValue());
        final DataColumnSpec destinationColSpec =
            tableSpec.getColumnSpec(m_config.getSelectedDestinationColumnModel().getStringValue());

        final int sourceColIdx = tableSpec.findColumnIndex(m_config.getSelectedSourceColumnModel().getStringValue());
        final int destinationColIdx =
            tableSpec.findColumnIndex(m_config.getSelectedDestinationColumnModel().getStringValue());

        final FSLocationSpec sourceFSLocationSpec = getFSLocationSpec(sourceColSpec);
        final FSLocationSpec destinationFSLocationSpec = getFSLocationSpec(destinationColSpec);

        //TODO we need source and target fs empty in case of local thingy
        final Optional<FSConnection> fsConnectionSource = Optional.empty();
        final Optional<FSConnection> fsConnectionDestination = Optional.empty();

        final FileCopier fileCopier = new FileCopier(rowConsumer, m_config,
            FileStoreFactory.createFileStoreFactory(exec), getFSLocationSpec(sourceColSpec), destinationFSLocationSpec,
            m_config.getDestinationFileChooserModel().getFileOverwritePolicy());

        try (final FSPathProviderFactory fsSourceProviderFactory =
            FSPathProviderFactory.newFactory(fsConnectionSource, sourceFSLocationSpec);
                final FSPathProviderFactory fsDestProviderFactory =
                    FSPathProviderFactory.newFactory(fsConnectionDestination, destinationFSLocationSpec);
                final CloseableRowIterator rowIterator = inputDataTable.iterator()) {

            final PathRelativizer pathRelativizer =
                new PathRelativizerTableInput(m_config.getSettingsModelIncludeParentFolder().getBooleanValue());

            long rowIdx = 0L;
            final Long noOfFiles = inputDataTable.size();
            Long copiedFiles = 1L;

            while (rowIterator.hasNext()) {
                final DataRow dataRow = rowIterator.next();
                final FSLocationCell sourceCell = (FSLocationCell)dataRow.getCell(sourceColIdx);
                final FSLocationCell destinationCell = (FSLocationCell)dataRow.getCell(destinationColIdx);

                if (sourceCell.isMissing() || destinationCell.isMissing()) {
                    fileCopier.pushEmptyRow(rowIdx);
                    rowIdx++;
                } else {

                    try (final FSPathProvider fsSourcePathProvider =
                        fsSourceProviderFactory.create(sourceCell.getFSLocation());
                            final FSPathProvider fsDestPathProvider =
                                fsDestProviderFactory.create(sourceCell.getFSLocation())) {

                        final FSPath sourcePath = fsSourcePathProvider.getPath();
                        final FSPath destinationDir = fsDestPathProvider.getPath();

                        if (Files.isRegularFile(destinationDir)) {
                            fileCopier.pushEmptyRow(rowIdx);
                            rowIdx++;
                        } else if (FSFiles.exists(sourcePath) && Files.isDirectory(sourcePath)) {

                            rowIdx = copyFolder(sourcePath, destinationDir, pathRelativizer, fileCopier, exec,
                                copiedFiles, noOfFiles, rowIdx);

                            final long progress = copiedFiles;
                            copiedFiles++;
                            setProgress(exec, copiedFiles, noOfFiles, "Copied files / folders : %d", progress);
                        } else {
                            fileCopier.copy(sourcePath, destinationDir.resolve(pathRelativizer.apply(sourcePath)),
                                rowIdx);
                            rowIdx++;
                            final long progress = copiedFiles;
                            copiedFiles++;
                            setProgress(exec, copiedFiles, noOfFiles, "Copied files / folders : %d", progress);
                        }
                    }
                }
            }
        }
    }

    private static long copyFolder(final FSPath sourcePath, final FSPath destinationDir,
        final PathRelativizer pathRelativizer, final FileCopier fileCopier, final ExecutionContext exec,
        final Long copiedFiles, final Long noOfFiles, final long rowIdx)
        throws IOException, CanceledExecutionException {
        final List<FSPath> folderPaths = FSFiles.getFilePathsFromFolder(sourcePath);
        final long noOfFilesInFolder = folderPaths.size();
        long noFilesProcessed = 1;
        long rowIdxTemp = rowIdx;

        for (FSPath file : folderPaths) {
            fileCopier.copy(file, destinationDir.resolve(pathRelativizer.apply(file)), rowIdxTemp);
            rowIdxTemp++;

            final long tempCounter = noFilesProcessed;
            noFilesProcessed++;
            setProgress(exec, copiedFiles, noOfFiles, "Copied %d files of %d", tempCounter, noOfFilesInFolder);
        }

        return rowIdxTemp;

    }

    private static void setProgress(final ExecutionContext exec, final long copiedFiles, final long noOfFiles,
        final String format, final Object... args) throws CanceledExecutionException {
        exec.setProgress(copiedFiles / (double)noOfFiles, () -> (String.format(format, args)));
        exec.checkCanceled();
    }

    private static FSLocationSpec getFSLocationSpec(final DataColumnSpec colSpec) {
        return colSpec.getMetaDataOfType(FSLocationValueMetaData.class).orElseThrow(IllegalStateException::new);
    }

    @Override
    public void pushFlowVariables(final BiConsumer<String, FSLocation> variableConsumer) {
        //TODO do implementation
    }

}
