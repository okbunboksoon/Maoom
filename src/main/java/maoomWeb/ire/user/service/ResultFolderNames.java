package maoomWeb.ire.user.service;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

final class ResultFolderNames {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyMMdd");
    private static final String MARKER = "_Result_Folder";

    private ResultFolderNames() {
    }

    static Path resolve(Path inputDirectory, String suffix) {
        return inputDirectory.resolve(
                LocalDate.now().format(DATE_FORMAT)
                + MARKER
                + "_"
                + suffix);
    }

    static boolean isGeneratedResultFolder(String fileName) {
        if(fileName == null){
            return false;
        }

        return fileName.equals("Result_Folder")
                || fileName.equals("result_Folder")
                || fileName.matches("\\d{6}_Result_Folder(?:_.+)?");
    }
}
