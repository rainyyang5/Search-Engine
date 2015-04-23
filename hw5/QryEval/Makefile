all:
ifeq ($(OS),Windows_NT)
	# assume windows
	javac -cp ".;lucene-4.3.0/*" *.java
else
	# assume Linux
	javac -cp ".:lucene-4.3.0/*" *.java
endif
