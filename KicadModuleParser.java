// KicadModuleParser - a utility for turning kicad modules to gEDA PCB and pcb-rnd footprints
// KicadModuleParser.java v1.0
// Copyright (C) 2019 Erich S. Heinzle, a1039181@gmail.com

// Based on KicadModuleToGEDA.java v1.0

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
//    KicadModuleParser Copyright (C) 2019 Erich S. Heinzle a1039181@gmail.com
//    translate2coralEDA, translate2geda Copyright (C) 2015, 2019
//    Erich S. Heinzle a1039181@gmail.com



import java.util.Scanner;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class KicadModuleParser extends CADParser
{

  static String format = "pcb-rnd";
  private static List<String> convertedFiles = new ArrayList<String>();

  static boolean insertElementPreliminaryComments = false;
  static boolean useDefaultAuthorCredits = true;
  static boolean verboseMode = false;
  static boolean quietMode = false;
  static boolean defaultHTMLsummary = true;
  static boolean legacyKicadMmMetricUnits = false; // the usual legacy format is decimils
  static boolean usingStdInForModule = false;
  static boolean generateFontGlyphs = false;
  static long minimumViaAndDrillSizeNM = 0; // default is no minimum drill size
  // in nanometres
  // i.e. 300000 = 0.3mm = 11.81mil


  // the following are default strings which can be changed to suit the user's needs,
  // particularly if usage is intended via stdin, as these will be the defaults used
  // when generating output files
  static String htmlSummaryFileName = "HTMLsummary.html";
  static String kicadModuleFileName = "kicad.mod";
  static String moduleDescriptionText = " converted kicad module";
  static String preliminaryCommentsFileName = "DefaultPrependedCommentsFile.txt";
  static String convertedKicadModulePath = "Converted/";
  static String htmlSummaryPathToConvertedModule = "kicad/footprints/";
  static String defaultAuthorCreditsFileName = "AuthorCredits/DefaultFootprintPreliminaryText.txt";
  static String tempStringArg = "";

  // first, we parse the command line arguments passed to the utility when started

  public KicadModuleParser(String filename, String format, boolean verbose) {
    File KicadModuleFile = new File(filename);
    if (!KicadModuleFile.exists()) {
      System.exit(0);
    } else {
      this.format = format;
      System.out.println("Parsing: " + filename + " and exporting format: " + format);
    }
    verboseMode = verbose;
  }

  public static String [] convert(String kicadModuleFileName) throws IOException {

    // we now come up with a more unique default HTML summary filename if a filename was
    // not specified at the command line
    if (defaultHTMLsummary)
      {
        // we replace any symbols in the Module path that will cause file IO conniptions
        htmlSummaryFileName = kicadModuleFileName.replaceAll("[^a-zA-Z0-9-]", "_") +
            "-" +  htmlSummaryFileName;
        if (verboseMode)
          {
            System.out.println("Using: " + htmlSummaryFileName + 
                               " for HTML summary of converted modules");
          }
      }

    Scanner kicadLibraryFile;
    File file1 = new File(kicadModuleFileName);
    if (!usingStdInForModule)
      {
        // if the user specified a kicad module with command line arguments
        // we will now look for the kicad module passed on the command line
        //	                File file1 = new File(kicadModuleFileName);
        if (!file1.exists())
          {
            System.out.println("Hmm, the library file " + kicadModuleFileName + " was not found.");
            System.exit(0);
          }
        kicadLibraryFile = new Scanner(file1);
      }
    else // we are using StdIn for the module, and args is of length one
      {
        kicadLibraryFile = new Scanner(System.in);
      }

    // sort out the default preliminary licencing comments that will prepend the generated footprints  
    if (useDefaultAuthorCredits)
      {
        preliminaryCommentsFileName = defaultAuthorCreditsFileName;
      }

    // we now look for the preliminary licencing and author credits file
    File file2 = new File(preliminaryCommentsFileName);
    if (!file2.exists())
      {
        System.out.println("Hmm, a preliminary comments file "
                           + preliminaryCommentsFileName + " was not found in the AuthorCredits directory...");
      }

    // we get rid of the "kicad_modules/" at the front of the converted module filename
    if (kicadModuleFileName.startsWith("kicad_modules"))
      {
        kicadModuleFileName = kicadModuleFileName.substring(14);
      }

    String[] loadedLibraryStringArray = new String[59999];

    int loadedLibraryLineCounter = 0;
    int modulesInLibraryCount = 0;

    String tempString = "";
    Boolean legacyFlag = true;

    int extractedModuleLineCounter = 0;
    int extractedModuleCount = 0;
    Footprint[] footprintsInLibrary = new Footprint[100];
    float magnificationRatio = 1.0f;

    boolean firstLine = true;


    // first of all, we load the library into a string array
    // and count the number of lines
    // and count the number of modules therein

    if (kicadLibraryFile.hasNext())
      {
        tempString = kicadLibraryFile.nextLine();
      }

    if (tempString.contains("(kicad_pcb") || tempString.contains("module"))
      { // if so, it is either a standalone s-file module or a library of s-file modules 
        legacyFlag = false;
        if (verboseMode)
          {
            System.out.println("For my next trick, s-file parsing");
          }
      } // otherwise, we just assume it is legacy format

    if (legacyFlag) // we will be processing a legacy format file
      {
        while (kicadLibraryFile.hasNext())
          { // we do this in case the very first line is $MODULE, but it shouldn't be usually
            //most modules start with an INDEX, so this should be safe
            if (firstLine)
              //			if (loadedLibraryLineCounter == 0)
              {
                loadedLibraryStringArray[loadedLibraryLineCounter] = tempString;
                firstLine = false;
              }
            else // we continue loading lines into our string array
              // maybe we can dispense with this preliminary counting business
              {
                loadedLibraryStringArray[loadedLibraryLineCounter] = kicadLibraryFile.nextLine();			
              }

            if (loadedLibraryStringArray[loadedLibraryLineCounter].startsWith("$MODULE"))
              {
                modulesInLibraryCount++;
              } 
            //			System.out.println(loadedLibraryStringArray[loadedLibraryLineCounter]);
            loadedLibraryLineCounter++;
            //			System.out.println("Modules in library count: " + modulesInLibraryCount +
            //					"\nLoaded library line cout: " + loadedLibraryLineCounter );
          }
	//	kicadLibraryFile.close();

        // we create a string array to store individual module definitions

        String[] extractedModuleDefinition = new String[3000];
        boolean inModule = false;

        for (int counter = 0; counter < loadedLibraryLineCounter; counter++)
          {
            if (loadedLibraryStringArray[counter].startsWith("Units mm"))
              {
                legacyKicadMmMetricUnits = true;
              }

            // this has been added to allow a footprint to be magnified
            // it is not something supported by Kicad, but has been done
            // to allow families of related footprints to be generated
            // for gEDA PCB by simply adding a "Magnification X.xxx"
            // string near the beginning of a Kicad module
            if (loadedLibraryStringArray[counter].startsWith("Magnification"))
              {
                magnificationRatio = Float.parseFloat(loadedLibraryStringArray[counter].substring(14, loadedLibraryStringArray[counter].length()));
                System.out.println("# Magnification ratio applied: " +
                                   magnificationRatio);
              }

            else if (loadedLibraryStringArray[counter].startsWith("$MODULE"))
              {
                inModule = true;
              }
            else if (loadedLibraryStringArray[counter].startsWith("$EndMOD"))
              {
                inModule = false;
                extractedModuleDefinition[extractedModuleLineCounter] =
                    loadedLibraryStringArray[counter];
                // having found and extracted a module
                // we now store it in a footprint object
                if (verboseMode)
                  {
                    System.out.println("We've found " + extractedModuleCount
                                       + " modules so far.");
                  }
                // we convert the array of strings to one string
                // so that it can be passed to the Footprint object
                // we may be able to dispence with the array

                tempStringArg = "";
                for (int stringCounter = 0; stringCounter < extractedModuleLineCounter; stringCounter++)
                  {
                    tempStringArg = tempStringArg + "\n" +
                        extractedModuleDefinition[stringCounter];
                  }
                footprintsInLibrary[extractedModuleCount] = new Footprint(tempStringArg, legacyKicadMmMetricUnits, minimumViaAndDrillSizeNM);
                extractedModuleLineCounter = 0;
                extractedModuleCount++;
				
              }

            if (inModule)
              {
                extractedModuleDefinition[extractedModuleLineCounter] =
                    loadedLibraryStringArray[counter];
                extractedModuleLineCounter++;
              }

          }	
      }
    else
      {
        if (verboseMode)
          {
            System.out.println("Not legacy, into the s-file parsing code");
          }

        boolean inModule = false;
        boolean gotOneModule = false;

        int index = 0;
        int LeftBracketCount = 0;  // for s-file parsing
        int RightBracketCount = 0; // for s-file parsing
        int moduleBracketTally = 0;
        int elementBracketTally = 0; // positive is "(" bracket, negative is ")" bracket

        String currentLine = "";
        String[] tokens;
        String parsedString = "";
        String parsedString2 = "";
        String completeModule = "";
        String trimmedModuleLine = "";

        while (kicadLibraryFile.hasNext())
          {
            //			System.out.println(.nextLine());
            if (firstLine)
              {
                currentLine = tempString;
                // may in fact not need the next two lines
                loadedLibraryStringArray[loadedLibraryLineCounter] = tempString;
                loadedLibraryLineCounter++;
                firstLine = false;
              }
            else
              {
                currentLine = kicadLibraryFile.nextLine();
              }
            parsedString = currentLine.trim();
            tokens = parsedString.split(" ");

            if (parsedString.contains("module") // look for module start, but...
                && !(parsedString.contains("modules"))) // ...ignore the header
              {
                if (verboseMode)
                  {
                    System.out.println("Module header found: " + currentLine);
                  }
                moduleBracketTally = 0;
                elementBracketTally = -1; // negate the module start bracket
                inModule = true;
                modulesInLibraryCount++;
              }

            if (inModule)
              {
                moduleBracketTally += tallyBracketsInString(parsedString);
                elementBracketTally += tallyBracketsInString(parsedString);

                //				System.out.println("we start with element bracket tally of: "
                //					+ elementBracketTally);
                while ((elementBracketTally > 0) && kicadLibraryFile.hasNext())
                  {
                    parsedString2 = kicadLibraryFile.nextLine();
                    parsedString = currentLine + parsedString2.trim();
                    elementBracketTally += tallyBracketsInString(parsedString2);
                    moduleBracketTally += tallyBracketsInString(parsedString2);
                  }
                //				System.out.println("Updated element bracket tally of: "
                //                       	 	               + elementBracketTally);
                trimmedModuleLine = "";
                // we run split again in case we lengthened parsedString
                tokens = parsedString.split(" ");
                // we now assemble a single line of tokens for the single element
                for (int count = 0; count < tokens.length; count++)
                  {
                    //					System.out.println(tokens[count]);
                    parsedString = tokens[count].replaceAll("[()]", " ");
                    //               tokens[count] = parsedString.trim();
                    trimmedModuleLine = trimmedModuleLine + " " +
                        parsedString.trim(); // tokens[count];
                  }
                if (trimmedModuleLine.length() > 1)
                  {
                    //	System.out.println("Trimmed module element line:" + 
                    //	trimmedModuleLine);
                  }
                //we now add this to the completed module string with a carriage return
                completeModule = completeModule + " " + 
                    trimmedModuleLine.trim() + "\n";
                // we check to see if we are at the end of the module
                if ((moduleBracketTally == 0) && inModule)
                  {
                    if (verboseMode)
                      {
                        System.out.println("End of Module");
                      }
                    inModule = false;
                    gotOneModule = true;
                    footprintsInLibrary[extractedModuleCount] = new Footprint(completeModule, legacyKicadMmMetricUnits, minimumViaAndDrillSizeNM);
                    completeModule = "";
                    extractedModuleCount++;
                  }
              }
          }

      }
    //	we close kicadLibaryFile, which wasn't used if stdin was the source of the module
    //      and wwould have been used if the user specified a module filename 
    kicadLibraryFile.close(); // we'll try it down here



    // we now have finished parsing the library file, and we have an array of footprint objects
    // that we can interogate, namely:  footprintsInLibrary[extractedModuleCount] 

    if (verboseMode)
      {
        System.out.println("Just closed the open file, now counting to: " + 
                           extractedModuleCount + " - the extracted module count\n" +
                           "versus counted modules in library: " + modulesInLibraryCount);
      }

    // we can now step through the array of footprints we generated from the kicad module(s)
    // we generate a GEDA format footprint for each of them, save each one to a module_name.fp,
    // and create a gedasymbols.org compatible HTML segment for inclusion in a user index 

    // we insert a heading for the HTML summary
    String HTMLsummaryOfConvertedModules = "<html><h2>" +
        kicadModuleFileName + "</h2>\n";

    for (int counter = 0; counter < extractedModuleCount; counter++)
      {
        if (verboseMode)
          {
            System.out.println("Footprint object array index: " + counter);
          }

        // we generate a string containing the GEDA footprint filename
        String outputFileName = footprintsInLibrary[counter].generateFootprintFilename(format);

        // we then append a listing for this particular footprint
        // to the HTML summary
        HTMLsummaryOfConvertedModules = HTMLsummaryOfConvertedModules +
            "<li><a href=\"" +
            htmlSummaryPathToConvertedModule +
            kicadModuleFileName + "/" +
            outputFileName + "\"> " +
            outputFileName + " </a> - " +
            moduleDescriptionText +
            " </li>\n";

        if (!quietMode)
          {
            System.out.println(outputFileName);
          }

        // a String variable to contain the footprint description
        String footprintData = "";

        // we start by prepending some preliminaries, which can include author credit
        // as well as licencing information, and use either the default or user
        // supplied footprint preliminary comments file 

        if (!file2.exists())
          {
            if (format.equals("pcb")) {
              footprintData = "# No element preliminaries text file found";
            }
          }
        else
          {
            Scanner footprintPreliminaryComments = new Scanner(file2);
            while (footprintPreliminaryComments.hasNext())
              {
                footprintData = footprintData + footprintPreliminaryComments.nextLine() + "\n";
              }
            footprintPreliminaryComments.close();
          }

        // we now append a generated element header and it's fields
        if (generateFontGlyphs) {

          //////////////////////////
          // glyphs were working in Kicad Module2GEDA, need to reinstate
          /////////////////////////

          /*				footprintData = "Symbol(\'"
                                        + counter
                                        need to reinstate glyphs sometime		+ "\' 12)\n(\n"
                                        + footprintsInLibrary[counter].generateGEDAglyph(magnificationRatio);*/
        } else {
          footprintData = footprintData +
              footprintsInLibrary[counter].generateFootprint(magnificationRatio, format);
        }
        // this is where we could insert some Attribute fields
        //                        System.out.println("Attribute(use-licence \"GPLv3\")");
        //                        System.out.println("Attribute(dist-licence \"unlimited\")");
        //                        System.out.println("Attribute(author xxxx)"); // default licence is GPLv3
        // but ? PCB breaks when these are inserted due to
        // ? bad formatting or ? a missing share/pcb/listLibraryContents.sh file

        // and we now finish off the footprint element with a bracket

        if (format.equals("pcb")) { //this belongs in Footprint.java
          footprintData = footprintData + ")";
        }

        if (verboseMode)
          {
            System.out.println(footprintData);
            // and we now use the toString method to return the module text
            System.out.println("\n\n" + footprintsInLibrary[counter] + "\n\n");
          }
        elementWrite(convertedKicadModulePath + outputFileName, footprintData);
        convertedFiles.add(outputFileName);
      }

    // having populated footprint objects in an array
    // we now finish off the HTML summary of the created modules

    HTMLsummaryOfConvertedModules = HTMLsummaryOfConvertedModules + "\n</ul></html>\n";
    if (verboseMode)
      {
        System.out.println(HTMLsummaryOfConvertedModules);
      }

    // and we pass the HTML to a subroutine to save the summary to disc, using either a user
    // supplied file name, or alternatively,  an auto generated name kicad_module_name-HTMLsummary.html

    elementWrite(convertedKicadModulePath + htmlSummaryFileName, HTMLsummaryOfConvertedModules);

    return convertedFiles.toArray(new String[convertedFiles.size()]);
  }

  // we need this method to see if we are at the end of an s-file module, and to see if we
  // are at the end of an s-file element, by allowing us to keep a running tally of the number
  // of L "(" and R ")" brackets
  private static int tallyBracketsInString(String arg)
  {
    int leftBrackets = 0;
    int rightBrackets = 0;

    for (int stringIndex = 0; stringIndex < arg.length(); stringIndex++)
      {
        if (arg.charAt(stringIndex) == '(')
          {
            leftBrackets++;
          }
        else if (arg.charAt(stringIndex) == ')')
          {
            rightBrackets++;
          }
      }
    return (leftBrackets - rightBrackets);
  }

}
