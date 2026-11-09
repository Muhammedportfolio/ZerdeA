import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

class Documents extends Util {
   static List<Object> extract(String var0, byte[] var1) throws Exception {
      check(var1.length <= 12000000, "Максимальный размер документа — 12 МБ");
      String var2 = var0.toLowerCase(Locale.ROOT);
      ArrayList var3 = new ArrayList();
      int var4 = 0;
      if (var2.endsWith(".pdf")) {
         PDDocument var5 = Loader.loadPDF(var1);

         try {
            check(!var5.isEncrypted(), "PDF защищён паролем");
            check(var5.getNumberOfPages() <= 300, "Максимум 300 страниц PDF");
            PDFTextStripper var6 = new PDFTextStripper();

            for (int var7 = 1; var7 <= var5.getNumberOfPages(); var7++) {
               var6.setStartPage(var7);
               var6.setEndPage(var7);
               String var8 = var6.getText(var5).strip();
               var4 += var8.length();
               check(var4 <= 600000, "Слишком много текста в документе");
               if (!var8.isBlank()) {
                  var3.add(m("ref", "p" + var7, "label", "Страница " + var7, "text", var8));
               }
            }
         } catch (Throwable var15) {
            if (var5 != null) {
               try {
                  var5.close();
               } catch (Throwable var14) {
                  var15.addSuppressed(var14);
               }
            }

            throw var15;
         }

         if (var5 != null) {
            var5.close();
         }
      } else if (var2.endsWith(".docx")) {
         byte[] var17 = null;

         ZipEntry var22;
         try (ZipInputStream var19 = new ZipInputStream(new ByteArrayInputStream(var1))) {
            while ((var22 = var19.getNextEntry()) != null) {
               if (var22.getName().equals("word/document.xml")) {
                  var17 = var19.readNBytes(4000001);
                  break;
               }
            }
         }

         check(var17 != null && var17.length <= 4000000, "Не удалось прочитать Word");
         DocumentBuilderFactory var20 = DocumentBuilderFactory.newInstance();
         var20.setNamespaceAware(true);
         var20.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
         var20.setFeature("http://xml.org/sax/features/external-general-entities", false);
         var20.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
         var20.setExpandEntityReferences(false);
         Document var23 = var20.newDocumentBuilder().parse(new ByteArrayInputStream(var17));
         NodeList var24 = var23.getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "p");
         StringBuilder var9 = new StringBuilder();
         int var10 = 1;

         for (int var11 = 0; var11 < var24.getLength(); var11++) {
            var9.append(var24.item(var11).getTextContent()).append('\n');
            if (var9.length() >= 3500 || var11 == var24.getLength() - 1) {
               String var12 = var9.toString().strip();
               var4 += var12.length();
               check(var4 <= 600000, "Слишком много текста");
               if (!var12.isBlank()) {
                  var3.add(m("ref", "f" + var10, "label", "Фрагмент " + var10, "text", var12));
               }

               var10++;
               var9.setLength(0);
            }
         }
      } else {
         if (!var2.endsWith(".txt") && !var2.endsWith(".md")) {
            throw new IllegalArgumentException("Поддерживаются PDF, Word DOCX, TXT и Markdown. Старый DOC сохраните как DOCX.");
         }

         String var18 = new String(var1, StandardCharsets.UTF_8);
         check(var18.length() <= 600000, "Слишком много текста");

         for (short var21 = 0; var21 < var18.length(); var21 += 5000) {
            var3.add(
               m(
                  "ref",
                  "f" + (var21 / 5000 + 1),
                  "label",
                  "Фрагмент " + (var21 / 5000 + 1),
                  "text",
                  var18.substring(var21, Math.min(var18.length(), var21 + 5000))
               )
            );
         }
      }

      check(!var3.isEmpty(), "Текст не найден. Для сканированного PDF сначала выполните OCR и загрузите PDF с текстовым слоем.");
      return var3;
   }

   static String avatar(String var0) throws Exception {
      String var1 = var0.contains(",") ? var0.substring(var0.indexOf(44) + 1) : var0;
      byte[] var2 = Base64.getDecoder().decode(var1);
      check(var2.length <= 4000000, "Фото больше 4 МБ");

      String var13;
      try (ImageInputStream var3 = ImageIO.createImageInputStream(new ByteArrayInputStream(var2))) {
         Iterator var4 = ImageIO.getImageReaders(var3);
         check(var4.hasNext(), "Неподдерживаемое изображение");
         ImageReader var5 = (ImageReader)var4.next();

         try {
            var5.setInput(var3);
            check((long)var5.getWidth(0) * var5.getHeight(0) <= 25000000L, "Слишком большое разрешение фотографии");
            BufferedImage var6 = var5.read(0);
            int var7 = Math.min(var6.getWidth(), var6.getHeight());
            BufferedImage var8 = new BufferedImage(256, 256, 1);
            Graphics2D var9 = var8.createGraphics();
            var9.setColor(Color.WHITE);
            var9.fillRect(0, 0, 256, 256);
            var9.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            int var10 = (var6.getWidth() - var7) / 2;
            int var11 = (var6.getHeight() - var7) / 2;
            var9.drawImage(var6, 0, 0, 256, 256, var10, var11, var10 + var7, var11 + var7, null);
            var9.dispose();
            ByteArrayOutputStream var12 = new ByteArrayOutputStream();
            ImageIO.write(var8, "jpg", var12);
            var13 = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(var12.toByteArray());
         } finally {
            var5.dispose();
         }
      }

      return var13;
   }
}
