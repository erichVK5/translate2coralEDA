// IBISParser - a utility for converting IBIS definitions into gEDA/coralEDA CAD elements
// IBISParser.java v1.0
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
//    IBISParser Copyright (C) 2015,2019 Erich S. Heinzle a1039181@gmail.com

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

class IBISParser extends CADParser {

  public IBISParser(String filename, boolean verbose) {
    File symDefFile = new File(filename);
    if (!symDefFile.exists()) {
      System.exit(0);
    } else {
      System.out.println("Parsing: " + filename + " and exporting format: " + symFormat);
    }
  }

  // IBIS files provide pin mapping suitable for symbol generation
  // but do not provide package/footprint information
  public static String [] convert(String IBISFile) throws IOException {
    File input = new File(IBISFile);
    Scanner inputIBIS = new Scanner(input);
    String currentLine = "";
    String newSymbol = "";
    String symAttributes = "";
    String FPName = "DefaultFPName";
    // now we trim the .ibs file ending off:
    String symName = IBISFile.substring(0,IBISFile.length()-4);
    PinList pins = new PinList(0); // slots = 0 for IBIS data

    long xOffset = 0;
    long yOffset = 0;
    boolean extractedSym = false;
    int lineCount = 0;
    String lastline = ""; 
    while (inputIBIS.hasNext()
           && !extractedSym
           && (lastline != null)) {
      lastline = inputIBIS.nextLine(); // making nextLine() null safe 
      currentLine = safelyTrim(lastline); // when using gcj libs
      if (currentLine.startsWith("[Pin]")) {
        while (inputIBIS.hasNext()
               && (lastline != null)
               && (!currentLine.startsWith("[") || (lineCount == 0))) {
          lastline = inputIBIS.nextLine();// make nextLine() null safe 
          currentLine = safelyTrim(lastline); // when using gcj libs
          lineCount++;
          if (!currentLine.startsWith("[")) {
            // the pin mapping info ends at the next [] marker
            pins = new PinList(0); // slots = 0
            //boolean lastLine = false; //unused
            while (inputIBIS.hasNext() &&
                   !extractedSym) {
              // we make sure it isn't a comment line, i.e. "|" prefix
              if (!currentLine.startsWith("|")) {
                SymbolPin latestPin = new SymbolPin();
                latestPin.populateIBISElement(currentLine, symFormat);
                pins.addPin(latestPin);
              }
              lastline = inputIBIS.nextLine();//makenextLine()nullsafe 
              currentLine = safelyTrim(lastline);// when using gcj libs
              if (currentLine.startsWith("[")) {
                extractedSym = true;
              }
            }
          }
        }
      }
    }
    PinList newPinList = pins.createDILSymbol(symFormat);

    // we can now build the final gschem symbol
    newSymbol = symbolHeader(symFormat);
    String FPAttr = "footprint=" + FPName;
    symAttributes = symAttributes
        + SymbolText.BXLAttributeString(newPinList.textRHS(),0, FPAttr);       
    String elData = newSymbol   // we now add pins to the header...
        + newPinList.toString(xOffset,yOffset,symFormat)
        // remembering that we built this symbol with coords of
        // our own choosing, i.e. well defined y coords, so don't need
        // to worry about justifying it to display nicely in gschem
        // unlike BXL or similar symbol definitions
        + "\n"
        + newPinList.calculatedBoundingBox(0,0).toString(0,0,symFormat)
        + symAttributes;
    String elName = symName + ".sym";

    // we now write the element to a file
    elementWrite(elName, elData);
    String [] returnedFilename = {elName};
    return returnedFilename;
  }

}
