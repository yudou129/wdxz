import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.FileInputStream;

public class DumpExcel {
    public static void main(String[] args) throws Exception {
        String path = "E:/coding/wangdianxuanzhi/数据表格/贵阳市测试数据/网点信息表_贵阳市.xlsx";
        XSSFWorkbook wb = new XSSFWorkbook(new FileInputStream(path));
        for (int si = 0; si < wb.getNumberOfSheets(); si++) {
            Sheet sheet = wb.getSheetAt(si);
            System.out.println("\n===== " + sheet.getSheetName() + " =====");
            System.out.println("Rows: " + (sheet.getLastRowNum() + 1) + ", Cols: " + sheet.getRow(0).getLastCellNum());

            // Merged regions
            System.out.println("Merged: " + sheet.getNumMergedRegions());
            for (int mi = 0; mi < sheet.getNumMergedRegions(); mi++) {
                System.out.println("  " + sheet.getMergedRegion(mi).formatAsString());
            }

            // Print first 15 rows with all cells
            int maxRow = Math.min(15, sheet.getLastRowNum());
            for (int r = 0; r <= maxRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String[] vals = new String[row.getLastCellNum()];
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    Cell cell = row.getCell(c);
                    if (cell != null) {
                        Object v = null;
                        switch (cell.getCellType()) {
                            case STRING: v = cell.getStringCellValue(); break;
                            case NUMERIC: v = cell.getNumericCellValue(); break;
                            case BOOLEAN: v = cell.getBooleanCellValue(); break;
                            case FORMULA: v = cell.getCellFormula(); break;
                            default: v = null;
                        }
                        if (v != null) vals[c] = v.toString();
                    }
                }
                // Print non-null cells
                StringBuilder sb = new StringBuilder("  Row" + r + ":");
                for (int c = 0; c < vals.length; c++) {
                    if (vals[c] != null && !vals[c].isEmpty()) {
                        sb.append(" [").append(c).append("]=").append(vals[c]);
                    }
                }
                if (sb.length() > 10) System.out.println(sb);
            }
        }
        wb.close();
    }
}
