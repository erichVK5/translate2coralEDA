// translate2coralEDA - a utility for turning various EDA formats into gEDA/coralEDA CAD elements
// Arc.java v1.2
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
//    Arc.java Copyright (C) 2015, 2019 Erich S. Heinzle a1039181@gmail.com



/**
 *
 * This class is passed a Kicad Draw Circle string of the form
 * "DC Xcentre Ycentre X_startpoint Y_startpoint angle lineWidth layer"
 * and implements a method which can generate a gEDA ElementArc
 * definition for a gEDA PCB footprint
 *
 * @param long xOffset the X offset of the Draw Segment relative to the module origin
 * @param long yOffset the Y offset of the Draw Segment relative to the module origin
 * @param float magnificationRatio magnification to be applied to segment position and size, default 1.0
 *
 * @return String = "ElementArc[x y width height startangle deltaangle thickness]"
 *
 */

public class Arc extends FootprintElementArchetype
{
  String output = "";

  long gEDAxCoord = 0;
  long gEDAyCoord = 0;
  long xCoordNm = 0;
  long yCoordNm = 0;
  long xPointNm = 0;
  long yPointNm = 0;
  long kicadStartAngle = 0;    // in 0.1 degree increments
  long gEDAstartAngle = 0;     // in degrees
  long kicadDeltaAngle = 3600; // Kicad angle in 0.1 degree increments
  int kicadLayer = 0;          // not used for arcs
  long gEDAdeltaAngle = 0;     // in degrees CCW
  double radiusNm = 0;
  long gEDAwidth = 0;
  long gEDAheight = 0;
  long gEDAradius = 0;
  long gEDAlineThickness = 1000; // this is 10 mil in 0.01 mil units
  long defaultLineThicknessNm = 254000; // which is 254000 nanometres
  // which is 254 microns, which is 0.254 mm
  // which is 0.01 inches, which is 10 mil = 10 thou
  long lineThicknessNm = defaultLineThicknessNm;
  boolean reverseDirection = false;

  String kicadArcDescriptor = "";

  public void KicadDrawingArc()
  {
    output = "#Hmm, the no arg KicadDrawingArc constructor didn't do much";
  }

  public String toString()
  {
    return kicadArcDescriptor;
  }

  // here, we populate the arc object with a string
  // extracted from an Eagle .lbr file
  // eagle uses "<wire..." for both arcs and lines
  // it seems that the y-axis is inverted vs gEDA/kicad
  // gEDA's arc direction is +ve CCW
  // and Eagle's "curve" directive is +ve CCW, nifty
  // unlike kicad; CW +ve for kicad
  public void populateEagleElement(String EagleWireArc) {
    EagleWireArc = EagleWireArc.replaceAll("[<>/]","");
    long xCoordOneNm = 0;
    long yCoordOneNm = 0;
    long xCoordTwoNm = 0;
    long yCoordTwoNm = 0;
    float floatDeltaDegrees = 0; 
    kicadLayer = 21; // i.e. F.Silk is assumed for now
    // since we only call this method if it is top silk
    // Incidentally, we don't need no XML/DOM...
    String [] tokens = EagleWireArc.split(" ");
    for (int index = 0; index < tokens.length; index++) {
      if (tokens[index].startsWith("x1=")) {
        xCoordOneNm = mmTextToNM(tokens[index].substring(3));
      } else if (tokens[index].startsWith("y1=")) {
        yCoordOneNm = mmTextToNM(tokens[index].substring(3));
      } else if (tokens[index].startsWith("x2=")) {
        xCoordTwoNm = mmTextToNM(tokens[index].substring(3));
      } else if (tokens[index].startsWith("y2=")) {
        yCoordTwoNm = mmTextToNM(tokens[index].substring(3));
      } else if (tokens[index].startsWith("curve=")) {
        String textAngle = tokens[index].replaceAll("[\"]", "");
        //System.out.println("Pre parsing text angle: " + textAngle);
        floatDeltaDegrees = Float.parseFloat(textAngle.substring(6));
        // -ve delta in Eagle means "convex left" when moving from
        // point one to point two 
        // +ve delta in Eagle means "convex right" when moving from
        // point one to point two in Eagle cartesian plane
        // and convex left in inverted kicad/geda plane
        kicadDeltaAngle
            = (long)(-10*floatDeltaDegrees);
        // we calculate gEDAdeltaAngle now, because it is easy
        // negation is needed since kicad is +ve CW
        gEDAdeltaAngle = Math.round(kicadDeltaAngle/-10.0);
        // gEDA PCB doesn't rely on fractions of a degree like Eagle
        // to accurately define arc locations, so rounding is OK.
        //System.out.println("Post parsing angle: " + gEDAdeltaAngle);
      } else if (tokens[index].startsWith("width=")) {
        lineThicknessNm = mmTextToNM(tokens[index].substring(6));
        if (lineThicknessNm == 0) {
          lineThicknessNm = defaultLineThicknessNm;
        }
      } 
    }

    // we find the mid point between the start and end points
    double midX = (xCoordOneNm + xCoordTwoNm)/2;
    double midY = (yCoordOneNm + yCoordTwoNm)/2;

    // and find their offsets relative to the arc start point
    //double midXdx = midX - xCoordOneNm;
    //double midYdy = midY - yCoordOneNm; // not needed

    // the following is not really necessary, but it
    // makes the deltaAngle sign the same in all cases
    // by reversing the direction of the arc 
    if (gEDAdeltaAngle < 0) {
      reverseDirection = true;
      long tempVal1 = xCoordTwoNm;
      long tempVal2 = yCoordTwoNm;
      xCoordTwoNm = xCoordOneNm;
      yCoordTwoNm = yCoordOneNm;
      xCoordOneNm = tempVal1;
      yCoordOneNm = tempVal2;
      gEDAdeltaAngle = -gEDAdeltaAngle;
    }

    // we will use the law of cosines to establish
    // the centre of the arc
    // i.e. sqrt(dist^2/(2-cos(theta))) = radius
    long dx = xCoordTwoNm - xCoordOneNm;
    long dy = yCoordTwoNm - yCoordOneNm;
    // we first use Pythagoras' theorem to find the length
    // of one side, the line P1P2
    long P1P2DistSqNm = (dx*dx + dy*dy);
    double P1P2DistNm = Math.sqrt(P1P2DistSqNm);
    // and find angle between the two radial "arms" of equal
    // length sweeping out the circular arc segment, in radians 
    double theta = Math.abs(2*Math.PI*(floatDeltaDegrees/360.0));
    //    double theta = 2*Math.PI*(gEDAdeltaAngle/360.0);

    // we now employ the law of cosines
    // thanks to Euclid, circa 400BC, and 
    // his propositions 12 and 13
    double halfP1P2 = P1P2DistNm/2;
    radiusNm = (long)Math.sqrt(P1P2DistSqNm/(2-2*Math.cos(theta)));

    // now, the distance from our midpoint to the arc centre
    // is found using pythagorus' theorem
    double P1P2arcCentreDistance
        = Math.sqrt(radiusNm*radiusNm - halfP1P2*halfP1P2);

    double P1P2Angle = Math.atan2(dy, dx);
    double CLAngle = P1P2Angle - Math.PI/2;

    // with these angles we can establish the location of x,y,
    // the centre of the swept arc
    long dyStart = 0;
    long dxStart = 0;

    dxStart // vs midP1P2
        = (long)(P1P2arcCentreDistance*Math.cos(CLAngle)); 
    dyStart // vs midP1P2
        = (long)(P1P2arcCentreDistance*Math.sin(CLAngle));

    // now, dxStart and dystart are the offset from the midpoint (MP)
    // of P1P2 to the centre of the swept arc
    // but.... the centre of the desired arc that passes through
    // P1 and P2 could be +(dxStart,dyStart) or -(dxStart,dyStart)
    // from the midpoint of P1P2
    // to establish which direction our arc centre (AC) must be in,
    // we use the vector cross product of PIP2xMPAC to establish if
    // the wire has negative or positive curve
    // this should also work in seven dimensional space, but YMMV
    double vectorCrossProduct = dxStart*dy - dx*dyStart;
    // corner case with Eagle Arc Angle > -180 needs to be tested for
    if (vectorCrossProduct < 0 || gEDAdeltaAngle > 180)  {
      xCoordNm = (long)(midX + dxStart);
      yCoordNm = (long)(midY + dyStart); 
    } else {
      xCoordNm = (long)(midX - dxStart);
      yCoordNm = (long)(midY - dyStart); 
    }

    // the next thing is to sort out the start angle
    // remembering that gEDA treats the -ve X axis as zero radians
    // and goes in a CCW  = +ve direction
    // and we negate deltas to get direction relative to centre of arc
    double startAngle = Math.atan2(-(yCoordNm - yCoordOneNm),
                                   -(xCoordNm - xCoordOneNm));
    // here we sort out the differing "zero" radians from Eagle
    // and gEDA PCB by subtracting from 180
    //gEDAstartAngle = -(long)(Math.toDegrees(startAngle-Math.PI));//-180);
    gEDAstartAngle = (long)(Math.toDegrees(startAngle-Math.PI));//-180);

    // we now translate the yCoord to suit gEDA PCB's coordinate system
    yCoordNm = -yCoordNm; // NB inverted y-axis in gEDA
    // why couldn't Eagle just use a centre point x,y...
    //gEDAdeltaAngle = -gEDAdeltaAngle;

  }


  // here, we populate the line object with a string
  // extracted from a BXL file
  // noting that the y-axis is inverted vs gEDA/kicad
  public void populateBXLElement(String BXLLine) {
    BXLLine = BXLLine.replaceAll("[\"(),]","");
    String [] tokens = BXLLine.split(" ");
    for (int index = 0; index < tokens.length; index++) {
      if (tokens[index].equals("Origin")) {
        xCoordNm = milToNM(Float.parseFloat(tokens[++index]));
        yCoordNm = -milToNM(Float.parseFloat(tokens[++index]));
      } else if (tokens[index].equals("TOP_SILKSCREEN")) {
        kicadLayer = 21; // i.e. F.Silk
      } else if(tokens[index].equals("Radius")) {
        radiusNm = milToNM(Float.parseFloat(tokens[++index]));
      } else if(tokens[index].equals("Width")) {
        lineThicknessNm = milToNM(Float.parseFloat(tokens[++index]));
      } else if(tokens[index].equals("StartAngle")) { // in degrees
        gEDAstartAngle = Integer.parseInt(tokens[++index]);
      } else if(tokens[index].equals("SweepAngle")) { // in degrees
        gEDAdeltaAngle = -Integer.parseInt(tokens[++index]);
        kicadDeltaAngle = -10*gEDAdeltaAngle;
        // seem to need negative here, since y-axis is flipped
        // also, note that
        // gEDAdeltaAngle = Math.round(kicadDeltaAngle/(-10.0));
      }
    }
  }

  public void populateGerberElement(long cx1,
                                    long cy1,
                                    long thickness,
                                    long radius,
                                    double start,
                                    double arc,
                                    int pinNum) {

        kicadLayer = 15; // i.e. F.Cu
        xCoordNm = cx1;
        yCoordNm = cy1;
        radiusNm = radius;
        lineThicknessNm = thickness;
        gEDAstartAngle = (int) start;
        gEDAdeltaAngle = (int) arc;
        kicadDeltaAngle = -10*gEDAdeltaAngle;
  }
  
  // here, we populate the line object with a string
  // extracted from a Kicad module    
  public void populateKicadElement(String arg, boolean metric)
  {

    kicadArcDescriptor = arg;

    String[] tokens = kicadArcDescriptor.split(" ");

    float parsedValue = 0;

    //		System.out.print("#The passed string:" + arg + "\n");
    if (tokens[0].startsWith("DA"))
      {
        parsedValue = Float.parseFloat(tokens[1]);
        xCoordNm = convertToNanometres(parsedValue, metric); 
        parsedValue = Float.parseFloat(tokens[2]);
        yCoordNm = convertToNanometres(parsedValue, metric);
        parsedValue = Float.parseFloat(tokens[3]);
        xPointNm = convertToNanometres(parsedValue, metric);
        parsedValue = Float.parseFloat(tokens[4]);
        yPointNm = convertToNanometres(parsedValue, metric);
        kicadDeltaAngle = Integer.parseInt(tokens[5]);
        parsedValue = Float.parseFloat(tokens[6]);
        lineThicknessNm = convertToNanometres(parsedValue, metric);
      }
    else if (tokens[0].startsWith("fp_arc"))
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
        kicadDeltaAngle = Integer.parseInt(tokens[8]);
        parsedValue = Float.parseFloat(tokens[12]);
        lineThicknessNm = convertToNanometres(parsedValue, metric);
        // this does not establish what layer it is to be on, but no biggy

      }
    else
      {
        output = "Hmm, a Draw Arc string was not passed to the object";
      }

    // it seems $MODULE (= footprints) support arcs with arbitrary
    // deci-degrees in Kicad,
    // http://en.wikibooks.org/wiki/Kicad/file_formats#Drawings
    // but drawing elements $DRAWSEGMENT only support 90 degree arcs

    long yDir = 0;
    long xDir = 0;

    // our first task is to establish the start point of the arc in degrees.
    // we are effectively given the start point in cartesian coordinates.
    // we use the arctan function to determine the angle.
    // we then convert this angle to degrees to suit gEDA, starting from
    // the -ve X axis, and going in a +ve = counterclockwise (CCW) direction.
    // we also catch the scenarios where cos(startAngle) = 0 since tan is
    // not well defined for tan = (sin (+/-90) / cos (+/- 90)) = 1/0 

    // we have 8 scenarios to consider
    // the simplest four scenarios being:
    //
    // the start point lies on one of the +ve X, -ve X, +ve Y or -ve Y axes
    //
    // or, the start point lies in one of the four quadrants of the x,y plane:
    //
    // the start point lies in LUQ -ve X, -ve Y,  with xDir > 0,  yDir > 0 
    // the start point lies in RUQ +ve X, -ve Y,  with xDir < 0,  yDir > 0
    // the start point lies in LLQ -ve X, +ve Y,  with xDir > 0,  yDir < 0
    // the start point lies in RLQ +ve X, +ve Y,  with xDir < 0,  yDir < 0
    //

    yDir = (yCoordNm - yPointNm);
    // the Y component of the start point coordinates
    xDir = (xCoordNm - xPointNm);
    // the X component of the start point coordinates

    // also, we determine the radius of the arc starting at the start
    // point (xDir, yDir)
    radiusNm = Math.sqrt((xDir*xDir) + (yDir*yDir));

    if ((yDir == 0) && (xDir < 0))
      {
        gEDAstartAngle = 180; // arc start point lies along +X axis
      }
    else if ((yDir == 0) && (xDir > 0))
      {
        gEDAstartAngle = 0;  // arc start point lies along -X axis
      }

    else if (yDir < 0) // this means arc starts in
      {                // the y > 0 hemiplane //< 0 hemiplane
        if (xDir == 0) // the start point lies on the + Y axis
          {
            gEDAstartAngle = 90; // = the +Y axis
            // 	in gEDA degrees, starting @ X- axis, +ve = CCW direction
            //	System.out.println("startangle A: " + startAngle);
          }
        else if (xDir < 0) // this is RLQ on screen, +ve X, +ve Y
          {
            gEDAstartAngle = 180 - Math.round(1.0 * Math.toDegrees(Math.atan((yDir)/(xDir))));
            //	System.out.println("startangle B: " + startAngle);
          }
        else if (xDir > 0) // this LLQ on screen, +ve X, -ve Y
          {
            gEDAstartAngle = 0 - Math.round(1.0 * Math.toDegrees(Math.atan((yDir)/(xDir)))); // atan(-veY/+veX) < 0
            //	System.out.println("startangle C: " + startAngle);
          }
      }
    else if (yDir > 0) // this means arc starts in the y > 0 hemiplane
      {
        if (xDir == 0) // the start point lies on the +Y axis
          {
            gEDAstartAngle = 270;
            // 	in gEDA degrees, starting @ x- axis, +ve = CCW direction
            //	System.out.println("startangle D: " + startAngle);
          }
        else if (xDir < 0) // this is RUQ on screen, +ve X, -ve Y
          {
            gEDAstartAngle = 180 - Math.round(1.0 * Math.toDegrees(Math.atan((yDir)/(xDir)))); // atan(-veY/+veX) < 0
            //	System.out.println("startangle E: " + startAngle);
          }
        else if (xDir > 0) // this is LUQ on screen, +ve X, +ve Y
          {
            gEDAstartAngle = 0 -  Math.round(1.0 * Math.toDegrees(Math.atan((yDir)/(xDir))));
            //	System.out.println("startangle F: " + startAngle);
          }

      }

    // first, we calculate the magnification invariant stuff, namely
    // the start angle and delta angle for the arc

    // as far as can be determined from the Kicad documentation,
    // http://en.wikibooks.org/wiki/Kicad/file_formats#Drawings
    // 
    // the origin for rotation is the positive x direction, and going CW
    //
    // whereas in gEDA, the gEDA origin for rotation is the negative
    // x axis, with rotation CCW, so we need to reverse delta angle,
    // and scale
    //
    // deltaAngle is CW in Kicad in deci-degrees, and CCW in
    // degrees in gEDA and in Eagle
    //
    // ... having tested the code, Kicad deltaAngle is indeed
    // opposite to gEDA
    //  

    gEDAdeltaAngle = Math.round(kicadDeltaAngle/(-10.0));

    // hmmm, it seems some arcs in Kicad foootprint modules have
    // decidegrees of arc > 3600, which is basically a circle,
    // but defined with an arc statement for some reason.
    // This seems to be the result of a dodgy Eagle2Kicad.ulp footprint
    // conversion script
    // So, we need to check for this and establish the delta angle
    // modulo 360 to avoid funny looking partial arcs or circles when
    // rendered by gEDA.

    //	System.out.println("deltaAngle: " + deltaAngle);

    while (gEDAdeltaAngle > 360)
      {
        gEDAdeltaAngle -= 360;
      }

    while (gEDAdeltaAngle < -360)
      {
        gEDAdeltaAngle += 360;
      }
  }

  // we use this for polygon outlines in Eagle, which can employ arcs
  public long [] asSegments() {
    return asSegments(15); // a reasonable default 
  }
  
  // we use this for polygon outlines in Eagle, which can employ arcs
  public long [] asSegments(int segments) {
    int nSections = segments;
    long [] xpoints = new long[nSections + 1];
    long [] ypoints = new long[nSections + 1];
    long [] points = new long[2*nSections + 2];
    double ddelta = Math.PI*gEDAdeltaAngle/180.0/nSections;
    //System.out.println("start angle: " + gEDAdeltaAngle
    //                   + "ddelta angle: " + ddelta );
    for (int i = 0; i < nSections + 1; i++) {
      xpoints[i] = xCoordNm
          + (long) (radiusNm*Math.cos(Math.PI*gEDAstartAngle/180
                                      + Math.PI + i*ddelta));
      ypoints[i] = yCoordNm
          + (long) (radiusNm*Math.sin(Math.PI*gEDAstartAngle/180
                                      + Math.PI + i*ddelta));
      // System.out.println("Points: " + xpoints[i] + ", " + ypoints[i]);
    }

    // to simplify things, we generate arcs with positive delta
    // but this affects path definitions, such as for polygons
    if (reverseDirection) { // in which case we reverse the point order
      for (int j = 0; j < nSections + 1; j++) {
        points[2*j] = xpoints[nSections - j];
        points[2*j+1] = ypoints[nSections - j];
      }
    } else {
      for (int j = 0; j < nSections + 1; j++) {
        points[2*j] = xpoints[j];
        points[2*j+1] = ypoints[j];
      }
    }
    //System.out.println("arc segments calculated");
    return points;
  }
  
  public String lihataArc(long xOffset, long yOffset, float magnificationRatio) {
    return "      ha:arc." + arcCount++ + " {\n" +
	"       clearance = 0.0\n" +
	"       astart = " + gEDAstartAngle + "\n" +
	"       thickness = " + lineThicknessNm + "nm\n" +
	"       width = " + radiusNm + "nm\n" +
	"       height = " + radiusNm + "nm\n" +
	"       adelta = " + gEDAdeltaAngle + "\n" +
	"       x = " + (long)((xCoordNm + xOffsetNm)*magnificationRatio) + "nm\n" +
	"       y = " + (long)((yCoordNm + xOffsetNm)*magnificationRatio) + "nm\n" +
	"       ha:attributes {\n       }\n       ha:flags {\n       }\n      }\n";
  }

  public String generateElement(long xOffset, long yOffset, float magnificationRatio, String format)
  {
    if (format.equals("pcb")) {
      return generateGEDAelement(xOffset, yOffset, magnificationRatio);
    } else {
      return lihataArc(xOffset, yOffset, magnificationRatio);
    }
  }

  public String generateGEDAelement(long xOffsetNm, long yOffsetNm, float magnificationRatio)
  // offsets are in nm, magnificationRatio is a float, default 1.0
  {
    // having established the start angle in gEDA degrees
    // we can move on to magnification variant stuff, namely,
    // x and y positions, and radius, to then be able to
    // generate an arc definition in gEDA format 

    // we take care of magnification here
    // this allows a family of footprint silkscreens graphics to
    // be generated fairly easily, for example different sized 7
    // or 17 segment LED displays
    // Device outlines may need to be hand tweaked, as some
    // device families share the same outline, despite different sized
    // features across the family of device, i.e. 0.5 inch and
    // 0.8 inch LED displays have the same outline silkscreen
    // which doesn't need to be magnified

    gEDAxCoord = (long)((xCoordNm + xOffsetNm)*magnificationRatio/254);
    // divide nm by 254 to produce
    gEDAyCoord = (long)((yCoordNm + yOffsetNm)*magnificationRatio/254);
    // 0.01 mil units

    // apply the magnificationRatio to radiusNm
    radiusNm = magnificationRatio*radiusNm;

    // now convert the radius in nm to gEDA 0.01 mil units
    gEDAradius = Math.round(radiusNm/254);
    // in 0.01mil units for gEDA, so nm/254

    gEDAlineThickness = (lineThicknessNm / 254);
    // every 254 nm is 0.01 mil

    output = "ElementArc[" +
        gEDAxCoord + " " +
        gEDAyCoord + " " +
        gEDAradius + " " +
        // gEDAradius is equal to width and height for a circle
        gEDAradius + " " +
        gEDAstartAngle + " " + // in degrees
        gEDAdeltaAngle + " " + // in degrees
        gEDAlineThickness + "]\n"; // in 0.01 mil units
    return output;
  }

  public boolean isTop() {
    return kicadLayer == 21;
  }

  public boolean isTopCopper() {
    return kicadLayer == 15;
  }

}
