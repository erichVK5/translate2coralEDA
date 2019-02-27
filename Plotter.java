// v1.0
// Copyright 2000 Philipp Knirsch
// Written by Philipp Knirsch
//
// v1.1
// modifications by Erich Heinzle 2015, to
//    support PCB design
//    fixed metric/inches flag detection when parsing
//    added a long nanometre Polygon class to improve pad detection 
//
// v1.2
// modifications by Erich Heinzle 2016, to
//    support headless operation
//
// Latest modifications to allow use in translate2geda.
// Hacky but it works.
// TODO
// - Graphic export is AWT 1.1 vintage and a bit broken currently.
// - Eliminate use of doubles and use long nanometres for all dimensions
//
//
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.

// This program is distributed in the hope that it will be useful, but
// WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
// General Public License for more details.

// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA. 
//
//
// RS-274X virtual plotter
//
// This class now is a virtual plotter. It can take an input in RS-274X
// format and can either render it into a Graphics context previously
// set using the setGraphics() method and scaling the image by the
// factors for the X and Y axis.
//



import java.awt.Graphics;
import java.awt.Polygon;
import java.awt.Rectangle; // use for bounds of polygons
import java.awt.Color;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Vector;
import java.util.StringTokenizer;

// EH addition
// something for writing out PCB primitives
import java.io.PrintWriter;
import java.io.*;
// and some stuff for png graphics
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

//

//
// This is our virtual plotter. It is mainly a state machine simulating a
// real plotter with some additional interface methods. Some of them are
// again corresponding to the real world plotter, like the plotInput()
// method, others are more for interaction with the main program, like
// setGraphics() or setScale().
//
class Plotter {
  static final boolean DEBUG = false; //true; //false;

  Graphics   g;	// Graphics context into which the plotter renders        

  BufferedImage bi; // try this here
  Graphics2D bg;

  int        xsize;	// X size of Graphics in pixels
  int        ysize;	// Y size of Graphics in pixels

  boolean    getDim;	// Flag if we are currently calculating the dimension
  boolean    firstCoordsRead = false;
  // if getDim is to work with images way off
  // centre then we need this - ESH
  double     xmin;	// Minimum value for X-axis
  double     ymin;	// Minimum value for Y-axis
  double     xmax;	// Maximum value for X-axis
  double     ymax;	// Maximum value for Y-axis

  double     sx;	// Scaling parameter of X-axis
  double     sy;	// Scaling parameter of Y-axis


  // added by eh for PCB / kicad layout output
  //  int     stupidPADSoffset = -2900; // for 10x 
  // PADS files have a strange Y offset
  // -1500 for 5x
  //
  // first, an ability to centre the displaced gerber data would be nice

  boolean    imageCentred;
  boolean    mirroredYaxis = false;
  boolean    headless = true;

  double     xOffset = 0.0; //-1.0; //0.0; // -1.0;
  double     yOffset = 0.0; //-28.0; //0.0; // -28.0;
  //  double     nxOffset = -1.0;   // not needed
  //double     nyOffset = -28.0;   // not needed
  double     tempx;
  double     tempy;
  double     tempnx;
  double     tempny;

  // now, a flag for generating PCB layout drawing primitive
  String     PCBelements = "";
  boolean    generatePCBelements = true;
  String     outputFileName = "PCBlayoutData.pcb";
  long       rectangularPolygonPadCount = 0;


  // EH addition
  //        if (generatePCBelements) {
  File outputFile = new File(outputFileName);

  // here endeth EH additions
  // we will make use of int  pos below as a pad/track label

  // added for centring and PCN element export

  Vector<String>     cmdVector;// Vector containing all single commands and tokens
  int        pos;	// Global position in cmdVector for parser
  boolean    ext;	// Flag wether we are inside an extended command

  // State variables. We have quite a few of them. First the current
  // position. Then the selected apterture, followed by the drawing mode.
  // We have also various interpolations states including a scaling factor
  // for linear interpolations and several flags for area filling,
  // multiquadrant interpolation and absolut or incremental positioning.
  // At last we also have a vector with the points of the area fill...
  double     x;	// Current X position
  double     y;	// Current Y position
  double     i;	// X center of arc
  double     j;	// Y center of arc
  int        d;	// Current draft

  // Aperture types
  static final int    CIRCLE    = 1;
  static final int    RECTANGLE = 2;
  static final int    OVAL      = 3;
  static final int    POLYGON   = 4;
  double[][] ad;	// Aperture definitions array


  // Drawing modes
  static final int    ON    = 1;
  static final int    OFF   = 2;
  static final int    FLASH = 3;

  int        draw;	// Current drawing mode
  int        drawColor;// Current drawing color (black==0 or white==1)


  // Interpolation modes
  static final int    LINEAR = 0;
  static final int    CLOCK  = 1;
  static final int    CCLOCK = 2;

  int        interpol;// Current interpolation
  double     scale;	// Scale for linear interpolation (10, 1, .1, .01)
  boolean    metric;	// Flag if the data is in inches or millimeters.

  boolean    areafill;// Flag for area fill
  Polygon    points;	// Polygon of the area to be filled

  LongPolygon  longPoints; // Polygon to hold nanometre dimensions

  boolean    multi;	// Flag for single or multiquadrant interpolation
  boolean    absolute;// Flag for absolute or incremental positioning

  boolean    stop;	// Stop program flag



  // Variables of the state machine for the extended commands
  boolean  omitLZeros;// Omit leading or trailing zeros.
  static final String[] trailZeros = { "", "0", "00", "000", "0000", "00000", "000000", "0000000", "00000000", "000000000", "0000000000", "00000000000", "000000000000"};

  int        xlen;	// Complete number of digits a X-axis number can have
  int        xdigits;	// Number of digits of X-axis after decimal.
  int        ylen;	// Complete number of digits a Y-axis number can have
  int        ydigits;	// Number of digits of Y-axis after decimal.

  // Helper array for parseDouble() method
  static final double[] div = {1.0, 10.0, 100.0, 1000.0, 10000.0, 100000.0, 1000000.0};

  private class LongPolygon {
    int initialVertices = 20;
    int vertexCount = 0;
    int pointCount = 0;

    long[] xcoords = new long[initialVertices];
    long[] ycoords = new long[initialVertices];
    long yPosition = 0;
    long xPosition = 0;

    public LongPolygon() {
      pointCount = 0;
      vertexCount = 0;
      xPosition = 0;
      yPosition = 0;
      xcoords[pointCount] = 0;
      ycoords[pointCount] = 0;      
    }

    public LongPolygon(long x, long y) { // x,y are position of polygon
      pointCount = 0;
      vertexCount = 0;
      xcoords[pointCount] = x;
      ycoords[pointCount] = y; // to allow centroid to return a meaningful value for null polygon
      xPosition = x;
      yPosition = y;
    }
    
    public Polygon polygon() {
      Polygon traditionalPolygon = new Polygon();
      return traditionalPolygon;
    }

    public int npoints() {
      return pointCount;
    }

    public void addPoint(long x, long y) {
      xcoords[pointCount] = x;
      ycoords[pointCount] = y;
      if ((xcoords[0] == x) && (ycoords[0] == y)) {
        vertexCount = pointCount;
      }
      if (DEBUG) {
        System.out.println("Added longPoly points: x : " + x + ", y: " + y);
      }
      pointCount++;
      if (pointCount == xcoords.length) {
        long[] newXcoords = new long[xcoords.length * 2];
        long[] newYcoords = new long[xcoords.length * 2];
        for (int i = 0; i < pointCount; i++) {
          newXcoords[i] = xcoords[i];
          newYcoords[i] = ycoords[i];
        }
        xcoords = newXcoords;
        ycoords = newYcoords;
      }
    }

    public int vertexCount() {
      return vertexCount;
    }

    public long centroidX() {
      long Xcentroid = 0;
      long Xmax = xcoords[0];
      long Xmin = xcoords[0];
      for (int i = 1; i < pointCount; i ++) {
        if (Xmax < xcoords[i]) {
          Xmax = xcoords[i];
        }
        if (Xmin > xcoords[i]) {
          Xmin = xcoords[i];
        }
      }
      Xcentroid = (Xmax - Xmin)/2 + Xmin;
      return Xcentroid;
    }

    public long centroidY() {
      long Ycentroid = 0;
      long Ymax = ycoords[0];
      long Ymin = ycoords[0];
      for (int i = 1; i < pointCount; i ++) {
        if (Ymax < ycoords[i]) {
          Ymax = ycoords[i];
        }
        if (Ymin > ycoords[i]) {
          Ymin = ycoords[i];
        }
      }
      Ycentroid = (Ymax - Ymin)/2 + Ymin;
      return Ycentroid;      
    }

    public long xSize(){
      long xSize = 0;
      long Xmax = xcoords[0];
      long Xmin = xcoords[0];
      for (int i = 1; i < pointCount; i ++) {
        if (Xmax < xcoords[i]) {
          Xmax = xcoords[i];
        }
        if (Xmin > xcoords[i]) {
          Xmin = xcoords[i];
        }
      }
      xSize = (Xmax - Xmin);
      if (xSize < 0) {
        xSize = -xSize;
      }
      return xSize;
    }

    public long ySize() {
      long ySize = 0;
      long Ymax = ycoords[0];
      long Ymin = ycoords[0];
      for (int i = 1; i < pointCount; i ++) {
        if (Ymax < ycoords[i]) {
          Ymax = ycoords[i];
        }
        if (Ymin > ycoords[i]) {
          Ymin = ycoords[i];
        }
      }
      ySize = (Ymax - Ymin);
      if (ySize < 0) {
        ySize = -ySize;
      }
      return ySize;
    }

    public void clearPolygon() {
      pointCount = 0;
      vertexCount = 0;
      xcoords[pointCount] = 0;
      ycoords[pointCount] = 0;
    }

  }

  //
  // We don't have to do a lot in the public constructor, only initialize a
  // few variables.
  //
  public Plotter() {
    g         = null;		// No default Graphics context
    //        bg = null; //try here
    getDim    = false;		// We usually don't calc the dim
    sx        = 1.0;		// Default is 1:1 scaling on X-axis
    sy        = 1.0;		// Default is 1:1 scaling on Y-axis
    cmdVector = new Vector<String>();	// New command vector
    points    = new Polygon();	// Polygon for area fill

    longPoints = new LongPolygon(); // for storing nanometre dimensions and returning centroid

    imageCentred = false;
    mirroredYaxis = false;
  }

  public boolean centred() {
    return imageCentred;
  }

  public void setMirrorYaxis() {
    mirroredYaxis = !mirroredYaxis;
  }

  //
  // Sets the Graphics context into which the virtual plotter will do it's
  // rendering. This allows us to render into any any Java Graphics device,
  // from a on screen Canvas over an off screen image up to a printer.
  //
  public void setGraphics(Graphics ig) {
    g = ig;

  }



  //
  // Sets the size in pixles for our Graphics context. Unfortunately we
  // can't extract that from the Graphics object itself, so we have to set
  // it prior to drawing... One more thing missing in the Java 1.1 stuff...
  //
  public void setSize(int x, int y) {
    xsize = x;
    ysize = y;

    // try this
    if (!headless) {
      bi = new BufferedImage(x ,y, BufferedImage.TYPE_INT_RGB);
      bg = bi.createGraphics();
      bg.setColor(Color.WHITE);
      bg.fillRect(0, 0, x, y);
      bg.setColor(Color.BLACK);
    }
  }



  //
  // Sets the scaling factor on x and y axis. We might want this to either
  // zoom the image or similar stuff.
  //
  public void setScale(double x, double y) {
    sx = x;
    sy = y;
  }



  //
  // Tries to parse and plot the given input. A Graphic context into which
  // the plot will be drawn has to be set prior to a call to this method.
  // THe Graphic context itself may be any Java Graphic, thereby enabling
  // either rendering on screen, in an off-screen image or even on a
  // printer.
  //
  // 4/25/00: Small new feature: We do the 'drawing' twice. In the first
  // run we keep a record of the dimensions of the drawing area, which we
  // the use in the second run to scale the plot to match the display
  // as good as possible.
  //
  public boolean plotInput(String in) throws IOException {
    if ((g == null) && !headless) {
      System.out.println("Error: No Graphics context for plotting set.");
      return false;
    }

    if ((in == null)  || in.equals("")) {
      System.out.println("Error: Input is null or empty.");
      return false;
    }

    //      PrintWriter PCBelementFile = new PrintWriter(outputFile);
    //        firstCoordsRead = false; // to allow centring to work
    //        xOffset = 0.0;
    //        yOffset = 0.0;
    //        getDim = true;

    getDim = false;  // new

    initStateMachine();
    parseInput(in, 0);

    //        processInput(999999999);

    // we now sort out the necessary offsets to centre at 0,0
    //        xOffset = ((xmin-xmax)/2.0);
    //        yOffset = ((ymin-ymax)/2.0);

    // getDim = false;
    // initStateMachine();
    // parseInput(in, 0);
    return processInput(999999999);
  }


  // a modified plotImage routine to centre and plot the image
  public boolean centreImage(String in) throws IOException {
    if ((g == null) && !headless) {
      System.out.println("Error: No Graphics context for plotting set.");
      return false;
    }

    if (in == null || in.equals("")) {
      System.out.println("Error: Input is null or empty.");
      return false;
    }

    //      PrintWriter PCBelementFile = new PrintWriter(outputFile);

    //        if (!imageCentred) {
    firstCoordsRead = false; // to allow centring to work
    xOffset = 0.0;
    yOffset = 0.0;
    getDim = true;
    initStateMachine();
    parseInput(in, 0);
    processInput(999999999);

    //PCBelementFile.println(PCBelements);
    //PCBelementFile.close();

    // we now sort out the necessary offsets to centre at 0,0

    xOffset = ((xmin-xmax)/2.0);
    yOffset = ((ymin-ymax)/2.0);
    imageCentred = true;
    // }
    getDim = false;
    initStateMachine();
    parseInput(in, 0);
    return processInput(999999999);
  }


  // 27/7/2015
  // Modified plotInput method tries to parse and convert the given
  // input into a gEDA PCB footprint file.
  // The method should ideally be passed the gerber filename
  //
  // 4/25/00: Small new feature: We do the 'drawing' twice. In the first
  // run we keep a record of the dimensions of the drawing area, which we
  // the use in the second run to scale the plot to match the display
  // as good as possible.
  //
  public boolean generatePCBFile(String in, String filename) throws IOException {

    if ((in == null) || in.equals("")) {
      System.out.println("Error: Input is null or empty.");
      return false;
    }

    firstCoordsRead = false; // to allow centring to work
    //        xOffset = 0.0;
    //yOffset = 0.0;
    PCBelements = "";
    // done to avoid doubling up data with successive
    // conversions of the same or different gerber files

    try {

      File footprintFile = new File(filename + ".fp");
      PrintWriter PCBelementFile = new PrintWriter(footprintFile);

      //          getDim = true; // actually, we don't care with footprints
      // if the gerber is not well centred
      getDim = false;
      initStateMachine();
      parseInput(in, 0);
      processInput(999999999);

      PCBelements = "Element[\"\" \"" 
          + "convertedGerber"
          + "\" \"\" \"\" 0 0 0 -3000 0 100 \"\"]\n(\n"
          + PCBelements
          + ")";
      PCBelementFile.println(PCBelements);
      PCBelementFile.close();

    }
    catch  (Exception e) {
      System.out.println("Error: Couldn't write footprint file.");
      return false;
    }        

    PCBelements = ""; // clear it out in case we convert more.

    //        getDim = false;
    // initStateMachine();
    //parseInput(in, 0);
    return true; // processInput(999999999);
  }


  //
  // Simple method to (re)initialize the state machine. We do this only
  // once at the beginning of the plotInput().
  //
  public void initStateMachine() {
    // Reset the state variables of the plotter
    x          = 0.0;
    y          = 0.0;
    d          = 10;
    ad         = new double[1000][6];

    ad[10]     = new double[]{1, 0.025, 0, 0, 0, 0};

    draw       = OFF;
    drawColor  = 0;
    interpol   = LINEAR;
    scale      = 1.0;
    metric     = true;
    areafill   = false;
    multi      = false;
    absolute   = true;
    stop       = false;

    omitLZeros = true;
    xlen       = 5;
    xdigits    = 3;
    ylen       = 5;
    ydigits    = 3;

    // New Polygon for area fill
    points    = new Polygon();

    // Clean up command vector
    cmdVector.removeAllElements();

    pos = 0;	// Reset initial parser position
    ext = false;	// We start of with normal commands
  }



  //
  // Here we split up the whole input into pieces to make processing it
  // much easier. Actually, we do something really nasty here. For the IF
  // extended command, we read in the file and insert the just read file
  // directly into out cmdVector. As we hopefully won't encounter this too
  // often this is a nice hack to do it. Other wise we would have to do
  // recursive calls which might drain our stack space.
  //
  public void parseInput(String in, int pos) {
    if (DEBUG)
      System.out.println("Debug: Start parsing input.");

    // Parse through the whole input and store every command and token
    // in the cmdVector for later processing
    StringTokenizer st = new StringTokenizer(in,"%*", true);

    // Store all commands and tokens in cmdVector
    while (st.hasMoreTokens()) {
      cmdVector.insertElementAt(st.nextToken(), pos++);
    }

    if (DEBUG)
      System.out.println("Debug: End parsing input.");
  }



  //
  // And finally we do the drawing. Here the real deal is being done, namely
  // the rendering of the image.
  //
  // 4/23/00: As of today we can instruct the process input to process a
  // certain number of commands instead of all of them.
  //
  public boolean processInput(int numCmd) throws IOException {
    String          cmd;		// Current command

    if (DEBUG) {
      System.out.println("Debug: Start processing input.");
    }

    // EH addition
    if (DEBUG && generatePCBelements) {
      System.out.println("Debug: Using " + outputFileName
                         + " for layout primitives");
    }
    // EH addition end


    // As long as there are more commands avaliable continue
    // If a command is found and executed, the position will be incremented
    // in those methods, so we don't need to do it here.
    while (pos < cmdVector.size()) {
      // Get the current command from the cmdVector
      cmd = (String) cmdVector.elementAt(pos);

      if (cmd == null) {
        cmd = "";
      }

      if (DEBUG)
        System.out.println("Debug: Processing command "+cmd);

      // If we entered or exited an eXtended command, switch the flag
      if (cmd.equals("%")) {
        ext = !ext;

        if (ext) {
          if (DEBUG)
            System.out.println("Debug: Start of extended command");
        }
        else {
          if (DEBUG)
            System.out.println("Debug: End of extended command");
        }

        pos++;
        //continue;
      }

      // Always skip the dang "*" tokens as they don't interest
      if (cmd.equals("*")) {
        pos++;
        //continue;
      }

      // Handle eXtended RS-274X command
      if (ext) {
        if (handleExtendedCmd() == false) {
          System.out.println("Error: Failed executing command.");
          if (DEBUG)
            System.out.println("Debug: End processing input.");
          return false;
        }
      }
      // Handle RS-274D command
      else {
        if (handleNormalCmd() == false) {
          System.out.println("Error: Failed executing command.");
          if (DEBUG)
            System.out.println("Debug: End processing input.");
          return false;
        }
      }

      // Ha! We encountered a stop command, so lets do it!
      if (stop) {
        if (DEBUG)
          System.out.println("Debug: End processing input.");
        return true;
      }

      numCmd--;

      if (numCmd == 0)
        return true;
    }

    // We reached the input without a M00 or M02... Ah well, maybe they
    // simply forgot to put it in... Not really critical, is it?! ;)

    // EH addition - moved to calling routine
    // have we been writing PCB drawing primitives
    // to a file, if so, close it 
    //                if (generatePCBelements) {
    //                    File outputFile = new outputfile(outputFileName);
    //        PCBelementFile.println(PCBelements);
    // PCBelementFile.close();
    //}
    //

    if (DEBUG)
      System.out.println("Debug: End processing input.");
    return true;
  }



  //
  // Handles an eXtended RS-274X command. They always start with a 2
  // character specifier, followed by various parameters, sometimes even
  // more commands.
  //
  private boolean handleExtendedCmd() {
    String          cmd;

    // As long as there are more command available
    while (pos < cmdVector.size()) {
      // Get the current command from the cmdVector
      cmd = (String) cmdVector.elementAt(pos);

      // see if this helps gcj
      if (cmd == null) {
        cmd = "";
      } else {
        cmd = cmd.trim();
      } // AND IT DOES!!!!!!! gcj does not trim CR properly

      // We came to the end of that eXtended command without problems,
      // so return true.
      if (cmd.equals("%")) {
        return true;
      }

      if (DEBUG)
        System.out.println("Debug: Executing extended command "+cmd);

      // Handle a single command. Might consist of several 'subcommands'
      // like G01X100Y200D02. We simply collect them one after the other
      // and execute the effect after we have processed all subcommands.
      while (cmd.length() > 0) {

        // EH addition
        // metric flag on by default, look for inches
        if (cmd.startsWith("MOIN")) {
          metric = false;
          cmd = "";
          if (DEBUG) {
            System.out.println("Debug: Inch units detected");

          }
        }

        // Aperture definition. This is a pretty complex thing, i hope
        // i'm doing it right. :)
        if (cmd.startsWith("ADD")) {
          int nl = numberLength(cmd, 3);
          int pd = parseInteger(cmd.substring(3, nl));
          cmd    = cmd.substring(nl);
          if (cmd == null) {
            cmd = "";
          }


          // New circle aperture
          if (cmd.startsWith("C,")) {
            ad[pd][0] = CIRCLE;
            cmd = cmd.substring(2);
          }
          if (cmd == null) {
            cmd = "";
          }

          // New circle aperture
          if (cmd.startsWith("R,")) {
            ad[pd][0] = RECTANGLE;
            cmd = cmd.substring(2);
          }
          if (cmd == null) {
            cmd = "";
          }

          // New circle aperture
          if (cmd.startsWith("O,")) {
            ad[pd][0] = OVAL;
            cmd = cmd.substring(2);
          }
          if (cmd == null) {
            cmd = "";
          }

          // New circle aperture
          if (cmd.startsWith("P,")) {
            ad[pd][0] = POLYGON;
            cmd = cmd.substring(2);
          }
          if (cmd == null) {
            cmd = "";
          }

          nl = 1;
          StringTokenizer st = new StringTokenizer(cmd, "X");
          while (st.hasMoreTokens()) {
            ad[pd][nl++] = parseDouble(st.nextToken());
          }
        }

        // Axis selection. We simply skip it, as portrait or land-
        // scape really doesn't matter for pictures here.
        if (cmd.startsWith("ASAXBY") || cmd.startsWith("ASAYBX")) {
          cmd = cmd.substring(6);
          if (cmd == null) {
            cmd = "";
          }

        }

        // Parse the format statement, one of the most important
        // extended commands.
        if (cmd.startsWith("FS")) {
          cmd = cmd.substring(2);
          if (cmd == null) {
            cmd = "";
          }


          // Decided if we omit leading or trailing zeros
          if (cmd.startsWith("L")) {
            omitLZeros = true;
          }
          else {
            omitLZeros = false;
          }
          cmd = cmd.substring(1);
          if (cmd == null) {
            cmd = "";
          }


          // Are we using absolute or relative coordinates
          if (cmd.startsWith("A")) {
            absolute = true;
          }
          else {
            absolute = false;
          }
          cmd = cmd.substring(1);
          if (cmd == null) {
            cmd = "";
          }


          // Our parser is clever enough to parse any number after N
          if (cmd.startsWith("N")) {
            cmd = cmd.substring(2);
          }
          if (cmd == null) {
            cmd = "";
          }


          // Our parser is clever enough to parse any number after G
          if (cmd.startsWith("G")) {
            cmd = cmd.substring(2);
            if (cmd == null) {
              cmd = "";
            }
          }

          if (cmd.startsWith("X")) {
            xdigits = parseInteger(cmd.substring(2,3));
            xlen    = xdigits + parseInteger(cmd.substring(1,2));
            cmd = cmd.substring(3);
            if (cmd == null) {
              cmd = "";
            }

          }

          if (cmd.startsWith("Y")) {
            ydigits = parseInteger(cmd.substring(2,3));
            ylen    = ydigits + parseInteger(cmd.substring(1,2));
            cmd = cmd.substring(3);
            if (cmd == null) {
              cmd = "";
            }

          }

          // Our parser is clever enough to parse any number after D
          if (cmd.startsWith("D")) {
            cmd = cmd.substring(2);
          }
          if (cmd == null) {
            cmd = "";
          }


          // Our parser is clever enough to parse any number after M
          if (cmd.startsWith("M")) {
            cmd = cmd.substring(2);
            if (cmd == null) {
              cmd = "";
            }

          }
        }

        // Include file. Really nasty trick how we do it, but it works
        // just fine :)
        if (cmd.startsWith("IF")) {
          includeFile(cmd.substring(2));
          cmd = "";
          if (cmd == null) {
            cmd = "";
          }

        }

        // Set layer to clear
        if (cmd.startsWith("LPC")) {
          drawColor = 1;
          if (!headless) {
            g.setColor(Color.white);
            bg.setColor(Color.white); // try
          }
          cmd = cmd.substring(3);
          if (cmd == null) {
            cmd = "";
          }

        }

        // Set layer to dark
        if (cmd.startsWith("LPD")) {
          drawColor = 0;
          if (!headless) {
            g.setColor(Color.black);
            bg.setColor(Color.black); // try
          }
          cmd = cmd.substring(3);
          if (cmd == null) {
            cmd = "";
          }

        }

        // Darn, didn't know what to do with that command, so lets
        // just forget about it at the moment... :)
        cmd = "";
      }
      // gcj:
      if (cmd == null) {
        cmd = "";
      }

      // Advance to next command
      pos++;
    }

    return true;
  }



  //
  // Handles a old RS command. They always start with only 1 character and
  // are much less complex and nested as the eXtended commands.
  //
  private boolean handleNormalCmd() throws IOException {
    String  cmd;
    boolean nareafill = areafill;
    boolean doDraw    = false;	// Flag if we encounter coordinate data
    double  nx;
    double  ny;

    // If we are in absolute mode, initialize the new x and y values to
    // the previous values. Otherwise set them to 0.0 (no rel. movement).
    if (absolute) {
      nx = x;
      ny = y;
    }
    else {
      nx = 0.0;
      ny = 0.0;
    }

    // As long as there are more command available
    while (pos < cmdVector.size()) {
      // Get the current command from the cmdVector
      cmd = (String) cmdVector.elementAt(pos);

      // see if this helps with gcj:
      if (cmd == null) {
        cmd = "";
      } else {
        cmd = cmd.trim();
      }  // AND IT DOES!!!!!!! gcj does not trim CR properly

      // We came to the end of that command without problems,
      // so return true.
      if (cmd.equals("*")) {
        return true;
      }

      // This is not quite correct, but in the documentation there is
      // a bad example where the first line starts with a *G04 but
      // doesn't end with a *, which violates their own spec 4 pages
      // ago... Grrrrr... I hate that...
      if (cmd.equals("%")) {
        return true;
      }

      if (DEBUG)
        System.out.println("Debug: Executing normal command "+ cmd.trim());

      // Handle a single command. Might consist of several 'subcommands'
      // like G01X100Y200D02. We simply collect them one after the other
      // and execute the effect after we have processed all subcommands.
      while (cmd.length() > 0) {

        // Line numbers rock, but we ignorantly ignore them... :)
        if (cmd.startsWith("N")) {
          int p  = numberLength(cmd, 1);
          cmd    = cmd.substring(p);
          if (cmd == null) {
            cmd = "";
          }
        }

        // Switch to linear interpolation with scale 1.0
        if (cmd.startsWith("G01")) {
          interpol = LINEAR;
          scale = 1.0;
          cmd = cmd.substring(3);
          if (cmd == null) {
            cmd = "";
          }
        }

        // Switch to clockwise interpolation
        if (cmd.startsWith("G02")) {
          interpol = CLOCK;
          cmd = cmd.substring(3);
          if (cmd == null) {
            cmd = "";
          }

          //                    continue;
        }

        // Switch to counter clockwise interpolation
        if (cmd.startsWith("G03")) {
          interpol = CCLOCK;
          cmd = cmd.substring(3);
          if (cmd == null) {
            cmd = "";
          }
          //continue;
        }

        // Simply a comment :)
        if (cmd.startsWith("G04")) {
          cmd = "";
          //continue;
        }

        // Switch to linear interpolation with scale 10
        if (cmd.startsWith("G10")) {
          interpol = LINEAR;
          scale = 10.0;
          cmd = cmd.substring(3);
        }
        if (cmd == null) {
          cmd = "";
        }

        // Switch to linear interpolation with scale 0.1
        if (cmd.startsWith("G11")) {
          interpol = LINEAR;
          scale = 0.1;
          cmd = cmd.substring(3);
        }
        if (cmd == null) {
          cmd = "";
        }

        // Switch to linear interpolation with scale 0.01
        if (cmd.startsWith("G12")) {
          interpol = LINEAR;
          scale = 0.01;
          cmd = cmd.substring(3);
        }
        if (cmd == null) {
          cmd = "";
        }

        // Lets do some filled polygons
        if (cmd.startsWith("G36")) {
          nareafill = true;
          points = new Polygon();	// Initialize our Polygon
          if (DEBUG) {
            System.out.println("G36 cmd: " + cmd
                               + ", cmd.substring(3): "
                               + cmd.substring(3));
          }
          cmd = cmd.substring(3);
          if (cmd == null) {
            cmd = "";
          }
        }

        // End area fill
        if (cmd.startsWith("G37")) {
          nareafill = false;
          if (DEBUG) {
            System.out.println("G37 cmd: " + cmd
                               + ", cmd.substring(3): "
                               + cmd.substring(3));
          }
          cmd = cmd.substring(3);
          if (cmd == null) {
            cmd = "";
          }
        }

        // Select an aperture, skip it as we don't care :)
        if (cmd.startsWith("G54")) {
          cmd = cmd.substring(3);
        }
        if (cmd == null) {
          cmd = "";
        }


        // Select an aperture, skip it as we don't care :)
        if (cmd.startsWith("G55")) {
          cmd = cmd.substring(3);
        }

        if (cmd == null) {
          cmd = "";
        }

        // All data is now given in inches
        if (cmd.startsWith("G70")) {
          metric = false;
          cmd = cmd.substring(3);
        }
        if (cmd == null) {
          cmd = "";
        }

        // All data is now interpreted in millimeters (metric)
        if (cmd.startsWith("G71")) {
          metric = true;
          cmd = cmd.substring(3);
        }
        if (cmd == null) {
          cmd = "";
        }

        // Switch to single quadrant (no circular interpolation)
        if (cmd.startsWith("G74")) {
          multi = false;
          cmd = cmd.substring(3);
        }
        if (cmd == null) {
          cmd = "";
        }

        // Switch to multi quadrant (circular interpolation)
        if (cmd.startsWith("G75")) {
          multi = true;
          cmd = cmd.substring(3);
        }
        if (cmd == null) {
          cmd = "";
        }

        // Switch to absolute coordinate system
        if (cmd.startsWith("G90")) {
          // If we were in incremental coordinate system before we
          // have to reset the movement variables. Tough luck if you
          // do the change between an X and a Y command (should never
          // happen anyway).
          if (!absolute) {
            nx = x;
            ny = y;
          }
          absolute = true;
          cmd = cmd.substring(3);
          if (cmd == null) {
            cmd = "";
          }
        }

        // Switch to incremental coordinate system
        if (cmd.startsWith("G91")) {
          // This time it's the other way round, but same applies as
          // before.
          if (absolute) {
            nx = 0.0;
            ny = 0.0;
          }
          absolute = false;
          cmd = cmd.substring(3);
        }
        if (cmd == null) {
          cmd = "";
        }

        // Wrooommm... Change X position.
        if (cmd.startsWith("X")) {
          int p  = numberLength(cmd, 1);
          nx     = normalizeX(cmd.substring(1, p));
          cmd    = cmd.substring(p);
          if (cmd == null) {
            cmd = "";
          }
          doDraw = true;
        }

        // Wrooommm... Change Y position.
        if (cmd.startsWith("Y")) {
          int p  = numberLength(cmd, 1);
          ny     = normalizeY(cmd.substring(1, p));
          cmd    = cmd.substring(p);
          if (cmd == null) {
            cmd = "";
          }
          doDraw = true;
        }

        // Find the relative X center of the circle
        if (cmd.startsWith("I")) {
          int p  = numberLength(cmd, 1);
          i      = normalizeX(cmd.substring(1, p));
          cmd    = cmd.substring(p);
          if (cmd == null) {
            cmd = "";
          }
          doDraw = true;
        }

        // Find the relative Y center of the circle
        if (cmd.startsWith("J")) {
          int p  = numberLength(cmd, 1);
          j      = normalizeY(cmd.substring(1, p));
          cmd    = cmd.substring(p);
          if (cmd == null) {
            cmd = "";
          }
          doDraw = true;
        }

        // Dang, sometimes something like D1 is used... Ah well, we
        // need to analyze anything after the D anyway, as we have to
        // store the found aperture.
        // Turn on the light, baby! (Translated: Draw)
        if (cmd.startsWith("D")) {
          int p  = numberLength(cmd, 1);
          int nd = parseInteger(cmd.substring(1, p));
          cmd    = cmd.substring(p);
          if (cmd == null) {
            cmd = "";
          }

          // Switch to new aperture?
          if (nd >= 10)
            d = nd;		// Set new aperture
          else if(nd == OFF)
            draw = OFF;	// Turn off light
          else if(nd == ON)
            draw = ON;	// Turn on light
          else if(nd == FLASH)
            draw = FLASH;	// Flash Gordon!!! :)
        }

        // Stop! Erhh.. Well, sort of :)
        if (cmd.startsWith("M00")) {
          stop = true;
          cmd = cmd.substring(3);
          if (cmd == null) {
            cmd = "";
          }
        }

        // Stop! Erhh.. Well, sort of :)
        if (cmd.startsWith("M02")) {
          stop = true;
          cmd = cmd.substring(3);
          if (cmd == null) {
            cmd = "";
          }
        }

        // Darn, didn't know what to do with that command, so lets
        // just forget about it at the moment... :)


        cmd = "";
      }

      // for pete's sake, gcj
      if (cmd == null) {
        cmd = "";
      }

      // If we are in incremental mode, add the old values of x and y
      // to the new ones.
      if (!absolute) {
        nx += x;
        ny += y;
      }

      // Now the current command should have been processed. Let's see
      // what we make of it...

      // EH additions

      // first, we sort out the overall dimensions if (getDim)

      if (getDim) {
        if (!firstCoordsRead) {
          xmin = x;
          xmax = x;
          ymin = y;
          ymax = y;
          firstCoordsRead = true;
        }
        if (nx > xmax) {
          xmax = nx;
        }
        if (nx < xmin) {
          xmin = nx;
        }
        if (ny > ymax) {
          ymax = ny;
        }
        if (ny < ymin) {
          ymin = ny;
        }
      }

      // this where we add some offsets to the
      // real world dimensions x, y, nx, ny
      // to account for PADS weirdness
      tempx = x;
      tempy = y;
      tempnx = nx;
      tempny = ny;

      x += xOffset;
      y += yOffset;
      nx += xOffset;
      ny += yOffset;
                    
      // here endeth EH addition

      // Have we been filling polygons?!
      if (areafill) {
        // Do we have to draw a partially filled arc?! Then call our
        // superb drawArc method, as the calculation is really messy
        // and i don't want to have it here in the main parser.
        if (interpol != LINEAR) {
          if (DEBUG)
            System.out.println("Debug: Filling arc ("+x+","+y+")-("+nx+","+ny+"), ("+i+","+j+")");
          if (!getDim) {
            drawArc(nx, ny, true);	// Rest can be calculated
          }
        }

        if (nareafill) {
          if (DEBUG)
            System.out.println("Debug: Adding area point("+nx+","+ny+")");
          points.addPoint(aX(nx), aY(ny));

          longPoints.addPoint(convertToNanometres(nx, metric), convertToNanometres(ny, metric));
          // for use when exporting to gEDA

        }

        if (draw == OFF || !nareafill) {
          if (DEBUG)
            System.out.println("Debug: Filling area");
          if (!getDim && !headless) {
            g.fillPolygon(points);
            bg.fillPolygon(points); // try this
          }
                    
          if (generatePCBelements && (points.npoints == 5)) {
            // int [] is problem in a Polygon
            // may need a new class with additional
            // float points or longs for real coords
            // and method to provide polygon to to AWT
                      
            System.out.println("Polygonal RECT pads: " +
                               rectangularPolygonPadCount);

            if (!mirroredYaxis) {
              PCBelements = PCBelements + generatePadNm(longPoints.centroidX(),longPoints.centroidY(),metric,RECTANGLE,longPoints.xSize(),longPoints.ySize()); // need to sort out bounds
            } else if (mirroredYaxis) {
              PCBelements = PCBelements + generatePadNm(-longPoints.centroidX(),longPoints.centroidY(),metric,RECTANGLE,longPoints.xSize(),longPoints.ySize()); 
              // need to sort out bounds
              // generatePad(-nx,ny,metric,RECTANGLE,genericPadX,genericPadY); // need to sort out bounds
            }
          }

          points = new Polygon();
          if (DEBUG)
            System.out.println("Debug: First area point("+nx+","+ny+")");
          points.addPoint(aX(nx), aY(ny));

          // we add this to enable polygons to be exported
          // using nanometre dimensions
          longPoints = new LongPolygon();
          longPoints.addPoint(convertToNanometres(nx, metric), convertToNanometres(ny, metric));
          // for use when exporting from/to gEDA

        }
      }
      else {
        if (draw == ON && doDraw) {
          // Do we have to draw a partial arc?! Use our messy
          // drawArc method.
          if (interpol != LINEAR) {
            if (DEBUG)
              System.out.println("Debug: Drawing arc ("+x+","+y+")-("+nx+","+ny+"), ("+i+","+j+")");
            if (!getDim) {
              drawArc(nx, ny, false);	// Rest can be calculated
            }
          }
          else {
            if (DEBUG)
              System.out.println("Debug: Drawing line ("+x+","+y+")-("+nx+","+ny+")");
            if (!getDim) {
              drawLineAperture(d, x, y, nx, ny);
            }
          }
        }
        else if (draw == FLASH && doDraw) {
          if (DEBUG)
            System.out.println("Debug: Flashing aperture ("+x+","+y+")");
          if (!getDim) {
            flashAperture(d, nx, ny);
          }
        }
      }

      // Reset the doDraw flag
      doDraw = false;

      // The non-linear interpolations are not modal, so we switch
      // back to linear after ever drawing command.
      interpol = LINEAR;


      // ESH additions
      // we undo the temporary x and y offsets
      // applied to the coords
      x = tempx;
      y = tempy;
      nx = tempnx;
      ny = tempny;
      // endeth

      // Update our state machine with the nex position and flags
      x = nx;
      y = ny;
      areafill = nareafill;

      // Advance to next command
      pos++;
    }

    if (!headless) {
      File biFile = new File("bi.png"); // try this
      ImageIO.write(bi, "PNG", biFile);
      // biFile.close();
    }
    return true;
  }



  //
  // Search for the end of a number and returns the length up to the last
  // character. The format of a number looks like this:
  //
  //   [+|-]xxx[.yyy]
  // 
  // So we are not really doing all the correct checks, but for now it should
  // work well enough. Same principle as stated somewhere else in this
  // document: Garbage in -> Garbage out.
  //
  private int numberLength(String cmd, int pstart) {
    int    p;

    for (p=pstart; p<cmd.length(); p++) {
      if ((cmd.charAt(p) < '0' || cmd.charAt(p) > '9') && 
          cmd.charAt(p) != '-' && cmd.charAt(p) != '+' && cmd.charAt(p) != '.')
        break;
    }

    return p;
  }



  //
  // As we need to 'normalize' the X and Y axis values seperately we just use
  // a method for each of them. Takes a String as input and returns the
  // 'real' values in inches for the X axis.
  //
  private double normalizeX(String num) {
    // If the data might omit trailing zeros we simply add the as
    // necessary. Not the fastes way, but foolprove :).
    if (omitLZeros == false) {
      num = num + trailZeros[xlen-num.length()];
    }

    return parseDouble(num)/div[xdigits];
  }




  //
  // Just the same, now only for Y axis values.
  //
  private double normalizeY(String num) {
    // If the data might omit trailing zeros we simply add the as
    // necessary. Not the fastes way, but foolprove :).
    if (omitLZeros == false) {
      num = num + trailZeros[ylen-num.length()];
    }

    return parseDouble(num)/div[ydigits];
  }



  //
  // To get around of the problem that the Double(String) constructor may
  // throw a NumberException we simply sort of ignore it :). Code would
  // get pretty bloated in the parser otherwise. 
  //
  private double parseDouble(String num) {
    Double d = new Double(0.0);

    try {
      d = new Double(num);
    }
    catch (Exception e) {};		// Cool exception handler, eh? ;)

    return d.doubleValue();
  }



  //
  // Same thing for Integer :)
  //
  private int parseInteger(String num) {
    Integer i = new Integer(0);

    try {
      i = new Integer(num);
    }
    catch (Exception e) {};		// Cool exception handler, eh? ;)

    return i.intValue();
  }



  //
  // The extended command IF allows a file to be included. To do this we
  // internally read the file and call out plotInput method again with the
  // file we just read. Clever, eh?! :)
  //
  private void includeFile(String file) {
    String nin;

    nin = "";	// Init new input

    try {
      BufferedReader br;
      String         line;

      if (DEBUG)
        System.out.println("Debug: Opening input file "+file);

      br = new BufferedReader(new FileReader(file));

      if (DEBUG)
        System.out.println("Debug: Reading input file "+file);

      line = br.readLine();
      while (line != null) {
        nin += line;
        line = br.readLine();
      }

      if (DEBUG)
        System.out.println("Debug: Closing input file "+file);

      br.close();
    }
    catch (Exception e) {
      System.out.println("Error: Couldn't read input file "+file);
      nin = "";
    }

    if (DEBUG)
      System.out.println("Debug: Input file read.");

    parseInput(nin, pos+3);
  }



  //
  // As we need it so often we provide methods to convert the plot coordinate
  // to the current graphics coordinate. This way we can change how we
  // compute that anytime and really have work with a 'virtual' coordinate
  // system, sort of like OpenGL (by no means as sophisticated, although i
  // tend to belive that support for the AM extended commands will likely
  // lead to some sort of display list, similar to OpenGL, where one node
  // can be a subnode of many other nodes. But thats for the future...
  // This one computes the 'real' value for a X-axis coordinate.
  //
  private int aX(double x) {
    return xsize/2 + dX(x);
  }



  //
  // Same for the Y-axis.
  //
  private int aY(double y) {
    return ysize/2 - dY(y);
  }



  //
  // To avoid ugly glitches during the drawing process we also provide two
  // methods to calculate values for distances or relative coordinates in
  // the real world. That way we even seperate the relative from the absolute
  // coordinate calculation, which is another advantage.
  // First again for the X-axis.
  //
  private int dX(double x) {
    return (int)(x*sx*xsize/64.0+0.5);
  }



  //
  // Same for the Y-axis.
  //
  private int dY(double y) {
    return (int)(y*sy*xsize/64.0+0.5);
  }



  //
  // ARGGHHH! That one really hurt! The problem is converting the coordinates
  // given for the plotter, which in real life really makes sense for it, to
  // a raster oriented 2D system where you have to specify the enclosing
  // rectangle of the arc as well as the start and end angle is a real PITA,
  // or pain in the ass.
  //
  private void drawArc(double nx, double ny, boolean filled) {
    double radius;
    double cx;
    double cy;
    int    upper;
    int    left;
    int    width;
    int    height;
    double start;
    double arc;

    // Calculate the radius first
    radius = Math.sqrt(Math.pow(i, 2) + Math.pow(j, 2));

    // If we are lucky they use the multiquadrant method. The center of the
    // circle is already easily well defined by the given parameters
    if (multi) {
      cx = x+i;
      cy = y+j;
    }
    else {	// This is the messy part, determining the center of the arc.
      if (interpol == CCLOCK) {
        if (x < nx)
          cy = y+j;	// Lower half
        else
          cy = y-j;	// Upper half 

        if (y < ny)
          cx = x-i;	// Right half
        else
          cx = x+i;	// Left half 
      }
      else {
        if (x < nx)
          cy = y-j;	// Upper half
        else
          cy = y+j;	// Lower half 

        if (y < ny)
          cx = x+i;	// Left half
        else
          cx = x-i;	// Right half 
      }
    }

    // Now calculate all the coordinates needed by Java.
    left   = aX(cx-radius);
    upper  = aY(cy+radius);
    width  = dX(2*radius);
    height = dY(2*radius);

    // Calculate angle of starting point, lazy \/    trick here
    start  = 180*Math.atan((y-cy)/(x-cx+.000000001))/Math.PI;
    if (x-cx < 0.0)
      start = 180 + start;	// Left side we have to add 180 degree

    // First calculate angle of the end point
    arc  = 180*Math.atan((ny-cy)/(nx-cx+.000000001))/Math.PI;
    if (nx-cx < 0.0)
      arc = 180 + arc;		// Left side we have to add 180 degree

    // Now lets check if we go clock or counterclockwise
    if (interpol == CCLOCK)
      arc = arc-start;	// Counterclock, just go from start to end
    else
      arc = arc-start-360;// Hah, try to figure out this one :)

    // We have one special case for a full circle.
    if (arc == 0)
      arc = 360;

    // And in the end draw or fill that sucker!
    if (filled && !headless) {
      g.fillArc(left, upper, width, height, (int)start, (int)arc);
      bg.fillArc(left, upper, width, height, (int)start, (int)arc);
    }
    else {
      // Unfortunately we have to use a FLASH aperture for drawing normal
      // circles, so this gets again a little more complicated.

      // First we need to know the angle step size we have to use to
      // draw every single point. As there are 2*pi*r or pi*dim points
      // in a circle, the number of points is simply width*pi. From that
      // we can deduct the arc step width.
      double step = 360.0/Math.PI/width;
 
      for (;arc > 0; arc -=step) {
        flashAperture(d, cx+radius*Math.cos((start+arc)*Math.PI/180),
                      cy+radius*Math.sin((start+arc)*Math.PI/180));
      }
    }
  }



  //
  // As Java 1.1 unfortunately doesn't provide any means of performing the
  // drawing operations with a self defined stencil we have to simulate that.
  // This has the other advantage that the drawing code are now abstracted as
  // well and can therefore now easily be adapted and/or extended without
  // changes to the parsing and processing engine. This first method simply
  // flashes the given aperture at the given coordinate. If no aperture is
  // defined for that one, we use the standard, a solid circle with a
  // diameter of .025.
  //
  private void flashAperture(int d, double x, double y) {
    // If no aperture was defined fall back to default aperture
    if (ad[d][0] == 0)
      d = 10;

    if (ad[d][0] == CIRCLE) {
      if (!headless) {
        g.fillArc(aX(x-ad[d][1]/2),
                  aY(y+ad[d][1]/2),
                  dX(ad[d][1]),
                  dY(ad[d][1]),
                  0,
                  360);
        bg.fillArc(aX(x-ad[d][1]/2),
                   aY(y+ad[d][1]/2),
                   dX(ad[d][1]),
                   dY(ad[d][1]),
                   0,
                   360);
      }
      // pcb element output
      // we can use the existing metric flag
      if(generatePCBelements && !mirroredYaxis) {
        PCBelements = PCBelements + generatePad(x,y,metric,ad[d][0],ad[d][1],ad[d][1]);
      } else if(generatePCBelements && mirroredYaxis) {
        PCBelements = PCBelements + generatePad(-x,y,metric,ad[d][0],ad[d][1],ad[d][1]);
      }

      // end pcb element output

      return;
    }

    if (ad[d][0] == RECTANGLE) {
      if (!headless) {
        g.fillRect(aX(x-ad[d][1]/2),
                   aY(y+ad[d][2]/2),
                   dX(ad[d][1]) + 1,
                   dY(ad[d][2]) + 1);
        bg.fillRect(aX(x-ad[d][1]/2),
                    aY(y+ad[d][2]/2),
                    dX(ad[d][1]) + 1,
                    dY(ad[d][2]) + 1);
      }
      // pcb element output
      // we can use the existing metric flag
      if(generatePCBelements && !mirroredYaxis) {
        PCBelements = PCBelements + generatePad(x,y,metric,ad[d][0],ad[d][1],ad[d][2]);
      } else if(generatePCBelements && mirroredYaxis) {
        PCBelements = PCBelements + generatePad(-x,y,metric,ad[d][0],ad[d][1],ad[d][2]);
      }

      // end pcb element output

      return;
    }

    if (ad[d][0] == OVAL) {
      if (!headless) {
        g.fillArc(aX(x-ad[d][1]/2),
                  aY(y+ad[d][2]/2),
                  dX(ad[d][1]) + 1,
                  dY(ad[d][2]) + 1,
                  0,
                  360);
        bg.fillArc(aX(x-ad[d][1]/2),
                   aY(y+ad[d][2]/2),
                   dX(ad[d][1]) + 1,
                   dY(ad[d][2]) + 1,
                   0,
                   360);
      }

      // pcb element output
      // we can use the existing metric flag
      if(generatePCBelements && !mirroredYaxis) {
        PCBelements = PCBelements + generatePad(x,y,metric,ad[d][0],ad[d][1],ad[d][2]);
      } else if(generatePCBelements && mirroredYaxis) {
        PCBelements = PCBelements + generatePad(-x,y,metric,ad[d][0],ad[d][1],ad[d][2]);
        // end pcb element output
      }
      return;
    }

    if (ad[d][0] == POLYGON) {
      Polygon p;
      double radius;
      double arc;
      double cx;
      double cy;
      int s;
      int max;
      // format of gerber P directive is
      // P, outer D, # vertices, rotation +ve degrees CCW, hole D
      // Calculate the radius first
      radius = ad[d][1]/2.0;
      max    = (int) ad[d][2];
      arc    = ad[d][3];
      p      = new Polygon();

      for (s = 0; s<max; s++) {
        cx = x+radius*Math.cos((arc+360*s/max)*Math.PI/180);
        cy = y+radius*Math.sin((arc+360*s/max)*Math.PI/180);
        p.addPoint(aX(cx), aY(cy));
      }

      // here. we try to identify square pads or pins & fail 
      if (generatePCBelements && !mirroredYaxis) { // && (max <= 9) && (convertToNanometres(radius, metric) < 4000000)) {
        // i.e square pad < 8mm "round" at vertex
        PCBelements = PCBelements + generatePad(x,y,metric,RECTANGLE,(ad[d][1]/1.414214),(ad[d][1]/1.414214));
        System.out.println("Vertices: " + ad[d][2] +
                           "Radius: " + ad[d][1] +
                           "Rotation: " + ad[d][3]);
      } else if (generatePCBelements && mirroredYaxis) { 
        PCBelements = PCBelements + generatePad(-x,y,metric,RECTANGLE,(ad[d][1]/1.414214),(ad[d][1]/1.414214));
      }
      if (!headless) {
        g.fillPolygon(p);
        bg.fillPolygon(p);
      }
      return;
    }
  }



  //
  // Now we do the same thing for lines. To my understanding only lines and
  // flashes are affected by the aperture.
  // For lines i use a rather inefficient way of doing it, but for a reason:
  // I first calculate the maximum distance in points that we need to draw
  // and then iterate drawing single flashes on the way. For one i want to
  // draw as little apertures as possible but as much as needed, so i need
  // to calculate the number of points to be drawn anyway. To use floating
  // point arithmetic for the loop has another reason: The drawing procedure
  // should happen as acurately as possible, so using integers is not an
  // option. And last but not least on modern machines there is really not
  // that big a difference between using floating point or integer arithmetic
  // anymore. And belive me, its fast on a P75, so it's fast enough on your
  // PII, PIII or even Athlon or Alpha ;).
  //

  private void drawLineAperture(int d, double x, double y, double nx, double ny) {
    int dx, dy, max;
    double ix, iy;

    // pcb element output
    // we can use the existing metric flag
    if(generatePCBelements && !mirroredYaxis) {
      PCBelements = PCBelements +
          generateLine(x, y, metric, ad[d][1], ad[d][0], nx, ny);
    } else if(generatePCBelements && mirroredYaxis) {
      PCBelements = PCBelements +
          generateLine(-x, y, metric, ad[d][1], ad[d][0], -nx, ny);
    }

    // end pcb element output

    dx = dX(nx-x) + 1;
    dy = dY(ny-y) + 1;

    dx *= dx>0?1:-1;
    dy *= dy>0?1:-1;

    max = dx>dy?dx:dy;

    ix = (nx-x)/max;
    iy = (ny-y)/max;

    for(;max>0;max--) {
      if (!generatePCBelements) {
        flashAperture(d, x, y);
      }
      x += ix;
      y += iy;
    }
  }


  //some functions added to enable PCB layout element output



  private String generatePad(double x, double y, boolean metric, double shape, double adx, double ady) {
    if (ady == 0) {
      ady = adx;
    }
    if (adx == 0) {
      adx = ady;
    }

    String output = "Pad[";
    long xNm = convertToNanometres(x, metric);
    // y axis is in other direction on gerbers
    long yNm = -convertToNanometres(y, metric);
    long xsizeNm = convertToNanometres(adx, metric);
    long ysizeNm = convertToNanometres(ady, metric);
    long xOffsetNm = 0;
    long yOffsetNm = 0;

    String gEDAflag = "";
    if (shape == RECTANGLE) {
      gEDAflag = "square";
    }

    long gEDAdefaultMetalClearance = 10; // in mils
    long gEDAdefaultSolderMaskRelief = 10; // in mils

    //    String shapeNetName = "1";
    //    String shapePadName = "1";

    int shapeNetName = pos;
    int shapePadName = pos;

    // scenario with wider SMD pad than tall, which determines
    // which dimension is used for thickness
    // i.e. shapeYsize is equivalent to gEDA's "thickness"
    // attribute for a pad
    if (xsizeNm >= ysizeNm) {
      output = "Pad[" +
          ((xOffsetNm + xNm - xsizeNm/2 + ysizeNm/2)/254) + " " +
          ((yOffsetNm + yNm)/254) + " " +
          ((xOffsetNm + xNm + xsizeNm/2 - ysizeNm/2)/254) + " " +
          ((yOffsetNm + yNm)/254) + " " +
          (ysizeNm/254) + " " +
          (100*gEDAdefaultMetalClearance) + " " +
          (100*gEDAdefaultSolderMaskRelief + (ysizeNm/254)) + " " +
          '"' + shapeNetName + "\" " +
          '"' + shapePadName + "\" " +
          '"' +
          gEDAflag +   // square vs rounded ends of SMD pad, or onsolder
          '"' + "]\n";
    }

    // scenario with taller SMD pad than wide,
    // which determines which dimension is used for thickness
    // i.e. shapeXsize is equivalent to gEDA's
    // "thickness" attribute for a pad
    else {
      output = "Pad[" +
          ((xOffsetNm + xNm)/254) + " " +
          ((yOffsetNm + yNm - ysizeNm/2 + xsizeNm/2)/254) + " " +
          ((xOffsetNm + xNm)/254) + " " +
          ((yOffsetNm + yNm + ysizeNm/2 - xsizeNm/2)/254) + " " +
          (xsizeNm/254) + " " +
          (100*gEDAdefaultMetalClearance) + " " +
          (100*gEDAdefaultSolderMaskRelief + (xsizeNm/254)) + " " +
          '"' + shapeNetName + "\" " +
          '"' + shapePadName + "\" " +
          '"' +
          gEDAflag + // sets rounded or square pad
          '"' + "]\n";
    }    
    return output;
  }

  private String generatePadNm(long x, long y, boolean metric, double shape, long adx, long ady) {
    // we pass this the centroid of the polygon for x,y
    // and pass it the xSizeNM and xSizeNM of the polygon as adx, ady
    if (ady == 0) {
      ady = adx;
    }
    if (adx == 0) {
      adx = ady;
    }

    System.out.println("Polygonal RECT pad: xNm: " + x + ", yNm: " + y + " , adx: " + adx + ", ady: " + ady);

    String output = "Pad[";
    long xNm = x;
    // y axis is in other direction on gerbers
    long yNm = -y;
    long xsizeNm = adx;
    long ysizeNm = ady;
    long xOffsetNm = 0;
    long yOffsetNm = 0;

    String gEDAflag = "";
    if (shape == RECTANGLE) {
      gEDAflag = "square";
    }

    long gEDAdefaultMetalClearance = 10; // in mils
    long gEDAdefaultSolderMaskRelief = 10; // in mils

    //    String shapeNetName = "1";
    //    String shapePadName = "1";

    int shapeNetName = pos;
    int shapePadName = pos;

    // scenario with wider SMD pad than tall, which determines
    // which dimension is used for thickness
    // i.e. shapeYsize is equivalent to gEDA's "thickness"
    // attribute for a pad
    if (xsizeNm >= ysizeNm) {
      output = "Pad[" +
          ((xOffsetNm + xNm - xsizeNm/2 + ysizeNm/2)/254) + " " +
          ((yOffsetNm + yNm)/254) + " " +
          ((xOffsetNm + xNm + xsizeNm/2 - ysizeNm/2)/254) + " " +
          ((yOffsetNm + yNm)/254) + " " +
          (ysizeNm/254) + " " +
          (100*gEDAdefaultMetalClearance) + " " +
          (100*gEDAdefaultSolderMaskRelief + (ysizeNm/254)) + " " +
          '"' + shapeNetName + "\" " +
          '"' + shapePadName + "\" " +
          '"' +
          gEDAflag +   // square vs rounded ends of SMD pad, or onsolder
          '"' + "]\n";
    }

    // scenario with taller SMD pad than wide,
    // which determines which dimension is used for thickness
    // i.e. shapeXsize is equivalent to gEDA's
    // "thickness" attribute for a pad
    else {
      output = "Pad[" +
          ((xOffsetNm + xNm)/254) + " " +
          ((yOffsetNm + yNm - ysizeNm/2 + xsizeNm/2)/254) + " " +
          ((xOffsetNm + xNm)/254) + " " +
          ((yOffsetNm + yNm + ysizeNm/2 - xsizeNm/2)/254) + " " +
          (xsizeNm/254) + " " +
          (100*gEDAdefaultMetalClearance) + " " +
          (100*gEDAdefaultSolderMaskRelief + (xsizeNm/254)) + " " +
          '"' + shapeNetName + "\" " +
          '"' + shapePadName + "\" " +
          '"' +
          gEDAflag + // sets rounded or square pad
          '"' + "]\n";
    }    
    return output;
  }



  private String generateLine(double x, double y, boolean metric, double thickness, double shape, double nx, double ny) {

    String output = "Pad[";
    long xNm = convertToNanometres(x, metric);
    // y axis is in other direction on gerbers
    long yNm = -convertToNanometres(y, metric);
    long xnNm = convertToNanometres(nx, metric);
    long ynNm = -convertToNanometres(ny, metric);
    long xOffsetNm = 0;
    long yOffsetNm = 0;
    long thicknessNm = convertToNanometres(thickness, metric);

    String gEDAflag = "";
    if (shape == RECTANGLE) {
      gEDAflag = "square";
    }

    long gEDAdefaultMetalClearance = 10; // in mils
    long gEDAdefaultSolderMaskRelief = 10; // in mils

    //    String shapeNetName = "2";
    //    String shapePadName = "2"; // vs "1" for pads

    int shapeNetName = pos;
    int shapePadName = pos;

    output = "Pad[" +
        ((xOffsetNm + xNm)/254) + " " +
        ((yOffsetNm + yNm)/254) + " " +
        ((xOffsetNm + xnNm)/254) + " " +
        ((yOffsetNm + ynNm)/254) + " " +
        (thicknessNm/254) + " " +
        (100*gEDAdefaultMetalClearance) + " " +
        (100*gEDAdefaultSolderMaskRelief + (thicknessNm/254)) + " " +
        '"' + shapeNetName + "\" " +
        '"' + shapePadName + "\" " +
        '"' +
        gEDAflag +   // square vs rounded ends of SMD pad, or onsolder
        '"' + "]\n";
    return output;
  }


  private long convertToNanometres(double rawValue, boolean metric)
  {
    if (metric) {
      return (long)(rawValue*1000000);  // 1,000,000 nm per mm
    }
    else {
      return (long)(rawValue*25400000);  // 2540 nm per 0.1 mil
    }
  }

};
