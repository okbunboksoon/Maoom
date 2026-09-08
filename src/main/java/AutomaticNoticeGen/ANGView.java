package AutomaticNoticeGen;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;   // ⬅ 드래그&드롭용
import java.io.File;                        // ⬅ 드롭된 파일
import java.util.List;                      // ⬅ 파일 리스트

/**
 * ANGView
 * ───────────────────────────────────────────────────────────────
 * - 이 클래스는 "UI만" 담당한다. (화면 배치/스타일)
 * - 이벤트 처리, 파일 읽기/쓰기, 비즈니스 로직은 절대 넣지 않는다.
 *   → 그런 로직은 ANGController / ANGService 쪽에서 처리.
 *
 * 공개 필드(public)인 컴포넌트만 컨트롤러가 접근한다.
 * (예: pdfField에 파일 경로를 넣거나, 버튼에 리스너를 다는 작업)
 */
public class ANGView extends JFrame {
    // 컨트롤러에서 접근할 공개 컴포넌트
    public final JTextField pdfField = new JTextField("선택된 PDF가 없습니다.");
    public final JButton btnPick   = new JButton("파일선택");
    public final JButton btnClose  = new JButton("닫기");
    public final JButton btnRun    = new JButton("실행");

    public ANGView() {
        setTitle("협조문 작성 프로그램");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(550, 300);
        setLocationRelativeTo(null);

        // ===== 바깥 여백 컨테이너 =====
        JPanel root = new JPanel(new GridBagLayout());
        root.setBorder(new EmptyBorder(24, 24, 24, 24));
        add(root);

        // ===== 카드 컨테이너 =====
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(new Color(0xFAFBFD));
        card.setBorder(new CompoundBorder(
                new LineBorder(new Color(0xE4E8EE), 1, true),
                new EmptyBorder(18, 20, 20, 20)
        ));
        GridBagConstraints rc = gbc(0, 0, 1, 1, 1, 1);
        rc.fill = GridBagConstraints.BOTH;
        root.add(card, rc);

        GridBagConstraints g;

        // ===== 중앙: PDF 경로 + 파일선택 =====
        pdfField.setEditable(false);
        pdfField.setHorizontalAlignment(JTextField.CENTER);
        pdfField.setBorder(new CompoundBorder(
                new LineBorder(new Color(0xD7DEE8), 1, true),
                new EmptyBorder(8, 10, 8, 10)
        ));
        pdfField.setToolTipText("여기에 PDF 파일을 끌어다 놓아도 됩니다.");

        // ⬇⬇⬇ 드래그&드롭으로 PDF 경로 입력 지원 ⬇⬇⬇
        pdfField.setDragEnabled(true);
        pdfField.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                // 파일 드롭만 허용
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }
            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                try {
                    List<File> files = (List<File>) support.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    if (files == null || files.isEmpty()) return false;

                    File file = files.get(0); // 첫 번째 파일만 사용
                    String name = file.getName().toLowerCase();
                    if (!name.endsWith(".pdf")) {
                        JOptionPane.showMessageDialog(ANGView.this,
                                "PDF 파일만 지원합니다.", "오류", JOptionPane.ERROR_MESSAGE);
                        return false;
                    }
                    pdfField.setText(file.getAbsolutePath());
                    return true;
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(ANGView.this,
                            "파일을 드롭하는 중 오류가 발생했습니다.\n" + ex.getMessage(),
                            "오류", JOptionPane.ERROR_MESSAGE);
                    return false;
                }
            }
        });
        // ↑↑↑ 드래그&드롭 끝 ↑↑↑

        g = gbc(0, 1, 1, 1, 1, 0);
        g.insets = new Insets(6, 0, 6, 12);
        card.add(pdfField, g);

        btnPick.setPreferredSize(new Dimension(120, 36));
        g = gbc(1, 1, 1, 1, 0, 0);
        g.insets = new Insets(6, 0, 6, 0);
        card.add(btnPick, g);

        // ===== 중앙 여백(Spacer) =====
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        g = gbc(0, 2, 2, 1, 1, 1);
        g.insets = new Insets(8, 0, 0, 0);
        g.fill = GridBagConstraints.BOTH;
        card.add(spacer, g);

        // ===== 하단: 버튼 영역 =====
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        bottom.setOpaque(false);
        Dimension actBtnSize = new Dimension(100, 36);
        btnClose.setPreferredSize(actBtnSize);
        btnRun.setPreferredSize(actBtnSize);
        bottom.add(btnRun);
        bottom.add(btnClose);

        g = gbc(0, 3, 2, 1, 1, 0);
        g.insets = new Insets(12, 0, 0, 0);
        g.fill = GridBagConstraints.HORIZONTAL;
        card.add(bottom, g);
    }

    private static GridBagConstraints gbc(int x, int y, int w, int h, double wx, double wy) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = x; g.gridy = y; g.gridwidth = w; g.gridheight = h;
        g.weightx = wx; g.weighty = wy; g.fill = GridBagConstraints.HORIZONTAL;
        return g;
    }
}
