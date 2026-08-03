package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import maoomWeb.ire.user.dto.BerAsisTobeImportResult;
import maoomWeb.ire.user.dto.BerAsisTobePair;
import maoomWeb.ire.user.mapper.BerAsisTobePairMapper;

class BerAsisTobeAdminServiceTest {

    @Test
    void importsExcelRowsAndCountsInsertUpdateSkipAndUnchanged()
            throws Exception {

        InMemoryMapper mapper = new InMemoryMapper();
        mapper.upsert(pair("EU", "EXISTING", "old", "same"));
        mapper.upsert(pair("US", "UPDATE", "old", "before"));

        BerAsisTobeAdminService service =
                new BerAsisTobeAdminService(mapper);

        BerAsisTobeImportResult result =
                service.importExcel(new ByteArrayInputStream(workbookBytes()));

        assertThat(result.insertedCount()).isEqualTo(1);
        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(result.unchangedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(mapper.findByRegionAndHash("US", "UPDATE").getNewText())
                .isEqualTo("after");
        assertThat(mapper.findByRegionAndHash("EU", "NEW").getNewText())
                .isEqualTo("new");
    }

    private byte[] workbookBytes() throws Exception {
        try(Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()){
            Sheet sheet = workbook.createSheet("BER");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("region");
            header.createCell(1).setCellValue("hash");
            header.createCell(2).setCellValue("old_text");
            header.createCell(3).setCellValue("new_text");

            createRow(sheet, 1, "EU", "EXISTING", "old", "same");
            createRow(sheet, 2, "US", "UPDATE", "old", "after");
            createRow(sheet, 3, "EU", "NEW", "old", "new");
            createRow(sheet, 4, "KR", "BAD", "old", "new");

            workbook.write(output);
            return output.toByteArray();
        }
    }

    private void createRow(
            Sheet sheet,
            int rowIndex,
            String region,
            String hash,
            String oldText,
            String newText) {

        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(region);
        row.createCell(1).setCellValue(hash);
        row.createCell(2).setCellValue(oldText);
        row.createCell(3).setCellValue(newText);
    }

    private static BerAsisTobePair pair(
            String region,
            String hash,
            String oldText,
            String newText) {

        BerAsisTobePair pair = new BerAsisTobePair();
        pair.setRegion(region);
        pair.setHash(hash);
        pair.setOldText(oldText);
        pair.setNewText(newText);
        return pair;
    }

    private static class InMemoryMapper implements BerAsisTobePairMapper {

        private final Map<String,BerAsisTobePair> rows =
                new LinkedHashMap<>();

        @Override
        public List<BerAsisTobePair> findByRegion(String region) {
            return rows.values()
                    .stream()
                    .filter(item -> region.equals(item.getRegion()))
                    .toList();
        }

        @Override
        public int countByRegion(String region) {
            return (int) rows.values()
                    .stream()
                    .filter(item -> region.equals(item.getRegion()))
                    .count();
        }

        @Override
        public List<BerAsisTobePair> findAll() {
            return List.copyOf(rows.values());
        }

        @Override
        public BerAsisTobePair findByRegionAndHash(
                String region,
                String hash) {
            return rows.get(key(region, hash));
        }

        @Override
        public int upsert(BerAsisTobePair pair) {
            rows.put(key(pair.getRegion(), pair.getHash()), pair);
            return 1;
        }

        @Override
        public int deleteByRegionAndHash(String region, String hash) {
            rows.remove(key(region, hash));
            return 1;
        }

        private String key(String region, String hash) {
            return region + "\n" + hash;
        }
    }
}
