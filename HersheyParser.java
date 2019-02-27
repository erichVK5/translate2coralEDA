import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

// Eagle libraries provide footprint data, pin mapping, and
// schematic symbol data
// This parser can also be used for XML .brd files to extract contents

class HersheyParser extends CADParser {

  private static boolean verbose = false;

  private static String format;

  public HersheyParser(String filename, String format, boolean verbose) {
    File HersheyFile = new File(filename);
    if (!HersheyFile.exists()) {
      System.exit(0);
    } else {
      this.format = format;
      System.out.println("Parsing: " + filename + " and exporting format: " + format);
      setPinSpacing(format); // we won't be using pin spacing or format for now
    }
    this.verbose = verbose;
  }

  // Hershey files provide stroked font information in NIST format,
  // sometimes found in the wild in .py files
  public static String [] convert(String hersheyData) throws IOException {
    File hersheyDefs = new File(hersheyData);
    Scanner hersheyFonts = new Scanner(hersheyDefs);
    List<String> convertedFiles = new ArrayList<String>();
    ArrayList<String> fontDefs = new ArrayList<String>();
    String newElement = "";
    String newGlyph = "";
    String lastline = "";
    String currentLine = "";
    boolean inComment = false;

    while (hersheyFonts.hasNextLine() && (lastline != null)) {
      lastline = hersheyFonts.nextLine();// make nextLine() null safe 
      currentLine = safelyTrim(lastline); // when using gcj libs
      if (currentLine.startsWith("'''") && !inComment) {
        inComment = true;
        System.out.println("# Comment found in Hershey font def file");
      } else if (inComment) {
        if (currentLine.startsWith("'''")) {
          inComment = false;
        }
      } else if (currentLine.endsWith("\"]")) {
        if (currentLine.contains(" = ")) {
          fontDefs.add(currentLine);
          System.out.println("# Font def found:  "
                             + currentLine.substring(0,currentLine.indexOf(" = ")));
        }
      }
    }

    System.out.println("# Number of font defs = "
                       + fontDefs.size());

    System.out.println("# USE RESTRICTION:");
    System.out.println("#	This distribution of the Hershey Fonts may be used by anyone for");
    System.out.println("#	any purpose, commercial or otherwise, providing that:");
    System.out.println("#		1. The following acknowledgements must be distributed with");
    System.out.println("#			the font data:");
    System.out.println("#			- The Hershey Fonts were originally created by Dr.");
    System.out.println("#				A. V. Hershey while working at the U. S.");
    System.out.println("#				National Bureau of Standards.");
    System.out.println("#			- The format of the Font data in this distribution");
    System.out.println("#				was originally created by");
    System.out.println("#					James Hurt");
    System.out.println("#					Cognition, Inc.");
    System.out.println("#					900 Technology Park Drive");
    System.out.println("#					Billerica, MA 01821");
    System.out.println("#					(mit-eddie!ci-dandelion!hurt)");
    System.out.println("#		2. The font data in this distribution may be converted into");
    System.out.println("#			any other format *EXCEPT* the format distributed by");
    System.out.println("#			the U.S. NTIS (which organization holds the rights");
    System.out.println("#			to the distribution and use of the font data in that");
    System.out.println("#			particular format). Not that anybody would really");
    System.out.println("#			*want* to use their format... each point is described");
    System.out.println("#			in eight bytes as \"xxx yyy:\", where xxx and yyy are");
    System.out.println("#			the coordinate values as ASCII numbers.");

    float currentMinX = 0.0f;
    float currentMaxX = 0.0f;
    float currentMinY = 0.0f;
    float currentMaxY = 0.0f;
    float temp = 0.0f;
    for (String font : fontDefs) {
      String fontName = font.substring(0,font.indexOf(" = "));
      font = font.substring(font.indexOf(" = ") + 5, font.length() - 2);
      System.out.println("######################################################");
      System.out.println("# Font converted from EggBot font file ");
      if (fontName.startsWith("EMS")) {
	System.out.println("# SIL Open Font License http://scripts.sil.org/OFL applies.");
      } else {
        System.out.println("# NIST Hershey Font licence applies");
      }
      String [] tokens = font.split("\",\"");
      System.out.println("# Glyphs in "
                         + fontName +
                         " = " + tokens.length);
      // preliminary sizing iteration for font glyphs to establish scaling +/- offsets 
      float scaling = 150.0f; //a default value, shouldn't get used
      for (int i = 1; i < tokens.length; i++) { // now inside a font, skip initial size coords
        String coords [] = tokens[i].split(" ");
        for (int j = 0; j < coords.length; j++) { // now inside a glyph
          if (!coords[j].equals("L") && !coords[j].equals("M")) {
            temp = Float.parseFloat(coords[j]);
            if (currentMinX > temp) {
              currentMinX = temp;
            }
            if (currentMaxX < temp) {
              currentMaxX = temp;
            }
            temp = Float.parseFloat(coords[j+1]);
            if (currentMinY > temp) {
              currentMinY = temp;
            }
            if (currentMaxY < temp) {
              currentMaxY = temp;
            }
            j++;
          }
	}
      }
      scaling = 6000/(currentMaxY-currentMinY); // 600 decimils is about right for gEDA PCB
      System.out.println("#\tMinimum X coord: " + currentMinX
                         + "\n#\tMaximum X coord: " + currentMaxX
                         + "\n#\tMinimum Y coord: " + currentMinY
                         + "\n#\tMaximum Y coord: " + currentMaxY
                         + "\n#\tMaximal Y extent: " + (currentMaxY - currentMinY)
                         + "\n#\tMaximal X extent: " + (currentMaxX - currentMinX)
                         + "\n#\tY scaling to achieve 6000 centimils: " + scaling
                         + "\n######################################################");

      // with a value for scaling, we can now proceed in earnest

      for (int i = 0; i < tokens.length; i++) { // now inside a font, skip initial size coords for now
        String coords [] = tokens[i].split(" ");
	Float [] previous = new Float [2]; // for start coords for the line segments
        Float [] current = new Float [2];  // for end coords for the line segments
        current[0] = Float.parseFloat(coords[0]); // the x size of the glyph
	if (current[0] < 0)  {
          current[0] = -current[0]; // may need +/- something here i.e. half glyph width
	}
        current[1] = Float.parseFloat(coords[1]); // the y size of the glyph
        
        System.out.println("Symbol['" + (char)(i+32) + "' " // start with space char ' '
                           + (long)(current[0]*scaling)        // width based on x dimension +/- ?
                           + "]\n(");

        for (int j = 2; j < coords.length; j++) { // now inside a glyph, skip initial size coords
	  //System.out.println("Symbol('" + (char)(i+32) + "' 18)\n(");
          if (coords[j].equals("L") || coords[j].equals("M")) {
            current[0] = Float.parseFloat(coords[j+1]);
	    current[1] = Float.parseFloat(coords[j+2]);
          }
	  if (coords[j].equals("L")) {
            System.out.println("\tSymbolLine[" +
                               (long)(previous[0]*scaling) + " " +
                               (long)(previous[1]*scaling) + " " +
                               (long)(current[0]*scaling) + " " +
                               (long)(current[1]*scaling) + " 700]");
          }
	  if (coords[j].equals("L") || coords[j].equals("M")) {
            previous[0] = current[0];
            previous[1] = current[1];
            j += 2;
          }
        }
        System.out.println(")");
      }
      //convertedFiles.add(fontName);
    }    

    return convertedFiles.toArray(new String[convertedFiles.size()]); 
  }

}
