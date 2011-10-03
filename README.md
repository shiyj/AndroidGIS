# Android GIS

## App introduction:


This is a native GIS plantform made for Android.

This app is a copy of [spatialite-android](http://code.google.com/p/spatialite-android/) 
but I want do something spatial for me.

You must use Android-NDK to build it and , of cours, SDK & eclipse.

Here is the way:


	git clone git@github.com:shiyj/AndroidGIS.git
	cd ./AndroidGIS
Now, you can build it just run the
	ndk-build
 commend.


	<android-ndk-HOME>/ndk-build

After this you will find some files in your /lib/armeabi  which is the lib you build from JNI.

Now,you can import it into eclipse,enjoy it!
