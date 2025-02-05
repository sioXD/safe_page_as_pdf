package de.pdf;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;


public class PdfToTxt {

    final Boolean delete_folder_contents = true;
    private static int removedFooters = 0;

    public void toTxt(File inputDir, File outputDir)throws Exception{

        if (!outputDir.exists()) {
            outputDir.mkdirs();  // create dir, if not exists
        }
        if (delete_folder_contents) {
            FileUtils.cleanDirectory(outputDir); // delete all files in the folder
        }

        File[] files = inputDir.listFiles((d, name) -> name.toLowerCase().endsWith(".pdf"));

        if (files != null) {
            int count = 0;

            for (File file : files) {
                count += 1;

                try (PDDocument document = Loader.loadPDF(file)) {
                    //Font Size for Header
                    List<Float> headerFontSizes = Arrays.asList(17.0f);

                    HeaderAwarePDFTextStripper pdfStripper = new HeaderAwarePDFTextStripper(headerFontSizes); //extends PDFTextStripper  bla bla ...
                    StringBuilder fullText = new StringBuilder();
                    StringBuilder originalText = new StringBuilder();
                    PDDocumentOutline outline =  document.getDocumentCatalog().getDocumentOutline();

                    //processing pdf to txt
                    String fileName = file.getName();
                    fileName = fileName.replaceAll(".pdf", ".txt");
                    File outputFile = new File(outputDir, fileName);

                    //console output
                    String fileVolume = fileName.replaceAll("Vol.", "Volume"); 
                    fileVolume = fileVolume.replaceAll(".txt", "");
                    String fileVolume_console = fileVolume; // for console output 

                    String[] parts;
                    String beforeParts = fileVolume;
                    String afterParts = "";

                    try {
                        if (fileVolume_console.contains("Year")) {
                            parts = fileVolume_console.split("(?=\\bYear\\b)", 2); // split before Year
                            beforeParts = parts[0].trim(); // everything before "Year"
                            afterParts = parts[1].trim();  // everything after "Year"
                        }else{
                            parts = fileVolume_console.split("(?=\\bVolume\\b)", 2); // split before Volume
                            beforeParts = parts[0].trim(); // everything before "Volume"
                            afterParts = parts[1].trim();  // everything after "Volume"
                        }
                    } catch (ArrayIndexOutOfBoundsException e) {
                        int midIndex = fileVolume_console.length() / 2; // split in the middle
                        beforeParts = fileVolume.substring(0, midIndex).trim(); 
                        afterParts = fileVolume_console.substring(midIndex).trim();
                    }

                    // console output
                    System.out.print(count + "/" + files.length); // show count
                    System.out.print(" ~~~ (" + afterParts + ")"); // Names of the files 


                    //custom starting point
                    if (getBookmark(outline) == null) {throw new Exception("\u001B[31m" + "no starting point found" + "\u001B[0m");}
                    PDPage destinationPage = getBookmark(outline).findDestinationPage(document);
                    int pageNumber = document.getPages().indexOf(destinationPage);

                    //custom end point
                    int pageNumberEnd;
                    if(getBookmarkEnd(outline, document) == null){
                        pageNumberEnd = document.getNumberOfPages();
                    }else{
                        pageNumberEnd = document.getPages().indexOf(getBookmarkEnd(outline, document).findDestinationPage(document)); 
                    }



                    //make Introduction
                    String intro = "Hello, and thank you for listening with Pixco. Just a few reminders before we begin. " + beforeParts + " Illustrations will be announced, so please listen for the narrator to say, please view the illustration. " + fileVolume + ", written by Syougo Kinugasa. Art by Tomo Sessionsaku. Audio by Pixco."; //Author and Illustrator needs to be made dynamic
                    String outro = "That is the end of the story; thank you for listening all the way to the end. If you enjoyed it, please subscribe. It will help grow the channel. I am planning to make more of these types of videos, but my lack of time makes this a challenging approach. Perhaps subscribing would be a big help. Anyways, thanks for watching, and I'll see you next time."; // custom outro
                    
                    // append the intro to the StringBuilder
                    fullText.append(processText(intro)).append("\n");
                    originalText.append(intro).append("\n");

                    // Go through all sides of the PDF
                    for (int page = pageNumber; page < pageNumberEnd; page++) { 
                        pdfStripper.setStartPage(page + 1);
                        pdfStripper.setEndPage(page + 1);

                        String text = pdfStripper.getText(document);
                        text = removeLinesWithLinks(text); 

                        // Check whether the page is blank or only consists of images
                        if (text.isEmpty()) {
                            fullText.append("\nPlease view the Illustration.\n");
                            originalText.append("\nPlease view the Illustration.\n");
                        } else {
                            fullText.append(text).append(" "); //StringBuilder with processed text
                            originalText.append(text).append("\n"); //StringBuilder with original text
                        }
                    }

                    // append the outro to the StringBuilder
                    fullText.append(processText(outro)).append("\n");
                    originalText.append(outro).append("\n");


                    //? Here are too long lines processed
                    String processedLongLines = processLongLines(   //check for long lines
                        processText(fullText.toString().replaceAll("(?m)^[\\s]*$[\n\r]{1,}", "")), // deletes all empty lines
                        originalText.toString().replaceAll("(?m)^[\\s]*$[\n\r]{1,}", "") // deletes all empty lines
                    ); 


                    // Final cleanup
                    String finalCleaned = processedLongLines.replaceAll("(?m)^[\\s]*$[\n\r]{1,}", ""); // deletes all empty lines

                    // Write the edited text to the output file
                    try (FileWriter writer = new FileWriter(outputFile)) {
                        writer.write(finalCleaned);
                    }


                    performFinalScan(outputFile); //Scan for errors


                } catch (Exception e) {
                    System.err.println(" --- " + "\u001b[31;1m" +"Error processing PDF file: " + "\u001B[0m" + e);
                }

            }//EO-for
        }//OFif
    }//EOF

    // Function for text processing
    private static String processText(String text) { 

        String noLineBreaks = text.replace("\r", "").replace("\n", "");// Remove any original line breaks
        
        String cleanedText = normalizeText(noLineBreaks); // Normalize and process individual words

        return cleanedText
            .replaceAll("[.?!] \\s*", "$0\n") //.|?|! with spaces, is replaced by \n
            .replaceAll("[.?!]\"\\s*", "$0\n"); //.|?|! with ", is replaced by \n
                                                                  
            //* maybe add \n after ";"  -- depends on the book
        }


    // Normalized the text
    private static String normalizeText(String text) {

        // Header processing for individual words (if needed)
        text = combineHeader(text);

        // Remove diacritical marks, accents, etc.
        String cleanedText = Normalizer.normalize(text, Normalizer.Form.NFD); 
        cleanedText = cleanedText.replaceAll("\\p{M}", "");
 
        return cleanedText
             .replace("No.", "Number") //No. 11 --> Number 11
             .replace("”", "\"") 
             .replace("“", "\"") 
             .replace("—", "-") 
             .replace("–", "-") 
             .replace("‘", "'") 
             .replace("’", "'") 
             .replace("★", "")
             .replace("☆", "")
             .replace("…", "...")
             .replace("¾", "...") //editor mistake in volume 3
             .replace("°C", "Celsius")
             .replace("×", "*")
             .replace("÷", "/")
             .replace("•", "/")
             .replace("\t", " ")
             .replace("(?m)^[\\s]*$[\n\r]{1,}", "") // empty lines
            .trim();
    }

    // Word-based search    ---     //* for testing if something is double use: ^(.*)(?:\r?\n\1)+$ with $1
    private static String processLongLines(String processedText, String originalText) {
        final int LONG_LINE_THRESHOLD = 250; //^.{250,}$
        final double MATCH_THRESHOLD = 0.8; // 80% of the words must match

        // Normalize and save original lines with their words
        List<List<String>> originalWordLines = Arrays.stream(originalText.split("\n"))
            .map(line -> Arrays.stream(normalizeText(line).split("\\s+"))
                            .filter(word -> !word.isEmpty())
                            .collect(Collectors.toList()))
            .filter(line -> !line.isEmpty())
            .collect(Collectors.toList());

        AtomicInteger perfectMatches = new AtomicInteger(0);
        AtomicInteger partialMatches = new AtomicInteger(0);
        AtomicInteger noMatches = new AtomicInteger(0);

        String result = Arrays.stream(processedText.split("\n"))
            .map(processedLine -> {
                if (processedLine.length() < LONG_LINE_THRESHOLD) {
                    return processedLine;
                }

                // Extract and normalize words from the processed line
                List<String> targetWords = Arrays.stream(normalizeText(processedLine).split("\\s+"))
                    .filter(word -> !word.isEmpty())
                    .collect(Collectors.toList());

                // Search original lines for suitable word sequences
                for (int i = 0; i < originalWordLines.size(); i++) {
                    List<String> combined = new ArrayList<>();
                    
                    for (int j = i; j < originalWordLines.size(); j++) {
                        combined.addAll(originalWordLines.get(j));
                        
                        // Check for exact sequence agreement
                        if (Collections.indexOfSubList(combined, targetWords) != -1) {
                            perfectMatches.incrementAndGet();
                            return reconstructOriginalLines(originalWordLines, i, j);
                        }
                        
                        //check partial agreement
                        long wordMatches = combined.stream()
                            .filter(targetWords::contains)
                            .count();
                        
                        if ((double) wordMatches / targetWords.size() >= MATCH_THRESHOLD) {
                            partialMatches.incrementAndGet();
                            return reconstructOriginalLines(originalWordLines, i, j);
                        }
                        
                        if (combined.size() > targetWords.size() * 1.5) break;
                    }
                }
                
                noMatches.incrementAndGet();
                return processedLine;
            })
            .collect(Collectors.joining("\n"));

        System.out.println();
        if (perfectMatches.get() != 0) System.out.println("\033[32m"+"    Perfect matches: " + perfectMatches.get()+"\033[0m"); //green
        if (partialMatches.get() != 0) System.out.println("\033[33m"+"    Partially matches: " + partialMatches.get()+"\033[0m"); //yellow
        if (noMatches.get() != 0) System.out.println("\033[31m"+"    No matches: " + noMatches.get()+"\033[0m"); //red
        
        return result;
    }

    private static String reconstructOriginalLines(List<List<String>> wordLines, int start, int end) {
        return wordLines.subList(start, end + 1).stream()
            .map(lineWords -> String.join(" ", lineWords))
            .collect(Collectors.joining("\n"));
    }

    //find Bookmarks start
    public PDOutlineItem getBookmark(PDOutlineNode bookmark) throws IOException{
        PDOutlineItem current = bookmark.getFirstChild();
        while (current != null){
            if(current.getTitle().contains("1")){ 
                return current;
            }
            getBookmark(current);
            current = current.getNextSibling();
        }
        return null; 
    }

    //find Bookmarks end
    public PDOutlineItem getBookmarkEnd(PDOutlineNode bookmark, PDDocument document) throws IOException{
        PDOutlineItem current = bookmark.getFirstChild();
        while (current != null){
            if(current.getTitle().contains("Postscript")){ 
                int postscript = document.getPages().indexOf(current.findDestinationPage(document));
                getBookmark(current);
                current = current.getNextSibling();
                int afterPostscript = document.getPages().indexOf(current.findDestinationPage(document));

                if(current.getTitle().contains("Postscript")){return null;}// if no bookmark after postscript
                if(postscript > afterPostscript){return null;}


                return current;
            }
            getBookmark(current);
            current = current.getNextSibling();
        }
        return null;
    }

    //Fix Headers
    private static String combineHeader(String text) {
        return text
				 .replace("ßß ß", " ") //TODO need to check!
             .replace("ßßß", " ") // words inside headers
             .replace("ßß", "\n") // end of headers
             .replace("ß", "\n"); // beginning of headers
             // if too many: � --> problem might be ß
    }
    

    
    // Function that removes lines with "mp4directs.com"                 //! maybe just cut the top and bottom
    private static String removeLinesWithLinks(String text) {
        StringBuilder result = new StringBuilder();
        String[] lines = text.split("\n"); // Split text into lines
        removedFooters = 0;
        for (String line : lines) {
            if (line.contains("mp4directs.com")) { // Ignore lines that contain "mp4directs.com" 
                removedFooters++; //for FinalScan
            }else{
                result.append(line).append("\n");
            }
        }
        return result.toString().trim(); // Return result
    }


   

    //final Scan
    private static void performFinalScan(File file) {
        final String ANSI_RED = "\u001B[31m";
        final String ANSI_RED_BRIGHT = "\u001b[31;1m";
        final String ANSI_RESET = "\u001B[0m"; //this too: \e[0m
        final String ANSI_MAGENTA = "\u001b[35m";    //"\uu001b[34m"; 


        try {
            String fileContent = new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
    
            int invalidCharCount = 0; //�
            int controlCharCount = 0; //other
    
            // Scan for invalid characters and control characters
            for (char c : fileContent.toCharArray()) {
                if (c == '�') {
                    invalidCharCount++;
                } else if (Character.isISOControl(c) && c != '\n' && c != '\r') {
                    controlCharCount++;
                }
            }

            // Scan for separated words
            String regex_subchapter = "\\d\\.\\d\\n";
            String regex_error = "\\d\\.\\d\\n[^\\s]{1,2}\\s";

            Pattern p = Pattern.compile(regex_subchapter);
            Pattern pe = Pattern.compile(regex_error);
            Matcher m = p.matcher(fileContent);
            Matcher me = pe.matcher(fileContent);

            int error_count = 0;
            int subchapter_count = 0;
            while (m.find()) {
                subchapter_count += 1;
            }while (me.find()) {
                error_count += 1;
            }

            float error_rate = (float) error_count / subchapter_count * 100;
            boolean failed_detection = (subchapter_count==0 && error_count==0) ? true : false;

            // Print error message if issues were detected
            if (invalidCharCount > 0 || controlCharCount > 0 || error_count >= subchapter_count-5 || removedFooters == 0) {
                System.err.println(ANSI_RED_BRIGHT + " --- Final scan detected issues:" + ANSI_RESET);
                if (invalidCharCount > 0) {
                    System.err.println(ANSI_RED + "  -- Detected " + ANSI_RESET + invalidCharCount +  ANSI_RED + " occurrences of the invalid character '�'." + ANSI_RESET);
                }
                if (controlCharCount > 0) {
                    System.err.println(ANSI_RED + "  -- Detected " + ANSI_RESET + controlCharCount + ANSI_RED + " control characters that may indicate encoding issues." + ANSI_RESET);
                }
                if (failed_detection) {
                    System.err.println(ANSI_RED + "  -- Detected " + ANSI_RESET + "no subchapters. " + ANSI_RED + "Please check the file manually." + ANSI_RESET);
                } else if (error_count >= subchapter_count-5) {
                    System.err.println(ANSI_RED + "  -- Detected: " + ANSI_RESET + error_rate + "%" + ANSI_RED + " of the subchapter beginnings are separated" + ANSI_RESET);
                }
                if (removedFooters == 0) {
                    System.err.println(ANSI_RED + "  -- Detected: " + ANSI_MAGENTA + removedFooters + " footers removed" + ANSI_RESET);
                }

                File errorFile = new File(file.getParent(), "error_" + file.getName());
                file.renameTo(errorFile);

                throw new Exception("Issues detected during the final scan. Please review the output file.\n");
            }
    
            System.out.println(" --- Final scan detected no errors.");
    
        } catch (Exception e) {
            System.err.println();
        }
    }

}//EOC
