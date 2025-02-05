package de.pdf;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import java.awt.image.BufferedImage;


public class IllustrationExtractor {

    final Boolean delete_folder_contents = true;

    public void onlyPic(File inputDir, File outputDir) throws Exception {
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        if (delete_folder_contents) {
            FileUtils.cleanDirectory(outputDir);
        }

        File[] files = inputDir.listFiles((d, name) -> name.toLowerCase().endsWith(".pdf"));

        if (files != null) {
            for (File file : files) {
                try (PDDocument inputDocument = Loader.loadPDF(file)) {
                    // Create output PDF for this input file
                    try (PDDocument outputDocument = new PDDocument()) {
                        int imageCount = 0;

                        for (PDPage page : inputDocument.getPages()) {
                            PDResources resources = page.getResources();
                            
                            for (COSName name : resources.getXObjectNames()) {
                                if (resources.isImageXObject(name)) {
                                    PDImageXObject image = (PDImageXObject) resources.getXObject(name);
                                    BufferedImage bufferedImage = image.getImage();
                                    String suffix = image.getSuffix();
                                    
                                    // Create new image in output document
                                    PDImageXObject outputImage = createOutputImage(outputDocument, bufferedImage, suffix);
                                    
                                    // Create page with image dimensions
                                    PDPage newPage = new PDPage(
                                        new PDRectangle(outputImage.getWidth(), outputImage.getHeight())
                                    );
                                    outputDocument.addPage(newPage);
                                    
                                    // Draw image on the page
                                    try (PDPageContentStream cs = new PDPageContentStream(outputDocument, newPage)) {
                                        cs.drawImage(outputImage, 0, 0, 
                                            outputImage.getWidth(), 
                                            outputImage.getHeight()
                                        );
                                    }
                                    imageCount++;
                                }
                            }
                        }

                        if (imageCount > 0) {
                            // Generate output filename
                            String outputName = file.getName()
                                .replaceFirst("\\.pdf$", "") + "_images.pdf";
                            File outputFile = new File(outputDir, outputName);
                            outputDocument.save(outputFile);
                            System.out.println("Created image PDF: " + outputFile.getAbsolutePath());
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error processing " + file.getName() + ": " + e.getMessage());
                }
            }
        }
    }

    private PDImageXObject createOutputImage(PDDocument document, BufferedImage image, String suffix) 
        throws Exception {
        if (suffix == null || suffix.isEmpty()) {
            suffix = "png";
        }
        
        return switch (suffix.toLowerCase()) {
            case "jpg", "jpeg" -> JPEGFactory.createFromImage(document, image, 0.85f);
            default -> LosslessFactory.createFromImage(document, image);
        };
    }
}