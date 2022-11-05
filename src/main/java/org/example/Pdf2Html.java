package org.example;

import com.aspose.cells.Workbook;
import com.aspose.cells.Worksheet;
import com.aspose.cells.WorksheetCollection;
import com.aspose.pdf.*;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.apache.pdfbox.pdmodel.font.PDCIDFont;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.commons.lang3.StringEscapeUtils;

import java.io.*;
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

    private static StringBuilder nodesTree = new StringBuilder();

    private static long currMCID = -1;

    private static StringBuilder currText = new StringBuilder();

    private static Float currX = 0.0f;
    private static Map<Long, TextBlock> textMap = new HashMap<>();


    public static void main(String[] args) throws Exception {

        if (args.length == 0 || args.length > 2) {
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
            outputFile = pathInputPDF.substring(0, pathInputPDF.lastIndexOf("."));
            outputFile = outputFile + ".html";
        }
        File fOutputHTML = new File (outputFile);

        process(fInputPDF, fOutputHTML);
    }

    private static void process(File fin, File fout) throws Exception {

        if (1 == 2) {
            try (PDDocument doc = PDDocument.load(fin)) {

                // read text strings
                PDPageTree pages = doc.getDocumentCatalog().getPages();
                for (PDPage page : pages) {
                    PDFStreamParser parser = new PDFStreamParser(page);
                    parser.parse();
                    List tokens = parser.getTokens();

                    for (int i = 0; i < tokens.size(); i++) {
                        Object next = tokens.get(i);
                        if (next instanceof Operator) {
                            Operator op = (Operator) next;
                            switch (op.getName()) {
                                case "BDC":
                                    COSDictionary previous = (COSDictionary) tokens.get(i - 1);
                                    for (Map.Entry<COSName, COSBase> cosNameCOSBaseEntry : previous.entrySet()) {
                                        if (cosNameCOSBaseEntry.getKey().getName().toString().equals("MCID")) {
                                            if (cosNameCOSBaseEntry.getValue() instanceof COSInteger) {
                                                currMCID = ((COSInteger) cosNameCOSBaseEntry.getValue()).longValue();
                                            }
                                        }
                                    }
                                    break;
                                case "BT":
                                    //BT: Begin Text.
                                    currText.setLength(0);
                                    break;
                                case "TJ":
                                    COSArray previousTest = (COSArray) tokens.get(i - 1);
                                    for (int k = 0; k < previousTest.size(); k++) {
                                        Object arrElement = previousTest.getObject(k);
                                        if (arrElement instanceof COSString) {
                                            COSString cosString = (COSString) arrElement;
                                            currText.append(cosString.getString());
                                        }
                                    }
                                    break;
                                case "Tm":
                                    COSFloat previousTmX = (COSFloat) tokens.get(i - 2);
                                    currX = previousTmX.floatValue();
                                    break;
                                case "Tj":
                                    System.out.println("Tj processing missing");
                                    break;
                                case "ET":
                                    //ET: End Text.
                                    textMap.put(currMCID, new TextBlock(currText.toString(), currX));
                                    break;
                            }
                        }
                    }
                } // end text reading


                PDStructureTreeRoot structureTreeRoot = doc.getDocumentCatalog().getStructureTreeRoot();
                if (structureTreeRoot != null) {
                    COSBase entryK = structureTreeRoot.getK();
                    buildTree(entryK);
                }

            }
        }

        if (nodesTree.length() == -1) { //!= 0
            BufferedWriter writer = new BufferedWriter(new FileWriter(fout.getAbsoluteFile()));
            writer.write(nodesTree.toString());
            writer.close();
        } else {

            // convert to XLSX, then to HTML
            Document document = new Document(fin.getAbsolutePath());
            ExcelSaveOptions excelSave = new ExcelSaveOptions();
            excelSave.setFormat(ExcelSaveOptions.ExcelFormat.XLSX);

            ByteArrayOutputStream dstStream = new ByteArrayOutputStream();

            //document.save("temp.xlsx", excelSave);
            document.save(dstStream, excelSave);

            byte[] excelResult = dstStream.toByteArray();

            ByteArrayInputStream inStream = new ByteArrayInputStream(excelResult);

            //Workbook workbook = new Workbook("temp.xlsx");
            Workbook workbook = new Workbook(inStream);

            int sheetCount = workbook.getWorksheets().getCount();

            WorksheetCollection sheets = workbook.getWorksheets();
            // Take Pdfs of each sheet
            for (int j = 0; j < sheetCount; j++) {
                Worksheet ws = workbook.getWorksheets().get(j);

                sheets.setActiveSheetIndex(j);

                com.aspose.cells.HtmlSaveOptions htmlSaveOptions = new com.aspose.cells.HtmlSaveOptions();
                htmlSaveOptions.setExportActiveWorksheetOnly(true);
                htmlSaveOptions.setExportImagesAsBase64(true);

                String outHTMLFilename = fout.getAbsolutePath().replace(".html", "table" + j + ".html");
                workbook.save(outHTMLFilename, htmlSaveOptions);
            }
        }
    }

    private static void buildTree (COSBase node) {
        if (node instanceof COSInteger) {
            long id = ((COSInteger) node).longValue();
            TextBlock tb =textMap.get(id);
            if (tb != null) {
                updateTree("<x hidden=\"true\">" + tb.getX() + "</x>");
                updateTree(tb.getText());
            }
            return;
        }
        COSArray cosArray = (COSArray) node.getCOSObject();
        for (int i = 0; i < cosArray.size(); i++) {
            COSBase cosBase = cosArray.get(i);
            if (cosBase instanceof COSInteger) {
                buildTree(cosBase);
                return;
            }
            COSObject cosObject = (COSObject) cosBase;

            COSName cosName = (COSName)cosObject.getItem(COSName.S);
            String elementName = cosName.getName();

            updateTree("<" + elementName + ">");

            buildTree(cosObject.getItem(COSName.K));

            updateTree("</" + elementName + ">");
        }
    }

    private static void updateTree(String s) {
        //System.out.println(s);
        nodesTree.append(s);
    }

    private static String getUsage() {
        StringBuilder sb = new StringBuilder();
        sb.append("java -jar " + APP_NAME + ".jar <input PDF file path> [output HTML file path]");
        return sb.toString();
    }

}