#!/bin/sh
mv ?*.tar proj2.tar
tar xvf proj2.tar; rm *.class
rm *.log
javac *.java; java start
