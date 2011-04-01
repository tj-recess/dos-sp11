#!/bin/sh
mv ?*.tar proj4.tar
tar xvf proj4.tar; rm *.class
rm *.log
javac *.java; java start
