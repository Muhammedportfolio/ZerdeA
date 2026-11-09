import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class Excel extends Util {
   static String xml(String var0) {
      return var0.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
   }

   static void entry(ZipOutputStream var0, String var1, String var2) throws Exception {
      var0.putNextEntry(new ZipEntry(var1));
      var0.write(var2.getBytes(StandardCharsets.UTF_8));
      var0.closeEntry();
   }

   static byte[] export(List<Map<String, Object>> var0) throws Exception {
      ByteArrayOutputStream var1 = new ByteArrayOutputStream();

      try (ZipOutputStream var2 = new ZipOutputStream(var1)) {
         entry(
            var2,
            "[Content_Types].xml",
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>"
         );
         entry(
            var2,
            "_rels/.rels",
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>"
         );
         entry(
            var2,
            "xl/workbook.xml",
            "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Результаты\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>"
         );
         entry(
            var2,
            "xl/_rels/workbook.xml.rels",
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>"
         );
         StringBuilder var3 = new StringBuilder("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
         ArrayList<List<String>> var4 = new ArrayList<>();
         var4.add(List.of("ФИО", "Задание", "Версия", "Баллы", "Всего", "Первичная оценка", "Комментарий"));

         for (Map var6 : var0) {
            var4.add(
               List.of(
                  s(var6, "student"),
                  s(var6, "title"),
                  String.valueOf(var6.get("version")),
                  String.valueOf(var6.get("score")),
                  String.valueOf(var6.get("total")),
                  String.valueOf(var6.get("originalScore")),
                  s(var6, "comment")
               )
            );
         }

         int var11 = 0;

         for (List var7 : var4) {
            var3.append("<row r=\"").append(++var11).append("\">");

            for (int var8 = 0; var8 < var7.size(); var8++) {
               var3.append("<c r=\"")
                  .append((char)(65 + var8))
                  .append(var11)
                  .append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                  .append(xml((String)var7.get(var8)))
                  .append("</t></is></c>");
            }

            var3.append("</row>");
         }

         var3.append("</sheetData></worksheet>");
         entry(var2, "xl/worksheets/sheet1.xml", var3.toString());
      }

      return var1.toByteArray();
   }
}
