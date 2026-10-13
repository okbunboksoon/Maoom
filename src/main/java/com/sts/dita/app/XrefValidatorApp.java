package com.sts.dita.app;

import com.sts.dita.xref.XrefHtmlReportWriter;
import com.sts.dita.xref.XrefValidationEngine;
import com.sts.dita.xref.XrefValidationResult;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;

public final class XrefValidatorApp extends JFrame {
    private final JTextField mapField = new JTextField();
    private final JLabel status = new JLabel("ditamap을 선택하세요.");
    public XrefValidatorApp() {
        super("DITA xref 검사기"); setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(760,160); setLocationRelativeTo(null);
        JButton find = new JButton("ditamap 찾기"); find.addActionListener(e -> choose());
        JButton run = new JButton("xref 검사 실행"); run.addActionListener(e -> runCheck());
        JPanel row = new JPanel(new BorderLayout(6,0)); row.add(mapField,BorderLayout.CENTER); row.add(find,BorderLayout.EAST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT)); buttons.add(run);
        add(row,BorderLayout.NORTH); add(buttons,BorderLayout.CENTER); add(status,BorderLayout.SOUTH);
    }
    private void choose() { JFileChooser c=new JFileChooser(); if(c.showOpenDialog(this)==JFileChooser.APPROVE_OPTION) mapField.setText(c.getSelectedFile().getAbsolutePath()); }
    private void runCheck() {
        if(mapField.getText().isBlank()){JOptionPane.showMessageDialog(this,"ditamap을 선택하세요.");return;}
        status.setText("검사 중...");
        new SwingWorker<XrefValidationResult,Void>(){
            protected XrefValidationResult doInBackground() throws Exception { return new XrefValidationEngine().validate(Path.of(mapField.getText())); }
            protected void done(){try{XrefValidationResult r=get();Path out=r.mapFile().getParent().resolve("xref-report.html");new XrefHtmlReportWriter().write(r,out);status.setText("완료: xref "+r.xrefs().size()+"건, 오류 "+r.errorCount()+"건");Desktop.getDesktop().browse(out.toUri());}catch(Exception ex){status.setText("오류");JOptionPane.showMessageDialog(XrefValidatorApp.this,ex.getMessage(),"오류",JOptionPane.ERROR_MESSAGE);}}
        }.execute();
    }
    public static void main(String[] args){SwingUtilities.invokeLater(()->new XrefValidatorApp().setVisible(true));}
}
