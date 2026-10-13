package com.sts.dita.app;

import com.sts.dita.core.*;
import com.sts.dita.report.HtmlReportWriter;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** STS 웹 연동 전에도 동일한 검사 코어를 로컬에서 실행할 수 있는 Swing 화면이다. */
public final class ValidatorApp extends JFrame {
    private final JTextField mapField = new JTextField();
    private final JTextField schemaField = new JTextField();
    private final JTree tree = new JTree(new DefaultMutableTreeNode("ditamap을 선택하세요"));
    private final JComboBox<ValidationScope> scope = new JComboBox<>(ValidationScope.values());
    private final JCheckBox validateMap = new JCheckBox("ditamap 자체 검사", true);
    private final JLabel status = new JLabel("준비");
    private final ValidationEngine engine = new ValidationEngine();
    private MapModel model;

    public ValidatorApp() {
        super("DITA Schematron 검사기");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);
        Path bundledSchema = findBundledSchema();
        if (Files.isRegularFile(bundledSchema)) schemaField.setText(bundledSchema.toString());
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        JPanel files = new JPanel(new GridLayout(2, 1, 4, 4));
        files.add(fileRow("DITAMAP", mapField, this::chooseMap));
        files.add(fileRow("Schematron", schemaField, () -> chooseFile(schemaField, "sch")));
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(new JLabel("검사 범위:")); controls.add(scope); controls.add(validateMap);
        JButton run = new JButton("검사 실행"); run.addActionListener(e -> runValidation()); controls.add(run);
        JPanel north = new JPanel(new BorderLayout()); north.add(files, BorderLayout.NORTH); north.add(controls, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH); add(new JScrollPane(tree), BorderLayout.CENTER); add(status, BorderLayout.SOUTH);
    }

    private JPanel fileRow(String label, JTextField field, Runnable action) {
        JPanel panel = new JPanel(new BorderLayout(6, 0));
        panel.add(new JLabel(label), BorderLayout.WEST); panel.add(field, BorderLayout.CENTER);
        JButton button = new JButton("찾기"); button.addActionListener(e -> action.run()); panel.add(button, BorderLayout.EAST);
        return panel;
    }

    private void chooseMap() {
        if (chooseFile(mapField, "ditamap")) {
            try { model = engine.loadMap(Path.of(mapField.getText())); rebuildTree(); status.setText("map 로드 완료: " + count(model.roots()) + "개 항목"); }
            catch (Exception ex) { showError(ex); }
        }
    }

    private boolean chooseFile(JTextField target, String extension) {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            target.setText(chooser.getSelectedFile().getAbsolutePath()); return true;
        }
        return false;
    }

    private void rebuildTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(model.mapFile().getFileName());
        for (MapNode node : model.roots()) root.add(toSwingNode(node));
        tree.setModel(new DefaultTreeModel(root));
    }

    private DefaultMutableTreeNode toSwingNode(MapNode node) {
        DefaultMutableTreeNode result = new DefaultMutableTreeNode(node);
        for (MapNode child : node.children()) result.add(toSwingNode(child));
        return result;
    }

    private void runValidation() {
        if (model == null || schemaField.getText().isBlank()) { JOptionPane.showMessageDialog(this, "ditamap과 Schematron 파일을 선택하세요."); return; }
        Set<String> selected = new HashSet<>();
        TreePath[] paths = tree.getSelectionPaths();
        if (paths != null) for (TreePath path : paths) {
            Object value = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
            if (value instanceof MapNode node) selected.add(node.id());
        }
        ValidationScope selectedScope = (ValidationScope) scope.getSelectedItem();
        if (selectedScope != ValidationScope.ALL && selected.isEmpty()) { JOptionPane.showMessageDialog(this, "검사할 트리 항목을 선택하세요."); return; }
        status.setText("검사 중...");
        new SwingWorker<ValidationResult, Void>() {
            protected ValidationResult doInBackground() throws Exception {
                return engine.validate(new ValidationRequest(model.mapFile(), Path.of(schemaField.getText()), selectedScope, selected, validateMap.isSelected()));
            }
            protected void done() {
                try {
                    ValidationResult result = get();
                    Path output = model.mapFile().getParent().resolve("schematron-report.html");
                    new HtmlReportWriter().write(result, output);
                    status.setText("완료: " + result.validatedFileCount() + "개 파일, " + result.issues().size() + "개 오류");
                    Desktop.getDesktop().browse(output.toUri());
                } catch (Exception ex) { showError(ex); }
            }
        }.execute();
    }

    private static int count(java.util.List<MapNode> nodes) { int n = nodes.size(); for (MapNode node : nodes) n += count(node.children()); return n; }
    private static Path findBundledSchema() {
        Path development = Path.of("src", "main", "resources", "schematron", "DOCS_CHECK_2026-05-27.sch").toAbsolutePath();
        if (Files.isRegularFile(development)) return development;
        Path commandBundle = Path.of("schematron", "DOCS_CHECK_2026-05-27.sch").toAbsolutePath();
        if (Files.isRegularFile(commandBundle)) return commandBundle;
        String appPath = System.getProperty("jpackage.app-path");
        if (appPath != null && !appPath.isBlank()) {
            Path packaged = Path.of(appPath).toAbsolutePath().getParent().resolve("app").resolve("DOCS_CHECK_2026-05-27.sch");
            if (Files.isRegularFile(packaged)) return packaged;
        }
        return development;
    }
    private void showError(Exception ex) { status.setText("오류"); JOptionPane.showMessageDialog(this, ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE); }
    public static void main(String[] args) { SwingUtilities.invokeLater(() -> new ValidatorApp().setVisible(true)); }
}
