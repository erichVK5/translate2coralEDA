// translate2coralEDA - a utility for turning various EDA formats into gEDA/coralEDA CAD elements
// footprintElementArchetype.java v1.2
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
//    translate2coralEDA Copyright (C) 2015, 2019 Erich S. Heinzle a1039181@gmail.com
//    translate2coralEDA, translate2geda Copyright (C) 2015, 2019
//    Erich S. Heinzle a1039181@gmail.com


public class FootprintElementArchetype
{

  long xOffsetNm = 0;
  long yOffsetNm = 0;

  static int lineCount = 0;
  static int arcCount = 0;
  static int textCount = 0;
  static int padStackProtos = 0;
  static int padstackInstances = 0;
  static int pourCount = 0;

  public long Xposition()
  {
    return xOffsetNm;
  }

  public long Yposition()
  {
    return yOffsetNm;
  }

  public FootprintElementArchetype()
  {
    xOffsetNm = 0;
    yOffsetNm = 0;
  }

  public FootprintElementArchetype(long x, long y)
  {
    xOffsetNm = x;
    yOffsetNm = y;
  }

  public String toString()
  {
    return("x: " + xOffsetNm + ", y: " + yOffsetNm);
  }

  public String generateElement(long xOffset, long yOffset, float magnificationRatio, String format)
  {
    return "";
  }

  public void populateElement(String moduleDefinition, boolean metric)
  {
    System.out.println("You're not supposed to see this.");		
  }

  public void populateElement(String moduleDefinition, boolean metric, long minimumViaDrillSize)
  {
    System.out.println("You're not supposed to see this.");
  }

  public boolean isPad() {
    return false;
  }
	
  public boolean isTop() {
    return false;
  }

  public boolean isTopCopper() {
    return false;
  }

  public boolean isBottomCopper() {
    return false;
  }

  public boolean isBottom() {
    return false;
  }

  public boolean isInternal() {
    return false;
  }

  public static long mmTextToNM(String mmValue) {
    mmValue = mmValue.replaceAll("[\"]","");
    return (long)(1000000*Float.parseFloat(mmValue)); 
  }


  public static long milToNM(float rawValue)
  {
    return (long)(rawValue * 25400 ); // 1 mil unit = 25400 nm
  }


  public static long convertToNanometres(float rawValue, Boolean metricSystem)
  {
    if (metricSystem)
      {
        return (long)(rawValue * 1000000); // 1 mm = 1000000 nm
      }
    else
      {
        return (long)(rawValue * 2540 ); // 0.1 mil unit = 2540 nm
      }
  }
	
}
