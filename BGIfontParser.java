// BGIfontParser.java - a utility for parsing Borland BGI font files
//
// contains methods to parse Borland BGI font files (see also
// https://moddingwiki.shikadi.net/wiki/BGI_Stroked_Font )
//
// BGIfontParser.java v1.0
//
// Copyright (C) 2022 Erich S. Heinzle, a1039181@gmail.com

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
//    BGIfontParser Copyright (C) 2022 Erich S. Heinzle a1039181@gmail.com

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

// a class to decode Borland Turbo Pascal BGI stroked font data 

class BGIfontParser extends CADParser {

  private static boolean verbose = false;

  private static String filenameOrig = "";

  public BGIfontParser(String filename, boolean verbose) {
    File BGIFile = new File(filename);
    filenameOrig = filename;
    if (!BGIFile.exists()) {
      System.exit(0);
    } else {
      System.out.println("Parsing: " + filename + " and exporting format: " + fpFormat);
    }
    this.verbose = verbose;
  }

  // BGI files provide stroked font information in a plotter-esque binary format
  public static String [] convert(String BGIData) throws IOException {
    File BGIDefs = new File(BGIData);
    SourceBuffer BGIFonts = new SourceBuffer(BGIData);
    String newElement = "";
    String newGlyph = "";
    String lastline = "";
    String currentLine = "";
    int currentByte = 0;
    boolean inComment = false;
    String fontData = "";
    while (BGIFonts.hasNextByte()) {
      System.out.println("Is BGI file: " + BGIFonts.isBGIfont());
      System.out.println("BGI font description: " + BGIFonts.BGIfontDescription());
      System.out.println("BGI header size: " + BGIFonts.BGIheaderBlockSize() + " bytes");
      System.out.println("BGI font ID: " + BGIFonts.BGIfontID());
      System.out.println("BGI data block size: " + BGIFonts.BGIdataBlockSize());
      System.out.println("BGI font version (Maj.Min): " + BGIFonts.BGIfontMajorVersion() + "." + BGIFonts.BGIfontMinorVersion());
      System.out.println(BGIFonts.BGIjumpToDataBlock());
      System.out.println("BGI font stroked: " + BGIFonts.BGIconfirmStroked());
      System.out.println("BGI font glyph count: " + BGIFonts.BGIglyphCount());
      System.out.println(BGIFonts.BGIskipByte());
      System.out.println("Starting char: " + BGIFonts.BGIstartingChar());
      System.out.println("BGI stroke definition offset: " + BGIFonts.BGIstrokeDefinitionOffset());
      System.out.println(BGIFonts.BGIskipByte());
      System.out.println("BGI ascender: " + BGIFonts.BGIoriginToCapital());
      System.out.println("BGI baseline: " + BGIFonts.BGIoriginToBaseline());
      System.out.println("BGI descender: " + BGIFonts.BGIoriginToDescender());
      System.out.println(BGIFonts.BGIskipBytes(5));
      System.out.println("BGIcharacterStrokeOffsets():\n" + BGIFonts.BGIcharacterStrokeOffsets());
      System.out.println("BGIcharacterWidths():\n" + BGIFonts.BGIcharacterWidths());
      fontData = BGIFonts.BGIgetGlyphStrokes();
      System.out.println("Symbol defs:\n" + fontData);
      break;
    }
    elementWrite(filenameOrig + ".font", fontData);
    String [] returnedFilename = {filenameOrig};
    return returnedFilename;
  }

}
