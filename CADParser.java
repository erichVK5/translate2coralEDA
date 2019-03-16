// CADParser - a utility for converting eagle elements into coralEDA CAD elements
// CADParser.java v1.0
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
//    CADParser Copyright (C) 2015,2019 Erich S. Heinzle a1039181@gmail.com
//    translate2coralEDA, translate2geda Copyright (C) 2015, 2019
//    Erich S. Heinzle a1039181@gmail.com



import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

class CADParser {

  static String theFile = null;
  static Integer pinSpacing = 20;
  static String symFormat = "xschem";
  static String fpFormat = "pcb-rnd";

  public static void setFormat(String format) {
    setSymFormat(format);
    setFPFormat(format);
  }
  
  public static void setSymFormat(String format) {
    if (format.equals("gschem") || format.equals("gEDA")) {
      symFormat = "gschem";
      pinSpacing = 200;
    } else if (format.equals("xschem") || format.equals("coral")) {
      symFormat = "xschem";
      pinSpacing = 20; 
    }
  }
  
  public static void setFPFormat(String format) {
    if (format.equals("PCB") || format.equals("gEDA")) {
      fpFormat = "PCB";
    } else if (format.equals("pcb-rnd") || format.equals("coral")) {
      fpFormat = "pcb-rnd";
    }
  }

  public Integer setPinSpacing(String format) {
    if (format.equals("gschem") || format.equals("gEDA")) {
      pinSpacing = 200; 
    } else if (format.equals("xschem") || format.equals("coral")) {
      pinSpacing = 20; 
    }
    return pinSpacing;
  }

  public static String symbolHeader(String format) {
    if (format.equals("gschem")) {
      return "v 20110115 1"; 
    } else { /* if (format.equals("xschem")) { */
      return "v {xschem version=2.8.1 file_version=1.0}\nG {}\nV {}\nS {}\nE {}\n"; 
    }
  }

  private long centimilToNM(float rawValue) {
    return (long)(254 * rawValue);
    // multiply 0.01 mil units by 254 to turn into nm
  }

  private long decimilToNM(float rawValue) {
    return (long)(2540 * rawValue);
    // multiply 0.1 mil units by 2540 to turn into nm
  }

  private long mmToNM(float rawValue) {
    return (long)(1000000 * rawValue);
    // multiply mm by 1000000 to turn into nm
  }


  private long convertToNanometres(float rawValue, boolean metricSystem) {
    if (metricSystem) { // metricSystem = units mm
      return (long)(1000000 * rawValue);
      // multiply mm by 1000000 to turn into nm
    } else {
      return (long)(2540 * rawValue);
      // multiply 0.1 mil units by 2540 to turn into nm
    }
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

  // the following method is used to avoid problems
  // with the gcj libs, which seem to occasionally return nulls
  // if hasNext() instead of hasNextLine() is used before
  // calling nextLine() to provide the string
  public static String safelyTrim(String text) {
    if (text != null) {
      return text.trim();
    } else {
      return "";
    }
  }
}
