// translate2coralEDA.java - a utility for converting various EDA file
// formats to coralEDA and FLOSS EDA footprints and symbols
//
// based on translate2geda.java
//
// translate2coralEDA.java v1.0
// Copyright (C) 2019 Erich S. Heinzle, a1039181@gmail.com

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
//    translate2coralEDA Copyright (C) 2019 Erich S. Heinzle a1039181@gmail.com


import java.io.*;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.List;

public class translate2coralEDA {

  static boolean verbose = false;

  public static void main (String [] args) {

    boolean textOutputOnly = false;
    boolean quietMode = false;
    String filename = "";
    String [] convertedFiles = null;

    if (args.length == 0) {
      printHelp();
      System.exit(0);
    } else {
      filename = args[0];
      for (String arg : args) {
        if (arg.equals("-t")) {
          textOutputOnly = true;
        } else if (arg.equals("-q")){
          quietMode = true;
        } else if (arg.equals("-v")){
          verbose = true;
        }
      }
    }

    if (!quietMode) {
      System.out.println("Using filename: " + filename);
    }

    // we'll now try and decide what to do with the supplied file
    // based on the file ending

    if (filename.endsWith(".bsd") ||
        filename.endsWith(".BSD")) {
      try {
        convertedFiles = parseBSDL(filename);
      } catch (Exception e) {
        defaultFileIOError(e);
      }
    } else if ((filename.endsWith(".bxl") ||
                filename.endsWith(".BXL")) && 
               textOutputOnly)  {
      textOnlyBXL(filename);
      System.exit(0);
    } else if (filename.endsWith(".bxl") ||
               filename.endsWith(".BXL"))  {
      try {
        convertedFiles = parseBXL(filename);
      } catch (Exception e) {
        defaultFileIOError(e);
      }
    } else if (filename.endsWith(".ibs") ||
               filename.endsWith(".IBS") ) {
      try {
        convertedFiles = parseIBIS(filename);
      } catch (Exception e) {
        defaultFileIOError(e);
      }
    } else if (filename.endsWith(".symdef") ||
               filename.endsWith(".SYMDEF") ) {
      try {
        convertedFiles = parseSymdef(filename);
      } catch (Exception e) {
        defaultFileIOError(e);
      }
    } else if (filename.endsWith(".lbr") ||
               filename.endsWith(".LBR") ||
               filename.endsWith(".brd") || // will catch XML eagle, but fall over on kicad
               filename.endsWith(".BRD") ) {
      try {
        convertedFiles = parseEagleLBR(filename);
      } catch (Exception e) {
        defaultFileIOError(e);
      }
    } else if (filename.endsWith(".lib") ||
               filename.endsWith(".LIB") ) {
      try {
        convertedFiles = parseKicadLib(filename);
      } catch (Exception e) {
        defaultFileIOError(e);
      }
    } else if (filename.endsWith(".mod") ||
               filename.endsWith(".MOD") ||
               filename.endsWith(".KICAD_MOD") ||
               filename.endsWith(".kicad_mod") ) {
      try {
        convertedFiles = parseKicadModule(filename);
      } catch (Exception e) {
        defaultFileIOError(e);
      }
    } else if (filename.endsWith(".asc") ||
               filename.endsWith(".ASC") ) {
      try {
        convertedFiles = parseLTSpice(filename);
      } catch (Exception e) {
        defaultFileIOError(e);
      }
    } else if (filename.endsWith(".sch") ||
               filename.endsWith(".SCH") ) {
      try { // NB: gschem also saves as .sch
        convertedFiles = parseQUCS(filename);
      } catch (Exception e) {
        defaultFileIOError(e);
      }
    } else if (filename.endsWith(".gbr") ||
               filename.endsWith(".GBR") ||
               filename.endsWith(".gbl") ||
               filename.endsWith(".GBL") ||
               filename.endsWith(".gtl") ||
               filename.endsWith(".GTL") ||
               filename.endsWith(".gto") ||
               filename.endsWith(".GTO") ||
               filename.endsWith(".gbo") ||
               filename.endsWith(".GBO") ||
               filename.endsWith(".gbs") ||
               filename.endsWith(".GBS") ||
               filename.endsWith(".gts") ||
               filename.endsWith(".GTS") ||
               filename.endsWith(".PHO") ||
               filename.endsWith(".pho") ) {
      try { // NB: there's a lot of variety here
        // i.e. .pho, .gm1, .gbo .gbs .gto .gts etc...
        convertedFiles = parseGerber(filename);
      } catch (Exception e) {
        defaultFileIOError(e);
      }
    } else if (filename.endsWith(".py") ||
               filename.endsWith(".PY") ) {
      try { // might be an eggbot font def file
        convertedFiles = parseHersheyData(filename);
      } catch (Exception e) {
        defaultFileIOError(e);
      }
    } else {
      System.out.println("I didn't recognise a suitable file " +
                         "ending for conversion, i.e..\n" +
                         "\t.bxl, .bsd, .ibs, .symdef, .asc, .sch, " +
                         ".gbr, hersheydata.py, .mod, .kicad_mod etc...");
    }

    if (convertedFiles != null &&
        !quietMode) {
      for (String converted : convertedFiles) {
        System.out.println(converted);
      }
    }

  }


  // BSDL files provide pin mapping suitable for symbol generation
  // but do not provide package/footprint information
  private static String [] parseBSDL(String BSDLFilename) throws IOException {

    BSDLParser BSDLp = new BSDLParser(BSDLFilename, "xschem", verbose);
    return BSDLp.convert(BSDLFilename);

  } 

  // BXL files provide both pin mapping suitable for symbol
  // generation as well as package/footprint information
  // here we export the raw BXL text, without further conversion
  private static void textOnlyBXL(String BXLFile) {

    SourceBuffer buffer = new SourceBuffer(BXLFile); 
    System.out.println(buffer.decode());

  }

  // BXL files provide both pin mapping suitable for symbol
  // generation as well as package/footprint information
  private static String [] parseBXL(String BXLFilename) throws IOException {

    BXLParser BXLp = new BXLParser(BXLFilename, "xschem", verbose);
    return BXLp.convert(BXLFilename);

  } 

  // Eagle libraries provide footprint data, pin mapping, and
  // schematic symbol data
  private static String [] parseEagleLBR(String EagleFilename) throws IOException {

    EagleParser Eaglep = new EagleParser(EagleFilename, "xschem", verbose);
    return Eaglep.convert(EagleFilename);

  } 

  // Hershey files provide stroked font information in NIST format,
  // sometimes found in the wild in .py files
  private static String [] parseHersheyData(String hersheyFilename) throws IOException {
  
    HersheyParser Hersheyp = new HersheyParser(hersheyFilename, "xschem", verbose);
    return Hersheyp.convert(hersheyFilename);

  } 

  // IBIS files provide pin mapping suitable for symbol generation
  // but do not provide package/footprint information
  private static String [] parseIBIS(String IBISFilename) throws IOException {

    IBISParser IBISp = new IBISParser(IBISFilename, "xschem", verbose);
    return IBISp.convert(IBISFilename);

  } 

  // Kicad stores its symbols in library files, which exist in legacy format for now
  private static String [] parseKicadLib(String KicadLibFilename) throws IOException {

    KicadSymbolParser KSp = new KicadSymbolParser(KicadLibFilename, "xschem", verbose);
    return KSp.convert(KicadLibFilename);

  } 

  // Kicad stores its modules (fp's) in module files, which exist in legacy and s-expr formats
  private static String [] parseKicadModule(String KicadModuleFilename) throws IOException {

    KicadModuleParser KMp = new KicadModuleParser(KicadModuleFilename, "pcb-rnd", verbose);
    return KMp.convert(KicadModuleFilename);

  } 

  // LTSpice files contain components, and nets, which can be turned
  // into a gschem schematic file
  private static String [] parseLTSpice(String LTSpiceFilename) throws IOException {

    LTSpiceParser LTSpicep = new LTSpiceParser(LTSpiceFilename, "xschem", verbose);
    return LTSpicep.convert(LTSpiceFilename);

  } 

  // .symdef files provide pin mapping suitable for symbol generation
  // but do not provide package/footprint information
  private static String [] parseSymdef(String symDefFilename) throws IOException {

    symdefParser sdp = new symdefParser(symDefFilename, "xschem", verbose);
    return symdefParser.convert(symDefFilename);

  } 

  // QUCS schematics:
  // 1) grid spacings of 20, and as little as 10
  // 2) most components 60 units high
  // 3) coord provided is centre of component
  // 4) rotation CCW +ve, 0 = 0, 1 = 90, 2 = 180, 3= 270
  // 5) +ve Y is down
  // qucs files contain components, and nets, which can be turned
  // into a gschem schematic file
  private static String [] parseQUCS(String QUCSsch) throws IOException {

    QUCSParser QUCSp = new QUCSParser(QUCSsch, "gschem", verbose);
    return QUCSParser.convert(QUCSsch);

  } 

  // we use a modified version of Phillip Knirsch's gerber
  // parser/plotter code from the turn of the century
  // http://www.wizards.de/phil/java/rs274x.html
  // this routine exports a PCB footprint version of the gerber file
  // It still needs a bit of work, i.e. 
  // - arcs might be a bit broken
  // - a bit verbose with polygon processing
  // - only exports a footprint, not a layout file
  // - would need heuristics for pin/pad vs wire/trace detection
  // - this is tricky though due to naughty EDAs painting some features
  //   instead of flashing
  private static String [] parseGerber(String gerberFile)
    throws IOException {
    File input = new File(gerberFile);
    Scanner gerberData = new Scanner(input);
    String gerbText = "";
    while (gerberData.hasNextLine()) {
      gerbText = gerbText + gerberData.nextLine();
    }
    Plotter gerberPlotter = new Plotter();
    gerberPlotter.setScale(1.0, 1.0);
    gerberPlotter.setSize(800, 640); // might make it behave
    String [] retString = new String [1];
    try {
      gerberPlotter.generatePCBFile(gerbText,gerberFile);
      retString[0] = gerberFile + ".fp";
    }
    catch (Exception e) {
      retString[0] = "Error: Gerber plotter unable to parse file.";
      defaultFileIOError(e);
    }
    return retString;
  }

  public static void elementWrite(String elementName,
                                  String data) throws IOException {
    try {
      File newElement = new File(elementName);
      PrintWriter elementOutput = new PrintWriter(newElement);
      elementOutput.println(data);
      elementOutput.close();
    } catch(Exception e) {
      System.out.println("There was an error saving: "
                         + elementName); 
      System.out.println(e);
    }
  }

  public static void printHelp() {
    System.out.println("usage:\n\n\tjava translate2coralEDA BSDLFILE.bsd\n\n"
                       + "options:\n\n"
                       + "\t\t-t\tonly output converted text"
                       + " without further conversion\n\n"
                       + "example:\n\n"
                       + "\tjava BSDL2GEDA BSDLFILE.bsd"
                       + " -t > BSDLFILE.txt\n");

  }

  private static void defaultFileIOError(Exception e) {
    System.out.println("Hmm, that didn't work. "
                       + "Probably a file IO issue:");
    System.out.println(e);
  }

}

