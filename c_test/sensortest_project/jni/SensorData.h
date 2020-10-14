
#ifndef SENSORDATA_H
#define SENSORDATA_H

class SensorData
{
public:
  unsigned long long time_stamp;
  float value_x;
  float value_y;
  float value_z;
  
  SensorData();
};

#endif