// SourceBuffer.java - an object used to manage raw binary files
//
// contains methods to cache and convert a Huffman encoded file
// plus methods to parse Borland BGI font files (see also
// https://moddingwiki.shikadi.net/wiki/BGI_Stroked_Font )
//
// BXLDecoder.java v1.0
// SourceBuffer.java v1.1
// BGIfontParser.java v1.0
//
// Copyright (C) 2016,2022 Erich S. Heinzle, a1039181@gmail.com

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
//    SourceBuffer Copyright (C) 2016,2022 Erich S. Heinzle a1039181@gmail.com


import java.io.*;
import java.util.Scanner;
import java.lang.StringBuffer;

public class SourceBuffer {

  private char[] source_buffer = null;
  private int source_index = 4;
  private int source_char = 0;
  private int bit = 0;
  private int BGIglyphOriginToAscender = 0;
  private int BGIglyphOriginToDescender = 0;
  private int BGIheaderSizeBytes = 0;
  private int BGIstartingChar = 0;
  private int BGIcharacterCount = 0;
  private int BGIstrokeDefOffset = 0;
  private int[] characterWidths;
  private int[] strokeOffsets; 
  public SourceBuffer(String filename) {
    FileInputStream input = null;
    char [] ret_buffer = null; 
    try {
      input = new FileInputStream(filename);
      int c;
      char [] buffer = new char[1000];
      int bufferIndex = 0;
      // System.out.println("about to read bytes from file");
      while ((c = input.read()) != -1) {
        if (bufferIndex == buffer.length) {
          char [] newBuffer = new char[buffer.length*2];
          for (int index = 0; index < buffer.length; index++) {
            newBuffer[index] = buffer[index];
          }
          buffer = newBuffer;
        }
        buffer[bufferIndex] = (char)c;
        bufferIndex++;
      }
      ret_buffer = new char[bufferIndex];
      for (int index = 0; index < bufferIndex; index++) {
        ret_buffer[index] = buffer[index];
      }
      //System.out.println("finished reading bytes from file");
      //      is_filled = true; // hack, should check
      input.close();
    } catch (Exception e) {
      System.out.println ("Exception: " + e);
    }
    source_buffer = ret_buffer;
  }


  public int read_next_bit() {
    int result = 0;
    if (bit < 0) {
      // Fetch next byte from source_buffer
      bit = 7;
      // System.out.println("About to get byte number " +
      //                   source_index + " from source buffer");
      source_char = (int)source_buffer[source_index];
      result = source_char & (1 << bit);
      source_index++;
    } else {
      result = source_char & (1 << bit);
    }
    bit--;
    // System.out.println("bit now: " + bit);
    // System.out.println("source_index now: " + source_index);
    return result;
  }

  public int readInt16() {
    int result = nextByte();
    result += 256*nextByte(); // little endian format int16
    return result;
  }

  public int jumpToByte(int index) {
    source_index = index;
    return source_index;
  }

  // following BGI properties must be read in the following order

  public boolean isBGIfont() {
    source_index = 0;
    boolean result = true;
    int current = nextByte();
    System.out.println(current);
    result &= (current == 80);
    current = nextByte();
    System.out.println(current);
    result &= (current == 75);
    current = nextByte();
    System.out.println(current);
    result &= (current == 8);
    current = nextByte();
    System.out.println(current);
    result &= (current == 8);
    return result; 
  }

  private int BGIglyphHeight() {
	  return BGIglyphOriginToAscender + BGIglyphOriginToDescender;
  }

  public String BGIfontDescription() {
    String result = "";
    int current = nextByte();
    while (current != 26) {
      result = result + String.valueOf((char)current);
      current = nextByte();
    }
    return result; 
  }

  public int BGIheaderBlockSize() {
    BGIheaderSizeBytes = readInt16();
    return BGIheaderSizeBytes;
  }

  public String BGIfontID() {
    String result = "";
    result = result + (char)nextByte();
    result = result + (char)nextByte();
    result = result + (char)nextByte();
    result = result + (char)nextByte();
    return result;
  }

  public int BGIdataBlockSize() {
    return readInt16();
  } 

  public int BGIfontMajorVersion() {
    return readInt16();
  }

  public int BGIfontMinorVersion() {
    return readInt16();
  }

  public String BGIjumpToDataBlock() {
    //source_index = BGIheaderSizeBytes;
    String ret = "Jumped to index " + jumpToByte(BGIheaderSizeBytes);
    return ret;
  }

  public boolean BGIconfirmStroked() {
    return ((int)nextByte() == 43);
  }

  public int BGIglyphCount() {
    BGIcharacterCount = readInt16();
    return BGIcharacterCount;
  }

  public String BGIskipByte() {
    source_index++;
    String ret = "Index now: " + source_index + " after skipped byte";
    return ret;
  }

  // skip byte here

  public String BGIstartingChar() {
    String retval = "";
    BGIstartingChar = nextByte();
    System.out.println("Starting char read: " + BGIstartingChar); 
    retval = retval + BGIstartingChar;
    return retval;
  }

  public int BGIstrokeDefinitionOffset() {
    BGIstrokeDefOffset = readInt16();
    return BGIstrokeDefOffset;
  }

  // skip byte here

  public int BGIoriginToCapital() {
    BGIglyphOriginToAscender = nextByte();
    return BGIglyphOriginToAscender;
  }

  public int BGIoriginToBaseline() {
    return nextByte();
  }

  public int BGIoriginToDescender() {
    BGIglyphOriginToDescender = nextByte();
    return BGIglyphOriginToDescender;
  }

  public String BGIskipBytes(int n) {
    for (int i = 0; i < n; i++) {
      BGIskipByte();
    }
    return "Index now: " + source_index + " after " + n + " skipped bytes"; 
  }

  public int[] BGIcharacterStrokeOffsets() {
    strokeOffsets = new int[BGIstartingChar + BGIcharacterCount];
    for (int i = BGIstartingChar; i < BGIstartingChar + BGIcharacterCount; i++) {
      strokeOffsets[i] = readInt16();
      System.out.println("Stroke offset [" + i + "]: " + strokeOffsets[i]);
    }
    return strokeOffsets;
  }

  public int[] BGIcharacterWidths() {
    characterWidths = new int[BGIstartingChar + BGIcharacterCount];
    for (int i = BGIstartingChar; i < BGIstartingChar + BGIcharacterCount; i++) {
      characterWidths[i] = nextByte();
      System.out.println("Width [" + i + "]: " + characterWidths[i]); 
    }
    return characterWidths; 
  }

  public String BGIgetGlyphStrokes() {
    boolean finished = false;
    int XByte, YByte, XCurrent, YCurrent, XStart, YStart, XBuffer, YBuffer;
    int XNew, YNew, XOpcode, YOpcode, XSign, YSign;
    float scaling  = 5000.0f/BGIglyphHeight();
    String result = "";
    XStart = 0; // glyph x offset
    YStart = 0; // glyph y offset
    XCurrent = 0;
    YCurrent = 0;
    for (int i = BGIstartingChar; i < (BGIcharacterCount - BGIstartingChar); i++) {
     jumpToByte(strokeOffsets[i] + BGIstrokeDefOffset + BGIheaderSizeBytes);
     result = result + "Symbol['" + (char)i + "' " // start with space char ' '
                           + characterWidths[i]*scaling
                           + "]\n(\n";
     finished = false;
     while (!finished) {
      XByte = nextByte();
      YByte = nextByte();
      XOpcode = XByte & 0b10000000;
      YOpcode = YByte & 0b10000000;
      XSign = XByte & 0b01000000;
      YSign = YByte & 0b01000000;
      XByte = XByte & 0b00111111;
      YByte = YByte & 0b00111111;

      XNew = XByte;
      YNew = YByte;

      if (XSign > 0)
        XNew = -XNew;
      if (YSign > 0) {
        YNew = (64 - YNew); //              ' Normalize the descender's value.
        YNew = -YNew;
      }
      //' Normalize the Y position.
      YNew = (64 - YNew);
      if (XOpcode == 0) {
	     if (YOpcode == 0) {
                                //' End of character definition. Move to the next character.
                                //XStart = XStart + CharacterWidths(I) + XBuffer
                                //If XStart > 950 Then
                                //        XStart = 10
                                //        YStart = YStart + CharacterHeight + YBuffer
                                //End If
				result = result + ")\n";
                                finished = true;
	     }
      } else {
	      if (YOpcode == 0) {
                                //' Move Pointer to X, Y.
                                //' This happens below since both move and draw must change the position.
              } else {
                                // Draw From Current Pointer to new X, Y.
				result = result + "\tSymbolLine[" +
                               (long)(XCurrent*scaling) + " " +
                               (long)(YCurrent*scaling) + " " +
                               (long)(XNew*scaling) + " " +
                               (long)(YNew*scaling) + " 700]\n";
                                //Line(XCurrent, YCurrent)-(XStart + XNew, YStart + YNew), RGB(255, 255, 255)
              }
              XCurrent = XStart + XNew;
              YCurrent = YStart + YNew;
      }
     }
    }
    return result;
  }

// the above BGI access methods need to be read in the order they
// appear to keep the index into the file kosher  

  public int nextByte() {
    int result = 0;
      // System.out.println("About to get byte number " +
      //                   source_index + " from source buffer");
    result = (int)source_buffer[source_index];
    source_index++;
    bit = 7;
    // System.out.println("bit now: " + bit);
    // System.out.println("source_index now: " + source_index);
    return result;
  }

  public boolean hasNextByte() {
    return source_index < source_buffer.length;
  }

  public int uncompressed_size() {
    /* Uncompressed size =
       B0b7 * 1<<0 + B0b6 * 1<<1 + ... + B0b0 * 1<<7 +
       B1b7 * 1<<0 + B1b6 * 1<<1 + ... + B2b0 * 1<<7 +
       B2b7 * 1<<0 + B2b6 * 1<<1 + ... + B3b0 * 1<<7 +
       B3b7 * 1<<0 + B3b6 * 1<<1 + ... + B4b0 * 1<<7
    */
    int size = 0;
    int mask = 0;
    for (int i = 7 ; i >=0 ; i--) {
      if ((source_buffer[0] & (1 << i)) != 0) {
        size |= (1 << mask);
      }
      mask++;
    }
    for (int i = 7 ; i >=0 ; i--) {
      if ((source_buffer[1] & (1 << i)) != 0) {
        size |= (1<<mask);
      }
      mask++;
    }
    for (int i = 7 ; i >=0 ; i--) {
      if ((source_buffer[2] & (1 << i)) != 0) {
        size |= (1<<mask);
      }
      mask++;
    }
    for (int i = 7 ; i >=0 ; i--) {
      if ((source_buffer[3] & (1 << i)) != 0) {
        size |= (1<<mask);
      }
      mask++;
    }
    return size;
  }

  public String decode() {

    NodeTree tree = new NodeTree();

    int out_file_length = uncompressed_size();
    //String sb = "";
    // immutable Strings replaced with more efficient string handling, suggested by wlbaker: 
    StringBuffer sb = new StringBuffer(out_file_length);
    // System.out.println("About to enter decoding while loop...");
    while (source_index < source_buffer.length && sb.length() != out_file_length) {
      //System.out.println("Have entered decoding while loop...");
      Node node = tree.getRoot();
      //      System.out.println("About to enter leaf finding while loop...");
      while (!node.is_leaf()) {
        // find leaf node
        // System.out.println("now searching for leaf node...");
        if (read_next_bit() != 0) {
          //        if (read_next_bit(source_index, source_char, bit, source_buffer) != 0) {
          node = node.left;
          // System.out.println("Picking left node, source bit != 0.");
        } else {
          node = node.right;
          // System.out.println("Picking right node, source bit == 0.");
        }
      }
      // System.out.println("Node symbol: " + (char)(node.symbol));
      // System.out.println("Node symbol as toString: " + node);

      sb.append((char)node.symbol); // more efficient string building, thanks wlbaker
      //sb = sb + (char)(node.symbol);

      //      sb = sb + node;
      //      sb = sb + ((char)(node.symbol & 0xff));
      //      node.weight += 1;
      node.incrementWeight();
      // System.out.println("decoded text so far is: " +
      //                   sb + ", now to update tree...");
      tree.update_tree(node);
    }
    //source_buffer = null; // not needed for standalone utility
    //is_filled = false;
    return sb.toString();
  }

}
