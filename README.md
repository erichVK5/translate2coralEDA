# translate2coralEDA
A utility to convert symbols and footprints to xschem, pcb-rnd, gEDA PCB, and gschem formats

The utility currently exports by default to xschem and pcb-rnd formats. translate2geda is effectively a deprecated utility as gEDA PCB is unable to support more complex features in footprints and layouts, in particular:

 - polygonal features on copper in footprints
 - arcs in footprints
 - arbitrary, multiple text elements in footprints
 - padstacks with arbitrary pad shapes

pcb-rnd's data model accomodates octagonal, roundrect, obround, square and circular pads, and padstacks, arbitrary rotation of padstacks, as well as allowing text, polygons and copper and padstacks within subcircuits (a more generalised form of a footprint). Accordingly, attempting to convert more sophisticated features like this into gEDA PCB format will lead to data loss. pcb-rnd can load gEDA PCB format layouts, Kicad layout and modules, Eagle binary and Eagle XML layouts and libraries, as well as Protel Autotrax, HPGL and various other formats. Users are encouraged to upgrade their toolchain to pcb-rnd with its richer data model and feature set.

A utility allowing conversion from

- BSDL
- BXL
- Eagle XML (symbols, footprints)
- Gerber files
- Hershey Eggbot Fonts
- IBIS
- kicad eeschema symbols
- kicad pcbnew footprints (modules)
- LT-Spice networks
- QUCS networks
- symdef

to xschem, pcb-rnd, gschem, and gEDA PCB formats

see also http://repo.hu/projects/coraleda/

Caveats:

gerber conversion will necessarily result in loss of connectivity information, such as clearances, and padstack stack-ups. Heuristics trying to identify pads and pins can be fooled by polygonal pours. Also, gerbers generated by EDA tools which "paint" features with a raster, like Eagle, will likely result in unusable converted outputs. The gerber conversion is intended only for niche applications where geometries need to be duplicated or features lifted from gerber files.

usage:

	git clone https://github.com/erichVK5/translate2coralEDA
	cd translate2coralEDA
	javac *.java
	java translate2coralEDA myfile.lib

TODO:

- code the unique uid for exported .lht footprints
- continued refactoring of the parser code to simplify and unify footprint export
- implement arbitrary polygonal pad shapes in .lht exports - now done for gerber
- refine xschem symbol outputs
- add SVG font conversion support
- add SVG path conversion support
- port QUCS and LT-Spice symbols to xschem for use with converted network 
Licence GPL 2.0
