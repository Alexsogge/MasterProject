# How to run C code


# Build
[1]: Doc1
NDK package offers prebuild binaries. We can use /prebuilt/linux-x86_64/bin/armv7a-linux-androideabi16-clang.

## ndk-build
Can be downloaded at [16]. Needs and **Android.mk** and **Application.mk**. Then run `ndk-build` in project folder [21] [19].  
We need android library for the sensor framework. Therefore we have to add `LOCAL_LDLIBS    := -landroid` to our **Android.mk**.

#### LD_LIBRARY
Could occur missing library `libc++_shared.so`. We have to set path variable `LD_LIBRARY_PATH` [22]

# Run code at startup
We have to change init.rc
This file is mounted read only -> change it in boot image [2]
[4]


## Edit boot image
Bootimage tools link [3]
Find boot.img [17][18]

## Using Magisk
Magisk enables to run scripts after boot in an easy way. We can simply add shell scripts to the /data/adb/service.d/ directory. [5][15]
Need wake-lock[6] 
Could be interesting, RTC-Wackeup[7]


# I2C
[8]

# Device
Ne need to specify the device name and device address.  
To find the device name we take a look in `/sys/class/i2c-dev/`. The devices are numerated by `i2c-i`.  
To get the address we open the device and go to `device`. There is a file `i-xxxx` where `xxxx` is the address.

  




Error codes [20]
## i2c-tools
provides some commands [9][10][11][12]


# Android Open Source Project
AOSP is used to build kernel modules [13][14]

---------------------------------
Refs:  
[1]: http://nickdesaulniers.github.io/blog/2016/07/01/android-cli/  
[2]: http://droidcore.blogspot.com/2012/12/how-to-edit-initrc-in-android.html  
[3]: https://unix.stackexchange.com/questions/64628/how-to-extract-boot-img  
[4]: https://stackoverflow.com/questions/9768103/make-persistent-changes-to-init-rc  
[5]: https://topjohnwu.github.io/Magisk/guides.html#boot-scripts  
[6]: https://stackoverflow.com/questions/17654140/is-there-a-way-to-get-wakelock-on-android-through-the-jni-ndk  
[7]: https://ragsagar.wordpress.com/2011/08/15/how-to-automatically-wake-up-your-computer-at-a-particular-time-resume-by-rtc-alarm-in-arch-linux/  
[8]: https://android.googlesource.com/kernel/msm/+/f5335159eed416b26b7c8a5a4e8820f97dc1ad19/Documentation/i2c/dev-interface  
[9]: https://stackoverflow.com/questions/19763831/building-i2c-tools-on-android  
[10]: https://github.com/richardtin/i2c-tools  
[11]: https://ara-mdk.googlesource.com/platform/external/i2c-tools/  
[12]: https://discuss.96boards.org/t/building-i2c-tools-for-aosp-on-hikey-960/5163  
[13]: https://source.android.com/setup/build/downloading  
[14]: https://source.android.com/setup/build/building  
[15]: https://github.com/topjohnwu/Magisk/blob/master/docs/guides.md  
[16]: https://developer.android.com/ndk/downloads  
[17]: https://androidforums.com/threads/where-is-my-boot-img.892350/  
[18]: https://android.stackexchange.com/questions/190095/backup-boot-img-via-terminal-one-line-command  
[19]: https://code.tutsplus.com/tutorials/advanced-android-getting-started-with-the-ndk--mobile-2152  
[20]: https://www-numi.fnal.gov/offline_software/srt_public_context/WebDocs/Errors/unix_system_errors.html  
[21]: http://web.guohuiwang.com/technical-notes/androidndk1  
[22]: https://libcxx.llvm.org/docs/UsingLibcxx.html  
