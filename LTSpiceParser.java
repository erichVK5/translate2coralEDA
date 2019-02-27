// LTSpiceParser - a utility for converting LTSPice networks into gEDA/coralEDA schematics
// LTSpiceParser.java v1.0
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
//    LTSpiceParser Copyright (C) 2015,2019 Erich S. Heinzle a1039181@gmail.com


import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

// LTSpice files contain components, and nets, which can be turned
// into a gschem schematic file

class LTSpiceParser extends CADParser {

  private static boolean verbose = false;

  private static String format;

  public LTSpiceParser(String filename, String format, boolean verbose) {
    File LTSpiceFile = new File(filename);
    if (!LTSpiceFile.exists()) {
      System.exit(0);
    } else {
      this.format = format;
      System.out.println("Parsing: " + filename + " and exporting format: " + format);
      setPinSpacing(format); // we won't be using pin spacing or format for now
    }
    this.verbose = verbose;
  }

  // LTSpice files contain components, and nets, which can be turned
  // into a gschem schematic file
  /*grid spacing is 16
    y is +ve down i.e. need to flip y-axis
    x is +ve right
    resistors, inductors, voltage, current, are 80 long
    caps, diodes, are 64 long
    *6.25 gives 400, 500
    *12.5 gives 800, 1000
    R0 means zero degrees rotation, with origin at bottom, and symbol vertical
    need to consider which end of components LTSpice considers 1, 2 etc...
    need to consider x,y offset to centre schematic, i.e. ?  +(40000,40000)
  */
  public static String [] convert(String spiceFile) throws IOException {
    File input = new File(spiceFile);
    Scanner inputAsc = new Scanner(input);
    String currentLine = "";
    String newSchematic = "";
    String symAttributes = "";
    // now we trim the .asc file ending off:
    String schematicName = spiceFile.substring(0,spiceFile.length()-4);
    long xOffset = 40000; // to centre things a bit in gschem
    long yOffset = 40000; // to centre things a bit in gschem
    String lastline = "";
    long lastX = 0;
    long lastY = 0;

    // we start build a gschem schematic
    newSchematic = "v 20110115 1";

    String symbolAttributeSet = null;

    while (inputAsc.hasNext()
           && (lastline != null) ) {
      lastline = inputAsc.nextLine(); // making nextLine() null safe 
      currentLine = safelyTrim(lastline); // when using gcj libs
      if (currentLine.startsWith("SYMATTR")) {
        String[] tokens = currentLine.split(" ");
        if ("InstName".equals(tokens[1])) {
          symAttributes = "refdes=" + tokens[2];
          SymbolText.resetSymbolTextAttributeOffsets();
          if (symbolAttributeSet == null) {
            symbolAttributeSet = "\n{"
                + SymbolText.LTSpiceRefdesString(lastX,
                                                 lastY,
                                                 symAttributes);
          } else {
            symbolAttributeSet = symbolAttributeSet
                + SymbolText.LTSpiceRefdesString(lastX,
                                                 lastY,
                                                 symAttributes);
          }
        }
        if ("Value".equals(tokens[1])) {
          symAttributes = "value=" + tokens[2];
          if (symbolAttributeSet == null) {
            symbolAttributeSet = "\n{"
                + SymbolText.LTSpiceRefdesString(lastX,
                                                 lastY,
                                                 symAttributes);
          } else {
            symbolAttributeSet = symbolAttributeSet
                + SymbolText.LTSpiceRefdesString(lastX,
                                                 lastY,
                                                 symAttributes);
          }
        }
      } else if (currentLine.startsWith("WIRE")) {
        SymbolNet wire = new SymbolNet(currentLine);
        newSchematic = newSchematic
            + "\n"
            + wire.toString(xOffset, yOffset);
      } else if (currentLine.startsWith("SYMBOL")
                 || currentLine.startsWith("FLAG")) {
        // ? move this code into the Symbol object in due course
        if (symbolAttributeSet != null ) { // hmm, onto next symbol
          newSchematic = newSchematic
              + symbolAttributeSet
              + "\n}"; // so we finish off the last one's attributes
        }
        symbolAttributeSet = null; // reset this
        currentLine = currentLine.replaceAll("  "," ");
        String[] tokens = currentLine.split(" ");
        String elType = tokens[1];
        String symName = "";
        if (elType.equals("res")) {
          symName = "resistor-LTS.sym";
        } else if (elType.equals("cap")) {
          symName = "capacitor-LTS.sym";
        } else if (elType.equals("ind")) {
          symName = "inductor-LTS.sym";
        } else if (elType.equals("diode")) {
          symName = "diode-LTS.sym";
        } else if (elType.equals("voltage")) {
          symName = "voltage-source-LTS.sym";
        } else if (elType.equals("current")) {
          symName = "current-source-LTS.sym";
        } else if (elType.equals("npn")) {
          symName = "npn-LTS.sym";
        } else if (tokens[0].equals("FLAG")) {
          symName = "ground-LTS.sym";
        } else if (elType.startsWith("Opamps")) {
          symName = "opamp-LTS.sym";
        } else {
          symName = "unknown-" + elType + "-LTS.sym";
        }
        long xCoord = 0;
        long yCoord = 0;
        String rotation = "0";
        if (tokens[0].equals("FLAG")) { // it's a ground symbol
          xCoord = (long)(12.5*Integer.parseInt(tokens[1]));
          yCoord = (long)(-(12.5*Integer.parseInt(tokens[2])));
        } else { // not a ground symbol, an actual component
          xCoord = (long)(12.5*Integer.parseInt(tokens[2]));
          yCoord = (long)(-(12.5*Integer.parseInt(tokens[3])));
          String elRotation = tokens[4];
          if (elRotation.equals("R90")) {
            rotation = "270";
          } else if (elRotation.equals("R180")) {
            rotation = "180";
          } else if (elRotation.equals("R270")) {
            rotation = "90";
          }
        }
        newSchematic = newSchematic
            + "\n"
            + "C "
            + (xOffset + xCoord) 
            + " "
            + (yOffset + yCoord)
            + " "
            + "1" + " "
            + rotation + " "
            + "0" + " " 
            + symName;
        // will need to process "SYMATTR" here in due course
        //        while (inputAsc.hasNext() &&
        //   (!currentLine.startsWith("[")
        //    || (lineCount == 0))) {
        //currentLine = inputAsc.nextLine().trim();
        //          if (!currentLine.startsWith("[")) {
        //            }
        lastX = xOffset + xCoord;
        lastY = yOffset + yCoord;// for use with attributes, if any
      }
    }
    // we can now finalise the gschem schematic
    //symAttributes = symAttributes
    // + SymbolText.BXLAttributeString(newPinList.textRHS(),0, FPAttr);
    String networkName = schematicName + ".sch";
    // we now write the converted schematic data to a file
    elementWrite(networkName, newSchematic);
    String [] returnedFilename = {networkName};
    return returnedFilename;
  }

}
