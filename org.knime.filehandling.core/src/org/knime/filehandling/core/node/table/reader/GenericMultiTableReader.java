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
 *   Nov 13, 2020 (Tobias): created
 */
package org.knime.filehandling.core.node.table.reader;

import java.io.IOException;
import java.util.List;

import org.knime.core.data.DataTableSpec;
import org.knime.core.data.filestore.FileStoreFactory;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.streamable.BufferedDataTableRowOutput;
import org.knime.core.node.streamable.RowOutput;
import org.knime.filehandling.core.node.table.reader.config.GenericMultiTableReadConfig;
import org.knime.filehandling.core.node.table.reader.config.MultiTableReadConfig;
import org.knime.filehandling.core.node.table.reader.config.ReaderSpecificConfig;
import org.knime.filehandling.core.node.table.reader.util.GenericMultiTableRead;
import org.knime.filehandling.core.node.table.reader.util.GenericStagedMultiTableRead;

/**
 * Uses a {@link TableReader} to read tables from multiple items, combines them according to the user settings and
 * performs type mapping.
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 * @author Simon Schmid, KNIME GmbH, Konstanz, Germany
 * @author Tobias Koetter, KNIME GmbH, Konstanz, Germany
 * @param <I> the type of the item to read from
 * @param <C> the type of the {@link ReaderSpecificConfig}
 * @noreference non-public API
 * @noinstantiate non-public API
 * @noextend non-public API
 */
public class GenericMultiTableReader<I, C extends ReaderSpecificConfig<C>> {

    private final GenericMultiTableReadFactory<I, C, ?> m_multiTableReadFactory;

    private GenericStagedMultiTableRead<I, ?> m_currentMultiRead;

    private static <I> void fillRowOutput(final GenericMultiTableRead<I> multiTableRead, final RowOutput output,
        final ExecutionContext exec) throws Exception {
        final FileStoreFactory fsFactory = FileStoreFactory.createFileStoreFactory(exec);
        multiTableRead.fillRowOutput(output, exec, fsFactory);
    }

    /**
     * Constructor.
     *
     * @param multiTableReadFactory for creating MultiTableRead objects
     */
    public GenericMultiTableReader(final GenericMultiTableReadFactory<I, C, ?> multiTableReadFactory) {
        m_multiTableReadFactory = multiTableReadFactory;
    }

    /**
     * Resets the spec read by {@code createSpec}, {@code fillRowOutput} or {@code readTable} i.e. a subsequent call to
     * {@code fillRowOutput} or {@code readTable} will read the spec again.
     */
    public void reset() {
        m_currentMultiRead = null;
    }

    /**
     * Creates the {@link DataTableSpec} corresponding to the tables stored in <b>items</b> combined according to the
     * provided {@link MultiTableReadConfig config}.
     *
     * @param rootItem the root item
     * @param items to read from
     * @param config for reading
     * @return the {@link DataTableSpec} of the merged table consisting of the tables stored in <b>items</b>
     * @throws IOException if reading the specs from item fails
     */
    public DataTableSpec createTableSpec(final String rootItem, final List<I> items,
        final GenericMultiTableReadConfig<I, C> config) throws IOException {
        GenericStagedMultiTableRead<I, ?> stagedMultiRead =
            createMultiRead(rootItem, items, config, new ExecutionMonitor());
        GenericMultiTableRead<I> multiRead = stagedMultiRead.withoutTransformation(items);
        return multiRead.getOutputSpec();
    }

    private GenericStagedMultiTableRead<I, ?> createMultiRead(final String rootItem, final List<I> items,
        final GenericMultiTableReadConfig<I, C> config, final ExecutionMonitor exec) throws IOException {
        if (config.isConfiguredWith(rootItem, items)) {
            m_currentMultiRead = m_multiTableReadFactory.createFromConfig(rootItem, items, config);
        } else {
            m_currentMultiRead = m_multiTableReadFactory.create(rootItem, items, config, exec);
        }
        return m_currentMultiRead;
    }

    /**
     * Reads a table from the provided items according to the provided {@link MultiTableReadConfig config}.
     *
     * @param rootItem the root item
     * @param items to read from
     * @param config for reading
     * @param exec for table creation and reporting progress
     * @return the read table
     * @throws Exception
     */
    public BufferedDataTable readTable(final String rootItem, final List<I> items,
        final GenericMultiTableReadConfig<I, C> config, final ExecutionContext exec) throws Exception {
        exec.setMessage("Creating table spec");
        final boolean specConfigured = config.isConfiguredWith(rootItem, items);
        final GenericStagedMultiTableRead<I, ?> runConfig =
            getMultiRead(rootItem, items, config, exec.createSubExecutionContext(specConfigured ? 0 : 0.5));
        exec.setMessage("Reading table");
        final GenericMultiTableRead<I> multiTableRead = runConfig.withoutTransformation(items);
        final BufferedDataTableRowOutput output =
            new BufferedDataTableRowOutput(exec.createDataContainer(multiTableRead.getOutputSpec()));
        fillRowOutput(multiTableRead, output, exec.createSubExecutionContext(specConfigured ? 1 : 0.5));
        return output.getDataTable();
    }

    /**
     * Fills the {@link RowOutput output} with the tables stored in <b>items</b> using the provided
     * {@link MultiTableReadConfig config}. The {@link ExecutionContext} is required for type mapping and reporting
     * progress. Note: The {@link RowOutput output} must have a {@link DataTableSpec} compatible with the
     * {@link DataTableSpec} returned by createTableSpec(List, MultiTableReadConfig).
     *
     * @param rootItem the root item
     * @param items to read from
     * @param config for reading
     * @param output the {@link RowOutput} to fill
     * @param exec needed by the mapping framework
     * @throws Exception
     */
    public void fillRowOutput(final String rootItem, final List<I> items,
        final GenericMultiTableReadConfig<I, C> config, final RowOutput output, final ExecutionContext exec)
        throws Exception {
        exec.setMessage("Creating table spec");
        final GenericStagedMultiTableRead<I, ?> multiRead = getMultiRead(rootItem, items, config, exec);
        exec.setMessage("Reading table");
        fillRowOutput(multiRead.withoutTransformation(items), output, exec);
    }

    private GenericStagedMultiTableRead<I, ?> getMultiRead(final String rootItem, final List<I> items,
        final GenericMultiTableReadConfig<I, C> config, final ExecutionContext exec) throws IOException {
        if (m_currentMultiRead == null || !m_currentMultiRead.isValidFor(items)) {
            return createMultiRead(rootItem, items, config, exec);
        } else {
            return m_currentMultiRead;
        }
    }

}