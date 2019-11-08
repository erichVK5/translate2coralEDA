// hkpParser - a utility for converting veribest definitions into gEDA/coralEDA CAD elements
// hkpParser.java v1.0
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

class hkpParser extends CADParser {

  private static boolean verbose = false;

  static float magnificationRatio = 1.0f;
  static String exportPath = "Converted/";
  
  public hkpParser(String filename, boolean verbose) {
    File hkpFile = new File(filename);
    if (!hkpFile.exists()) {
      System.exit(0);
    } else {
      System.out.println("Parsing: " + filename + " and exporting formats: !" + fpFormat + ", " + symFormat);
    }
    this.verbose = verbose;
  }

  // HKP files provide both pin mapping suitable for symbol
  // generation as well as package/footprint information
  // For now, we only convert symbols, since pcb-rnd takes
  // care of footprint import 
  public static String [] convert(String hkpFile) throws IOException {

    File HKP = new File(hkpFile);
    Scanner textHKP = new Scanner(HKP);

    String symbolName = "NO_NAME_FOUND";
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

    while (textHKP.hasNextLine() && (lastline != null)) {
      lastline = textHKP.nextLine(); // making nextLine() null safe 
      currentLine = safelyTrim(lastline); // when using gcj libs

      if (currentLine.startsWith("*CELL_OPEN")) {
        String [] tokens = currentLine.split(" ");
        symbolName = tokens[1].replaceAll("[\"]","");
        List<String> silkFeatures = new ArrayList<String>();
        List<String> attributeFields = new ArrayList<String>();
        pins = new PinList(0); // slots = 0
        while (textHKP.hasNextLine() && (lastline != null) &&
               !currentLine.startsWith("*CELL_CLOSE")) {
          lastline = textHKP.nextLine();// making nextLine() null safe 
          currentLine = safelyTrim(lastline); // when using gcj libs
          if (currentLine.startsWith("*PIN")) {
            //System.out.println("#Making new pin: " + currentLine);
            SymbolPin latestPin = new SymbolPin();
            latestPin.populateHKPElement(currentLine);
            pins.addPin(latestPin);
          } else if (currentLine.startsWith("*GFX_LINE")) {
            silkFeatures.add(currentLine);
          } else if (currentLine.startsWith("*TEXT")) {
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
          if (feature.startsWith("*GFX_LINE")) {
            SymbolPolyline symbolLine = new SymbolPolyline();
            symbolLine.populateHKPElement(feature);
            newElement = newElement
                + "\n" + symbolLine.toString(0,-yOffset,symFormat);
          } 
        }

        newSymbol = symbolHeader(symFormat)
            + newElement; // we have created the header for the symbol
        newElement = "";
        silkFeatures.clear();
        attributeFields.clear();
        
        // we can now put the pieces of the HKP defined symbol together
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

//  May get used uf we decided to try and convert footprints in hkp files
//  but pcb-rnd does a better job 
//    List<String> footprintsExported
//        = Arrays.asList(Footprint.exportFootprints(hkpFile, footprints, fpFormat,
//                                                   magnificationRatio, exportPath,
//                                                   true, verbose));
//    convertedFiles.addAll(footprintsExported);    
    return convertedFiles.toArray(new String[convertedFiles.size()]);
  } 
}
