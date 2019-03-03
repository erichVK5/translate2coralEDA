// translate2coralEDA - a utility for turning various EDA formats into gEDA/coralEDA CAD elements
// Circle.java v1.2
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
//    Circle.java Copyright (C) 2015, 2019 Erich S. Heinzle a1039181@gmail.com
//    translate2coralEDA, translate2geda Copyright (C) 2015, 2019
//    Erich S. Heinzle a1039181@gmail.com


/**
 *
 * This class is passed a Kicad Draw Circle string of the
 * form "DC Xcentre Ycentre Xpoint Ypoint width layer"
 * and implements a method which can generate a gEDA LineElement definition for a gEDA PCB footprint
 *
 * @param long xOffset the X offset of the Draw Segment relative to the module origin
 * @param long yOffset the Y offset of the Draw Segment relative to the module origin
 * @param float magnificationRatio the magnification applied to position and dimensions, default is 1.0
 *
 * @return String = "ElementArc[x y width height startangle deltaangle thickness]"
 *
 */

public class Circle extends FootprintElementArchetype
{

  String output = "";

  long gEDAxCoord = 0;
  long gEDAyCoord = 0;
  long xCoordNm = 0;
  long yCoordNm = 0;
  long xPointNm = 0;
  long yPointNm = 0;
  long gEDAstartAngle = 0;  // gEDA uses -ve X axis as origin for rotation CW
  long gEDAdeltaAngle = 360; // Kicad uses +ve X axis as origin for rotation CCW, I think
  double radiusNm = 0;
  long widthNm = 0;
  long heightNm = 0;
  long gEDAheight = 0;
  long gEDAwidth = 0;
  long gEDAradius = 0;
  long lineThicknessNm = 254000; // this is 10 mil in nanometres 
  long gEDAlineThickness = 1000; // this is 10 mil in 0.1 mil units
  long defaultLineThicknessNm = lineThicknessNm;
  String kicadCircleDescriptor = "";
  long kicadLayer = 21; // might be useful for conversions to kicad

  public Circle()
  {
    output = "#Hmm, the no arg KicadDrawingCircle constructor didn't do much";
  }

  public String toString()
  {
    return kicadCircleDescriptor;
  }

  public void populateKicadElement(String arg, boolean metric)
  {
    float parsedValue = 0;
		
    kicadCircleDescriptor = arg;

    String[] tokens = arg.split(" ");

    //		System.out.print("#The passed string:" + arg + "\n");
    if (tokens[0].startsWith("DC"))
      {
        parsedValue = Float.parseFloat(tokens[1]);
        xCoordNm = convertToNanometres(parsedValue, metric);
        parsedValue = Float.parseFloat(tokens[2]);
        yCoordNm = convertToNanometres(parsedValue, metric);
        parsedValue = Float.parseFloat(tokens[3]);
        xPointNm = convertToNanometres(parsedValue, metric);
        parsedValue = Float.parseFloat(tokens[4]);
        yPointNm = convertToNanometres(parsedValue, metric);
        parsedValue = Float.parseFloat(tokens[5]);
        lineThicknessNm = convertToNanometres(parsedValue, metric);
      }
    else if (tokens[0].startsWith("fp_circle"))
      {
        metric = true;
        parsedValue = Float.parseFloat(tokens[2]);
        xCoordNm = convertToNanometres(parsedValue, metric);
        parsedValue = Float.parseFloat(tokens[3]);
        yCoordNm = convertToNanometres(parsedValue, metric);
        parsedValue = Float.parseFloat(tokens[5]);
        xPointNm = convertToNanometres(parsedValue, metric);
        parsedValue = Float.parseFloat(tokens[6]);
        yPointNm = convertToNanometres(parsedValue, metric);
        if (tokens[9].startsWith("width")) {
          parsedValue = Float.parseFloat(tokens[10]);
          lineThicknessNm = convertToNanometres(parsedValue, metric);
        }
      }

    else
      {
        output = "Hmm, a Draw Circle string was not passed to the object";
      }

    // shift this here from geda generation element section
    // to allow eagle circles to work
    radiusNm = Math.sqrt(((xCoordNm - xPointNm)*(xCoordNm - xPointNm)) + ((yCoordNm - yPointNm)*(yCoordNm - yPointNm)));

  }

  // here, we populate the circle object with a string
  // extracted from an Eagle .lbr file
  // eagle uses "<circle..." for circles
  // it seems that the y-axis is inverted vs gEDA/kicad
  // gEDA's arc direction is +ve CCW
  // and Eagle's "curve" directive is +ve CCW, nifty
  // unlike kicad; CW +ve for kicad
  public void populateEagleElement(String EagleCircle) {
    EagleCircle = EagleCircle.replaceAll("[<>/]","");
    kicadLayer = 21; // i.e. F.Silk is assumed for now
    // since we only call this method if it is top silk
    // Incidentally, we don't need no XML/DOM...
    String [] tokens = EagleCircle.split(" ");
    for (int index = 0; index < tokens.length; index++) {
      if (tokens[index].startsWith("x=")) {
        xCoordNm = mmTextToNM(tokens[index].substring(3));
      } else if (tokens[index].startsWith("y=")) {
        yCoordNm = -mmTextToNM(tokens[index].substring(3));
      } else if (tokens[index].startsWith("radius=")) {
        radiusNm = mmTextToNM(tokens[index].substring(7));
      } else if (tokens[index].startsWith("width=")) {
        lineThicknessNm = mmTextToNM(tokens[index].substring(6));
        if (lineThicknessNm == 0) {
          lineThicknessNm = defaultLineThicknessNm;
        }
      } 
    }
  }

  public String lihataCircle(long xOffset, long yOffset, float magnificationRatio) {
    return "      ha:arc." + arcCount++ + " {\n       astart = 0\n       thickness = " +
	lineThicknessNm + "nm\n" +
	"       clearance = 0.0\n" +
	"       width = " + radiusNm*2 + "nm\n" +
	"       height = " + radiusNm*2 + "nm\n" +
	"       adelta = 360\n" +
	"       x = " + (long)((xCoordNm + xOffsetNm + xOffsetNm)*magnificationRatio) + "nm\n" +
	"       y = " + (long)((yCoordNm + xOffsetNm + yOffsetNm)*magnificationRatio) + "nm\n" +
	"       ha:attributes {\n       }\n       ha:flags {\n       }\n      }\n";
  }

  public String generateElement(long xOffset, long yOffset, float magnificationRatio, String format)
  {
    if (format.equals("pcb")) {
      return generateGEDAelement(xOffset, yOffset, magnificationRatio);
    } else {
      return lihataCircle(xOffset, yOffset, magnificationRatio);
    }
  }

  public String generateGEDAelement(long xOffsetNm, long yOffsetNm, float magnificationRatio)
  // offsets in nm, magnificationRatio a float, default 1.0
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

    // we previously calculated the radius in nanometres
    // and then apply the magnification ratio

    radiusNm = magnificationRatio*radiusNm;
    //		radiusNm = magnificationRatio*Math.sqrt(((xCoordNm - xPointNm)*(xCoordNm - xPointNm)) + ((yCoordNm - yPointNm)*(yCoordNm - yPointNm)));

    //		System.out.println("X, Y, Xp, Yp: " + xCoord + " " + yCoord + " " + xPoint + " " + yPoint);
    //		System.out.println("radius: " + radius );

    // we then convert the radius to decimils

    gEDAradius = Math.round(radiusNm / 254); // 254 nm per 0.01 mil unit used by gEDA

    //		System.out.println("gEDAradius = radius * 10: " + gEDAradius );

    gEDAxCoord = (long)((xCoordNm + xOffsetNm)*magnificationRatio/254);
    // divide nm by 254 to produce
    gEDAyCoord = (long)((yCoordNm + yOffsetNm)*magnificationRatio/254);
    // 0.01 mil units



    gEDAlineThickness = (lineThicknessNm / 254); // 254 nm per 0.01 mil unit used by gEDA

    output = "ElementArc[" +
        gEDAxCoord + " " +
        gEDAyCoord + " " +
        gEDAradius + " " + // for a circle, radius can be used for width
        gEDAradius + " " + // and height definitions in gEDA, as they are equal
        gEDAstartAngle + " " +
        gEDAdeltaAngle + " " +
        gEDAlineThickness + "]\n";
    return output;
  }

  public boolean isTop() {
    return true; // TODO: needs to reflect actual layer for object
  }


}
