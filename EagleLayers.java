// EagleLayers.java - part of a utility for converting Eagle files
// into gschem symbols
//
// EagleLayers.java v1.0
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
//    EagleLayers Copyright (C) 2016 Erich S. Heinzle a1039181@gmail.com

// Some good reading at 
// https://learn.adafruit.com/ktowns-ultimate-creating-parts-in-eagle-tutorial/creating-a-package-outline


import java.util.ArrayList;
import java.util.List;

public class EagleLayers {

  List<List<String>> layers = new ArrayList<List<String>>();
  String line = "";
  String [] tokens;
  String [] lookupVals = new String[2];
  String newNum = "";
  String newName = "";
  ArrayList<String> valPair = new ArrayList<String>();
  boolean verbose = false;
  int layerCount = 0;

  // we use the following to speed up searches for a layer match
  String currentTopCopper = "";
  String currentBottomCopper = "";
  String currentTopSilk = "";
  String currentDrawnSilkText = "";

  public EagleLayers() {
    //empty
  }

  public EagleLayers(ArrayList<String> layerDefs) {
    for (int index = 0; index < layerDefs.size(); index++) {
      line = layerDefs.get(index).trim();
      while (line.startsWith("<layer ") && 
             !line.endsWith("/>") &&
             (index != layerDefs.size() - 1)) {
        line = line + " " + layerDefs.get(++index).trim();
        // if it spans more than one line, we combine it
        // with next line, provided there is a next line
      }
      if (line.startsWith("<layers>") ||
          line.startsWith("</layers>")) {
        // we do nothing
      } else if (line.startsWith("<layer ")) {
        line = line.replaceAll("[<>/]", "");
        tokens = line.split(" ");
        ArrayList<String> valPair = new ArrayList<String>();
        for (String field : tokens) {
          if (field.startsWith("number")) {
            valPair.add(0,field.substring(7).replaceAll("[\"]", ""));
          } else if (field.startsWith("name")) {
            valPair.add(1,field.substring(5).replaceAll("[\"]", ""));
          }
        }
        layers.add(layerCount++, valPair);
      }
    }
    if (verbose) {
      for (int index = 0; index < layerCount; index++) {
        System.out.println("Eagle layer readback test, at index : " + 
                           index + " : " +layers.get(index));
      }
    }

    for (List<String> layerValues : layers) {
      if (layerValues.get(1).equals("tPlace")) {
        currentTopSilk = layerValues.get(0); // the layer number
      } else if (layerValues.get(1).equals("Top")) {
        currentTopCopper = layerValues.get(0); // the layer number
      } else if(layerValues.get(1).equals("Bottom")) {
        currentBottomCopper = layerValues.get(0); // the layer number
      } else if(layerValues.get(1).equals("tNames")) {
        currentDrawnSilkText = layerValues.get(0); // the layer number
      }
    }

    if (currentTopSilk.equals("") ||
        currentDrawnSilkText.equals("") ||
        currentTopCopper.equals("") ||
        currentBottomCopper.equals("")) {
      System.out.println("Eagle library's layer definitions seem to be"
                         + "be incomplete.\nConversion may be "
                         + "severely compromised.");
    }
  }

  public String layerNameIs(String layerNum) {
    String layerIs = "";
    layerNum = extractLayerNumText(layerNum);
    for (List<String> layerDesc : layers) {
      if (layerDesc.get(0).equals(layerNum)) {
        layerIs = layerDesc.get(1);
      }
    }
    return layerIs;
  }

  public boolean isDrawnTopSilk(String layerNum) {
    layerNum = extractLayerNumText(layerNum);
    return (layerNum.equals(currentTopSilk));
  }

  public boolean isTopCopper(String layerNum) {
    layerNum = extractLayerNumText(layerNum);
    return (layerNum.equals(currentTopCopper));
  }

  public boolean isBottomCopper(String layerNum) {
    layerNum = extractLayerNumText(layerNum);
    return (layerNum.equals(currentBottomCopper));
  }

  public boolean isSilkText(String layerNum) {
    layerNum = extractLayerNumText(layerNum);
    return (layerNum.equals(currentDrawnSilkText));
  }

  private String extractLayerNumText(String arg) {
    // we let the method cope with an entire XML line
    // or just the "number=\"number\"" text 
    String[] tokenised = arg.split(" ");
    for (String token : tokenised) {
      if (token.startsWith("layer=")) {
        arg = token.substring(7);
      }
    }
    return arg.replaceAll("[\"/>]", "");
  } 

}
