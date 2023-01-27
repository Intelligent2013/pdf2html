package org.example;

import com.aspose.cells.*;
import com.aspose.pdf.*;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/*
Converter from PDF to HTML.
Author: Alexander Dyuzhev (dyuzhev@gmail.com)
GitHub: https://github.com/Intelligent2013/pdf2html
*/

public class Pdf2Html {

    static final String USAGE = getUsage();

    private static final String APP_NAME = "pdf2html";

    private static final int ERROR_EXIT_CODE = -1;

    public static void main(String[] args) throws Exception {

        if (args.length == 0 || args.length > 3) {
            System.out.println(USAGE);
            System.exit(ERROR_EXIT_CODE);
        }

        String inputPDF = args[0];

        File fInputPDF = new File(inputPDF);
        if (!fInputPDF.exists()) {
            System.out.println("Error: file '" + inputPDF + "' not found!");
            System.exit(ERROR_EXIT_CODE);
        }

        String pathInputPDF = fInputPDF.getAbsolutePath();

        String outputFile = "";
        try {
            outputFile = args[1];
        } catch (Exception ex){ };

        if (outputFile.isEmpty()) {
            outputFile = pathInputPDF.substring(0, pathInputPDF.lastIndexOf(".")) + ".html";
        }
        File fOutputFile = new File (outputFile);

        process(fInputPDF, fOutputFile);
    }

    private static void process(File fin, File fout) throws Exception {

        ArrayList<byte[]> htmlPages = new ArrayList<>();

        // open PDF in Apache PDFBox
        try (PDDocument doc = PDDocument.load(fin)) {

            Splitter splitter = new Splitter();
            List<PDDocument> Pages = splitter.split(doc);
            Iterator<PDDocument> iterator = Pages.listIterator();

            // iterate for each page
            while (iterator.hasNext()) {

                // save page by PDFBox
                PDDocument pdocOnePage = iterator.next();
                ByteArrayOutputStream baosOnePage = new ByteArrayOutputStream();
                pdocOnePage.save(baosOnePage);

                // open page by Aspose.PDF
                byte[] pdfPage = baosOnePage.toByteArray();
                ByteArrayInputStream baisOnePage = new ByteArrayInputStream(pdfPage);
                Document document = new Document(baisOnePage);

                document.getPages().get_Item(1).getResources().getImages().delete();

                // convert to XLSX, then to HTML
                ExcelSaveOptions excelSave = new ExcelSaveOptions();
                excelSave.setFormat(ExcelSaveOptions.ExcelFormat.XLSX);

                ByteArrayOutputStream baosExcelStream = new ByteArrayOutputStream();
                document.save(baosExcelStream, excelSave);

                byte[] excelResult = baosExcelStream.toByteArray();

                ByteArrayInputStream baisExcelStream = new ByteArrayInputStream(excelResult);

                Workbook workbook = new Workbook(baisExcelStream);

                int sheetCount = workbook.getWorksheets().getCount();

                WorksheetCollection sheets = workbook.getWorksheets();
                // Take Pdfs of each sheet
                for (int j = 0; j < sheetCount; j++) {
                    Worksheet ws = workbook.getWorksheets().get(j);

                    sheets.setActiveSheetIndex(j);

                    com.aspose.cells.HtmlSaveOptions htmlSaveOptions = new com.aspose.cells.HtmlSaveOptions();
                    htmlSaveOptions.setExportActiveWorksheetOnly(true);
                    htmlSaveOptions.setExportImagesAsBase64(true);

                    //String outHTMLFilename = fout.getAbsolutePath().replace(".html", "_table" + j + ".html");

                    ByteArrayOutputStream baosHTMLStream = new ByteArrayOutputStream();
                    workbook.save(baosHTMLStream, htmlSaveOptions);

                    byte[] excelHTML = baosHTMLStream.toByteArray();
                    htmlPages.add(excelHTML);
                }
            }
            mergeHTMLs(htmlPages, fout);
        }
    }

    private static String getUsage() {
        StringBuilder sb = new StringBuilder();
        sb.append("java -jar " + APP_NAME + ".jar <input PDF file path> [output HTML file path]");
        return sb.toString();
    }


    private static void mergeHTMLs(ArrayList<byte[]> htmlPages, File fout) {
        // write first HTML
        if (!htmlPages.isEmpty()) {
            try (FileOutputStream outputStream = new FileOutputStream(fout)) {
                outputStream.write(htmlPages.get(0));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            try {
                // append 'table' from 2nd, 3rd, 4th, etc. html
                for (int i = 1; i < htmlPages.size(); i++) {
                    FileWriter fw = new FileWriter(fout.getAbsolutePath(), true);
                    byte[] htmlContent = htmlPages.get(i);

                    String htmlString = new String(htmlContent, StandardCharsets.UTF_8);

                    org.jsoup.nodes.Document doc = Jsoup.parse(htmlString);

                    Elements tables = doc.select("table");

                    fw.write("<h3>Page " + i + ".</h3>");
                    fw.write(tables.toString());
                    fw.close();
                }
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }
    }

}