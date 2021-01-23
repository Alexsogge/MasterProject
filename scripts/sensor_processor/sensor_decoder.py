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
        test_max = data_array[-1, 0]
        test_min = data_array[0, 0]
        if test_max > self.max_time_stamp:
            self.max_time_stamp = test_max
        if test_min < self.min_time_stamp:
            self.min_time_stamp = test_min

    def add_time_stamps(self, new_data):
        self.time_stamps = np.union1d(self.time_stamps, new_data)

    @classmethod
    def extend_battery_values(cls, x_min, x_max, values):
        new_vals = np.ndarray((values.shape[0] + 2, values.shape[1]))
        new_vals[0, :] = [x_min, values[0, 1]]
        new_vals[-1, :] = [x_max, values[-1, 1]]
        new_vals[1:-1, :] = values

        return new_vals



if __name__ == '__main__':
    print("Read:", sys.argv[1])

    decoder = SensorDecoder(sys.argv[1])
    data = decoder.read_data('acc')
    print(data)