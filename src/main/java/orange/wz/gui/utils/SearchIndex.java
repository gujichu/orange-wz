package orange.wz.gui.utils;

import orange.wz.gui.component.form.data.SearchFormData;
import orange.wz.gui.component.form.data.SearchResult;
import orange.wz.provider.WzObject;
import orange.wz.provider.properties.WzStringProperty;
import orange.wz.provider.properties.WzUOLProperty;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

public final class SearchIndex {
    private final ConcurrentHashMap<DefaultMutableTreeNode, SearchResult> entries = new ConcurrentHashMap<>();

    public void addSubtree(DefaultMutableTreeNode node) {
        if (node == null) {
            return;
        }
        Enumeration<?> enumeration = node.preorderEnumeration();
        while (enumeration.hasMoreElements()) {
            Object item = enumeration.nextElement();
            if (item instanceof DefaultMutableTreeNode treeNode) {
                addNode(treeNode);
            }
        }
    }

    public void addNode(DefaultMutableTreeNode node) {
        if (node == null || !(node.getUserObject() instanceof WzObject wzObject)) {
            return;
        }
        String name = wzObject.getName();
        if ("List.wz".equals(name)) {
            return;
        }
        entries.put(node, new SearchResult(name, resolveValue(wzObject), TreeNodeUtil.getNodePathWithoutRoot(node)));
    }

    public void removeSubtree(DefaultMutableTreeNode node) {
        if (node == null) {
            return;
        }
        Enumeration<?> enumeration = node.preorderEnumeration();
        while (enumeration.hasMoreElements()) {
            Object item = enumeration.nextElement();
            if (item instanceof DefaultMutableTreeNode treeNode) {
                entries.remove(treeNode);
            }
        }
    }

    public void clear() {
        entries.clear();
    }

    public List<SearchResult> search(SearchFormData option, List<DefaultMutableTreeNode> scopeRoots, BooleanSupplier cancelled) {
        if (option == null || scopeRoots == null || scopeRoots.isEmpty()) {
            return Collections.emptyList();
        }
        Set<DefaultMutableTreeNode> scopeSet = new HashSet<>(scopeRoots);
        Stream<SearchResult> stream = entries.entrySet()
                .parallelStream()
                .filter(entry -> !cancelled.getAsBoolean())
                .filter(entry -> isInScope(entry.getKey(), scopeSet))
                .map(java.util.Map.Entry::getValue)
                .filter(result -> SearchMatcher.matches(
                        result,
                        option.search(),
                        option.nameMod(),
                        option.valueMod(),
                        option.equalMod(),
                        option.lowMod()
                ));
        if (cancelled.getAsBoolean()) {
            return Collections.emptyList();
        }
        return stream.toList();
    }

    public List<SearchResult> snapshot() {
        return new ArrayList<>(entries.values());
    }

    private boolean isInScope(DefaultMutableTreeNode node, Set<DefaultMutableTreeNode> scopeSet) {
        DefaultMutableTreeNode cursor = node;
        while (cursor != null) {
            if (scopeSet.contains(cursor)) {
                return true;
            }
            cursor = (DefaultMutableTreeNode) cursor.getParent();
        }
        return false;
    }

    private static String resolveValue(WzObject wzObject) {
        if (wzObject instanceof WzStringProperty property) {
            return property.getValue();
        }
        if (wzObject instanceof WzUOLProperty property) {
            return property.getValue();
        }
        return null;
    }
}