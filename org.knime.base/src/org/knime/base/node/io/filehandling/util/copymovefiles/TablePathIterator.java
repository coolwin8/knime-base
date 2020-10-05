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
 *   5 Oct 2020 (lars.schweikardt): created
 */
package org.knime.base.node.io.filehandling.util.copymovefiles;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.apache.commons.lang3.tuple.Pair;
import org.knime.base.node.io.filehandling.util.PathRelativizer;
import org.knime.base.node.io.filehandling.util.PathRelativizerTableInput;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataRow;
import org.knime.core.data.container.CloseableRowIterator;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.InvalidSettingsException;
import org.knime.filehandling.core.connections.FSConnection;
import org.knime.filehandling.core.connections.FSLocation;
import org.knime.filehandling.core.connections.FSLocationSpec;
import org.knime.filehandling.core.connections.FSPath;
import org.knime.filehandling.core.connections.location.FSPathProviderFactory;
import org.knime.filehandling.core.data.location.FSLocationValueMetaData;
import org.knime.filehandling.core.data.location.cell.FSLocationCell;
import org.knime.filehandling.core.defaultnodesettings.filechooser.writer.WritePathAccessor;
import org.knime.filehandling.core.defaultnodesettings.status.StatusMessage;

/**
 *
 * @author lars.schweikardt
 */
final class TablePathIterator implements CloseableIterator {

    private CloseableRowIterator m_rowIterator;

    private final int m_index;

    private final FSPathProviderFactory m_pathProvFactory;

    private final CopyMoveFilesNodeConfig m_config;

    private final Consumer<StatusMessage> m_statusConsumer;

    private final PathRelativizer m_pathRelativizer;

    TablePathIterator(final BufferedDataTable table, final String columnName, final CopyMoveFilesNodeConfig config,
        final Consumer<StatusMessage> statusMessageConsumer) throws InvalidSettingsException {
        m_rowIterator = table.iterator();
        m_index = table.getDataTableSpec().findColumnIndex(columnName);
        //TODO checkout how to do to
        final Optional<FSConnection> fsConnectionSource = Optional.empty();
        m_pathProvFactory = FSPathProviderFactory.newFactory(fsConnectionSource,
            getFSLocationSpec(table.getSpec().getColumnSpec(columnName)));
        m_config = config;
        m_statusConsumer = statusMessageConsumer;
        //TODO do we offer include parent?
        m_pathRelativizer = new PathRelativizerTableInput(false);
    }

    //TODO auslagern somewhere
    private static FSLocationSpec getFSLocationSpec(final DataColumnSpec colSpec) {
        return colSpec.getMetaDataOfType(FSLocationValueMetaData.class).orElseThrow(IllegalStateException::new);
    }

    @Override
    public boolean hasNext() {
        return m_rowIterator.hasNext();
    }

    @Override
    public List<Pair<FSPath, FSPath>> next() {
        final DataRow dataRow = m_rowIterator.next();
        final FSLocation fsLocation = ((FSLocationCell)dataRow.getCell(m_index)).getFSLocation();

        try (final WritePathAccessor writePathAccessor =
            m_config.getDestinationFileChooserModel().createWritePathAccessor()) {
            final FSPath destinationPath = writePathAccessor.getOutputPath(m_statusConsumer);
            try (MyPair myPair = new MyPair(m_pathProvFactory, fsLocation, destinationPath, m_pathRelativizer)) {
                return myPair.getEntries();
            } catch (Exception e) {
                return null;
            }
        } catch (IOException | InvalidSettingsException e1) {
            return null;
        }
    }

    @Override
    public void close() throws IOException {
        m_rowIterator.close();
        m_pathProvFactory.close();

    }
}
