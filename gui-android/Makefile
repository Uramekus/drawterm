ROOT=..
include ../Make.config
LIB=libgui.a

OFILES=\
	cpp/android.$O\
	cpp/native-lib.$O\
	cpp/devandroid.$O\

default: $(LIB) gen/org/echoline/drawterm/R.java bin/classes.dex
$(LIB): $(OFILES)
	$(AR) r $(LIB) $(OFILES)
	$(RANLIB) $(LIB)

gen/org/echoline/drawterm/R.java: $(shell find res/ -type f)
	$(ANDROID_HOME)/build-tools/$(BUILDTOOLSVER)/aapt package -f -m -M AndroidManifest.xml -I $(ANDROID_HOME)/platforms/android-$(API)/android.jar -S res/ -J gen

bin/classes.dex: obj/org/echoline/drawterm/MainActivity.class obj/org/echoline/drawterm/DrawTermThread.class obj/org/echoline/drawterm/MySurfaceView.class
	$(ANDROID_HOME)/build-tools/$(BUILDTOOLSVER)/d8 --output bin/ obj/org/echoline/drawterm/*.class --lib $(ANDROID_HOME)/platforms/android-$(API)/android.jar

obj/org/echoline/drawterm/%.class: java/org/echoline/drawterm/%.java
	$(JAVA_HOME)/bin/javac -d obj/ -classpath $(ANDROID_HOME)/platforms/android-$(API)/android.jar -sourcepath java java/org/echoline/drawterm/$*.java gen/org/echoline/drawterm/R.java

clean:
	rm -rf *.a *.o */*.o */*.a bin/* gen/org/echoline/drawterm/* obj/org/echoline/drawterm/*
