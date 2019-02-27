// KicadSymbolToGEDA - a utility for turning kicad modules to gEDA PCB footprints
// SymbolNet.java v1.0
// Copyright (C) 2016 Erich S. Heinzle, a1039181@gmail.com

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
//    KicadSymbolToGEDA Copyright (C) 2015 Erich S. Heinzle a1039181@gmail.com



/**
 *
 * This class is passed an LT spice net descriptor string of the form
 * "WIRE 128 80 -16 80"
 * and implements a method which can generate a gschema
 * definition for a gEDA net; zero length nets are ignored
 * colour is usually: "4"	NET_COLOR 
 *
 * @param long xOffset the X offset of the net relative to the module origin
 * @param long yOffset the Y offset of the Draw net relative to the module origin
 * @param float magnificationRatio the magnification ratio to be applied to element position and size
 *
 * @return String = "N x1 y1 x2 y2 color"
 *
 */

public class SymbolNet extends SymbolElement
{

  String netDescriptor = "";  
  String output = "";
  
  long xCoordOne = 0;
  long yCoordOne = 0;
  long xCoordTwo = 0;
  long yCoordTwo = 0;
  int netColor = 4;

  double LTSpiceScalingFactor = 12.5;
  // some QUCS is on 10 spaced grids, most is on 20 spaced grids
  // with devices ~60 units long/high
  double QUCSScalingFactor = 10;

  public SymbolNet()
  {
    output = "#Hmm, the no arg net constructor didn't do much";
  }

  public SymbolNet(int x1, int y1, int x2, int y2, int color) {
    xCoordOne = x1;
    yCoordOne = y1;
    xCoordTwo = x2;
    yCoordTwo = y2;
    super.updateXdimensions(xCoordOne);
    super.updateYdimensions(yCoordOne);
    super.updateXdimensions(xCoordTwo);
    super.updateYdimensions(yCoordTwo);
    netColor = color;
  }

  public SymbolNet(String descriptor) {
    if (descriptor.startsWith("WIRE")) { // can extend for other formats
      LTSconstructor(descriptor);
    } else {
      QUCSconstructor(descriptor);
    }
  }

  public void QUCSconstructor(String arg) // takes QUCS wire descriptor
  {
    netDescriptor = arg;
    arg = arg.replaceAll("  "," ");
    arg = arg.replaceAll("<","");
    String[] tokens = arg.split(" ");    
    xCoordOne = QUCSScale(Integer.parseInt(tokens[0]));
    yCoordOne = QUCSScale(-Integer.parseInt(tokens[1]));
    xCoordTwo = QUCSScale(Integer.parseInt(tokens[2]));
    yCoordTwo = QUCSScale(-Integer.parseInt(tokens[3]));
    super.updateXdimensions(xCoordOne);
    super.updateYdimensions(yCoordOne);
    super.updateXdimensions(xCoordTwo);
    super.updateYdimensions(yCoordTwo);
  }



  public void LTSconstructor(String arg) // takes LTSpice descriptor
  { // could have others to deal with Eagle or kicad descriptors
    netDescriptor = arg;
    //System.out.println(arg);
    arg = arg.replaceAll("  "," ");
    String[] tokens = arg.split(" ");    
    xCoordOne = LTSpiceScale(Integer.parseInt(tokens[1]));
    yCoordOne = LTSpiceScale(-Integer.parseInt(tokens[2]));
    xCoordTwo = LTSpiceScale(Integer.parseInt(tokens[3]));
    yCoordTwo = LTSpiceScale(-Integer.parseInt(tokens[4]));
    super.updateXdimensions(xCoordOne);
    super.updateYdimensions(yCoordOne);
    super.updateXdimensions(xCoordTwo);
    super.updateYdimensions(yCoordTwo);
  }

  public long localMinXCoord() {
    if (xCoordOne < xCoordTwo) {
      return xCoordOne;
    } else {
      return xCoordTwo;
    }
  }

  public long localMinYCoord() {
    if (yCoordOne < yCoordTwo) {
      return yCoordOne;
    } else {
      return yCoordTwo;
    }
  }

  public String toString(long xOffset, long yOffset) {
    //int colorIndex = 3;
    return ("N "
            + (xCoordOne + xOffset) + " "
            + (yCoordOne + yOffset) + " " 
            + (xCoordTwo + xOffset) + " "
            + (yCoordTwo + yOffset) + " "
            + netColor); /// default colr index is 4 (= green)
  }

  private long LTSpiceScale(long dimension) {
    return (long)(dimension*LTSpiceScalingFactor);
  } 

  private long QUCSScale(long dimension) {
    return (long)(dimension*QUCSScalingFactor);
  } 


}
