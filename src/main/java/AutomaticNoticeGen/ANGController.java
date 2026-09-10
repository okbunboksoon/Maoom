package AutomaticNoticeGen;

import javax.swing.*;

import java.awt.Dimension;
import java.io.File;

public class ANGController {
    private final ANGView view;
    private final ANGService service;
    private File pickedPdf;

    public ANGController(ANGView view, ANGService service) {
        this.view = view;
        this.service = service;
        bind();
    }

    public void bind() {
        // PDF 선택
    	// PDF 선택
    	view.btnPick.addActionListener(e -> {
    	    JFileChooser fc = new JFileChooser();
    	    fc.setDialogTitle("PDF 선택");

    	    // 창 크기 지정 (가로 800, 세로 600)
    	    fc.setPreferredSize(new Dimension(1000, 600));

    	    int res = fc.showOpenDialog(view);
    	    if (res == JFileChooser.APPROVE_OPTION) {
    	        pickedPdf = fc.getSelectedFile();
    	        view.pdfField.setText(pickedPdf.getAbsolutePath());
    	    }
    	});


        // 실행 → PDF와 같은 폴더에 자동 저장
        view.btnRun.addActionListener(e -> {
            String pdfPath = view.pdfField.getText();
            if (pdfPath == null || pdfPath.isBlank()) {
                JOptionPane.showMessageDialog(view, "PDF 파일을 먼저 선택하세요.");
                return;
            }

            File pdfFile = new File(pdfPath);
            String baseName = removeExt(pdfFile.getName());
            File outFile = new File(pdfFile.getParentFile(), baseName + "_설계중점_확인사항.xlsx");
            String outPath = outFile.getAbsolutePath();

            new SwingWorker<String, Void>() {
                @Override protected String doInBackground() throws Exception {
                    return service.exportExcel(pdfPath, outPath);
                }
                @Override protected void done() {
                    try {
                        JOptionPane.showMessageDialog(view,
                            "엑셀 저장 완료:\n" + get(),
                            "완료", JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(view,
                            "실패: " + ex.getMessage(),
                            "오류", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        });

        // 닫기
        view.btnClose.addActionListener(e -> view.dispose());
    }

    private static String removeExt(String name) {
        int i = name.lastIndexOf('.');
        return (i > 0) ? name.substring(0, i) : name;
    }
}
