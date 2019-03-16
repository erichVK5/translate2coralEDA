// symdefParser - a utility for converting symdef definitions into gEDA/coralEDA CAD elements
// symdefParser.java v1.0
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
//    symdefParser Copyright (C) 2015,2019 Erich S. Heinzle a1039181@gmail.com


import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

class symdefParser extends CADParser {

  public symdefParser(String filename, boolean verbose) {
    File symDefFile = new File(filename);
    if (!symDefFile.exists()) {
      System.exit(0);
    } else {
      System.out.println("Parsing: " + filename + " and exporting format: " + symFormat);
    }
  }

  // .symdef files provide pin mapping suitable for symbol generation
  // but do not provide package/footprint information
  public static String [] convert(String symDefFilename) throws IOException {

    File symDefFile = new File(symDefFilename);
    Scanner symDef = new Scanner(symDefFile);

    String currentLine = "";
    //String newElement = ""; // not used
    String newSymbol = "";
    String symAttributes = "";
    String elData = "";
    String elName = "";

    long xOffset = 0;
    long yOffset = 0; // used to justify symbol
    long textXOffset = 0; // used for attribute fields

    List<String> convertedFiles = new ArrayList<String>();
    List<String> textLabels = new ArrayList<String>();
    List<String> left = new ArrayList<String>();
    List<String> right = new ArrayList<String>();
    List<String> top = new ArrayList<String>();
    List<String> bottom = new ArrayList<String>();

    String currentState = "labels";
    String lastline = "";

    while (symDef.hasNext() && (lastline != null)) {
      lastline = symDef.nextLine();// make nextLine() null safe 
      currentLine = safelyTrim(lastline); // when using gcj libs
      if (currentLine.startsWith("[labels]") ||
          currentLine.startsWith("[LABELS]")) {
        currentState = "labels";
      } else if (currentLine.startsWith("[left]") ||
                 currentLine.startsWith("[LEFT]")) {
        currentState = "left";
      } else if (currentLine.startsWith("[right]") ||
                 currentLine.startsWith("[RIGHT]")) {
        currentState = "right";
      } else if (currentLine.startsWith("[top]") ||
                 currentLine.startsWith("[TOP]")) {
        currentState = "top";
      } else if (currentLine.startsWith("[bottom]") ||
                 currentLine.startsWith("[BOTTOM]")) {
        currentState = "bottom";
      } else if (currentLine.startsWith(".bus") ||
                 currentLine.startsWith(".BUS")) {
        // don't do anything
      } else if ((currentLine.length() > 1) &&
                 (!currentLine.startsWith("#"))) {
        if (currentState.equals("labels")) {
          if (currentLine.length() > 0) {
            textLabels.add(currentLine);
          }
        } else if (currentState.equals("left")) {
          left.add(currentLine);
        } else if (currentState.equals("bottom")) {
          bottom.add(currentLine);
        } else if (currentState.equals("right")) {
          right.add(currentLine);
        } else if (currentState.equals("top")) {
          top.add(currentLine);
        } 
      }
    }
    PinList pins = new PinList(0); // slots = 0
    for (String line : left) {
      SymbolPin newPin = new SymbolPin();
      newPin.populateSymDefElement(line, "R", symFormat);
      pins.addPin(newPin);
    }
    for (String line : bottom) {
      SymbolPin newPin = new SymbolPin();
      newPin.populateSymDefElement(line, "U", symFormat);
      pins.addPin(newPin);
    }
    for (String line : top) {
      SymbolPin newPin = new SymbolPin();
      newPin.populateSymDefElement(line, "D", symFormat);
      pins.addPin(newPin);
    }
    for (String line : right) {
      SymbolPin newPin = new SymbolPin();
      newPin.populateSymDefElement(line, "L", symFormat);
      pins.addPin(newPin);
    }

    // our pinsGridAligned method will make the pins nicely spaced
    // around the symbol. 
    PinList newPinList = pins.pinsGridAligned(pinSpacing);

    // now we have a list of pins, we can calculate the offsets
    // to justify the element in gschem, and justify the attribute
    // fields.
    yOffset = newPinList.minY()-pinSpacing;  // includes bounding box
    // spacing of ~ 200 takes care of the bounding box

    textXOffset = newPinList.textRHS();
    // additional bounding box extents are calculated by minY()

    for (String attr : textLabels) {
      symAttributes = symAttributes
          + SymbolText.symDefAttributeString(textXOffset, 0, attr);
    }

    newSymbol = symbolHeader(symFormat); // don;t need newElement
    //        + newElement; // we have created the header for the symbol
    //newElement = ""; //not used
    
    xOffset = 0;

    // we can now put the pieces of the symdef defined symbol together
    elName = "symDefSymbol.sym";
    elData = newSymbol   // we now add pins to the
        + newPinList.toString(xOffset,-yOffset, symFormat) // the header, and then
        + "\n"
	+ newPinList.boundingBox(0,0).toString(xOffset,-yOffset, symFormat);

    if (symFormat.equals("gschem")) {
      elData = elData + symAttributes; // the final attributes
    }

    // we now write the element to a file
    elementWrite(elName, elData);
    // add the symbol to our list of converted elements
    convertedFiles.add(elName);
    return convertedFiles.toArray(new String[convertedFiles.size()]);
  }
}
