# Reports of basic sensor listening

# without sensorreadings:
3 hours: 11%
4 hours: 10%

# first attempt
55 minutes: 7240 wakeups, (~5%, error in code)
  ->

# second attempt
3 hours 10 minutes: 8843 wakeups, 10% 


## Added advanced registering of wakeups -> more computations in background, which will be removed in production

### Recording 1
4 hours 9 minutes: 157 wake ups, 18%


# Added Alarm

### Recording 1
1 hour 14 minutes: 1 wakeup, 3%
lost 2 readings:
  - 601406538528000 ns (initial error)
  - 4391095290000 ns -> 73 minutes

# Changed Alarm to AllowWhileIdle
### Recording 1
16 minutes, 8 wake ups, 31 batches, 8 alarms, 2%
lost 2 readings:
  - 301382000 ns -> 0.005 minutes
  - 470620117000 ns -> 7 minutes

### Recording 2
17 minutes, 0 wake ups, 164 batches, 3 alarms, 3%
lost 1 readings:
  837 sec -> 13 min
Alarms at:
  - 10 sec
  - 90 sec 
  - 90 sec

# Added WackeLock
### Recording 1
2 hours 36 minutes, 12 wake ups, 126 batches, 1 alarm, 11%
lost 2 readings:
  - 7 sec
  - 8613 sec -> 143 min
