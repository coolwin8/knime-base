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
 *   Sep 16, 2020 (Mark Ortmann, KNIME GmbH, Berlin, Germany): created
 */
package org.knime.base.node.flowvariable.converter.variabletocell;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.knime.core.data.collection.ListCell.getCollectionType;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.junit.Test;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.collection.CollectionCellFactory;
import org.knime.core.data.def.BooleanCell;
import org.knime.core.data.def.BooleanCell.BooleanCellFactory;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.data.def.DoubleCell.DoubleCellFactory;
import org.knime.core.data.def.IntCell;
import org.knime.core.data.def.IntCell.IntCellFactory;
import org.knime.core.data.def.LongCell;
import org.knime.core.data.def.LongCell.LongCellFactory;
import org.knime.core.data.def.StringCell;
import org.knime.core.node.workflow.FlowVariable;
import org.knime.core.node.workflow.VariableType.BooleanArrayType;
import org.knime.core.node.workflow.VariableType.BooleanType;
import org.knime.core.node.workflow.VariableType.DoubleArrayType;
import org.knime.core.node.workflow.VariableType.IntArrayType;
import org.knime.core.node.workflow.VariableType.LongArrayType;
import org.knime.core.node.workflow.VariableType.LongType;
import org.knime.core.node.workflow.VariableType.StringArrayType;
import org.knime.filehandling.core.connections.FSCategory;
import org.knime.filehandling.core.connections.FSLocation;
import org.knime.filehandling.core.connections.FSLocationSpec;
import org.knime.filehandling.core.data.location.FSLocationValueMetaData;
import org.knime.filehandling.core.data.location.cell.FSLocationCell;
import org.knime.filehandling.core.data.location.cell.FSLocationCellFactory;
import org.knime.filehandling.core.data.location.variable.FSLocationVariableType;

/**
 * Tests for all the available {@link VariableToCellConverter}s.
 *
 * @author Mark Ortmann, KNIME GmbH, Berlin, Germany
 */
public class VariableToCellConverterTest {

    /**
     * Tests the correctness of the int flow var converter.
     */
    @Test
    public void testIntFlowVarConverter() {
        final int val = 4;
        final String name = "int_var";
        final FlowVariable var = new FlowVariable(name, val);
        try (final VariableToCellConverter converter = VariableToCellConverterFactory.createConverter(var)) {
            DataColumnSpec createSpec = converter.createSpec(name, var);
            assertEquals(IntCell.TYPE, createSpec.getType());

            assertEquals(IntCellFactory.create(val), converter.getDataCell(null, var));
        }
    }

    /**
     * Tests the correctness of the long flow var converter.
     */
    @Test
    public void testLongFlowVarConverter() {
        final long val = 1232;
        final String name = "long_var";
        final FlowVariable var = new FlowVariable(name, LongType.INSTANCE, val);
        try (final VariableToCellConverter converter = VariableToCellConverterFactory.createConverter(var)) {
            DataColumnSpec createSpec = converter.createSpec(name, var);
            assertEquals(LongCell.TYPE, createSpec.getType());

            assertEquals(LongCellFactory.create(val), converter.getDataCell(null, var));
        }
    }

    /**
     * Tests the correctness of the double flow var converter.
     */
    @Test
    public void testDoubleFlowVarConverter() {
        final double val = -124.39239;
        final String name = "double_var";
        final FlowVariable var = new FlowVariable(name, val);
        try (final VariableToCellConverter converter = VariableToCellConverterFactory.createConverter(var)) {
            DataColumnSpec createSpec = converter.createSpec(name, var);
            assertEquals(DoubleCell.TYPE, createSpec.getType());

            assertEquals(DoubleCellFactory.create(val), converter.getDataCell(null, var));
        }
    }

    /**
     * Tests the correctness of the String flow var converter.
     */
    @Test
    public void testStringFlowVarConverter() {
        final String val = "dummy";
        final String name = "string_var";
        final FlowVariable var = new FlowVariable(name, val);
        try (final VariableToCellConverter converter = VariableToCellConverterFactory.createConverter(var)) {
            DataColumnSpec createSpec = converter.createSpec(name, var);
            assertEquals(StringCell.TYPE, createSpec.getType());

            assertEquals(new StringCell(val), converter.getDataCell(null, var));
        }
    }

    /**
     * Tests the correctness of the boolean flow var converter.
     */
    @Test
    public void testBooleanFlowVarConverter() {
        final boolean val = true;
        final String name = "boolean_var";
        final FlowVariable var = new FlowVariable(name, BooleanType.INSTANCE, val);
        try (final VariableToCellConverter converter = VariableToCellConverterFactory.createConverter(var)) {
            DataColumnSpec createSpec = converter.createSpec(name, var);
            assertEquals(BooleanCell.TYPE, createSpec.getType());

            assertEquals(BooleanCellFactory.create(val), converter.getDataCell(null, var));
        }
    }

    /**
     * Tests the correctness of the FSLocation flow var converter.
     */
    @Test
    public void testFSLocationFlowVarConverter() {
        final FSLocation val = new FSLocation(FSCategory.CONNECTED, "dummy-specifier", "not-a-path");
        final String name = "fsLocation_var";
        final FlowVariable var = new FlowVariable(name, FSLocationVariableType.INSTANCE, val);
        try (final VariableToCellConverter converter = VariableToCellConverterFactory.createConverter(var)) {
            DataColumnSpec createSpec = converter.createSpec(name, var);
            assertEquals(FSLocationCellFactory.TYPE, createSpec.getType());
            final FSLocationValueMetaData metaData = createSpec.getMetaDataOfType(FSLocationValueMetaData.class)
                .orElseThrow(() -> new IllegalStateException("No meta data available"));
            assertTrue(FSLocationSpec.areEqual(metaData, val));

            final FSLocationCell cell = (FSLocationCell)converter.getDataCell(null, var);
            assertEquals(val, cell.getFSLocation());
        }
    }

    /**
     * Tests the correctness of the int array flow var converter.
     */
    @Test
    public void testIntArrFlowVarConverter() {
        final Integer[] val = new Integer[]{1, 589, -2};
        final String name = "int_arr_var";
        final FlowVariable var = new FlowVariable(name, IntArrayType.INSTANCE, val);
        try (final VariableToCellConverter converter = VariableToCellConverterFactory.createConverter(var)) {
            DataColumnSpec createSpec = converter.createSpec(name, var);
            assertEquals(getCollectionType(IntCell.TYPE), createSpec.getType());

            final DataCell c = CollectionCellFactory.createListCell(Arrays.stream(val)//
                .map(IntCellFactory::create)//
                .collect(Collectors.toList()));
            assertEquals(c, converter.getDataCell(null, var));
        }
    }

    /**
     * Tests the correctness of the long array flow var converter.
     */
    @Test
    public void testLongArrayFlowVarConverter() {
        final Long[] val = new Long[]{2930L, 2392932L, -9319L};
        final String name = "long_arr_var";
        final FlowVariable var = new FlowVariable(name, LongArrayType.INSTANCE, val);
        try (final VariableToCellConverter converter = VariableToCellConverterFactory.createConverter(var)) {
            DataColumnSpec createSpec = converter.createSpec(name, var);
            assertEquals(getCollectionType(LongCell.TYPE), createSpec.getType());

            final DataCell c = CollectionCellFactory.createListCell(Arrays.stream(val)//
                .map(LongCellFactory::create)//
                .collect(Collectors.toList()));
            assertEquals(c, converter.getDataCell(null, var));
        }
    }

    /**
     * Tests the correctness of the double array flow var converter.
     */
    @Test
    public void testDoubleArrFlowVarConverter() {
        final Double[] val = new Double[]{2432.4399, 29.3, -124.39239};
        final String name = "double_arr_var";
        final FlowVariable var = new FlowVariable(name, DoubleArrayType.INSTANCE, val);
        try (final VariableToCellConverter converter = VariableToCellConverterFactory.createConverter(var)) {
            DataColumnSpec createSpec = converter.createSpec(name, var);
            assertEquals(getCollectionType(DoubleCell.TYPE), createSpec.getType());

            final DataCell c = CollectionCellFactory.createListCell(Arrays.stream(val)//
                .map(DoubleCellFactory::create)//
                .collect(Collectors.toList()));
            assertEquals(c, converter.getDataCell(null, var));
        }
    }

    /**
     * Tests the correctness of the String array flow var converter.
     */
    @Test
    public void testStringArrFlowVarConverter() {
        final String[] val = new String[]{"dummy", "foo", "bar"};
        final String name = "string_arr_var";
        final FlowVariable var = new FlowVariable(name, StringArrayType.INSTANCE, val);
        try (final VariableToCellConverter converter = VariableToCellConverterFactory.createConverter(var)) {
            DataColumnSpec createSpec = converter.createSpec(name, var);
            assertEquals(getCollectionType(StringCell.TYPE), createSpec.getType());

            final DataCell c = CollectionCellFactory.createListCell(Arrays.stream(val)//
                .map(StringCell::new)//
                .collect(Collectors.toList()));
            assertEquals(c, converter.getDataCell(null, var));
        }
    }

    /**
     * Tests the correctness of the boolean array flow var converter.
     */
    @Test
    public void testBooleanArrFlowVarConverter() {
        final Boolean[] val = new Boolean[]{true, false, true};
        final String name = "boolean_arr_var";
        final FlowVariable var = new FlowVariable(name, BooleanArrayType.INSTANCE, val);
        try (final VariableToCellConverter converter = VariableToCellConverterFactory.createConverter(var)) {
            DataColumnSpec createSpec = converter.createSpec(name, var);
            assertEquals(getCollectionType(BooleanCell.TYPE), createSpec.getType());

            final DataCell c = CollectionCellFactory.createListCell(Arrays.stream(val)//
                .map(BooleanCellFactory::create)//
                .collect(Collectors.toList()));
            assertEquals(c, converter.getDataCell(null, var));
        }
    }

}
