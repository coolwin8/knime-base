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
package org.knime.filehandling.core.node.table.reader.config;

import static java.util.stream.Collectors.joining;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.knime.core.data.DataTableSpec;
import org.knime.core.data.convert.map.ProducerRegistry;
import org.knime.core.data.convert.map.ProductionPath;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.filehandling.core.node.ImmutableTableTransformation;
import org.knime.filehandling.core.node.table.reader.SourceGroup;
import org.knime.filehandling.core.node.table.reader.SpecMergeMode;
import org.knime.filehandling.core.node.table.reader.selector.ColumnFilterMode;
import org.knime.filehandling.core.node.table.reader.selector.ColumnTransformation;
import org.knime.filehandling.core.node.table.reader.selector.RawSpec;
import org.knime.filehandling.core.node.table.reader.selector.TableTransformation;
import org.knime.filehandling.core.node.table.reader.selector.TableTransformationUtils;
import org.knime.filehandling.core.node.table.reader.spec.ReaderColumnSpec;
import org.knime.filehandling.core.node.table.reader.spec.ReaderTableSpec;
import org.knime.filehandling.core.node.table.reader.spec.TypedReaderTableSpec;
import org.knime.filehandling.core.node.table.reader.util.MultiTableUtils;

/**
 * Configuration storing all the information needed to create a {@link DataTableSpec}.
 *
 * @author Mark Ortmann, KNIME GmbH, Berlin, Germany
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 * @author Tobias Koetter, KNIME GmbH, Konstanz, Germany
 * @noreference non-public API
 * @noinstantiate non-public API
 */
public final class DefaultTableSpecConfig implements TableSpecConfig {

    private final String m_rootItem;

    private final Map<String, ReaderTableSpec<?>> m_individualSpecs;

    private final TableTransformation<?> m_tableTransformation;

    <I> DefaultTableSpecConfig(final String sourceGroupID, final Map<I, ? extends ReaderTableSpec<?>> individualSpecs,
        final TableTransformation<?> tableTransformation) {
        m_rootItem = sourceGroupID;
        m_individualSpecs = individualSpecs.entrySet().stream()//
            .collect(Collectors.toMap(//
                e -> e.getKey().toString()//
                , Map.Entry::getValue//
                , (x, y) -> y//
                , LinkedHashMap::new));
        m_tableTransformation = new ImmutableTableTransformation<>(tableTransformation);
    }

    DefaultTableSpecConfig(final String sourceGroupID, final String[] items, final ReaderTableSpec<?>[] individualSpecs,
        final ImmutableTableTransformation<?> tableTransformation) {
        m_rootItem = sourceGroupID;
        m_individualSpecs = createIndividualSpecsMap(items, individualSpecs);
        m_tableTransformation = tableTransformation;
    }

    private static LinkedHashMap<String, ReaderTableSpec<?>> createIndividualSpecsMap(final String[] items,
        final ReaderTableSpec<?>[] individualSpecs) {
        final LinkedHashMap<String, ReaderTableSpec<?>> map = new LinkedHashMap<>();
        for (int i = 0; i < items.length; i++) {
            map.put(items[i], individualSpecs[i]);
        }
        return map;
    }

    /**
     * Creates a {@link DefaultTableSpecConfig} that corresponds to the provided parameters.
     *
     * @param <T> the type used to identify external types
     * @param rootItem the root item
     * @param individualSpecs a map from the path/file to its individual {@link ReaderTableSpec}
     * @param tableTransformation defines the transformation (type-mapping, filtering, renaming and reordering) of the
     *            output spec
     * @return a {@link DefaultTableSpecConfig} for the provided parameters
     */
    public static <I, T> TableSpecConfig createFromTransformationModel(final String rootItem,
        final Map<I, ? extends ReaderTableSpec<?>> individualSpecs, final TableTransformation<T> tableTransformation) {
        return new DefaultTableSpecConfig(rootItem, individualSpecs, tableTransformation);
    }

    private static <T extends ReaderColumnSpec> Set<String> extractNameSet(final ReaderTableSpec<T> spec) {
        return spec.stream()//
            .map(MultiTableUtils::getNameAfterInit)//
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Returns the raw {@link TypedReaderTableSpec} before type mapping, filtering, reordering or renaming.
     *
     * @param <T> the type used to identify external types
     * @return the raw spec
     */
    @Override
    public <T> RawSpec<T> getRawSpec() {
        return (RawSpec<T>)m_tableTransformation.getRawSpec();
    }

    /**
     * Checks that this configuration can be loaded from the provided settings.
     *
     * @param settings to validate
     * @param pathLoader the {@link ProductionPathLoader}
     * @throws InvalidSettingsException if the settings are invalid
     */
    public static void validate(final NodeSettingsRO settings, final ProductionPathLoader pathLoader)
        throws InvalidSettingsException {
        new DefaultTableSpecConfigSerializer(pathLoader, null).validate(settings);
    }

    /**
     * Checks that this configuration can be loaded from the provided settings.
     *
     * @param settings to validate
     * @param registry the {@link ProducerRegistry} used to restore the {@link ProductionPath ProductionPaths}
     * @throws InvalidSettingsException if the settings are invalid
     */
    public static void validate(final NodeSettingsRO settings, final ProducerRegistry<?, ?> registry)
        throws InvalidSettingsException {
        new DefaultTableSpecConfigSerializer(registry, null).validate(settings);
    }

    @Override
    public <T> TableTransformation<T> getTransformationModel() {
        return (TableTransformation<T>)m_tableTransformation;
    }


    @Override
    public boolean isConfiguredWith(final SourceGroup<String> sourceGroup) {
        return isConfiguredWith(sourceGroup.getID()) && m_individualSpecs.size() == sourceGroup.size() //
            && sourceGroup.stream()//
                .allMatch(m_individualSpecs::containsKey);
    }

    @Override
    public boolean isConfiguredWith(final String rootItem) {
        return m_rootItem.equals(rootItem);
    }

    @Override
    public DataTableSpec getDataTableSpec() {
        return TableTransformationUtils.toDataTableSpec(getTransformationModel());
    }

    @Override
    public List<String> getItems() {
        return Collections.unmodifiableList(new ArrayList<>(m_individualSpecs.keySet()));
    }

    @Override
    public ReaderTableSpec<?> getSpec(final String item) {
        return m_individualSpecs.get(item);
    }

    @Override
    public ProductionPath[] getProductionPaths() {
        return m_tableTransformation.stream().filter(ColumnTransformation::keep).sorted()
            .map(ColumnTransformation::getProductionPath).toArray(ProductionPath[]::new);
    }

    @Override
    public ColumnFilterMode getColumnFilterMode() {
        return m_tableTransformation.getColumnFilterMode();
        //        return m_columnFilterMode;
    }

    @Override
    public void save(final NodeSettingsWO settings) {
        DefaultTableSpecConfigSerializer.save(this, settings);
    }

    /**
     * De-serializes the {@link DefaultTableSpecConfig} previously written to the given settings.
     *
     * @param settings containing the serialized {@link DefaultTableSpecConfig}
     * @param pathLoader the {@link ProductionPathLoader}
     * @param mostGenericExternalType used as default type for columns that were previously (4.2) filtered out
     * @param specMergeModeOld for workflows stored with 4.2, should be {@code null} for workflows stored with 4.3 and
     *            later
     * @return the de-serialized {@link DefaultTableSpecConfig}
     * @throws InvalidSettingsException - if the settings do not exists / cannot be loaded
     */
    public static DefaultTableSpecConfig load(final Object mostGenericExternalType, final NodeSettingsRO settings,
        final ProductionPathLoader pathLoader, @SuppressWarnings("deprecation") final SpecMergeMode specMergeModeOld)
        throws InvalidSettingsException {
        return new DefaultTableSpecConfigSerializer(pathLoader, mostGenericExternalType).load(settings,
            specMergeModeOld);
    }

    /**
     * De-serializes the {@link DefaultTableSpecConfig} previously written to the given settings.
     *
     * @param settings containing the serialized {@link DefaultTableSpecConfig}
     * @param registry the {@link ProducerRegistry} for restoring {@link ProductionPath ProductionPaths}
     * @param mostGenericExternalType used as default type for columns that were previously (4.2) filtered out
     * @param specMergeModeOld for workflows stored with 4.2, should be {@code null} for workflows stored with 4.3 and
     *            later
     * @return the de-serialized {@link DefaultTableSpecConfig}
     * @throws InvalidSettingsException - if the settings do not exists / cannot be loaded
     */
    public static DefaultTableSpecConfig load(final NodeSettingsRO settings, final ProducerRegistry<?, ?> registry,
        final Object mostGenericExternalType, @SuppressWarnings("deprecation") final SpecMergeMode specMergeModeOld)
        throws InvalidSettingsException {
        return new DefaultTableSpecConfigSerializer(registry, mostGenericExternalType).load(settings, specMergeModeOld);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
//        result = prime * result + ((m_dataTableSpec == null) ? 0 : m_dataTableSpec.hashCode());
        result = prime * result + ((m_individualSpecs == null) ? 0 : m_individualSpecs.hashCode());
//        result = prime * result + Arrays.hashCode(m_prodPaths);
        result = prime * result + ((m_rootItem == null) ? 0 : m_rootItem.hashCode());
//        result = prime * result + Arrays.hashCode(m_originalNames);
//        result = prime * result + Arrays.hashCode(m_positions);
//        result = prime * result + Arrays.hashCode(m_keep);
//        result = prime * result + Integer.hashCode(m_unknownColPosition);
//        result = prime * result + Boolean.hashCode(m_includeUnknownColumns);
//        result = prime * result + m_columnFilterMode.hashCode();
        // TODO make sure the TableTransformation has a meaningful hashCode and equals implementation (maybe always create ImmutableTableTransformation?)
        result = prime * result + ((m_tableTransformation == null) ? 0 : m_tableTransformation.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj != null && getClass() == obj.getClass()) {
            DefaultTableSpecConfig other = (DefaultTableSpecConfig)obj;
            return  m_rootItem.equals(other.m_rootItem)//
                    && m_tableTransformation.equals(other.m_tableTransformation)
//                    m_includeUnknownColumns == other.m_includeUnknownColumns//
//                && m_unknownColPosition == other.m_unknownColPosition//
//                && m_columnFilterMode == other.m_columnFilterMode //
//                && m_dataTableSpec.equals(other.m_dataTableSpec)//
                && m_individualSpecs.equals(other.m_individualSpecs);
//                && Arrays.equals(m_prodPaths, other.m_prodPaths)//
//                && Arrays.equals(m_originalNames, other.m_originalNames)//
//                && Arrays.equals(m_positions, other.m_positions)//
//                && Arrays.equals(m_keep, other.m_keep);
        }
        return false;
    }

    @Override
    public String toString() {
        return new StringBuilder("[")//
            .append("Root item: ")//
            .append(m_rootItem)//
//            .append("\n DataTableSpec: ")//
//            .append(m_dataTableSpec)//
            .append("\nIndividual specs: ")//
            .append(m_individualSpecs.entrySet().stream()//
                .map(e -> e.getKey() + ": " + e.getValue())//
                .collect(joining(", ", "[", "]")))//
            .append("\nTableTransformation: ")
            .append(m_tableTransformation)
//            .append("\n ProductionPaths: ")//
//            .append(Arrays.stream(m_prodPaths)//
//                .map(ProductionPath::toString)//
//                .collect(joining(", ", "[", "]")))//
//            .append("\n OriginalNames: ")//
//            .append(Arrays.stream(m_originalNames)//
//                .collect(joining(", ", "[", "]")))//
//            .append("\n Positions: ")//
//            .append(Arrays.stream(m_positions)//
//                .mapToObj(Integer::toString)//
//                .collect(joining(", ", "[", "]")))//
//            .append("\n Keep: ")//
//            .append(Arrays.toString(m_keep))//
//            .append("\n Keep unknown: ")//
//            .append(m_includeUnknownColumns)//
//            .append("\n Position for unknown columns: ")//
//            .append(m_unknownColPosition)//
//            .append("\n ColumnFilterMode: ")//
//            .append(m_columnFilterMode)//
            .append("]\n").toString();
    }

    // Getters for DefaultTableSpecConfigSerializer

    String getRootItem() {
        return m_rootItem;
    }

    Collection<ReaderTableSpec<?>> getIndividualSpecs() {
        return m_individualSpecs.values();
    }

}