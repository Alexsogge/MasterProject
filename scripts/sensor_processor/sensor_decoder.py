#!/usr/bin/python3
import sys
from os import listdir
from os.path import join, isfile, splitext
from typing import Dict, List

import numpy as np
from sensor_processor.helpers import *

nano_sec = 0.000000001


class SensorDecoder:

    def __init__(self, folder_name):
        self.folder_name = folder_name
        self.max_time_stamp = -np.inf
        self.min_time_stamp = np.inf
        self.time_stamps = np.zeros(0)

    def read_data(self, data_name, read_zips=False) -> np.ndarray:
        value_array = read_csvs_in_folder(self.folder_name, data_name, 4)
        self.find_new_min_max_time_stamp(value_array)
        self.add_time_stamps(value_array[:, 0])
        return value_array

    def find_new_min_max_time_stamp(self, data_array):
        if data_array.shape[0] < 2:
            self.max_time_stamp = 0
            self.min_time_stamp = 0
            return
        test_max = data_array[-1, 0]
        test_min = data_array[0, 0]
        if test_max > self.max_time_stamp:
            self.max_time_stamp = test_max
        if test_min < self.min_time_stamp:
            self.min_time_stamp = test_min

    def find_min_max_cheap(self, data_names):
        for data_name in data_names:
            value_array = read_first_last_line(self.folder_name, data_name, 4)
            self.find_new_min_max_time_stamp(value_array)

    def add_time_stamps(self, new_data):
        self.time_stamps = np.union1d(self.time_stamps, new_data)




if __name__ == '__main__':
    print("Read:", sys.argv[1])

    decoder = SensorDecoder(sys.argv[1])

    gyro_data = decoder.read_data('gyro')
    print('gyro_data:', gyro_data.shape, gyro_data[0:5,0])
    print('min:', decoder.min_time_stamp)
    old_min = decoder.min_time_stamp

    acc_data = decoder.read_data('acc')
    print('acc_data:', acc_data.shape, acc_data[0:5,0])
    print('min:', decoder.min_time_stamp)
    print(old_min in acc_data[:,0])