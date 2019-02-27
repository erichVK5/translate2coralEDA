// translate2coralEDA - a utility for turning various EDA formats into gEDA/coralEDA CAD elements
// Footprint.java v1.2
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
//    translate2coralEDA, translate2geda Copyright (C) 2015, 2019
//    Erich S. Heinzle a1039181@gmail.com



/**
 *
 * This object coordinates the header, text descriptors,
 * drawn lines, drawn arcs, drawn circles
 * and also the pads for a given Kicad module,
 * passed as a string array of the form (String[] args)
 * and is then able to produce a gEDA footprint
 *
 */

import java.util.Scanner;
import java.util.ArrayList;

public class Footprint
{
  String footprintName = "newFootprint";
  int padStackProtoCount = 0;
  String passedString = "";
  FootprintHeader[] moduleTextDescriptors = new FootprintHeader[200];

  ArrayList<Pad> pads = new ArrayList<Pad>();
  static ArrayList<String> padStackHashes = new ArrayList<String>();
  static ArrayList<String> padStackDefs = new ArrayList<String>();

  FootprintElementArchetype footprintElements[] =  new FootprintElementArchetype[2000];
  int FPFeatureCount = 0;

  Boolean metricSystem = false;   // this will be changed to "true", if needed,
  // with parsing in due course

  int moduleLineCountTotal = 0;	
  int padDefinitionLineCount = 0;

  String padDefinitionLines;

  int lineCount = 0;

  int padCount = 0;
  int drawSegmentCount = 0;
  int drawArcCount = 0;
  int drawCircleCount = 0;
  int textDescriptorCount = 0;

  String licenceText1 = null;

  String licenceText2 = "\n# dist-license: GPL\n# use-license: unlimited\n"; //# unless specified otherwise in source Kicad module\n";

  String clearanceWarningNotice1 = "# Users of the foot print must ensure that the solder mask reliefs and clearances\n# are compatible with the PCB manufacturer's process tolerances\n";

  String clearanceWarningNotice2 = null;

  long xOffset = 0; // in case we wish to translate the footprint silkscreen in x or y plane
  long yOffset = 0; // useful for building complex multiple device footprints
  float parsedValue = 0;	

  String reconstructedKicadModuleAsString = ""; // we'll return this from the toString() method

  public Footprint(String args, Boolean MmMetricUnits, long minimumViaAndDrillSizeNM)
  {

    metricSystem = MmMetricUnits;
    boolean moduleFinished = false;

    Scanner moduleDefinition = new Scanner(args);

    String parseString = "";
    String trimmedString = "";
    String[] tokens;

    //		System.out.println(args);
    
    licenceText1 = "# Footprint converted from Kicad Module ";
    clearanceWarningNotice2 = "# Pins and SMD pads have been converted from Kicad foot prints which\n# do not have solder mask relief or clearances specified.\n# Fairly sane default values have been used for solder mask relief and clearances.\n";

    while (moduleDefinition.hasNext() && !moduleFinished)
      {			
        parseString = moduleDefinition.nextLine();
        trimmedString = parseString.trim();
        tokens = trimmedString.split(" ");

        // we now move into the main parsing section
        // which decides what each line is all about and then deploys
        // it to construct the relevant footprint element object 

        if (tokens[0].startsWith("$INDEX"))
          {
            //	System.out.println(footprintName); // we don't care about the index
          }
        else if (tokens[0].startsWith("$MODULE") || tokens[0].startsWith("module"))
          {       // it all starts here, with the module header
            // first we look for double quotes inserted by the likes of madparts
            footprintName = tokens[1];
            if (footprintName.length() > 2) {
              if (footprintName.charAt(0) == '"') {
                footprintName = footprintName.substring(1);
              }
              if (footprintName.charAt(footprintName.length()-1) == '"') {
                footprintName = footprintName.substring(0,footprintName.length()-1);
              }
            }
            // now we get rid of characters ![a-zA-Z0-9.-] which
            // may be unacceptable for a filename
            footprintName = footprintName.replaceAll("[^a-zA-Z0-9.-]", "_");
            // System.out.println(footprintName + " is the footprint name found.");

            // we now step through the module line by line
            while (moduleDefinition.hasNext() && !moduleFinished)
              {
                parseString = moduleDefinition.nextLine();
                trimmedString = parseString.trim();
                //	System.out.println("Current footprint def line:" + 
                //			trimmedString);

                // we tokenize the line
                tokens = trimmedString.split(" ");
					

                // and we then decide what to do with the tokenized lines
                // we ignore "attr" lines at this stage
                if (tokens[0].startsWith("Po") || tokens[0].startsWith("at "))
                  {
                    // we find the xOffset in the position, and remember
                    // it is passed in Nm units
                    parsedValue = Float.parseFloat(tokens[1]);
                    xOffset = convertToNanometres(parsedValue, metricSystem);
                    parsedValue = Float.parseFloat(tokens[2]);
                    yOffset = convertToNanometres(parsedValue, metricSystem);
                  }
                else if (tokens[0].startsWith("T0"))
                  {
                    moduleTextDescriptors[textDescriptorCount] = new FootprintHeader();
                    moduleTextDescriptors[textDescriptorCount].populateHeader(trimmedString, metricSystem);
                    textDescriptorCount++;
                  }
                else if (tokens[0].startsWith("T") && !tokens[0].startsWith("T1"))
                  {
                    // we exclude the component value T1, and look for other text to render
                    footprintElements[FPFeatureCount] = new FootprintText();
                    footprintElements[FPFeatureCount].populateElement(trimmedString, metricSystem);
                    FPFeatureCount++;
                  }
                else if (tokens[0].startsWith("fp_text") && tokens[1].startsWith("reference"))
                  {
                    moduleTextDescriptors[textDescriptorCount] = new FootprintHeader();
                    moduleTextDescriptors[textDescriptorCount].populateHeader(trimmedString, metricSystem);
                    textDescriptorCount++;
                    // we only want to know what the reference text is
                  }

                else if (tokens[0].startsWith("DS"))
                  {
                    footprintElements[FPFeatureCount] = new DrawnElement();
                    footprintElements[FPFeatureCount].populateElement(trimmedString, metricSystem);
                    FPFeatureCount++;
                    drawSegmentCount++;
                  }
                else if (tokens[0].startsWith("fp_line"))
                  {
                    footprintElements[FPFeatureCount] = new DrawnElement();
                    footprintElements[FPFeatureCount].populateElement(trimmedString, metricSystem);
                    FPFeatureCount++;
                    drawSegmentCount++;
                  }
                else if (tokens[0].startsWith("DC"))
                  {
                    footprintElements[FPFeatureCount] = new Circle();
                    footprintElements[FPFeatureCount].populateElement(trimmedString, metricSystem);
                    FPFeatureCount++;
                    drawCircleCount++;

                  }
                else if (tokens[0].startsWith("fp_circle"))
                  {
                    footprintElements[FPFeatureCount] = new Circle();
                    footprintElements[FPFeatureCount].populateElement(trimmedString, metricSystem);
                    FPFeatureCount++;
                    drawCircleCount++;
                  }
                else if (tokens[0].startsWith("DA"))
                  {
                    footprintElements[FPFeatureCount] = new Arc();
                    footprintElements[FPFeatureCount].populateElement(trimmedString, metricSystem);
                    FPFeatureCount++;
                    drawArcCount++;
                  }
                else if (tokens[0].startsWith("fp_arc"))
                  {
                    footprintElements[FPFeatureCount] = new Arc();
                    footprintElements[FPFeatureCount].populateElement(trimmedString, metricSystem);
                    FPFeatureCount++;
                    drawArcCount++;
                  }
                else if (tokens[0].startsWith("pad"))
                  {  // we have identified a pad definition in the module
                    padDefinitionLines = trimmedString;
                    padCount++;

                    footprintElements[FPFeatureCount] = new Pad();
                    footprintElements[FPFeatureCount].populateElement(padDefinitionLines, metricSystem, minimumViaAndDrillSizeNM);
		    pads.add((Pad) footprintElements[FPFeatureCount]);
                    FPFeatureCount++;	
                  }
                else if (tokens[0].startsWith("$PAD"))
                  {  // we have identified a $PAD definition in the module
                    padDefinitionLines = "";
                    padDefinitionLineCount = 0;
                    padCount++;
                    while (!trimmedString.startsWith("$EndPAD") && moduleDefinition.hasNext())
                      { 	// we now turn the multiline $PAD definition 
                        // into a single string
                        padDefinitionLines = padDefinitionLines +
                            trimmedString + " \n" ;
                        parseString = moduleDefinition.nextLine();
                        trimmedString = parseString.trim();
                        padDefinitionLineCount++;
                      }
                    padDefinitionLines = padDefinitionLines + "$EndPad \n";
                    // having created a single string containing the
                    // $PAD definition
                    // we can now pass it to a Pad constructor
                    footprintElements[FPFeatureCount] = new Pad();
                    footprintElements[FPFeatureCount].populateElement(padDefinitionLines, metricSystem, minimumViaAndDrillSizeNM);
		    pads.add((Pad) footprintElements[FPFeatureCount]);
                    FPFeatureCount++;
                  }

                if (tokens[0].startsWith("$EndMOD"))
                  {
                    moduleFinished = true;
                  }

              }

          }
        else {
          // not much to see, not $INDEX or $MODULE
        }
        
      }
    
    // we also create a single string version of the module for
    // use by the toString() method
    reconstructedKicadModuleAsString = args;

  }

  public String generateFootprintFilename(String format)
  {
    if (format.equals("pcb")) {
      return footprintName + ".fp";
    }
    return footprintName + ".lht";
  }

  private String roundRectShape(long x, long y, boolean top, boolean intern, boolean copper, boolean mask) {
    String maskComb = "";
    String layermask = "";
    long minDim = x;
    if (y < x) {
      minDim = y;
    } 
    String shape = // need tests for x > y
	"      ha:ps_shape_v4 {\n       clearance = 0.0\n       ha:ps_line {\n        square = 0\n" +
	"        thickness = " + minDim + "nm\n";
    if (x > y) {
      shape = shape +
          "        x1 = -" + (x/2 - minDim/2) + "nm\n        y1 = " + 0 + "nm\n" + 
          "        x2 = " + (x/2 - minDim/2)  + "nm\n        y2 = " + 0 + "nm\n" +
          "       }\n";
    } else {
      shape = shape +
          "        x1 = " + 0 + "nm\n        y1 = " + (y/2 - minDim/2) + "nm\n" + 
          "        x2 = " + 0 + "nm\n        y2 = -" + (y/2 - minDim/2) + "nm\n" +
          "       }\n";
    }
    if (top && copper) {
      layermask = "       ha:layer_mask {\n        copper = 1\n        top = 1\n       }\n";
    } else if (!top && intern && copper) {
      layermask = "       ha:layer_mask {\n        copper = 1\n        intern = 1\n       }\n";
    } else if (!top && !intern && copper) {
      layermask = "       ha:layer_mask {\n        copper = 1\n        bottom = 1\n       }\n";
    } else if (top && mask) {
      layermask = "       ha:layer_mask {\n        mask = 1\n        top = 1\n       }\n";
      maskComb = "        sub = 1\n        auto = 1\n";
    } else if (!top && mask) {
      layermask = "       ha:layer_mask {\n        mask = 1\n        bottom = 1\n       }\n";
      maskComb = "        sub = 1\n        auto = 1\n";
    }
    String combining = "       ha:combining {\n" + maskComb + "       }\n      }\n";
    return shape + layermask + combining;
  }

  private String rectShape(long x, long y, boolean top, boolean intern, boolean copper, boolean mask) {
    String maskComb = "";
    String layermask = "";
    String shape =
	"      ha:ps_shape_v4 {\n       clearance = 0.0\n       li:ps_poly {\n" +
	"        -" + x/2 + "nm\n        -" + y/2 + "nm\n        " + x/2 + "nm\n        -" + y/2 + "nm\n" +
	"        " + x/2 + "nm\n        " + y/2 + "nm\n        -" + x/2 + "nm\n        " + y/2 + "nm\n" +
	"       }\n";
    if (top && copper) {
      layermask = "       ha:layer_mask {\n        copper = 1\n        top = 1\n       }\n";
    } else if (!top && intern && copper) {
      layermask = "       ha:layer_mask {\n        copper = 1\n        intern = 1\n       }\n";
    } else if (!top && !intern && copper) {
      layermask = "       ha:layer_mask {\n        copper = 1\n        bottom = 1\n       }\n";
    } else if (top && mask) {
      layermask = "       ha:layer_mask {\n        mask = 1\n        top = 1\n       }\n";
      maskComb = "        sub = 1\n        auto = 1\n";
    } else if (!top && mask) {
      layermask = "       ha:layer_mask {\n        mask = 1\n        bottom = 1\n       }\n";
      maskComb = "        sub = 1\n        auto = 1\n";
    }
    String combining = "       ha:combining {\n" + maskComb + "       }\n      }\n";
    return shape + layermask + combining;
  }

  private String circShape(long x, long y, boolean top, boolean intern, boolean copper, boolean mask) {
    String maskComb = "";
    String layermask = "";
    String shape = "      ha:ps_shape_v4 {\n       clearance = 0.0\n       "
        + "ha:ps_circ {\n        x = 0.0\n        y = 0.0\n        dia = " +
        x + "nm\n       }\n";
    if (top && copper) {
      layermask = "       ha:layer_mask {\n        copper = 1\n        top = 1\n       }\n";
    } else if (!top && intern && copper) {
      layermask = "       ha:layer_mask {\n        copper = 1\n        intern = 1\n       }\n";
    } else if (!top && !intern && copper) {
      layermask = "       ha:layer_mask {\n        copper = 1\n        bottom = 1\n       }\n";
    } else if (top && mask) {
      layermask = "       ha:layer_mask {\n        mask = 1\n        top = 1\n       }\n";
      maskComb = "        sub = 1\n        auto = 1\n";
    } else if (!top && mask) {
      layermask = "       ha:layer_mask {\n        mask = 1\n        bottom = 1\n       }\n";
      maskComb = "        sub = 1\n        auto = 1\n";
    }
    String combining = "       ha:combining {\n" + maskComb + "       }\n      }\n";
    return shape + layermask + combining;
  }

  private String padStackBuilder(Pad p) {
    String shapes = 
	"    ha:ps_proto_v6." + padStackProtoCount++
	+ " {\n     htop = 0\n     hdia = " + p.kicadDrillOneSizeNm + "nm\n     li:shape {\n";
    if (p.kicadDrillShape == 'C' && p.kicadPadAttributeType.startsWith("STD")) {
      shapes = shapes
          + circShape(p.kicadShapeXsizeNm, p.kicadShapeYsizeNm, true, false, true, false) 
          + circShape(p.kicadShapeXsizeNm, p.kicadShapeYsizeNm, false, true, true, false) 
          + circShape(p.kicadShapeXsizeNm, p.kicadShapeYsizeNm, false, false, true, false) 
          + circShape(p.kicadShapeXsizeNm + p.gEDAdefaultSolderMaskRelief,
                      p.kicadShapeYsizeNm + p.gEDAdefaultSolderMaskRelief, true, false, false, true) 
          + circShape(p.kicadShapeXsizeNm + p.gEDAdefaultSolderMaskRelief,
                      p.kicadShapeYsizeNm + p.gEDAdefaultSolderMaskRelief, false, false, false, true);
    } else if (p.kicadDrillShape == 'R' && p.kicadPadAttributeType.startsWith("STD")) {
      shapes = shapes
          + rectShape(p.kicadShapeXsizeNm, p.kicadShapeYsizeNm, true, false, true, false) 
          + rectShape(p.kicadShapeXsizeNm, p.kicadShapeYsizeNm, false, true, true, false) 
          + rectShape(p.kicadShapeXsizeNm, p.kicadShapeYsizeNm, false, false, true, false) 
          + rectShape(p.kicadShapeXsizeNm + p.gEDAdefaultSolderMaskRelief,
                      p.kicadShapeYsizeNm + p.gEDAdefaultSolderMaskRelief, true, false, false, true) 
          + rectShape(p.kicadShapeXsizeNm + p.gEDAdefaultSolderMaskRelief,
                      p.kicadShapeYsizeNm + p.gEDAdefaultSolderMaskRelief, false, false, false, true);
    } else if (p.kicadDrillShape == 'O' && p.kicadPadAttributeType.startsWith("STD")) {
      shapes = shapes
          + roundRectShape(p.kicadShapeXsizeNm, p.kicadShapeYsizeNm, true, false, true, false) 
          + roundRectShape(p.kicadShapeXsizeNm, p.kicadShapeYsizeNm, false, true, true, false) 
          + roundRectShape(p.kicadShapeXsizeNm, p.kicadShapeYsizeNm, false, false, true, false) 
          + roundRectShape(p.kicadShapeXsizeNm + p.gEDAdefaultSolderMaskRelief,
                           p.kicadShapeYsizeNm + p.gEDAdefaultSolderMaskRelief, true, false, false, true) 
          + roundRectShape(p.kicadShapeXsizeNm + p.gEDAdefaultSolderMaskRelief,
                           p.kicadShapeYsizeNm + p.gEDAdefaultSolderMaskRelief, false, false, false, true);
    } else if (p.kicadDrillShape == 'R' 
               && (p.kicadPadAttributeType.startsWith("SMD") || p.kicadPadAttributeType.startsWith("CONN"))) {
      shapes = shapes
          + rectShape(p.kicadShapeXsizeNm, p.kicadShapeYsizeNm, true, false, true, false) 
          + rectShape(p.kicadShapeXsizeNm + p.gEDAdefaultSolderMaskRelief,
                      p.kicadShapeYsizeNm + p.gEDAdefaultSolderMaskRelief, true, false, false, true);
    } else if (p.kicadDrillShape == 'O' 
               && (p.kicadPadAttributeType.startsWith("SMD") || p.kicadPadAttributeType.startsWith("CONN"))) {
      shapes = shapes
          + roundRectShape(p.kicadShapeXsizeNm, p.kicadShapeYsizeNm, true, false, true, false) 
          + roundRectShape(p.kicadShapeXsizeNm + p.gEDAdefaultSolderMaskRelief,
                           p.kicadShapeYsizeNm + p.gEDAdefaultSolderMaskRelief, true, false, false, true);
    } else if (p.kicadDrillShape == 'C'
               && (p.kicadPadAttributeType.startsWith("SMD") || p.kicadPadAttributeType.startsWith("CONN"))) {
      shapes = shapes
          + circShape(p.kicadShapeXsizeNm, p.kicadShapeYsizeNm, true, false, true, false) 
          + circShape(p.kicadShapeXsizeNm + p.gEDAdefaultSolderMaskRelief,
                      p.kicadShapeYsizeNm + p.gEDAdefaultSolderMaskRelief, true, false, false, true);
    }// this catches holes
    if (p.kicadPadAttributeType.startsWith("HOLE")) {
      shapes = shapes + "     }\n     hbottom = 0\n     hplated = 0\n    }\n";
    } else {
      shapes = shapes + "     }\n     hbottom = 0\n     hplated = 1\n    }\n";
    }
    return shapes;
  }

  public static int padStackRef(Pad p) {
    return padStackHashes.indexOf(p.pinPadHash());
  }

  private String lihataSubcHeader() {
    return "li:pcb-rnd-subcircuit-v6 {\n ha:subc.4 {\n"; 
  }

  private String lihataSubcAttributes() {
    return    "  ha:attributes {\n   value = " + "VALUE"
        + "\n   footprint = " + footprintName + "\n"
        + "  }\n";
  }

  private String lihataPadstacks() {
    String padStacks = "";
    for (Pad p : pads)
      {
        if (!padStackHashes.contains(p.pinPadHash())) {
          padStackHashes.add(p.pinPadHash());			
          padStacks = padStacks + padStackBuilder(p);
        } // else pad stack is a known quantity
      }
    return "   li:padstack_prototypes {\n"
	+ padStacks
	+ "   }\n";
  }

  private String lihataPadStackObjects() {
    String padStacks = "";
    for (int counter = 0; counter < FPFeatureCount; counter++)
      {
        if (footprintElements[counter].isPad()) {
          padStacks = padStacks + footprintElements[counter].generateElement(0, 0, 1.0f, "pcb-rnd");
        }
      }
    return "   li:objects {\n"
	+ padStacks
	+ "   }\n";
  }

  private String lihataTopObjects() {
    String top = "";
    for (int counter = 0; counter < FPFeatureCount; counter++)
      {
        if (footprintElements[counter].isTop()) {
          top = top + footprintElements[counter].generateElement(0, 0, 1.0f, "pcb-rnd");
        }
      }
    for (int counter = 0; counter < textDescriptorCount; counter++)
      {
        if (moduleTextDescriptors[counter].isTop()) {
          top = top + moduleTextDescriptors[counter].generateElement(0, 0, 1.0f, "pcb-rnd");
        }
      }
    return "     li:objects {\n"
	+ top
	+ "     }\n";
  }

  private String lihataBottomObjects() {
    String bottom = "";
    for (int counter = 0; counter < FPFeatureCount; counter++)
      {
        if (footprintElements[counter].isBottom()) {
          bottom = bottom + footprintElements[counter].generateElement(0, 0, 1.0f, "pcb-rnd");
        }
      }
    for (int counter = 0; counter < textDescriptorCount; counter++)
      {
        if (moduleTextDescriptors[counter].isBottom()) {
          bottom = bottom + moduleTextDescriptors[counter].generateElement(0, 0, 1.0f, "pcb-rnd");
        }
      }
    return "     li:objects {\n"
	+ bottom
	+ "     }\n";
  }


  private String lihataLayers() {
    return "   li:layers {\n"
        //	+ " ......\n"  copper layers can go here
	+ "    ha:top-silk {\n     lid = 0\n     ha:type {\n      silk = 1\n      top = 1\n     }\n"
	+ lihataTopObjects()
	+ "     ha:combining {\n      auto = 1\n     }\n    }\n"
	+ "    ha:bottom-silk {\n     lid = 1\n     ha:type {\n      silk = 1\n      bottom = 1\n     }\n"
	+ lihataBottomObjects()
	+ "     ha:combining {\n      auto = 1\n     }\n    }\n"
	+ "    ha:subc-aux {\n     lid = 2\n     ha:type {\n      top = 1\n      misc = 1\n"
	+ "      virtual = 1\n     }\n     li:objects {\n      ha:line." + FootprintElementArchetype.lineCount++
	+ "14 {\n"
	+ "       clearance = 0.0\n       y2 = 0.0\n       thickness = 0.1mm\n"
	+ "       ha:attributes {\n        subc-role = pnp-origin\n       }\n       x1 = 0.0\n"
	+ "       x2 = 0.0\n       ha:flags {\n       }\n       y1 = 0.0\n      }\n"
	+ "      ha:line.17 {\n       clearance = 0.0\n       y2 = 0.0\n"
	+ "       thickness = 0.1mm\n       ha:attributes {\n        subc-role = origin\n"
	+ "       }\n       x1 = 0.0\n       x2 = 0.0\n       ha:flags {\n       }\n"
	+ "       y1 = 0.0\n      }\n      ha:line." + FootprintElementArchetype.lineCount++
	+ " {\n       clearance = 0.0\n       y2 = 0.0\n"
	+ "       thickness = 0.1mm\n       ha:attributes {\n        subc-role = x\n       }\n       x1 = 0.0\n"
	+ "       x2 = 1.0mm\n       ha:flags {\n       }\n       y1 = 0.0\n      }\n      "
	+ "ha:line." + FootprintElementArchetype.lineCount++ + " {\n"
	+ "       clearance = 0.0\n       y2 = 1.0mm\n       thickness = 0.1mm\n       ha:attributes {\n"
	+ "        subc-role = y\n       }\n       x1 = 0.0\n       x2 = 0.0\n       ha:flags {\n"
	+ "       }\n       y1 = 0.0\n      }\n     }\n     ha:combining {\n     }\n    }\n"
	+ "   }\n";
  }

  private String lihataSubcData() {
    return "  ha:data {\n"
	+ lihataPadstacks() + lihataPadStackObjects() + lihataLayers()
	+ "  }\n"
	+ "  uid = kazsgcWF3uqvvuDCYMsAAAAB\n  ha:flags {\n"
	+ "  }\n";
  }

  private String lihataSubcFooter() {
    return " }\n}\n"; 
  }

  private String lihataSubc() {
    return lihataSubcHeader() + lihataSubcAttributes() + lihataSubcData() + lihataSubcFooter();
  }

  public String generateFootprint(float magnificationRatio, String format)
  {
    String assembledElement = "";
    if (format.equals("pcb")) {
      assembledElement = licenceText1 + footprintName + licenceText2;
      if (padCount > 0)
      	{
          assembledElement = assembledElement + 
              clearanceWarningNotice1 + clearanceWarningNotice2 ;
      	}

      if (metricSystem)
      	{
          assembledElement = assembledElement +
              "# Kicad module units: mm\n";
      	}
      else
      	{
          assembledElement = assembledElement +
              "# Kicad module units: 0.1 mil\n";
      	}

      // we start by generating a generic gEDA footprint Element[...] field
      // in case the Kicad module failed to have a text field "T0 .... "
      // to base the gEDA element field on
      // We use the file name for the description, default text orientation
      // of 0, with an offset 250 mils to the right of centre, and 
      // text scaling of 100

      String gEDAfootprintElementField =
          "# Since the Kicad Module did not have a 'T0' field,\n" +
          "# The module name has been used for the Element description field\n" +
          "Element[\"\" \"" + footprintName +
          "\" \"\" \"\" 0 0 0 25000 0 100 \"\"]\n(\n";

      if (textDescriptorCount > 0)
      	{
          gEDAfootprintElementField = moduleTextDescriptors[0].generateGEDAtextField(0,0);
      	}

      assembledElement = assembledElement +
          ("# Footprint = module name: " + footprintName + "\n" +
           "# Text descriptor count: " + textDescriptorCount + "\n" +
           "# Draw segment object count: " + drawSegmentCount + "\n" +
           "# Draw circle object count: " + drawCircleCount + "\n" +
           "# Draw arc object count: " + drawArcCount + "\n" +
           "# Pad count: " + padCount + "\n#\n" +
           gEDAfootprintElementField );

      //		System.out.println(assembledElement);
      for (int counter = 0; counter < FPFeatureCount; counter++)
      	{
          assembledElement = assembledElement +
              footprintElements[counter].generateElement(xOffset,yOffset,magnificationRatio, format);
      	}
      return assembledElement;
    } else {
      return lihataSubc();
    }
  }

  public String getKicadModuleName()
  {
    return footprintName;
  }

  public String toString()
  {
    return reconstructedKicadModuleAsString;
  }

  private long convertToNanometres(float rawValue, boolean metricSystem)
  {
    if (metricSystem) // metricSystem = units mm
      {
        return (long)(1000000 * rawValue);
        // multiply mm by 1000000 to turn into nm
      }
    else
      {
        return (long)(2540 * rawValue);
        // multiply 0.1 mil units by 2540 to turn into nm
      }
  }



}
