/*
 * -------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright, 2003 - 2008
 * University of Konstanz, Germany
 * Chair for Bioinformatics and Information Mining (Prof. M. Berthold)
 * and KNIME GmbH, Konstanz, Germany
 *
 * You may not modify, publish, transmit, transfer or sell, reproduce,
 * create derivative works from, distribute, perform, display, or in
 * any way exploit any of the content, in whole or in part, except as
 * otherwise expressly permitted in writing by the copyright owner or
 * as specified in the license file distributed with this product.
 *
 * If you have any questions please contact the copyright holder:
 * website: www.knime.org
 * email: contact@knime.org
 * -------------------------------------------------------------------
 * 
 */
package org.knime.base.node.io.database;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.knime.base.node.io.database.DBConnectionDialogPanel.DBTableOptions;
import org.knime.core.data.DataTableSpec;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.NodeModel;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettings;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelIntegerBounded;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.knime.core.node.port.database.DatabasePortObject;
import org.knime.core.node.port.database.DatabasePortObjectSpec;

/**
 * 
 * @author Thomas Gabriel, University of Konstanz
 */
final class DBReaderConnectionNodeModel extends NodeModel {
    
    // private final DBQueryConnection m_conn;
    
    private final SettingsModelString m_tableOption =
        DBConnectionDialogPanel.createTableModel();

    private final SettingsModelIntegerBounded m_cachedRows =
        DBConnectionDialogPanel.createCachedRowsModel();
    
    private String m_tableId;
    
    private DBReaderConnection m_load; 
    
    /**
     * Creates a new database reader.
     */
    DBReaderConnectionNodeModel() {
        super(new PortType[0], new PortType[]{DatabasePortObject.TYPE});
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        m_load.getQueryConnection().saveConnection(settings);
        m_tableId = "table_" + System.identityHashCode(this);
        m_tableOption.saveSettingsTo(settings);
        m_cachedRows.saveSettingsTo(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        m_load.getQueryConnection().loadValidatedConnection(settings);
        m_tableOption.validateSettings(settings);
        m_cachedRows.validateSettings(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        m_load.getQueryConnection().loadValidatedConnection(settings);
        m_tableOption.loadSettingsFrom(settings);
        m_cachedRows.loadSettingsFrom(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected PortObject[] execute(final PortObject[] inData,
            final ExecutionContext exec) 
            throws CanceledExecutionException, Exception {
        DBQueryConnection conn = m_load.getQueryConnection();
        if (DBTableOptions.CREATE_TABLE.getActionCommand().equals(
                m_tableOption.getStringValue())) {
            conn.execute("CREATE TABLE " + m_tableId + " AS " 
                    + conn.getQuery());
            conn = new DBQueryConnection(conn, "SELECT * FROM " + m_tableId);
            m_load = new DBReaderConnection(conn);
        }
        DataTableSpec spec = m_load.getDataTableSpec();
        DatabasePortObject dbObj = new DatabasePortObject(
                new DatabasePortObjectSpec(spec, conn.createConnectionModel()));
        return new PortObject[]{dbObj};
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected void reset() {
        try {
            m_load.getQueryConnection().execute("DROP TABLE " + m_tableId);
        } catch (Exception e) {
            super.setWarningMessage("Can't drop table with id \"" 
                    + m_tableId + "\", reason: " + e.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadInternals(final File nodeInternDir,
            final ExecutionMonitor exec) throws IOException {
        NodeSettingsRO sett = NodeSettings.loadFromXML(new FileInputStream(
                    new File(nodeInternDir, DBQueryNodeModel.CFG_TABLE_ID)));
        m_tableId = sett.getString(DBQueryNodeModel.CFG_TABLE_ID, m_tableId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveInternals(final File nodeInternDir,
            final ExecutionMonitor exec) throws IOException {
        NodeSettings sett = new NodeSettings(DBQueryNodeModel.CFG_TABLE_ID);
        sett.addString(DBQueryNodeModel.CFG_TABLE_ID, m_tableId);
        sett.saveToXML(new FileOutputStream(
                new File(nodeInternDir, DBQueryNodeModel.CFG_TABLE_ID)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs)
            throws InvalidSettingsException {
        try {
            // try to create database connection
            DBQueryConnection conn = m_load.getQueryConnection();
            DataTableSpec spec = m_load.getDataTableSpec();
            DatabasePortObjectSpec dbSpec = new DatabasePortObjectSpec(spec,
                    conn.createConnectionModel());
            return new PortObjectSpec[]{dbSpec};
        } catch (Throwable t) {
            throw new InvalidSettingsException(t);
        }
    }

}
