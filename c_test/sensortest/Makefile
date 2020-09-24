all:
	/home/alex/tmp/android-ndk-r21d/toolchains/llvm/prebuilt/linux-x86_64/bin/armv7a-linux-androideabi16-clang++ sensor_reading.c -o sensor_reading -Wall libandroid.so -stdlib=libc++
send:
	adb push sensor_reading /data/local/tmp/
