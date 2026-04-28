package local.filehider.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class FileHiderConfigurable implements Configurable {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private JBCheckBox enabledCheckBox;
    private RuleTablePanel defaultRulesPanel;
    private RuleTablePanel userRulesPanel;
    private JPanel rootPanel;

    @Override
    public @Nls String getDisplayName() {
        return "File Hider";
    }

    @Override
    public @Nullable JComponent createComponent() {
        enabledCheckBox = new JBCheckBox("Enable File Hider");
        defaultRulesPanel = new RuleTablePanel(true);
        userRulesPanel = new RuleTablePanel(false);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Default rules", defaultRulesPanel);
        tabs.addTab("User rules", userRulesPanel);

        JBLabel note = new JBLabel("Rules are exact and case-sensitive. Hidden files remain on disk and still work with indexing, VCS, search, builds, and open editor tabs.");
        note.setForeground(JBColor.GRAY);

        rootPanel = new JBPanel<>(new BorderLayout(0, 8));
        rootPanel.add(enabledCheckBox, BorderLayout.NORTH);
        rootPanel.add(tabs, BorderLayout.CENTER);
        rootPanel.add(note, BorderLayout.SOUTH);
        reset();
        return rootPanel;
    }

    @Override
    public boolean isModified() {
        FileHiderSettings.State state = FileHiderSettings.getInstance().getState();
        return enabledCheckBox.isSelected() != state.enabled
                || !sameRules(defaultRulesPanel.rules(false), state.defaultRules)
                || !sameRules(userRulesPanel.rules(false), state.userRules);
    }

    @Override
    public void apply() throws ConfigurationException {
        List<FileHiderRule> defaultRules = defaultRulesPanel.rules(true);
        List<FileHiderRule> userRules = userRulesPanel.rules(true);

        String defaultError = RuleValidator.validate(defaultRules);
        if (defaultError != null) {
            throw new ConfigurationException(defaultError, "Default rules");
        }
        String userError = RuleValidator.validate(userRules);
        if (userError != null) {
            throw new ConfigurationException(userError, "User rules");
        }

        FileHiderSettings.State next = new FileHiderSettings.State();
        next.enabled = enabledCheckBox.isSelected();
        next.defaultRules = FileHiderSettings.normalizeRules(defaultRules);
        next.userRules = FileHiderSettings.normalizeRules(userRules);
        FileHiderSettings.getInstance().update(next);
    }

    @Override
    public void reset() {
        FileHiderSettings.State state = FileHiderSettings.getInstance().getState();
        enabledCheckBox.setSelected(state.enabled);
        defaultRulesPanel.setRules(state.defaultRules);
        userRulesPanel.setRules(state.userRules);
    }

    @Override
    public void disposeUIResources() {
        enabledCheckBox = null;
        defaultRulesPanel = null;
        userRulesPanel = null;
        rootPanel = null;
    }

    private static boolean sameRules(List<FileHiderRule> left, List<FileHiderRule> right) {
        return FileHiderSettings.normalizeRules(left).equals(FileHiderSettings.normalizeRules(right));
    }

    private static final class RuleTablePanel extends JPanel {
        private final DefaultTableModel model;
        private final JTable table;

        private RuleTablePanel(boolean defaultRules) {
            super(new BorderLayout(0, 8));
            model = new DefaultTableModel(new Object[]{"Name", "Type"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return true;
                }
            };
            table = new JBTable(model);
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.setFillsViewportHeight(true);
            TableColumn typeColumn = table.getColumnModel().getColumn(1);
            typeColumn.setCellEditor(new javax.swing.DefaultCellEditor(new javax.swing.JComboBox<>(RuleType.values())));

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            JButton add = new JButton("Add");
            JButton remove = new JButton("Remove");
            JButton importJson = new JButton("Import JSON");
            JButton exportJson = new JButton("Export JSON");
            buttons.add(add);
            buttons.add(remove);
            buttons.add(importJson);
            buttons.add(exportJson);
            if (defaultRules) {
                JButton resetFactory = new JButton("Reset to factory");
                resetFactory.addActionListener(event -> setRules(FileHiderSettings.factoryDefaultRules()));
                buttons.add(resetFactory);
            }

            add.addActionListener(event -> model.addRow(new Object[]{"", RuleType.BOTH}));
            remove.addActionListener(event -> removeSelectedRow());
            importJson.addActionListener(event -> importJson());
            exportJson.addActionListener(event -> exportJson());

            setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
            add(new JScrollPane(table), BorderLayout.CENTER);
            add(buttons, BorderLayout.SOUTH);
        }

        private List<FileHiderRule> rules(boolean commitEditing) {
            if (commitEditing) {
                stopEditing();
            }

            int editingRow = commitEditing ? -1 : table.getEditingRow();
            int editingColumn = commitEditing ? -1 : table.getEditingColumn();
            Object editingValue = currentEditingValue();

            List<FileHiderRule> rules = new ArrayList<>();
            for (int row = 0; row < model.getRowCount(); row++) {
                Object nameValue = model.getValueAt(row, 0);
                Object typeValue = model.getValueAt(row, 1);
                if (editingRow >= 0 && table.convertRowIndexToModel(editingRow) == row) {
                    if (editingColumn == 0) {
                        nameValue = editingValue;
                    } else if (editingColumn == 1) {
                        typeValue = editingValue;
                    }
                }
                String name = Objects.toString(nameValue, "");
                RuleType type = typeValue instanceof RuleType ruleType
                        ? ruleType
                        : RuleType.valueOf(Objects.toString(typeValue, RuleType.BOTH.name()));
                rules.add(new FileHiderRule(name.trim(), type));
            }
            return rules;
        }

        private void setRules(List<FileHiderRule> rules) {
            model.setRowCount(0);
            for (FileHiderRule rule : FileHiderSettings.copyRules(rules)) {
                model.addRow(new Object[]{rule.name, rule.type});
            }
        }

        private void removeSelectedRow() {
            int selected = table.getSelectedRow();
            if (selected >= 0) {
                model.removeRow(table.convertRowIndexToModel(selected));
            }
        }

        private void importJson() {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            try {
                String json = Files.readString(chooser.getSelectedFile().toPath(), StandardCharsets.UTF_8);
                FileHiderRule[] imported = GSON.fromJson(json, (Type) FileHiderRule[].class);
                if (imported == null) {
                    imported = new FileHiderRule[0];
                }
                List<FileHiderRule> rules = Arrays.asList(imported);
                String error = RuleValidator.validate(rules);
                if (error != null) {
                    Messages.showErrorDialog(this, error, "Invalid Rules");
                    return;
                }
                setRules(rules);
            } catch (RuntimeException | IOException exception) {
                Messages.showErrorDialog(this, exception.getMessage(), "Import Failed");
            }
        }

        private void exportJson() {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            try {
                Files.writeString(
                        chooser.getSelectedFile().toPath(),
                        GSON.toJson(rules(true)),
                        StandardCharsets.UTF_8
                );
            } catch (IOException exception) {
                Messages.showErrorDialog(this, exception.getMessage(), "Export Failed");
            }
        }

        private void stopEditing() {
            if (table.isEditing()) {
                table.getCellEditor().stopCellEditing();
            }
        }

        private Object currentEditingValue() {
            if (!table.isEditing()) {
                return null;
            }
            TableCellEditor editor = table.getCellEditor();
            return editor == null ? null : editor.getCellEditorValue();
        }
    }
}
