
#ifndef SENSORDATA_H
#define SENSORDATA_H

class SensorData
{
public:
  unsigned long long time_stamp;
  float value_x;
  float value_y;
  float value_z;
  float value_pitch;
  float value_roll;
  float value_azimuth;
  // float value_magnetic_x;
  // float value_magnetic_y;
  // float value_magnetic_z;
  
  SensorData();
};

#endif