// translate2coralEDA - a utility for turning various EDA formats into gEDA/coralEDA CAD elements
// PolyPour.java v1.2
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
//    PolyPour.java Copyright (C) 2019 Erich S. Heinzle a1039181@gmail.com



/**
 *
 * This class is passed a pair of ArrayList<long> objects, with
 * x and y coordinates for the polygon's vertices, or can be built
 * up point wise by addition of pairs of coordinates
 *
 * @param long xOffset the X offset of the Draw Segment relative to the module origin
 * @param long yOffset the Y offset of the Draw Segment relative to the module origin
 * @param float magnificationRatio magnification to be applied to segment position and size, default 1.0
 *
 * @return String = "ElementArc[x y width height startangle deltaangle thickness]"
 *
 */

import java.util.ArrayList;

public class PolyPour extends FootprintElementArchetype
{
  ArrayList<Long> xCoord = new ArrayList<Long>();
  ArrayList<Long> yCoord = new ArrayList<Long>();
  
  long xCoordNm = 0;
  long yCoordNm = 0;
  int kicadLayer = 0;

  String output = "";
  
  public String toString()
  {
    return "PolyPour toString() output";
  }

  public PolyPour() {
    xCoord.clear();
    yCoord.clear();
  }

  public PolyPour(ArrayList<Long> xCoords,
                  ArrayList<Long> yCoords,
                  int pinNum) {
    xCoord.clear();
    yCoord.clear();
    populateGerberElement(xCoords, yCoords, pinNum);
    kicadLayer = 15; // top copper default
  }

/* <polygon width="0.127" layer="1">
<vertex x="-1.2" y="0.7"/>
<vertex x="-1.2" y="-0.8"/>
<vertex x="1.1" y="-0.8"/>
<vertex x="1.1" y="0.4"/>
<vertex x="0.8" y="0.7"/>
</polygon>
*/

  public void populateEagleElement(String eagleLine) {
    eagleLine = eagleLine.replaceAll("[>/]"," ");
    kicadLayer = 15; // i.e. F.Cu is assumed for now
    long x = 0;
    long y = 0;
    long xOld = 0;
    long yOld = 0;
    float degrees = 0;
    float previousDegrees = 0;
    boolean previousArc = false;
    boolean pendingArc = false;
    int coordCount = 0;
    String [] tokens1 = eagleLine.split("<vertex ");
    for (int i = 0; i < tokens1.length; i++) {
      String [] tokens = tokens1[i].split(" ");
      for (int index = 0; index < tokens.length; index++) {
        if (tokens[index].startsWith("x=")) {
          x = mmTextToNM(tokens[index].substring(2));
          coordCount++;
        } else if (tokens[index].startsWith("y=")) {
          // yCoordOneNm = -mmTextToNM(tokens[index].substring(3));
          y = -mmTextToNM(tokens[index].substring(2));
          coordCount++;
        } else if (tokens[index].startsWith("curve=")) {
          String textAngle =  tokens[index].replaceAll("[\"]", "");
          degrees
              = Float.parseFloat(textAngle.substring(6));
          pendingArc = true;
          // System.out.println("Unsupported polygon border curve");
        }
      }
      if (coordCount == 2
          && previousArc == false && pendingArc == false) {
        xCoord.add(x); // simplest scenario
        yCoord.add(y);
        //        System.out.println("Added x: " + x + ", and y: "
        //                   + y + " to poly pour."); 
      } else if (coordCount == 2
                 && previousArc == false && pendingArc == true) {
        xCoord.add(x); // next simplest scenario
        yCoord.add(y);
        xOld = x;
        yOld = y;
        previousArc = true;
        previousDegrees = degrees;
        pendingArc = false;
      } else if (coordCount == 2 && previousArc == true) {
        // most complicated scenario
        Arc a = new Arc();
        String eagleCmd = "x1=" + xOld/1000000.0 + " " +
            "y1=" + (-yOld/1000000.0) + " " +
            "x2=" + x/1000000.0 + " " +
            "y2=" + (-y/1000000.0) + " " +
            "curve=" + previousDegrees + " " +
            "width=1.0"; // ignore actual value for now
        //System.out.println("Eagle arc descriptor: " + eagleCmd);
        a.populateEagleElement(eagleCmd);
        long [] points = a.asSegments();
        //System.out.println("About to approximate arc with segments");
        for (int j = 2; j < (points.length - 2); j = j+2) {
          xCoord.add(points[j]);
          yCoord.add(-points[j+1]);
        }
        xCoord.add(x);
        yCoord.add(y);
        previousArc = false;
        if (pendingArc == true) {
          xOld = x;
          yOld = y;
          previousArc = true;
          previousDegrees = degrees;
          pendingArc = false;
        }
      }
      coordCount = 0;
    }
    // we have run out of polygon defs now
    if (previousArc) {   // this catches a trailing curve=... in a final
      Arc a = new Arc(); // vertex definition
      String eagleCmd = "x1=" + xOld/1000000.0 + " " +
          "y1=" + (-yOld/1000000.0) + " " +
          "x2=" + xCoord.get(0)/1000000.0 + " " +
          "y2=" + (-yCoord.get(0)/1000000.0) + " " +
          "curve=" + previousDegrees + " " +
          "width=1.0";
      //System.out.println("Final Eagle arc descriptor: " + eagleCmd);
      a.populateEagleElement(eagleCmd);
      long [] points = a.asSegments();
      //System.out.println("About to approximate arc with segments");
      for (int j = 2; j < (points.length - 2); j = j+2) {
        xCoord.add(points[j]);
        yCoord.add(-points[j+1]);
      }
    }
    //System.out.println("Done with PolyPour elements");
    //System.out.println(lihataPolyPour(0,0,1.0f));
  }

  
  public void populateGerberElement(ArrayList<Long> xCoords,
                                    ArrayList<Long> yCoords,
                                    int pinNum) {
    kicadLayer = 15; // use top copper as default polygonal pour layer
    for (long x : xCoords) {
      xCoord.add(x);
    }
    for (long y : yCoords) {
      yCoord.add(y);
    }
    // System.out.println("populated PolyPour object with " +
    //                      + xCoords.size() + " vertices");
  }
  
  public String lihataPolyPour(long xOffset, long yOffset, float magnificationRatio) {
    String points = "";
    int exportMaxVertices = 10000;
    int npoints = xCoord.size();
    if (npoints == yCoord.size() && npoints < exportMaxVertices) {
      for (int i = 0; i < npoints; i++) {
        Long x = (long)((xOffset + xCoord.get(i))*magnificationRatio);
        Long y = (long)((yOffset - yCoord.get(i))*magnificationRatio);
        points = points
            + "         { " + x + "nm; " + y + "nm }\n";
      }
      points =
          "      ha:polygon." + pourCount++ + " {\n" +
          "       ha:attributes {\n" +
          "       }\n       li:geometry {\n        ta:contour {\n" +
          points +
          "        }\n       }\n       ha:flags {\n" +
          "         clearpoly = 0\n       }\n      }\n";
    }
    return points;
  }
  
  public String generateElement(long xOffset, long yOffset, float magnificationRatio, String format)
  {
    if (format.equals("pcb")) {
      return generateGEDAelement(xOffset, yOffset, magnificationRatio);
    } else {
      return lihataPolyPour(xOffset, yOffset, magnificationRatio);
    }
  }

  public String generateGEDAelement(long xOffsetNm, long yOffsetNm, float magnificationRatio)
  {
    output = "####ElementPolygonNotSupportedBygEDApcb[]\n";
    return output;
  }

  public boolean isTop() {
    return kicadLayer == 21;
  }

  public boolean isTopCopper() {
    return kicadLayer == 15;
  }

}
