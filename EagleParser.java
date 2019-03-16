// EagleParser - a utility for converting eagle elements into coralEDA CAD elements
// EagleParser.java v1.0
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
//    EagleParser Copyright (C) 2015,2019 Erich S. Heinzle a1039181@gmail.com
//    translate2coralEDA, translate2geda Copyright (C) 2015, 2019
//    Erich S. Heinzle a1039181@gmail.com


import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

// Eagle libraries provide footprint data, pin mapping, and
// schematic symbol data
// This parser can also be used for XML .brd files to extract contents

class EagleParser extends CADParser {

  private static boolean verbose = false;

  static String exportPath = "Converted/";
  static float magnificationRatio = 1.0f;

  public EagleParser(String filename, boolean verbose) {
    File symDefFile = new File(filename);
    if (!symDefFile.exists()) {
      System.exit(0);
    } else {
      System.out.println("Parsing: " + filename + " and exporting formats: " + fpFormat + ", " + symFormat);
    }
    this.verbose = verbose;
  }

  public static String [] convert(String LBRFile) throws IOException {
    File EagleLBR = new File(LBRFile);
    Scanner eagleLib = new Scanner(EagleLBR);

    String currentLine = "";
    String newElement = "";
    String newSymbol = "";
    String symAttributes = "";
    String elData = "";
    String elName = "";
    EagleLayers layers = new EagleLayers();
    EagleDeviceSet deviceSets = null;

    ArrayList<Footprint> footprints = new ArrayList<Footprint>();
    Footprint footprint;
    
    PinList pins = new PinList(0); // slots = 0

    List<String> convertedFiles = new ArrayList<String>();
    ArrayList<String> layerDefs = new ArrayList<String>();
    ArrayList<String> packageDefs = new ArrayList<String>();
    ArrayList<String> symbolDefs = new ArrayList<String>();
    ArrayList<String> deviceSetDefs = new ArrayList<String>();


    long xOffset = 0;
    long yOffset = 0; // used to justify symbol
    long textXOffset = 0; // used for attribute fields

    String lastline = "";

    while (eagleLib.hasNextLine() && (lastline != null)) {
      lastline = eagleLib.nextLine();// make nextLine() null safe 
      currentLine = safelyTrim(lastline); // when using gcj libs
      if (currentLine.startsWith("<layers>")) {
        while (eagleLib.hasNextLine() &&
               !currentLine.startsWith("</layers>")) {
          currentLine = eagleLib.nextLine().trim();
          layerDefs.add(currentLine);
        }
      } else if (currentLine.startsWith("<packages>")) {
        while (eagleLib.hasNextLine() &&
               !currentLine.startsWith("</packages>")) {
          currentLine = eagleLib.nextLine().trim();
          packageDefs.add(currentLine);
        }
      } else if (currentLine.startsWith("<symbols>")) {
        while (eagleLib.hasNextLine() &&
               !currentLine.startsWith("</symbols>")) {
          currentLine = eagleLib.nextLine().trim();
          symbolDefs.add(currentLine);
        }
      } else if (currentLine.startsWith("<devicesets>")) {
        currentLine = eagleLib.nextLine().trim();
        while (eagleLib.hasNextLine() &&
               !currentLine.startsWith("</devicesets>")) {
          if (currentLine.startsWith("<deviceset ") &&
              eagleLib.hasNextLine()) {
            String currentGates = "";
            currentLine = eagleLib.nextLine().trim();
            while (eagleLib.hasNextLine() &&
                   !currentLine.startsWith("</deviceset>")) {
              if (currentLine.startsWith("<gates>")) {
                currentGates = currentLine + "\n";
                currentLine = eagleLib.nextLine().trim();
                while (eagleLib.hasNextLine() &&
                       !currentLine.startsWith("</gates>")) {
                  currentGates = currentGates + currentLine + "\n";
                  // System.out.println("Found some gates");
                  currentLine = eagleLib.nextLine().trim();
                }
                currentGates = currentGates + currentLine + "\n";
                //System.out.println("Found a set of gates");
              } else if (currentLine.startsWith("<device ")) {
                String currentDef = currentLine + "\n";
                // System.out.println("Found a device set line");
                currentLine = eagleLib.nextLine().trim();
                while (eagleLib.hasNextLine() &&
                       !currentLine.startsWith("</device>")) {
                  currentDef = currentDef + currentLine + "\n";
                  // System.out.println("Found a device set line");
                  currentLine = eagleLib.nextLine().trim();
                }
                currentDef = currentGates +
                    currentDef + currentLine + "\n";
                deviceSetDefs.add(currentDef);
                //System.out.println("Found a device set:");
                //System.out.println(currentDef);
              }
              lastline = eagleLib.nextLine();//make nextLine()nullsafe 
              currentLine = safelyTrim(lastline);// when using gcj libs
            }
          }
          lastline = eagleLib.nextLine();// make nextLine() null safe 
          currentLine = safelyTrim(lastline); // when using gcj libs
        } //resume while loop
      }
    }

    // first, we create the layer definition object
    // which is used for extraction of other elements
    if (layerDefs.size() == 0) {
      System.out.println("This eagle library appears to be missing "
                         + "layer definitions needed for conversion");
    }
    layers = new EagleLayers(layerDefs);

    // we now turn our ArrayList into a string to pass to a scanner
    // object
    String packageDefString = "";
    for (String packageLine : packageDefs) {
      packageDefString = packageDefString + packageLine + "\n";
    }    

    // next, we parse and process the package (=FP )defs
    if (verbose) {
      System.out.println("Moving onto FPs");
    }

    Scanner packagesBundle = new Scanner(packageDefString);

    lastline = "";
    while (packagesBundle.hasNextLine() && (lastline != null)) {
      lastline = packagesBundle.nextLine();//make nextLine() null safe 
      currentLine = safelyTrim(lastline); // when using gcj libs
      if (currentLine.startsWith("<package name")) {
        String [] tokens = currentLine.split(" ");
        String FPName
            = tokens[1].replaceAll("[\">=/]","").substring(4);
        footprint = new Footprint(FPName);
        while (packagesBundle.hasNextLine() &&
               !currentLine.startsWith("</package>")) {
          currentLine = packagesBundle.nextLine().trim();
          if (currentLine.startsWith("<smd") ||
              currentLine.startsWith("<pad") ||
              currentLine.startsWith("<hole")) {
            Pad newPad = new Pad();
            newPad.populateEagleElement(currentLine);
            footprint.add(newPad);
          } else if (currentLine.startsWith("<wire") &&
                     layers.isDrawnTopSilk(currentLine)) {
            if (!currentLine.contains("curve=")) {
              DrawnElement silkLine = new DrawnElement();
              silkLine.populateEagleElement(currentLine);
              footprint.add(silkLine);
            } else {
              Arc silkArc = new Arc();
              silkArc.populateEagleElement(currentLine);
              footprint.add(silkArc);              
            }
          } else if (currentLine.startsWith("<rectangle") &&
                     layers.isDrawnTopSilk(currentLine)) {
            DrawnElement [] silkLines
                = DrawnElement.eagleRectangleAsLines(currentLine);
            for (DrawnElement side : silkLines) {
              footprint.add(side);
            }
          } else if (currentLine.startsWith("<circle") &&
                     layers.isDrawnTopSilk(currentLine)) {
            Circle silkCircle = new Circle();
            silkCircle.populateEagleElement(currentLine);
            footprint.add(silkCircle);;
          } else if (currentLine.startsWith("<polygon") &&
                     (layers.isTopCopper(currentLine) ||
                      layers.isBottomCopper(currentLine) ||
                      layers.isDrawnTopSilk(currentLine))) {
            String polyDef = currentLine; 
            while (packagesBundle.hasNextLine() &&
                   !currentLine.startsWith("</polygon")) {
              currentLine = packagesBundle.nextLine().trim();
              polyDef = polyDef + currentLine;
            }
            PolyPour polyPour = new PolyPour();
            polyPour.populateEagleElement(polyDef);
            footprint.add(polyPour);
          }
          
        } // end if for "<package name"

        footprints.add(footprint);
      } // end of this particular package while loop
    } // end of packagesBundle while loop


    // we now create the set of eagle devices from which pin mappings
    // can be retrieved
    if (deviceSetDefs.size() != 0) {
      if (verbose) {
        System.out.println("About to create EagleDeviceSet object"); 
      }
      deviceSets = new EagleDeviceSet(deviceSetDefs);
      if (verbose) {
        System.out.println("Created EagleDeviceSet object");
      }
    } // we leave it as null if none found during parsing

    if (verbose) {
      System.out.println("About to process SymbolDefs"); 
    }
    // we now turn our symbol ArrayList into a string to pass
    // to a scanner object
    // StringBuilder might perform better here...
    String symbolDefString = "";
    for (String symbolLine : symbolDefs) {
      symbolDefString = symbolDefString + symbolLine + "\n";
    }

    Scanner symbolBundle = new Scanner(symbolDefString);

    // next, we parse and process the package (=FP )defs
    if (verbose) {
      System.out.println("About to create individual symbols"); 
    }

    lastline = "";
    while (symbolBundle.hasNextLine()) {
      lastline = symbolBundle.nextLine();// make nextLine() null safe 
      currentLine = safelyTrim(lastline); // when using gcj libs
      if (currentLine.startsWith("<symbol ")) {
        String [] tokens = currentLine.split(" ");
        String symbolName // name="......"
            = tokens[1].substring(6).replaceAll("[\"\\/>]","");
        if (verbose) {
          System.out.println("Found symbol name:" + symbolName);
        }
        List<String> silkFeatures = new ArrayList<String>();
        List<String> attributeFields = new ArrayList<String>();
        pins = new PinList(0); // slots = 0
        while (symbolBundle.hasNextLine() &&
               !currentLine.startsWith("</symbol")) {
          currentLine = symbolBundle.nextLine().trim();
          if (currentLine.startsWith("<pin")) {
            //System.out.println("#Making new pin: " + currentLine);
            SymbolPin latestPin = new SymbolPin();
            latestPin.populateEagleElement(currentLine);
            pins.addPin(latestPin);
          } else if (currentLine.startsWith("Line") ||
                     currentLine.startsWith("Arc (Layer TOP_SILK")) {
            //silkFeatures.add(currentLine);
          } else if (currentLine.startsWith("Attribute")) {
            //attributeFields.add(currentLine);
          }
        }

        // now we have a list of pins, we can calculate the offsets
        // to justify the element in gschem, and justify the attribute
        // fields.

        // we may need to turn this off if converting entire schematics
        // at some point in the future
        if (!pins.empty()) {
          xOffset = pins.minX();
          yOffset = pins.minY()-200;  // includes bounding box
          // spacing of ~ 200 takes care of the bounding box
          textXOffset = pins.textRHS(); //??? broken for some reason
        }
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
                + "\n" + symbolLine.toString(0,-yOffset, symFormat);
          } 
        }

        String newSymbolHeader = symbolHeader(symFormat)
            + newElement; // we have created the header for the symbol
        newElement = "";
        String FPField = "";

        // first, we see if there are devicedefs for this symbol
        // mapping its pins onto footprint pads
        //System.out.println("Requesting device defs for " +
        //                   symbolName);
        ArrayList<EagleDevice> symbolDeviceDefs 
            = deviceSets.supplyDevicesFor(symbolName);

        // we have two scenarios, the first is that we have symbols
        // +/- pins defined but no pin mapping for
        // them (= symbolDeviceDef)
        // the second is we have 1 or more pin mappings defined for
        // the symbol found
        if (symbolDeviceDefs.size() == 0) {
          // it seems we have no pin mappings applicable to the symbol
          System.out.println("No matching footprint specified for: "
                             + symbolName);
          attributeFields.add("footprint=unknown");

          SymbolText.resetSymbolTextAttributeOffsets();
          // we now generate the text attribute fields for the current
          // symbol
          for (String attr : attributeFields) {
            symAttributes = symAttributes
                + SymbolText.BXLAttributeString(textXOffset, 0, attr);
          }

          elData = "";
          if (!pins.empty()) { // sometimes Eagle has rubbish symbols
            // with no pins, so we test before we build the symbol
            // note that we did not have a pin mapping we could apply
            // so pin numbers will default to zero
            elData = pins.toString(-xOffset,-yOffset, symFormat)
                //... header, and then
                + "\n"
                + pins.calculatedBoundingBox(0,0).toString(-xOffset,-yOffset, symFormat);
          }
            
          // add some attribute fields
          newSymbol = newSymbolHeader + elData + symAttributes;

          // customise symbol filename to reflect applicable FP
          elName = symbolName + ".sym";
          
          // we now write the element to a file
          elementWrite(elName, newSymbol);
          
          // add the symbol to our list of converted elements
          convertedFiles.add(elName);
          
          silkFeatures.clear();
          attributeFields.clear();
          symAttributes = "";
        
        } else { // we get here if >0 symbolDeviceDefs
          // TODO
          // need to generate n symbols for n pin mappings
          // also need to sort out FPName for each variant
          // also need to sort out sane naming convention for
          // the variants of the symbol
          
          for (int index = 0;
               index < symbolDeviceDefs.size();
               index++) {
            if (deviceSets != null &&
                deviceSets.containsSymbol(symbolName) ) {
              //System.out.println("About to renumber pins for "
              //                   + symbolName); 
              if (!pins.empty()) { // sometimes Eagle has odd symbols
                // for fiducials and so forth
                pins.applyEagleDeviceDef(symbolDeviceDefs.get(index));
                textXOffset = pins.textRHS(); // for text justification
              } 
              FPField = symbolDeviceDefs.get(index).supplyFPName();
              attributeFields.add("footprint=" + FPField);
              FPField = "_" + FPField;
            } // start with the first device def to begin with
            
            // when batch converting, we avoid incrementing the
            // justification of text from one symbol to the next, so 
            // we reset the offset variable for each new symbol thusly
            SymbolText.resetSymbolTextAttributeOffsets();
            // we no generate the text attribute fields for the current
            // symbol
            for (String attr : attributeFields) {
              symAttributes = symAttributes
                  + SymbolText.BXLAttributeString(textXOffset, 0, attr);
            }
            
            elData = "";
            if (!pins.empty()) { // sometimes Eagle has rubbish symbols
              // with no pins, so we test before we build the symbol
              elData = pins.toString(-xOffset,-yOffset,symFormat)
                  //... header, and then
                  + "\n"
                  + pins.calculatedBoundingBox(0,0).toString(-xOffset,-yOffset, symFormat);
            }
            
            // add some attribute fields
            newSymbol = newSymbolHeader + elData + symAttributes;
            // customise symbol filename to reflect applicable FP
            elName = symbolName + FPField + ".sym";
            
            // we now write the element to a file
            elementWrite(elName, newSymbol);
            
            // add the symbol to our list of converted elements
            convertedFiles.add(elName);
          
            attributeFields.clear();
            symAttributes = "";
          } // end of for loop for pin mappings
          silkFeatures.clear();
        } // end of else statement for >=1 pin mappings

      }
    }

    List<String> footprintsExported
        = Arrays.asList(Footprint.exportFootprints(LBRFile, footprints,
                                                   fpFormat,
                                                   magnificationRatio,
                                                   exportPath,
                                                   true, verbose));
    convertedFiles.addAll(footprintsExported);    
    return convertedFiles.toArray(new String[convertedFiles.size()]);
  } 

}
