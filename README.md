# PDF_changer

This is a Software for two things:

first: cropping, renaming and deleting link for PDFs, made in Java.

second: scraping PDF Documents and copying all the text in an .txt document.

## Folder Structure

`src\IllustrationChanger` - Main folder for the first task

- `src\IllustrationChanger\PDF_Files` - all original PDF files (not cropped)

- `src\IllustrationChanger\Cropped_Files` - all cropped files

---

`src\PdfToTxt` - Main folder for the second Task

- `src\PdfToTxt\Pdf` - PDF files where the text should be extracted from

- `src\PdfToTxt\Txt` - output txt files

## Getting Started

- go to the `main` folder and run the project

- then just select in the CLI which program should be run

- _[OPTIONAL]_ : you can change the input and output folder inside the main

## TODO

- better folder Structure

- better input handling (main)

- better footer removing (ptt)

### Infos (second)

<u>Where to get PDF files:</u>

In gerneral you can get them anywhere, but if you want to to use them for audiobooks, it's recommend to use for Example: <https://mp4directs.com/threads/classroom-of-the-elite-light-novels-all-volumes-pdf.549/> (here COTE)

- if you have problems finding the Fontsize of your headers, use: `Get_Fontsize_and_Position.java`
