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
 *   Mar 5, 2020 (Simon Schmid, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.base.node.io.filehandling.util.copymovefiles;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Iterator;

import org.apache.commons.lang3.tuple.Pair;
import org.knime.core.data.DataTableSpec;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.context.ports.PortsConfiguration;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.filehandling.core.connections.FSLocation;
import org.knime.filehandling.core.connections.FSPath;
import org.knime.filehandling.core.data.location.variable.FSLocationVariableType;
import org.knime.filehandling.core.defaultnodesettings.status.NodeModelStatusConsumer;
import org.knime.filehandling.core.defaultnodesettings.status.StatusMessage.MessageType;

/**
 * Node model of the Copy/Move Files node.
 *
 * @author Lars Schweikardt, KNIME GmbH, Konstanz, Germany
 */
final class CopyMoveFilesNodeModel extends NodeModel {

    /** The name of the optional source connection input port group. */
    static final String CONNECTION_SOURCE_PORT_GRP_NAME = "Source File System Connection";

    /** The name of the optional destination connection input port group. */
    static final String CONNECTION_DESTINATION_PORT_GRP_NAME = "Destination File System Connection";

    //TODO comes with AP-14932.
    /** The name of the optional table input port group. */
    static final String TABLE_PORT_GRP_NAME = "Path Table Input Port";

    private final CopyMoveFilesNodeConfig m_config;

    private CopyExecutionFactory m_copyExecutionFactory;

    private final NodeModelStatusConsumer m_statusConsumer =
        new NodeModelStatusConsumer(EnumSet.of(MessageType.ERROR, MessageType.WARNING));

    /**
     * Constructor.
     *
     * @param portsConfig the {@link PortsConfiguration}
     * @param config the {@link CopyMoveFilesNodeConfig}
     * @param copyExecutionFactory the {@link CopyExecutionFactory} depending on the {@link PortsConfiguration}
     */
    CopyMoveFilesNodeModel(final PortsConfiguration portsConfig, final CopyMoveFilesNodeConfig config,
        final CopyExecutionFactory copyExecutionFactory) {
        super(portsConfig.getInputPorts(), portsConfig.getOutputPorts());
        m_config = config;
        m_copyExecutionFactory = copyExecutionFactory;
    }

    private CloseableIterator getIterator(final PortObject[] inObjects) throws InvalidSettingsException {
        return new TablePathIterator((BufferedDataTable)inObjects[0], "Path", m_config, m_statusConsumer);
    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        //TODO?
        m_config.getSourceFileChooserModel().configureInModel(inSpecs, m_statusConsumer);
        m_config.getDestinationFileChooserModel().configureInModel(inSpecs, m_statusConsumer);
        m_statusConsumer.setWarningsIfRequired(this::setWarningMessage);

        final DataTableSpec outputSpec = m_copyExecutionFactory.createSpec(inSpecs, m_config);

        return new PortObjectSpec[]{outputSpec};
    }

    /**
     * Creates the {@link FSLocationVariableType#INSTANCE} flow variables.
     *
     * @param name of the flow variable
     * @param location the {@link FSLocation} of the flow variable
     */
    private void pushLocationVar(final String name, final FSLocation location) {
        pushFlowVariable(name, FSLocationVariableType.INSTANCE, location);
    }

    @Override
    protected PortObject[] execute(final PortObject[] inObjects, final ExecutionContext exec) throws Exception {
        final PortObjectSpec[] inSpecs = Arrays.stream(inObjects)//
            .map(PortObject::getSpec)//
            .toArray(PortObjectSpec[]::new);

//        final BufferedDataTable inputDataTable = (BufferedDataTable)inObjects[0];

        //TODO hier dann entsprechende Methode der entsprechenden RowIterator zurückliefert
        try (final CloseableIterator iterator = getIterator(inObjects)) {
            while (iterator.hasNext()) {
                final Iterator<Pair<FSPath, FSPath>> listIterator = iterator.next().iterator();
                while (listIterator.hasNext()) {
                    final Pair<FSPath, FSPath> pair = listIterator.next();
                    //TODO hier kopieren und status thingy
                }
            }
        }

        final DataTableSpec outputSpec = m_copyExecutionFactory.createSpec(inSpecs, m_config);
        final BufferedDataContainer container = exec.createDataContainer(outputSpec);
        //        final CopyExecution execution = m_copyExecutionFactory.createExecution(inObjects, m_config);

        //        execution.fillRowOutput(container::addRowToTable, m_statusConsumer, exec);
        //        execution.pushFlowVariables(this::pushLocationVar);
        //        m_statusConsumer.setWarningsIfRequired(this::setWarningMessage);

        container.close();
        return new PortObject[]{container.getTable()};
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

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        m_config.saveConfigurationForModel(settings);
    }

    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_config.validateConfigurationForModel(settings);
    }

    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_config.loadConfigurationForModel(settings);
    }

    @Override
    protected void reset() {
        // nothing to do
    }

}
