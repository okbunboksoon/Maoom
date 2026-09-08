package AutomaticNoticeGen;

import javax.swing.*;

public class App {
    public static void main(String[] args) {
        // [선택] Nimbus 룩앤필 적용 (버튼/텍스트 필드 등 기본 UI 모양을 깔끔하게)
        try {
            for (UIManager.LookAndFeelInfo i : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(i.getName())) {
                    UIManager.setLookAndFeel(i.getClassName());
                }
            }
        } catch (Exception ignored) {
            // Nimbus가 없거나 적용 실패해도 기본 룩앤필로 계속 진행
        }

        // Swing UI 코드는 반드시 EDT(Event Dispatch Thread)에서 실행해야 안전함
        SwingUtilities.invokeLater(() -> {
            // 1) View: 화면(UI) 준비
            ANGView view = new ANGView();

            // 2) Service: 실제 로직(파일 처리 등) 준비
            ANGService service = new ANGServiceImpl();

            // 3) Controller: 이벤트를 받아 Service 호출하고, 결과를 View에 반영
            new ANGController(view, service);

            // 4) 창 띄우기 (마지막에 보여주기)
            view.setVisible(true);
        });
    }
}
