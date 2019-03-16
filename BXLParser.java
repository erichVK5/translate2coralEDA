// BXLParser - a utility for converting BXL definitions into gEDA/coralEDA CAD elements
// BXLParser.java v1.0
//
// based on TranslateToGEDA.java
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
//    BXLParser Copyright (C) 2015,2019 Erich S. Heinzle a1039181@gmail.com

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

// Eagle libraries provide footprint data, pin mapping, and
// schematic symbol data
// This parser can also be used for XML .brd files to extract contents

class BXLParser extends CADParser {

  private static boolean verbose = false;

  static float magnificationRatio = 1.0f;
  static String exportPath = "Converted/";
  
  public BXLParser(String filename, boolean verbose) {
    File BXLFile = new File(filename);
    if (!BXLFile.exists()) {
      System.exit(0);
    } else {
      System.out.println("Parsing: " + filename + " and exporting formats: " + fpFormat + ", " + symFormat);
    }
    this.verbose = verbose;
  }

  // BXL files provide both pin mapping suitable for symbol
  // generation as well as package/footprint information
  public static String [] convert(String BXLFile) throws IOException {

    SourceBuffer buffer = new SourceBuffer(BXLFile); 
    Scanner textBXL = new Scanner(buffer.decode());

    String currentLine = "";
    String newElement = "";
    String newSymbol = "";
    String symAttributes = "";
    String elData = "";
    String elName = "";
    PadStackList padStacks = new PadStackList();
    PinList pins = new PinList(0); // slots = 0
    List<String> convertedFiles = new ArrayList<String>();
    ArrayList<Footprint> footprints = new ArrayList<Footprint>();
    Footprint footprint;

    long xOffset = 0;
    long yOffset = 0; // used to justify symbol
    long textXOffset = 0; // used for attribute fields
    String lastline = "";

    while (textBXL.hasNextLine() && (lastline != null)) {
      lastline = textBXL.nextLine(); // making nextLine() null safe 
      currentLine = safelyTrim(lastline); // when using gcj libs
      if (currentLine.startsWith("PadStack")) {
        newElement = currentLine;
        while (textBXL.hasNextLine() && (lastline != null) &&
               !currentLine.startsWith("EndPadStack")) {
          lastline = textBXL.nextLine();//make nextLine() null safe 
          currentLine = safelyTrim(lastline); // when using gcj libs
          newElement = newElement + "\n" + currentLine;
        }
        padStacks.addPadStack(newElement);
        newElement = ""; // reset the variable
      } else if (currentLine.startsWith("Pattern ")) {
        String [] tokens = currentLine.split(" ");
        String FPName = tokens[1].replaceAll("[\"]","");
	FPName = FPName.replaceAll("[ /]","_"); // seriously, who puts slashes in filenames //
        footprint = new Footprint(FPName);
        while (textBXL.hasNextLine() && (lastline != null) &&
               !currentLine.startsWith("EndPattern")) {
          lastline = textBXL.nextLine();// making nextLine() null safe 
          currentLine = safelyTrim(lastline); // when using gcj libs
          if (currentLine.startsWith("Pad")) {
            //System.out.println("#Making new pad: " + currentLine);
            Pad newPad = padStacks.GEDAPad(currentLine);
            footprint.add(newPad);
            //            footprint.pads.add(newPad);
          } else if (currentLine.startsWith("Line (Layer TOP_SILK")) {
            DrawnElement silkLine = new DrawnElement();
            silkLine.populateBXLElement(currentLine);
            footprint.add(silkLine);
          } else if (currentLine.startsWith("Arc (Layer TOP_SILK")) {
            Arc silkArc = new Arc();
            silkArc.populateBXLElement(currentLine);
            footprint.add(silkArc);
          }
        }

        footprints.add(footprint);
        newElement = ""; // reset the variable for batch mode

      } else if (currentLine.startsWith("Symbol ")) {
        //String [] tokens = currentLine.split(" "); //unused
        //String SymbolName = tokens[1].replaceAll("[\"]","");// unused
        List<String> silkFeatures = new ArrayList<String>();
        List<String> attributeFields = new ArrayList<String>();
        pins = new PinList(0); // slots = 0
        while (textBXL.hasNextLine() && (lastline != null) &&
               !currentLine.startsWith("EndSymbol")) {
          lastline = textBXL.nextLine();// making nextLine() null safe 
          currentLine = safelyTrim(lastline); // when using gcj libs
          if (currentLine.startsWith("Pin")) {
            //System.out.println("#Making new pin: " + currentLine);
            SymbolPin latestPin = new SymbolPin();
            currentLine = currentLine + " " +
                textBXL.nextLine().trim() + " " +
                textBXL.nextLine().trim(); // we combine the 3 lines
            latestPin.populateBXLElement(currentLine);
            pins.addPin(latestPin);
          } else if (currentLine.startsWith("Line") ||
                     currentLine.startsWith("Arc (Layer TOP_SILK")) {
            silkFeatures.add(currentLine);
          } else if (currentLine.startsWith("Attribute")) {
            attributeFields.add(currentLine);
          }
        }

        // now we have a list of pins, we can calculate the offsets
        // to justify the element in gschem, and justify the attribute
        // fields.
        yOffset = pins.minY()-200;  // includes bounding box
        // spacing of ~ 200 takes care of the bounding box
        textXOffset = pins.textRHS();
        // additional bounding box extents are calculated by minY()

        for (String feature : silkFeatures) {
          if (feature.startsWith("Arc (Layer TOP_SILKSCREEN)")) {
            Arc silkArc = new Arc();
            silkArc.populateBXLElement(feature);
            newElement = newElement
                + silkArc.generateGEDAelement(0,-yOffset,1.0f);
          } else if (feature.startsWith("Line")) {
            SymbolPolyline symbolLine = new SymbolPolyline();
            symbolLine.populateBXLElement(feature);
            newElement = newElement
                + "\n" + symbolLine.toString(0,-yOffset,symFormat);
          } 
        }

        for (String attr : attributeFields) {
          symAttributes = symAttributes
              + SymbolText.BXLAttributeString(textXOffset, 0, attr);
        }

        newSymbol = symbolHeader(symFormat)
            + newElement; // we have created the header for the symbol
        newElement = "";
        silkFeatures.clear();
        attributeFields.clear();
        
      } else if (currentLine.startsWith("Component ")) {
        // we now parse the other attributes for the component
        String [] tokens = currentLine.split(" ");
        String symbolName = tokens[1].replaceAll("[\"]","");
	symbolName = symbolName.replaceAll("[ /]","_"); // c'mon, slashes in filenames? really? //
        while (textBXL.hasNextLine() && (lastline != null) &&
               !currentLine.startsWith("EndComponent")) {
          lastline = textBXL.nextLine();// making nextLine() null safe 
          currentLine = safelyTrim(lastline); // when using gcj libs
          if (currentLine.startsWith("Attribute")) {
            //SymbolText attrText = new SymbolText();
            //attrText.populateBXLElement(currentLine);
            symAttributes = symAttributes
                + SymbolText.BXLAttributeString(textXOffset, 0, currentLine);
          } else if (currentLine.startsWith("RefDesPrefix")) {
            currentLine = currentLine.replaceAll(" ", "");
            currentLine = currentLine.split("\"")[1];
            String refDesAttr = "refdes=" + currentLine + "?";
            symAttributes = symAttributes
                + SymbolText.BXLAttributeString(textXOffset, 0, refDesAttr);
          } else if (currentLine.startsWith("PatternName")) {
            currentLine = currentLine.replaceAll(" ", "");
            currentLine = currentLine.split("\"")[1];
            String FPAttr = "footprint=" + currentLine;
            symAttributes = symAttributes
                + SymbolText.BXLAttributeString(textXOffset, 0, FPAttr);
          } else if (currentLine.startsWith("AlternatePattern")) {
            currentLine = currentLine.replaceAll(" ", "");
            currentLine = currentLine.split("\"")[1];
            String AltFPAttr = "alt-footprint=" + currentLine;
            symAttributes = symAttributes
                + SymbolText.BXLAttributeString(textXOffset, 0, AltFPAttr);
          } else if (currentLine.startsWith("CompPin ")) {
            pins.setBXLPinType(currentLine);
          }
        }

        // we can now put the pieces of the BXL defined symbol together
        elName = symbolName + ".sym";
        elData = newSymbol   // we now add pins to the
            + pins.toString(0,-yOffset, symFormat) // the header, and then
            + symAttributes; // the final attributes

        // we now write the element to a file
        elementWrite(elName, elData);
        // add the symbol to our list of converted elements
        convertedFiles.add(elName);
        // and we rest the variable for the next symbol
        symAttributes = "";
      }
    }

    List<String> footprintsExported
        = Arrays.asList(Footprint.exportFootprints(BXLFile, footprints, fpFormat,
                                                   magnificationRatio, exportPath,
                                                   true, verbose));
    convertedFiles.addAll(footprintsExported);    
    return convertedFiles.toArray(new String[convertedFiles.size()]);
  } 
}
