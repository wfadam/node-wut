#******************************************************************************************************
#       NOTICE: PLEASE READ CAREFULLY: ADVANTEST PROVIDES THIS SOFTWARE TO YOU ONLY UPON YOUR         *
#       ACCEPTANCE OF ADVANTEST.S TERMS OF USE. THE SOFTWARE IS PROVIDED .AS IS. WITHOUT WARRANTY     *
#       OF ANY KIND AND ADVANTEST.S LIABILITY FOR SUCH SOFTWARE LIMITED TO THE FULLEST EXTENT         *
#       PERMITTED UNDER THE LAW.                                                                      *
#******************************************************************************************************
	CC       = gcc
	TRANS    = javac -classpath /opt/ATKEI/java/t5773.jar:.. 
  
  	JAVADOC = javadoc
	JAVASRC =  Agent.java

	JAVAOBJ = ${JAVASRC:.java=.class}

all:              $(JAVAOBJ)    
%.class:%.java 
	$(TRANS)  $<

clean:
	rm -f *.class 


