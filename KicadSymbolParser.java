// translate2coralEDA - a utility for turning various EDA formats into gEDA/coralEDA CAD elements
// KicadSymbolParser - a utility for turning kicad modules to coralEDA xschem/gschem symbols
// KicadSymbolParser.java v1.0
//
// based on KicadSymbolToGEDA.java
//
// Copyright (C) 2015, 2019 Erich S. Heinzle, a1039181@gmail.com

//    see LICENSE-gpl-v2.txt for software license
//    see README.txt
//    
//    This program is free software; you can redistribute it and/or
//    modify it under the terms of the GNU General Public License
//    as published by the Free Software Foundation; either version 2
//    of the License, or (at your option) any later version.
//    
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//    
//    You should have received a copy of the GNU General Public License
//    along with this program; if not, write to the Free Software
//    Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
//    
//    KicadSymbolParser Copyright (C) 2015,2019 Erich S. Heinzle a1039181@gmail.com
//    translate2coralEDA, translate2geda Copyright (C) 2015, 2019
//    Erich S. Heinzle a1039181@gmail.com

import java.io.*;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.List;

class KicadSymbolParser extends CADParser {

  private static String format;
  private static boolean  verbose = true; 
  private static List<String> convertedFiles = new ArrayList<String>();

  public KicadSymbolParser(String filename, String format, boolean verbose) {
    File KicadSymbolFile = new File(filename);
    if (!KicadSymbolFile.exists()) {
      System.exit(0);
    } else {
      this.format = format;
      System.out.println("Parsing: " + filename + " and exporting format: " + format);
      setPinSpacing(format); // we won't be using pin spacing or format for now
    }
    this.verbose = verbose;
  }

  public static String [] convert(String KicadLib) throws IOException {

    boolean quietMode = false;
    boolean defaultHTMLsummary = true;
    boolean usingStdInForModule = false;
    boolean useDefaultAppendedAttributes = true;
    String useLicenceField = null;
    String distLicenceField = null;
    String authorField = null;

    int gridSpacing = 0; // default value

    // the following are default strings which can be changed to suit the user's needs,
    String htmlSummaryFileName = "HTMLsummary.html";
    String kicadLibName = KicadLib;
    String moduleDescriptionText = " converted kicad symbol";
    String convertedKicadModulePath = "Converted/";
    String htmlSummaryPathToConvertedModule = "kicad/symbols/";
    String tempStringArg = "";
    String appendedAttributesFileName = "";
    String defaultAppendedAttributesFileName =
        "AuthorCredits/DefaultSymbolAppendedAttributes.txt";

    if (defaultHTMLsummary)
      {
        // we replace any symbols in the Module path that will cause file IO conniptions
        htmlSummaryFileName =
            KicadLib.replaceAll("[^a-zA-Z0-9-]", "_") + "-" +  htmlSummaryFileName;
        if (verbose)
          {
            System.out.println("Using: " + htmlSummaryFileName + 
                               " for HTML summary of converted modules");
          }
      }

    Scanner kicadLibraryFile;
    File file1 = new File(KicadLib);
    kicadLibraryFile = new Scanner(file1);
    
    if (useDefaultAppendedAttributes)
      {
        appendedAttributesFileName = defaultAppendedAttributesFileName;
      }
    
    // we now look for the appended author and vendor attributes file
    File file2 = new File(appendedAttributesFileName);
    if (!file2.exists())
      {
        System.out.println("Hmm, an appended attributes file "
                           + appendedAttributesFileName + " was not found in the AuthorCredits directory...");
      }
    
    // we get rid of the "kicad_libraries/" at the front of the converted module filename
    if (kicadLibName.startsWith("kicad_libraries"))
      {
        kicadLibName = kicadLibName.substring(16);
      }

    ArrayList<String> loadedLibraryStringArray = new ArrayList<String>();
    ArrayList<Symbol> symbolsInLibrary = new ArrayList<Symbol>();

    int symbolDefsInLibraryCount = 0;

    String tempString = "";
    Boolean legacyFlag = true;

    int extractedSymbolCount = 0;

    boolean firstLine = true;

    // first of all, we load the library into a string array
    // and count the number of lines
    // and count the number of modules therein

    //    if (kicadLibraryFile.hasNext())
    //     {
    //      tempString = kicadLibraryFile.nextLine();
    //   }
                
    while (kicadLibraryFile.hasNext())
      { // we do this in case the very first line is $MODULE, but it shouldn't be usually
        //most modules start with an INDEX, so this should be safe
	tempString = kicadLibraryFile.nextLine();
	if (verbose)
          {
            System.out.println("Current line being read from lib: " + tempString);
	  }
	loadedLibraryStringArray.add(tempString);
        if (tempString.startsWith("DEF"))
          {
            symbolDefsInLibraryCount++;
          } 
      }

    // we create a string array to store individual module definitions

    ArrayList<String> extractedSymbolDefinition = new ArrayList<String>();
    boolean inSymbolDef = false;

    for (String s : loadedLibraryStringArray)
      {

        if (s.startsWith("DEF"))
          {
            if (verbose)
              {
                System.out.println("Found a DEF");
              }
            inSymbolDef = true;
          }
        else if (s.startsWith("ENDDEF"))
          {
            inSymbolDef = false;
            extractedSymbolDefinition.add(s);
            // having found and extracted a symbol
            // we now store it in a symbol object
            if (verbose)
              {
                System.out.println("We've found " + extractedSymbolCount
                                   + " modules so far.");
              }
            // we convert the array of strings to one string
            // so that it can be passed to the Symbol object

            tempStringArg = "";
            for (String ss : extractedSymbolDefinition) 
              {
                tempStringArg = tempStringArg + "\n" + ss;
              }
            symbolsInLibrary.add(new Symbol(tempStringArg));
            extractedSymbolCount++;
	    extractedSymbolDefinition = new ArrayList<String>();
          }

        if (inSymbolDef)
          {
            extractedSymbolDefinition.add(s);
          }

      }	
    //	we close kicadLibaryFile, for the user specified a module filename 
    kicadLibraryFile.close(); // we'll try it down here

    // we now have finished parsing the library file, and we have an array of symbol objects
    // that we can interogate, namely:  symbolsInLibrary<String> 

    // we can now step through the ArrayList of symbols we generated from the kicad library(s)
    // we generate a gschem/xschem format symbol for each of them, save each one to a symbol_name.sym,
    // and create a gedasymbols.org compatible HTML segment for inclusion in a user index 

    // we insert a heading for the HTML summary
    String HTMLsummaryOfConvertedSymbols = "<html><h2>" + kicadLibName + "</h2>\n";

    int counter = 0;
    for (Symbol sym : symbolsInLibrary)
      {
	counter++;
        if (verbose)
          {
            System.out.println("Symbol object array index: " + counter);
          }

        // we generate a string containing the GEDA element filename, ok for xschem too
        String outputFileName = sym.generateSymbolFilename(format);

        // we then append a listing for this particular footprint
        // to the HTML summary
        HTMLsummaryOfConvertedSymbols = HTMLsummaryOfConvertedSymbols +
            "<li><a href=\"" +
            htmlSummaryPathToConvertedModule +
            kicadLibName + "/" +
            outputFileName + "\"> " +
            outputFileName + " </a> - " +
            moduleDescriptionText +
            " </li>\n";

        if (verbose)
          {
            System.out.println("About to use: " + outputFileName + " for symbol: " + sym);
          }

        // a String variable to contain the symbol data
	sym.suppressTranslation(true); // adjusting translation to suit gschem (minX,minY) > (0,0) can break
        String symbolData = symbolHeader(format) + sym.generateSymbol(gridSpacing, format);
	if (verbose) {
          System.out.println("Just generated symbol.");
	}
	if (format.equals("gschem")) {
          if (authorField != null) {
            symbolData = symbolData +
                SymbolText.attributeString(-sym.xTranslate, -sym.yTranslate, ("author=" + authorField));
          }
          if (distLicenceField != null) {
            symbolData = symbolData +
                SymbolText.attributeString(-sym.xTranslate, -sym.yTranslate,
                                           ("dist-licence=" + distLicenceField));
          }
          if (useLicenceField != null) {
            symbolData = symbolData +
                SymbolText.attributeString(-sym.xTranslate, -sym.yTranslate,
                                           ("use-licence=" + useLicenceField));
          }
          if (verbose) {
            System.out.println("About to see if attributes file exists.");
          }
          if (!useDefaultAppendedAttributes && file2.exists()) {
            Scanner appendedAttributes = new Scanner(file2);
            while (appendedAttributes.hasNext())
              {
                symbolData = symbolData +
                    SymbolText.attributeString(-sym.xTranslate, -sym.yTranslate, (appendedAttributes.nextLine()));
              }
            appendedAttributes.close();
          }
          // now we add source = kicad.mod name
          symbolData = symbolData +
              SymbolText.attributeString(-sym.xTranslate, -sym.yTranslate, ("source=" + kicadLibName));
	}

        if (verbose)
          {
            System.out.println(symbolData);
            // and we now use the toString method to return the module text
            System.out.println("\n\n" + sym + "\n\n");
          }

        // we now create a file with the name of the module and conversion directory
        // path prepended
	if (verbose) {
          System.out.println("About to write symbol data to file.");
	}
	elementWrite(convertedKicadModulePath + outputFileName, symbolData);
	convertedFiles.add(outputFileName);
      }

    // having populated footprint objects in an array
    // we now finish off the HTML summary of the created symbols

    HTMLsummaryOfConvertedSymbols = HTMLsummaryOfConvertedSymbols + "\n</ul></html>\n";
    if (verbose)
      {
        System.out.println(HTMLsummaryOfConvertedSymbols);
      }

    // and we pass the HTML to a subroutine to save the summary to disc, using either a user
    // supplied file name, or alternatively,  an auto generated name kicad_module_name-HTMLsummary.html

    elementWrite(convertedKicadModulePath + htmlSummaryFileName, HTMLsummaryOfConvertedSymbols);

    return convertedFiles.toArray(new String[convertedFiles.size()]);
  }
}
