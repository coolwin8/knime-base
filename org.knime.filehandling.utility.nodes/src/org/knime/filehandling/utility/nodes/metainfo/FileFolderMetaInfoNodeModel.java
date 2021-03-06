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
 *   Sep 9, 2020 (Mark Ortmann, KNIME GmbH, Berlin, Germany): created
 */
package org.knime.filehandling.utility.nodes.metainfo;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.MissingCell;
import org.knime.core.data.container.AbstractCellFactory;
import org.knime.core.data.container.ColumnRearranger;
import org.knime.core.data.def.BooleanCell.BooleanCellFactory;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.context.ports.PortsConfiguration;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.util.UniqueNameGenerator;
import org.knime.filehandling.core.connections.FSCategory;
import org.knime.filehandling.core.connections.FSConnection;
import org.knime.filehandling.core.connections.FSFiles;
import org.knime.filehandling.core.connections.FSLocationSpec;
import org.knime.filehandling.core.connections.FSPath;
import org.knime.filehandling.core.connections.location.FSPathProvider;
import org.knime.filehandling.core.connections.location.FSPathProviderFactory;
import org.knime.filehandling.core.data.location.FSLocationValue;
import org.knime.filehandling.core.data.location.FSLocationValueMetaData;
import org.knime.filehandling.core.data.location.cell.FSLocationCell;
import org.knime.filehandling.core.port.FileSystemPortObjectSpec;

/**
 * The node model allowing to extract meta information about files and folders.
 *
 * @author Mark Ortmann, KNIME GmbH, Berlin, Germany
 */
final class FileFolderMetaInfoNodeModel extends NodeModel {

    private static final NodeLogger LOGGER = NodeLogger.getLogger(FileFolderMetaInfoNodeModel.class);

    private final int m_inputFsConnectionIdx;

    private final int m_inputTableIdx;

    private final SettingsModelString m_selectedColumn;

    private final SettingsModelBoolean m_failIfPathNotExists;

    private final SettingsModelBoolean m_calculateOverallFolderSize;

    static SettingsModelString createColumnSettingsModel() {
        return new SettingsModelString("column", null);
    }

    static SettingsModelBoolean createFailIfPathNotExistsSettingsModel() {
        return new SettingsModelBoolean("file_if_path_not_exists", true);
    }

    static SettingsModelBoolean createCalculateOverallFolderSizeSettingsModel() {
        return new SettingsModelBoolean("calculate_overall_folder_size", false);
    }

    FileFolderMetaInfoNodeModel(final PortsConfiguration config) {
        super(config.getInputPorts(), config.getOutputPorts());
        final Map<String, int[]> inputPortLocation = config.getInputPortLocation();
        m_inputFsConnectionIdx =
            Optional.ofNullable(inputPortLocation.get(FileFolderMetaInfoNodeFactory.CONNECTION_INPUT_PORT_GRP_NAME))//
                .map(a -> a[0])//
                .orElse(-1);
        m_inputTableIdx = inputPortLocation.get(FileFolderMetaInfoNodeFactory.DATA_TABLE_INPUT_PORT_GRP_NAME)[0];
        m_selectedColumn = createColumnSettingsModel();
        m_failIfPathNotExists = createFailIfPathNotExistsSettingsModel();
        m_calculateOverallFolderSize = createCalculateOverallFolderSizeSettingsModel();
    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        final DataTableSpec inputTableSpec = ((DataTableSpec)inSpecs[m_inputTableIdx]);

        if (m_selectedColumn.getStringValue() == null) {
            autoGuess(inSpecs);
            setWarningMessage(String.format("Auto-guessed column containing file/folder paths '%s'",
                m_selectedColumn.getStringValue()));
        }
        validateSettings(inSpecs);

        final int pathColIdx = inputTableSpec.findColumnIndex(m_selectedColumn.getStringValue());
        try (final FileAttributesFactory fac = new FileAttributesFactory(createNewColumns(inputTableSpec), pathColIdx,
            extractFSLocation(inSpecs, pathColIdx), getFSConnection(inSpecs).orElse(null), false)) {
            return new PortObjectSpec[]{createColumnRearranger(inputTableSpec, fac).createSpec()};
        }
    }

    @Override
    protected PortObject[] execute(final PortObject[] inObjects, final ExecutionContext exec) throws Exception {
        final PortObjectSpec[] inSpecs = Arrays.stream(inObjects)//
            .map(PortObject::getSpec)//
            .toArray(PortObjectSpec[]::new);

        final DataTableSpec inputTableSpec = (DataTableSpec)inSpecs[m_inputTableIdx];
        final int pathColIdx = inputTableSpec.findColumnIndex(m_selectedColumn.getStringValue());

        final FSLocationSpec fsLocationSpec = extractFSLocation(inSpecs, pathColIdx);

        try (final FileAttributesFactory fac = new FileAttributesFactory(createNewColumns(inputTableSpec), pathColIdx,
            fsLocationSpec, getFSConnection(inSpecs).orElse(null), true)) {
            return new PortObject[]{exec.createColumnRearrangeTable((BufferedDataTable)inObjects[m_inputTableIdx],
                createColumnRearranger(inputTableSpec, fac), exec)};
        }
    }

    private void autoGuess(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        final DataTableSpec inputTableSpec = (DataTableSpec)inSpecs[m_inputTableIdx];
        m_selectedColumn.setStringValue(inputTableSpec.stream()//
            .filter(dcs -> dcs.getType().isCompatible(FSLocationValue.class))//
            .map(DataColumnSpec::getName)//
            .findFirst()//
            .orElseThrow(() -> new InvalidSettingsException("No applicable column available"))//
        );
    }

    private void validateSettings(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        final DataTableSpec inSpec = (DataTableSpec)inSpecs[m_inputTableIdx];
        final String pathColName = m_selectedColumn.getStringValue();
        final int colIndex = inSpec.findColumnIndex(pathColName);

        // check column existence
        CheckUtils.checkSetting(colIndex >= 0, "The selected column '%s' is not part of the input", pathColName);

        // check column type
        final DataColumnSpec pathColSpec = inSpec.getColumnSpec(colIndex);
        if (!pathColSpec.getType().isCompatible(FSLocationValue.class)) {
            throw new InvalidSettingsException(
                String.format("The selected column '%s' has the wrong type", pathColName));
        }

        // check file systems
        final FSLocationValueMetaData metaData =
            pathColSpec.getMetaDataOfType(FSLocationValueMetaData.class).orElseThrow(() -> new IllegalStateException(
                String.format("Path column '%s' without meta data encountered", pathColSpec.getName())));

        if (m_inputFsConnectionIdx >= 0) {
            if (metaData.getFSCategory() != FSCategory.CONNECTED
                || !metaData.getFileSystemSpecifier().equals(((FileSystemPortObjectSpec)inSpecs[m_inputFsConnectionIdx])
                    .getFSLocationSpec().getFileSystemSpecifier())) {
                setWarningMessage(String.format(
                    "The file system at the input port differs from the file system used to create the selected column "
                        + "'%s'. Only the path will be used.",
                    pathColName));
            }
        } else {
            if (metaData.getFSCategory() == FSCategory.CONNECTED) {
                throw new InvalidSettingsException(String.format(
                    "The selected column '%s' references a connected file system. Please add the missing file system"
                        + " connection port",
                    pathColName));
            }
        }
    }

    private static ColumnRearranger createColumnRearranger(final DataTableSpec inputTableSpec,
        final FileAttributesFactory fac) {
        final ColumnRearranger colRearranger = new ColumnRearranger(inputTableSpec);
        colRearranger.append(fac);
        return colRearranger;
    }

    private Optional<FSConnection> getFSConnection(final PortObjectSpec[] inSpecs) {
        return m_inputFsConnectionIdx < 0 //
            ? Optional.empty() //
            : ((FileSystemPortObjectSpec)inSpecs[0]).getFileSystemConnection();
    }

    private FSLocationSpec extractFSLocation(final PortObjectSpec[] inSpecs, final int pathColIdx) {
        if (m_inputFsConnectionIdx < 0) {
            DataColumnSpec pathColSpec = ((DataTableSpec)inSpecs[m_inputTableIdx]).getColumnSpec(pathColIdx);
            return pathColSpec.getMetaDataOfType(FSLocationValueMetaData.class)//
                .orElseThrow(() -> new IllegalStateException(
                    String.format("Path column '%s' without meta data encountered", pathColSpec.getName())));
        } else {
            return ((FileSystemPortObjectSpec)inSpecs[m_inputFsConnectionIdx]).getFSLocationSpec();
        }
    }

    private static DataColumnSpec[] createNewColumns(final DataTableSpec inputTableSpec) {
        final UniqueNameGenerator uniqueNameGen = new UniqueNameGenerator(inputTableSpec);
        return Arrays.stream(KNIMEFileAttributesConverter.values())//
            .map(attr -> uniqueNameGen.newColumn(attr.getName(), attr.getType()))//
            .toArray(DataColumnSpec[]::new);
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        m_selectedColumn.saveSettingsTo(settings);
        m_failIfPathNotExists.saveSettingsTo(settings);
        m_calculateOverallFolderSize.saveSettingsTo(settings);
    }

    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_selectedColumn.validateSettings(settings);
        m_failIfPathNotExists.validateSettings(settings);
        m_calculateOverallFolderSize.validateSettings(settings);
    }

    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_selectedColumn.loadSettingsFrom(settings);
        m_failIfPathNotExists.loadSettingsFrom(settings);
        m_calculateOverallFolderSize.loadSettingsFrom(settings);
    }

    @Override
    protected void reset() {
        // nothing to do

    }

    @Override
    protected void loadInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        // nothing to do
    }

    @Override
    protected void saveInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        // nothing to do
    }

    private final class FileAttributesFactory extends AbstractCellFactory implements Closeable {

        private final int m_colIdx;

        private final FSPathProviderFactory m_providerFactory;

        private FileAttributesFactory(final DataColumnSpec[] colSpecs, final int pathColIdx,
            final FSLocationSpec fsLocationSpec, final FSConnection fsConnection, final boolean initFactory) {
            super(colSpecs);
            m_colIdx = pathColIdx;
            if (initFactory) {
                m_providerFactory = FSPathProviderFactory.newFactory(Optional.ofNullable(fsConnection), fsLocationSpec);
            } else {
                m_providerFactory = null;
            }
        }

        @Override
        public DataCell[] getCells(final DataRow row) {
            final DataCell c = row.getCell(m_colIdx);
            if (c.isMissing()) {
                return createMissingCells(DataType::getMissingCell);
            } else {
                return createCells((FSLocationCell)c);
            }
        }

        private DataCell[] createCells(final FSLocationCell cell) {
            try (final FSPathProvider pathProvder = m_providerFactory.create(cell.getFSLocation())) {
                final FSPath path = pathProvder.getPath();
                if (!FSFiles.exists(path) && !m_failIfPathNotExists.getBooleanValue()) {
                    final DataCell[] cells = createMissingCells(() -> new MissingCell("File/folder does not exist"));
                    cells[KNIMEFileAttributesConverter.EXISTS.getPosition()] = BooleanCellFactory.create(false);
                    return cells;
                } else {
                    return createCells(path);
                }
            } catch (final NoSuchFileException e) { //NOSONAR
                throw new IllegalArgumentException(
                    String.format("The file/folder '%s' does not exist", e.getMessage()));
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
        }

        private DataCell[] createMissingCells(final Supplier<DataCell> supplier) {
            return Stream.generate(supplier)//
                .limit(KNIMEFileAttributesConverter.values().length)//
                .toArray(DataCell[]::new);
        }

        private DataCell[] createCells(final FSPath path) throws IOException {
            final KNIMEFileAttributes attributes = getFileAttributes(path);
            return Arrays.stream(KNIMEFileAttributesConverter.values())//
                .map(a -> a.createCell(attributes))//
                .toArray(DataCell[]::new);
        }

        private KNIMEFileAttributes getFileAttributes(final FSPath path) throws IOException {
            final BasicFileAttributes basicAttributes = Files.readAttributes(path, BasicFileAttributes.class);
            return new KNIMEFileAttributes(path, m_calculateOverallFolderSize.getBooleanValue(), basicAttributes);
        }

        @Override
        public void close() {
            if (m_providerFactory != null) {
                try {
                    m_providerFactory.close();
                } catch (final IOException e) {
                    LOGGER.debug("Unable to close fs path provider factory", e);
                }
            }
        }
    }
}
