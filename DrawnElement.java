// translate2coralEDA - a utility for turning various EDA formats into gEDA/coralEDA CAD elements
// DrawnElement.java v1.2
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
//    GNU General Public License for more details.
//    
//    You should have received a copy of the GNU General Public License
//    along with this program; if not, write to the Free Software
//    Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
//    
//    DrawnElement.java Copyright (C) 2015, 2019 Erich S. Heinzle a1039181@gmail.com
//    translate2coralEDA, translate2geda Copyright (C) 2015, 2019
//    Erich S. Heinzle a1039181@gmail.com


/**
 *
 * This class is passed a Kicad Draw Segment string of the form "DS x1 y1 x2 y2 thickness layer"
 * and implements a method which can generate a gEDA LineElement definition for a gEDA PCB footprint
 *
 * @param long xOffset the X offset of the Draw Segment relative to the module origin
 * @param long yOffset the Y offset of the Draw Segment realtive to the module origin
 * @param float magnificationRatio the magnification ratio to be applied to element position and size
 *
 * @return String = "LineElement[x1 y1 x2 y2 thickness]"
 *
 */

// have implemented copper pads in gEDA output where a kicad DS
// statement specifies a copper layer as well
//
// following is based on
// http://kicad.sourcearchive.com/documentation/0.0.20090216/pcbstruct_8h-source.html
// layer "21" is SILKSCREEN_N_CMP
// layer "0" is first copper layer = "0. Back - Solder"
// and layer "15" is "15. Front - Component"
// and layer "20" SilkScreen Back
// and layer "21" SilkScreen Front

/* Layer identification (layer number)
   #define FIRST_COPPER_LAYER    0
   #define COPPER_LAYER_N        0
   #define LAYER_N_2             1     /* Numero layer 2 
   #define LAYER_N_3             2     /* Numero layer 3 
   #define LAYER_N_4             3     /* Numero layer 4 
   #define LAYER_N_5             4     /* Numero layer 5 
   #define LAYER_N_6             5     /* Numero layer 6 
   #define LAYER_N_7             6     /* Numero layer 7 
   #define LAYER_N_8             7     /* Numero layer 8 
   #define LAYER_N_9             8     /* Numero layer 9 
   #define LAYER_N_10            9     /* Numero layer 10
   #define LAYER_N_11            10    /* Numero layer 11
   #define LAYER_N_12            11    /* Numero layer 12
   #define LAYER_N_13            12    /* Numero layer 13
   #define LAYER_N_14            13    /* Numero layer 14
   #define LAYER_N_15            14    /* Numero layer 15
   #define LAYER_CMP_N           15
   #define CMP_N                 15
   #define LAST_COPPER_LAYER     15
   #define NB_COPPER_LAYERS      (LAST_COPPER_LAYER + 1)

   #define FIRST_NO_COPPER_LAYER 16
   #define ADHESIVE_N_CU         16
   #define ADHESIVE_N_CMP        17
   #define SOLDERPASTE_N_CU      18
   #define SOLDERPASTE_N_CMP     19
   #define SILKSCREEN_N_CU       20
   #define SILKSCREEN_N_CMP      21
   #define SOLDERMASK_N_CU       22
   #define SOLDERMASK_N_CMP      23
   #define DRAW_N                24
   #define COMMENT_N             25
   #define ECO1_N                26
   #define ECO2_N                27
   #define EDGE_N                28
   #define LAST_NO_COPPER_LAYER  28
   #define NB_LAYERS             (LAST_NO_COPPER_LAYER + 1)

   #define LAYER_COUNT           32 */ 

public class DrawnElement extends FootprintElementArchetype
{

  String output = "";

  long xCoordOneNm = 0;
  long yCoordOneNm = 0;
  long gEDAxCoordOne = 0;
  long gEDAyCoordOne = 0;
  long xCoordTwoNm = 0;
  long yCoordTwoNm = 0;
  long gEDAxCoordTwo = 0;
  long gEDAyCoordTwo = 0;

  long defaultLineThicknessNm = 25400; 
  // this is 10 mil in nanometres
  // which is 254 microns, which is 0.254 mm
  // which is 0.01 inches, which is 10 mil = 10 thou
  long lineThicknessNm = defaultLineThicknessNm;
  long gEDAlineThickness = 100; // this is 10 mil in 0.1 mil units

  int kicadLayer = 21; // 21 is the default = the top silkscreen layer

  String kicadDrawnSegmentDescriptor = "";

  public DrawnElement()
  {
    output = "#Hmm, the no arg KicadDrawingElement "
        + "constructor didn't do much";
  }

  public String toString()
  {
    return kicadDrawnSegmentDescriptor;
  }

  // here, we populate the line object with a string
  // extracted from a BXL file
  // noting that the y-axis is inverted vs gEDA/kicad
  public void populateBXLElement(String BXLLine) {
    BXLLine = BXLLine.replaceAll("[\"(),]","");
    String [] tokens = BXLLine.split(" ");
    for (int index = 0; index < tokens.length; index++) {
      if (tokens[index].equals("TOP_SILKSCREEN")) {
        kicadLayer = 21; // i.e. F.Silk
      } else if (tokens[index].equals("Origin")) {
        xCoordOneNm = milToNM(Float.parseFloat(tokens[++index]));
        yCoordOneNm = -milToNM(Float.parseFloat(tokens[++index]));
      } else if(tokens[index].equals("EndPoint")) {
        xCoordTwoNm = milToNM(Float.parseFloat(tokens[++index]));
        yCoordTwoNm = -milToNM(Float.parseFloat(tokens[++index]));
      } else if(tokens[index].equals("Width")) {
        lineThicknessNm = milToNM(Float.parseFloat(tokens[++index]));
      }
    }
  }

  // we use this to create an array of four silk line objects from 
  // an Eagle rectangle definition when passed a rectangle
  // definition from the presumed top silk layer, plus a fatter
  // central line to "fill" the rectangle
  // for now, only rectangles that are not rotated are supported 
  public static DrawnElement [] eagleRectangleAsLines(String arg) {
    arg = arg.replaceAll("[<>/]","");
    String [] tokens = arg.split(" ");
    String x1text = "";
    String y1text = "";
    String x2text = "";
    String y2text = "";
    String widthText = "width=\"0.1542\" "; // a default value

    for (int index = 0; index < tokens.length; index++) {
      if (tokens[index].startsWith("x1=")) {
        x1text = tokens[index].substring(4).replaceAll("\"","");
      } else if (tokens[index].startsWith("y1=")) {
        y1text = tokens[index].substring(4).replaceAll("\"","");
      } else if (tokens[index].startsWith("x2=")) {
        x2text = tokens[index].substring(4).replaceAll("\"","");
      } else if (tokens[index].startsWith("y2=")) {
        y2text = tokens[index].substring(4).replaceAll("\"","");
      } else if (tokens[index].startsWith("width=")) {
        widthText = tokens[index] + " ";
      }
    } 
    DrawnElement [] returnedLines = new DrawnElement [5];
    // this routine assumes that the eagle rectangle is not rotated.
    returnedLines[0] = new DrawnElement();
    returnedLines[0].populateEagleElement(eagleSilkLine(x1text,
                                                        y1text,
                                                        x1text,
                                                        y2text,
                                                        widthText));
    returnedLines[1] = new DrawnElement();
    returnedLines[1].populateEagleElement(eagleSilkLine(x1text,
                                                        y2text,
                                                        x2text,
                                                        y2text,
                                                        widthText));
    returnedLines[2] = new DrawnElement();
    returnedLines[2].populateEagleElement(eagleSilkLine(x2text,
                                                        y2text,
                                                        x2text,
                                                        y1text,
                                                        widthText));
    returnedLines[3] = new DrawnElement();
    returnedLines[3].populateEagleElement(eagleSilkLine(x2text,
                                                        y1text,
                                                        x1text,
                                                        y1text,
                                                        widthText));
    long x1 = mmTextToNM(x1text)/1000; // we round off to microns
    long y1 = mmTextToNM(y1text)/1000;
    long x2 = mmTextToNM(x2text)/1000;
    long y2 = mmTextToNM(y2text)/1000;
    //long width = mmTextToNM(widthText.substring(6))/1000; // not used
    long newWidthNm = 0;
    if (Math.abs(x2-x1) > Math.abs(y2-y1)) {
      newWidthNm = Math.abs(y2-y1);
      x1 = x1 + newWidthNm/2;
      x2 = x2 - newWidthNm/2;
      y1 = (y1+y2)/2;
      y2 = y1;
    } else {
      newWidthNm = Math.abs(x2-x1);
      y1 = y1 + newWidthNm/2;
      y2 = y2 - newWidthNm/2;
      x1 = (x1+x2)/2;
      x2 = x1;
    }

    returnedLines[4] = new DrawnElement();
    returnedLines[4].populateEagleElement(eagleSilkLineUM(x1,
                                                          y1,
                                                          x2,
                                                          y2,
                                                          newWidthNm));
    return returnedLines;
  }

  // this returns an eagle XML silk line descriptor using the text
  // extracted from an Eagle line or rectangle XML def  
  public static String eagleSilkLine(String x1mmText, String y1mmText,
                                     String x2mmText, String y2mmText,
                                     String widthText) { 
    return "<wire " +
        "x1=\"" + x1mmText + "\" " +
        "y1=\"" + y1mmText + "\" " +
        "x2=\"" + x2mmText + "\" " +
        "y2=\"" + y2mmText + "\" " +
        widthText +
        "layer=\"21\"/>"; // layer 21 = Eagle top silk is assumed
  }

  // this returns an eagle silk descriptor in mm units
  // to 3 decimal places
  // it might break a bit in locales using comma instead of DP '.'
  // if used by other classes
  public static String eagleSilkLineUM(long x1nm, long y1nm,
                                       long x2nm, long y2nm,
                                       long widthNm) { 
    return "<wire " +
        "x1=\"" + (float)x1nm/1000.0 // convert to um to mm
        + "\" " +
        "y1=\"" + (float)y1nm/1000.0
        + "\" " +
        "x2=\"" + (float)x2nm/1000.0
        + "\" " +
        "y2=\"" + (float)y2nm/1000.0
        + "\" " +
        "width=\"" + 
        (float)widthNm/1000.0
        + "\" " +
        "layer=\"21\"/>"; // silk top layer is assumed
  }


  // here, we populate the line object with a string
  // extracted from an Eagle .lbr file
  // it seems that the y-axis is inverted vs gEDA/kicad
  public void populateEagleElement(String EagleLine) {
    // Document Object Model? We don't need no steenking
    // Document Object Model
    EagleLine = EagleLine.replaceAll("[<>/]","");
    kicadLayer = 21; // i.e. F.Silk is assumed for now
    // since we only call this method it is top silk
    String [] tokens = EagleLine.split(" ");
    for (int index = 0; index < tokens.length; index++) {
      if (tokens[index].startsWith("x1=")) {
        xCoordOneNm = mmTextToNM(tokens[index].substring(3));
      } else if (tokens[index].startsWith("y1=")) {
        // yCoordOneNm = -mmTextToNM(tokens[index].substring(3));
        yCoordOneNm = -mmTextToNM(tokens[index].substring(3));
      } else if (tokens[index].startsWith("x2=")) {
        xCoordTwoNm = mmTextToNM(tokens[index].substring(3));
      } else if (tokens[index].startsWith("y2=")) {
        // yCoordTwoNm = -mmTextToNM(tokens[index].substring(3));
        yCoordTwoNm = -mmTextToNM(tokens[index].substring(3));
      } else if (tokens[index].startsWith("width=")) {
        lineThicknessNm = mmTextToNM(tokens[index].substring(6));
        if (lineThicknessNm == 0) {
          lineThicknessNm = defaultLineThicknessNm;
        }
      } 
    }
  }

  // here, we populate the line object with a string
  // extracted from a Kicad module    
  public void populateElement(String arg, boolean metric)
  {
    kicadDrawnSegmentDescriptor = arg;

    float parsedValue = 0;
		
    String[] tokens = arg.split(" ");

    // System.out.print("#The passed string:" + arg + "\n");

    if (tokens[0].startsWith("DS"))
      {
        parsedValue = Float.parseFloat(tokens[1]);
        xCoordOneNm = convertToNanometres(parsedValue, metric);
        parsedValue = Float.parseFloat(tokens[2]);
        yCoordOneNm = convertToNanometres(parsedValue, metric);
        parsedValue = Float.parseFloat(tokens[3]);
        xCoordTwoNm = convertToNanometres(parsedValue, metric);
        parsedValue = Float.parseFloat(tokens[4]);
        yCoordTwoNm = convertToNanometres(parsedValue, metric);
        parsedValue = Float.parseFloat(tokens[5]);
        lineThicknessNm = convertToNanometres(parsedValue, metric);
        kicadLayer = Integer.parseInt(tokens[6]);
        // System.out.println("Kicad DS Layer is :" + kicadLayer);
      }
    else if (tokens[0].startsWith("fp_line"))
      {
        metric = true;
        parsedValue = Float.parseFloat(tokens[2]);
        xCoordOneNm = convertToNanometres(parsedValue, metric);
        parsedValue = Float.parseFloat(tokens[3]);
        yCoordOneNm = convertToNanometres(parsedValue, metric);
        parsedValue = Float.parseFloat(tokens[5]);
        xCoordTwoNm = convertToNanometres(parsedValue, metric);
        parsedValue = Float.parseFloat(tokens[6]);
        yCoordTwoNm = convertToNanometres(parsedValue, metric);
        parsedValue = Float.parseFloat(tokens[10]);
        lineThicknessNm = convertToNanometres(parsedValue, metric);
        // need to sort out layers though and parse text options though
        if (tokens[8].startsWith("F.Cu")) {
          kicadLayer = 15; // front most copper layer
        } else if (tokens[8].startsWith("B.Cu")) {
          kicadLayer = 0;
        } else if (tokens[8].startsWith("B.Paste")) {
          kicadLayer = 18;
        } else if (tokens[8].startsWith("F.Paste")) {
          kicadLayer = 19;
        } else if (tokens[8].startsWith("B.Silk")) {
          kicadLayer = 20;
        } else if (tokens[8].startsWith("F.Silk")) {
          kicadLayer = 21;
        } else if (tokens[8].startsWith("B.Mask")) {
          kicadLayer = 22;
        } else if (tokens[8].startsWith("F.Mask")) {
          kicadLayer = 23;
        }
        // kicadLayer = Integer.parseInt(tokens[8]);
        // System.out.println("Kicad DS Layer is :" + kicadLayer);
      }

    else
      {
        System.out.println("Why wasn't the drawn segment passed something useful?");
        output = "Hmm, a Draw Segment string was not passed to the object";
      }
  }

  public String lihataLine(long xOffset, long yOffset, float magnificationRatio) {
    return "      ha:line." + lineCount++ + " {\n       clearance = 0.0\n       thickness = " +
	lineThicknessNm + "nm\n" +
	"       x1 = " + (long)((xCoordOneNm + xOffsetNm)*magnificationRatio) + "nm\n" +
	"       y1 = " + (long)((yCoordOneNm + yOffsetNm)*magnificationRatio) + "nm\n" +
	"       x2 = " + (long)((xCoordTwoNm + xOffsetNm)*magnificationRatio) + "nm\n" +
	"       y2 = " + (long)((yCoordTwoNm + yOffsetNm)*magnificationRatio) + "nm\n" +
	"       ha:attributes {\n       }\n       ha:flags {\n       }\n      }\n";
  }

  public String generateElement(long xOffset, long yOffset, float magnificationRatio, String format)
  {
    if (format.equals("pcb")) {
      return generateGEDAelement(xOffset, yOffset, magnificationRatio);
    } else {
      return lihataLine(xOffset, yOffset, magnificationRatio);
    }
  }

  public String generateGEDAelement(long xOffsetNm, long yOffsetNm, float magnificationRatio) // offsets in nm, and magnificationRatio as float
  {
    // we take care of magnification here
    // this allows a family of footprint silkscreens graphics to
    // be generated fairly easily, for example different sized 7
    // or 17 segment LED displays
    // Device outlines may need to be hand tweaked, as some
    // device families share the same outline, despite different sized
    // features across the family of device, i.e. 0.5 inch and
    // 0.8 inch LED displays have the same outline silkscreen
    // which doesn't need to be magnified

    gEDAxCoordOne = (long)((xCoordOneNm + xOffsetNm)*magnificationRatio/254);
    // divide nm by 254 to produce
    gEDAyCoordOne = (long)((yCoordOneNm + yOffsetNm)*magnificationRatio/254);
    // 0.01 mil units
    gEDAxCoordTwo = (long)((xCoordTwoNm + xOffsetNm)*magnificationRatio/254);
    gEDAyCoordTwo = (long)((yCoordTwoNm + yOffsetNm)*magnificationRatio/254);

    long gEDAdefaultMetalClearance = 20;
    // NB defined here in thousandths of an inch = mils
    // (clearance/2) = minimum distance from pad/pin metal
    // to nearest copper
    // this gets multiplied by 100 for 0.01 mil units in output

    String gEDAflag = "";

    if (kicadLayer == 0)
      {
        gEDAflag = "onsolder";
      }

    gEDAlineThickness = (lineThicknessNm / 254); // every 254 nm is 0.01 mil

    if (kicadLayer == 21) // i.e. drawn segment drawn on top silkscreen
      { // currently ignoring bottom silkscreen B.SilkS = 20, and
        // B.Paste = 18, F.Paste = 19, since gEDA uses clearance value
        // and ignoring the F.Mask = 23 and B.Mask = 22 as well    
        output = "ElementLine[" +
            gEDAxCoordOne + " " +
            gEDAyCoordOne + " " +
            gEDAxCoordTwo + " " +
            gEDAyCoordTwo + " " +
            gEDAlineThickness + "]\n";
      }
    // the following catches Kicad drawing segment lines
    // drawn on front or back copper
    else if ((kicadLayer == 0) || (kicadLayer == 15))
      {
        output = "Pad[" +
            gEDAxCoordOne + " " +
            gEDAyCoordOne + " " +
            gEDAxCoordTwo + " " +
            gEDAyCoordTwo + " " +
            gEDAlineThickness + " " +
            (100*gEDAdefaultMetalClearance) + " " +
            "0 " + // let's give the pads zero solder mask relief
            //                                (100*gEDAdefaultSolderMaskRelief + (kicadShapeYsizeNm/254)) + " " +
            '"' + "DrawnElement" + "\" " +
            //                      '"' + kicadShapeNetName + "\" " +
            '"' + "DrawnElement" + "\" " +
            //                      '"' + kicadShapePadName + "\" " +
            '"' +
            gEDAflag +   // the flag is useful,  top vs onsolder placement
            '"' + "]\n";
      }
    return output;
  }

  public boolean isTop() {
    return kicadLayer == 21;
  }

  public boolean isBottom() {
    return kicadLayer == 20;
  }

}
