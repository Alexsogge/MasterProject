#!/bin/sh
echo 'ctestlock' > /sys/power/wake_lock
/data/local/tmp/a.out
echo 'ctestlock' > /sys/power/wake_unlock
