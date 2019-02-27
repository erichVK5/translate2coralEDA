# translate2coralEDA
convert symbols and footprints to xschem, pcb-rnd, gEDA PCB, and gschem formats

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


usage:

git clone https://github.com/erichVK5/translate2coralEDA
cd translate2coralEDA
javac *.java
java translate2coralEDA myfile.lib

TODO:

refine xschem symbol outputs

Licence GPL 2.0
