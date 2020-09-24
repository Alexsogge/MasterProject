#!/bin/sh
echo 'ctestlock' > /sys/power/wake_lock
# export LD_LIBRARY_PATH=/data/local/tmp/
/data/local/tmp/test_pro/sensortest
echo 'ctestlock' > /sys/power/wake_unlock
