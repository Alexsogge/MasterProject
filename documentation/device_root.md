# How to root a Ticwatch C2

## Fastboot
Enable ADB in the Developer Settings
Once done connect your watch to your computer and do "adb reboot bootloader"
THIS WILL RESET YOUR WATCH:*Once in fastboot mode type "fastboot oem unlock" (if you already have an unlocked bootloader you can skip this) 


## unlock bootloader
https://prnt.sc/ljfivl
https://nerdschalk.com/unlock-bootloader-android-wear-watch-fastboot/

## Backup system
https://www.droidwiki.org/wiki/Backup/Ohne_Root



To enter TWRP type in bootloader:

$fastboot boot twrp.img

Wait until watch is in TRWP, then:

$adb shell
#dd if=/dev/block/mmcblk0p1 of=/sdcard/modem.img
#exit
$adb pull /sdcard/modem.img 


If you want to have backup that can take you out of almost every trouble, you need platform-tools like adb. Easy to find via Search.
Boot to TWRP, connect your watch to PC.
Open command prompt in folder where is adb and follow below commands:
$adb shell
#dd if=/dev/block/mmcblk0p22 of=/sdcard/system.img
#dd if=/dev/block/mmcblk0p1 of=/sdcard/modem.img
#dd if=/dev/block/mmcblk0p13 of=/sdcard/misc.img
#dd if=/dev/block/mmcblk0p25 of=/sdcard/recovery.img
#dd if=/dev/block/mmcblk0p21 of=/sdcard/boot.img
#dd if=/dev/block/mmcblk0p31 of=/sdcard/vendor.img
#dd if=/dev/block/mmcblk0p35 of=/sdcard/data.img (contains private data do not share!!)
#exit
$adb pull /sdcard/system.img
$adb pull /sdcard/modem.img
$adb pull /sdcard/misc.img
$adb pull /sdcard/recovery.img
$adb pull /sdcard/boot.img
$adb pull /sdcard/vendor.img
$adb pull /sdcard/data.img



## FLASH TWRP RECOVERY:
a) adb devices
b) adb reboot bootloader
c) fastboot devices [ENSURE IT IS WORKING]
d) fastboot flash recovery name-of-the-twrp.img (just name it recovery.img)
c) fastboot reboot


## FLASHING ROM:
a) connect your watch to computer with adb debugging on in watch
b) adb devices (to make sure your device is connected)
c) adb reboot recovery (this is all you need to reboot into TWRP)
d) Place the ROM, MAGISK, and BUSYBOX on your watch after you've booted into TWRP Recovery. Your watch will show up as a mounted storage device when connected to your computer in this mode.
e) full wipe is recommended....Wipe data/Factory reset
f) flash the ROM
g) reboot and complete your initial setting
h) reboot back into twrp (step c above) and flash Magisk, go back, then flash Busybox
i) reboot system (uncheck the TWRP app/apk install at then end because you don't need it - I tested it and it does nothing but take up space on the watch)
j) done - enjoy and donate to him if you like



#### Corrected
1) FLASH TWRP RECOVERY:
a) adb devices
b) adb reboot bootloader
c) fastboot devices [ENSURE IT IS WORKING]
d) fastboot flash recovery name-of-the-twrp.img (just name it recovery.img)
c) fastboot reboot

2) FLASHING ROM:
a) connect your watch to computer with adb debugging on in watch
b) adb devices (to make sure your device is connected)
c) adb reboot recovery (this is all you need to reboot into TWRP)
d) place the ROM, MAGISK, and BUSYBOX on your watch after you've booted into TWRP Recovery. Your watch will show up as a mounted storage device when connected to your computer in this mode.
e) full wipe is recommended....Wipe data/Factory reset
f) flash the ROM
g) reboot and complete your initial setting
h) reboot back into twrp (step c above) and flash Magisk, go back, then flash Busybox
i) reboot system (uncheck the TWRP app/apk install at then end because you don't need it - I tested it and it does nothing but take up space on the watch)
j) done - enjoy and donate to him if you like 



Boot into TWRP
Move the ROM to your device
Backup Everything for future
Flash the ROM




##############################
REQUIREMENT
##############################

- An Unlock bootloader
- Working adb/fastboot and driver
adb fastboot tool

##############################
HOW TO FLASH/BOOT INTO TWRP-RECOVERY
##############################

- Attach your devices/watch to your PC and enable USB Debugging from settings menu.
- Download the TWRP - recovery and move it to your adb/fastboot folder.
- Boot into bootloader mode/fastboot mode and apply the following code.
Code:

- adb devices
- adb reboot bootloader
- fastboot devices [ENSURE IT IS WORKING]
- fastboot boot name-of-the-twrp.img
- fastboot reboot

##############################
HOW TO FLASH KERNEL/BOOT IMAGE
##############################

- Steps via adb/fastboot
- Move the boot image into your adb/fastboot folder and apply the following commands
Code:

- adb devices
- adb reboot bootloader
- fastboot flash boot boot.img
- fastboot reboot

- Reboot

##############################
HOW TO FLASH THE ROM AND ROOT
##############################
0- Boot into bootloader first
1- Now move the build/ROM and Magisk to your watch
2- Make a backup - there's always 1% chance something goes wrong.
3- Full wipe is recommended....Wipe data/Factory reset
4- Flash the ROM
5- Reboot and complete your initial setting
6- Reboot back into twrp and Flash Magisk (Optional..ONLY IF YOU PREFER)
7- Done. Don't forget to donate if you like my work, Thanks. 


1053
./fastboot flash boot boot.img
./fastboot reboot-bootloader

./fastboot flash system system.img
./fastboot reboot-bootloader

./fastboot flash vendor vendor.img
./fastboot reboot-bootloader

./fastboot format userdata
./fastboot format cache
./fastboot reboot


## Comments
1018: Release1?(Pro)
1170: Release
1275   
1395: fix fastboot   
1585: tut?
1667: tut?   
1789: Backup?
1913: release
2299   
2313: release -> 2334   
2669: release
2723: root
2797: vendor flash
2805   Steps to unlock Ticwatch Pro, with Root and TWRP/Magisk
2874   
2949   
3091   flash just kernel
3102   questions -> 3105   
3155  release
3545   stock -> 3564   
3754  tut?
3776   bootloop


# Other stuff
## Build own kernel
https://appuals.com/how-to-build-a-custom-android-kernel/

## ADB ROOT
https://github.com/evdenis/adb_root
