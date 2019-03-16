// BSDLParser - a utility for converting BSDL definitions into gEDA/coralEDA CAD elements
// BSDLParser.java v1.0
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
//    BSDLParser Copyright (C) 2015,2019 Erich S. Heinzle a1039181@gmail.com

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

class BSDLParser extends CADParser {

  public BSDLParser(String filename, boolean verbose) {
    File symDefFile = new File(filename);
    if (!symDefFile.exists()) {
      System.exit(0);
    } else {
      System.out.println("Parsing: " + filename + " and exporting format: " + symFormat);
    }
  }

  // BSDL files provide pin mapping suitable for symbol generation
  // but do not provide package/footprint information

  public static String [] convert(String BSDLFile) throws IOException {
    File inputBSDL = new File(BSDLFile);
    Scanner textBSDL = new Scanner(inputBSDL);
    String currentLine = "";
    List<String> portPinDef = new ArrayList<String>();
    String newSymbol = "";
    String symAttributes = "";
    String FPName = "DefaultFPName";
    String elName = null;
    String elData = "";
    PinList pins = new PinList(0); // slots = 0 for BDSL data

    List<String> convertedFiles = new ArrayList<String>();

    long xOffset = 0;
    long yOffset = 0;
    String lastline = "";

    while (textBSDL.hasNextLine() && (lastline != null)) {
      lastline = textBSDL.nextLine();// make nextLine() null safe 
      currentLine = safelyTrim(lastline); // when using gcj libs
      if (currentLine.startsWith("entity")) {
        String [] tokens = currentLine.split(" ");
        String symName = tokens[1].replaceAll("[\"]","");
        while (textBSDL.hasNextLine()
               && !currentLine.startsWith("end")) {
          lastline = textBSDL.nextLine();// make nextLine() null safe 
          currentLine = safelyTrim(lastline); // when using gcj libs
          if (currentLine.startsWith("constant")) {
            currentLine = currentLine.replaceAll("[:=]"," ");
            tokens = currentLine.split(" ");
            FPName = tokens[1].replaceAll("[\"]","_");
            pins = new PinList(0); // slots = 0
            boolean lastLine = false;
            while (textBSDL.hasNextLine()
                   && !lastLine) {
              lastline = textBSDL.nextLine();//make nextLine()nullsafe 
              currentLine = safelyTrim(lastline);// when using gcj libs
              if ((currentLine.length() != 0) ) {
                //                  && !currentLine.equals(" ") ) {
                SymbolPin latestPin = new SymbolPin();
                latestPin.populateBSDLElement(currentLine, symFormat);
                pins.addPin(latestPin);
                if (currentLine.endsWith(";")) {
                  lastLine = true;
                }
              }
            }
          } else if (currentLine.startsWith("port (")) {
            boolean endOfPinDef = false;
            while (textBSDL.hasNextLine() &&
                   !endOfPinDef) {
              lastline = textBSDL.nextLine();//make nextLine()nullsafe 
              currentLine = safelyTrim(lastline);// when using gcj libs
              if (currentLine.startsWith(")")) {
                endOfPinDef = true;
              } else {
                portPinDef.add(currentLine);
              }
            }
          }
        }
        
        pins.setBSDPinType(portPinDef.toArray(new String[portPinDef.size()]));

        PinList newPinList = pins.createDILSymbol(symFormat);
        // with a pin list, we can now calculate text label positions
        long textRHSOffset = newPinList.textRHS();
        yOffset = newPinList.minY();// to justify the symbol in gschem 
        // header
        newSymbol = "v 20110115 1";
        // next some attributes
        symAttributes = symAttributes
            + SymbolText.BXLAttributeString(textRHSOffset, 0, "footprint=" + FPName)
            + SymbolText.BXLAttributeString(textRHSOffset, 0, "refdes=U?")
            + SymbolText.BXLAttributeString(textRHSOffset, 0, "documentation=" + BSDLFile);

        // we now build the symbol
        elData = newSymbol   // we now add pins to the...
            + newPinList.toString(xOffset,yOffset, symFormat)
            //... header, and then
            + "\n"
            + newPinList.calculatedBoundingBox(0,0).toString(0,yOffset,symFormat)
            + symAttributes;
        elName = symName + ".sym";

        // we now write the element to a file
        elementWrite(elName, elData);
        convertedFiles.add(elName);

        symAttributes = ""; // reset symbol data if batch processing
        // TODO - might be nice to reset BSDL coords in SymbolPinClass
        // if batch converting; probably not essential for usual use
      }
    }
    return convertedFiles.toArray(new String[convertedFiles.size()]);
  }
}
