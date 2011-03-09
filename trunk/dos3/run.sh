#!/bin/sh
mv ?*.tar proj3.tar
tar xvf proj3.tar; rm *.class
rm *.log
javac *.java; java start
