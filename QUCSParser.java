// QUCSParser - a utility for converting QUCS networks into gEDA/coralEDA schematics
// QUCSParser.java v1.0
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
//    QUCSParser Copyright (C) 2015,2019 Erich S. Heinzle a1039181@gmail.com

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

// LTSpice files contain components, and nets, which can be turned
// into a gschem schematic file

class QUCSParser extends CADParser {

  public QUCSParser(String filename, boolean verbose) {
    File QUCSFile = new File(filename);
    if (!QUCSFile.exists()) {
      System.exit(0);
    } else {
      System.out.println("Parsing: " + filename + " and exporting format: " + symFormat);
    }
  }

  // QUCS schematics:
  // 1) grid spacings of 20, and as little as 10
  // 2) most components 60 units high
  // 3) coord provided is centre of component
  // 4) rotation CCW +ve, 0 = 0, 1 = 90, 2 = 180, 3= 270
  // 5) +ve Y is down
  // qucs files contain components, and nets, which can be turned
  // into a gschem schematic file
  public static String [] convert(String QUCSsch) throws IOException {
    File input = new File(QUCSsch);
    Scanner inputQUCS = new Scanner(input);
    String currentLine = "";
    //    String newElement = ""; // unused
    //String newSymbol = ""; // unused
    String newSchematic = "";
    String symAttributes = "";
    // now we trim the .sch file ending off:
    String schematicName = QUCSsch.substring(0,QUCSsch.length()-4);
    long xOffset = 40000; // to centre things a bit in gschem
    long yOffset = 40000; // to centre things a bit in gschem
    //int lineCount = 0; //unused
    String lastline = "";
    long lastX = 0;
    long lastY = 0;

    // we start build a gschem schematic
    newSchematic = "v 20110115 1";

    while (inputQUCS.hasNext()
           && (lastline != null) ) {
      lastline = inputQUCS.nextLine(); // making nextLine() null safe 
      currentLine = safelyTrim(lastline); // when using gcj libs
      if (currentLine.startsWith("<Wires>")) {
        while (inputQUCS.hasNext()
               && !currentLine.startsWith("</Wires>")) {
          lastline = inputQUCS.nextLine(); // try to keep null safe 
          currentLine = safelyTrim(lastline); // when using gcj libs
          if (!currentLine.startsWith("</Wires>")) {
            SymbolNet wire = new SymbolNet(currentLine);
            newSchematic = newSchematic
                + "\n"
                + wire.toString(xOffset, yOffset);
          }
        }
      } else if (currentLine.startsWith("<Components>")) {
        // could move this code into the Symbol object in due course
        while (inputQUCS.hasNext()
               && !currentLine.startsWith("</Components>")) {
          lastline = inputQUCS.nextLine(); // try to keep null safe 
          currentLine = safelyTrim(lastline); // when using gcj libs
          if (!currentLine.startsWith("</Components>")) {
            String[] tokens = currentLine.split(" ");
            String elType = tokens[0];
            String symName = "";
            String valueField = null;
            int index1 = 0;
            int index2 = 0;
            //System.out.println("Element type: " + elType);

            // the following is just the first pass
            // bespoke symbols for QUCS purposes will be needed
            if (elType.equals("<R")) {
              symName = "resistor-QUCS.sym";
              index1 = currentLine.indexOf('"');
              index2 = currentLine.indexOf('"', index1 + 1);
              if (index1 != -1) {
                valueField = currentLine.substring(index1+1, index2);
                valueField = "value=" + valueField.replaceAll(" ", "");
              }
            } else if (elType.equals("<GND")) {
              symName = "ground-QUCS.sym";
            } else if (elType.equals("<C")) {
              symName = "capacitor-QUCS.sym";
              index1 = currentLine.indexOf('"');
              index2 = currentLine.indexOf('"', index1 + 1);
              if (index1 != -1) {
                valueField = currentLine.substring(index1+1, index2);
                valueField = "value=" + valueField.replaceAll(" ", "");
              }
            } else if (elType.equals("<L")) {
              symName = "inductor-QUCS.sym";
              index1 = currentLine.indexOf('"');
              index2 = currentLine.indexOf('"', index1 + 1);
              if (index1 != -1) {
                valueField = currentLine.substring(index1+1, index2);
                valueField = "value=" + valueField.replaceAll(" ", "");
              }
            } else if (elType.equals("<Lib")) {
              if (tokens[1].startsWith("LM3886")) {
                symName = "LM3886-opamp-QUCS.sym";
              } else if (tokens[1].startsWith("AD825")) {
                symName = "AD825-opamp-QUCS.sym";
              } else if (tokens[1].startsWith("OP")) {
                //System.out.println("op amp!... tokens[9]:"
                // + tokens[9]);
                if (tokens[9].equals("\"Ideal\"")) {
                  symName = "ideal-opamp-QUCS.sym";
                } else if (tokens[11].startsWith("\"uA741")) {
                  symName = "medium-opamp-QUCS.sym";
                } else {
                  symName = "opamp-QUCS.sym";
                }
              } else if(tokens[1].startsWith("D_")
                        || ((tokens[1].length() == 2) 
                            && tokens[1].startsWith("D"))) {
                symName = "diode-QUCS.sym";
              } else if(tokens[1].startsWith("LP1")
                        || tokens[1].startsWith("LP2")) {
                symName = "low-pass-filter-QUCS.sym";
              } else if(tokens[1].startsWith("BP2")) {
                symName = "band-pass-filter-QUCS.sym";
              } else if(tokens[1].startsWith("N2F")) {
                symName = "notch-filter-QUCS.sym";
              } else if(tokens[1].startsWith("HP1")
                        || tokens[1].startsWith("HP2")) {
                symName = "high-pass-filter-QUCS.sym";
              } else if(tokens[1].startsWith("AP1F")
                        || tokens[1].startsWith("AP2F")) {
                symName = "all-pass-filter-QUCS.sym";
              } else if(tokens[1].startsWith("LIM")) {
                if (tokens[11].startsWith("Hard")) {
                  symName = "hard-limiter-QUCS.sym";
                } else {
                  symName = "limiter-QUCS.sym";
                }
              } else if(tokens[1].startsWith("SQRT")) {
                symName = "square-root-QUCS.sym";
              } else if(tokens[1].startsWith("QNT")) {
                symName = "quantiser-QUCS.sym";
              } else if(tokens[1].startsWith("DIFF")) {
                symName = "differentiator-QUCS.sym";
              } else if(tokens[1].startsWith("DLY")) {
                symName = "Vdelay-QUCS.sym";
              } else if(tokens[1].startsWith("INT")) {
                symName = "integrator-QUCS.sym";
              } else if(tokens[1].startsWith("ABS")) {
                symName = "Abs-QUCS.sym";
              } else if(tokens[1].startsWith("MUL")) {
                symName = "multiplier-QUCS.sym";
              } else if(tokens[1].startsWith("VADD")) {
                symName = "VSum-QUCS.sym";
              } else if(tokens[1].startsWith("VSUB")) {
                symName = "VSub-QUCS.sym";
              } else {
                symName = "unknown-QUCS-Lib-" + tokens[1] + ".sym";
              }
            } else if (elType.equals("<Diode")) {
              symName = "diode-QUCS.sym";
            } else if (elType.equals("voltage")) {
              symName = "voltage-source-QUCS.sym";
            } else if (elType.equals("current")) {
              symName = "current-source-QUCS.sym";
            } else if (elType.equals("npn")) {
              symName = "npn-LTS.sym";
            } else {
              symName = "unknown-" + elType + "-QUCS.sym";
            }
            long xCoord = 0;
            long yCoord = 0;
            String rotation = "0";
            xCoord = (long)(10*Integer.parseInt(tokens[3]));
            yCoord = (long)(-(10*Integer.parseInt(tokens[4])));
            String elRotation = tokens[8].replaceAll(">","");
            //System.out.println("Rotation: " + elRotation);
            if (elRotation.equals("1")) {
              rotation = "90";
            } else if (elRotation.equals("2")) {
              rotation = "180";
            } else if (elRotation.equals("3")) {
              rotation = "270";
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
            lastX = xOffset + xCoord;
            lastY = yOffset + yCoord;// for use with refdes attribute
            if (tokens[1].equals("*")) {
              symAttributes = "refdes=GND";
            } else {
              symAttributes = "refdes=" + tokens[1];
            }
            SymbolText.resetSymbolTextAttributeOffsets();
            newSchematic = newSchematic
                + "\n{"
                + SymbolText.QUCSRefDesString(lastX,
                                              lastY,
                                              symAttributes);
            if (valueField != null) {
              newSchematic = newSchematic // it will be a touch lower
                  + SymbolText.QUCSValueString(lastX, // vs. refdes
                                               lastY,
                                               valueField);
              valueField = null;
            }
            newSchematic = newSchematic
                + "\n}";
          }
        }
      }
    }
    // we can now finalise the gschem schematic
    String networkName = schematicName + ".gschem.sch";
    // we now write the converted schematic data to a file
    elementWrite(networkName, newSchematic);
    String [] returnedFilename = {networkName};
    return returnedFilename;
  }
}
