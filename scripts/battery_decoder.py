#!/usr/bin/python3
import sys
from os import listdir
from os.path import join, isfile, splitext
from typing import Dict, List

import numpy as np
from helpers import *

nano_sec = 0.000000001


def read_states(folder_name):
    overall_entries = []
    overall_entries_length = 0

    for f in listdir(folder_name):
        print(f)
        path = join(folder_name, f)
        if isfile(path) and splitext(f)[1] == '.csv':
            if 'battery' in f:
                data_name = 'battery'
                data = read_csv(path)
                data_array = data_list_to_2d_array(data)
                overall_entries_length += data_array.shape[0]
                overall_entries.append(data_array)
    if overall_entries_length == 0:
        return np.ndarray([0, 2])
    time_stamps = np.ndarray([overall_entries_length, overall_entries[0].shape[1]])
    offset = 0
    for entry in sorted(overall_entries, key=lambda x: x[0, 0]):
        time_stamps[offset: offset+entry.shape[0], :] = entry
        offset += entry.shape[0]

    return time_stamps



class BatteryDecoder:

    @classmethod
    def read_folder(cls, folder_name: str) -> np.ndarray:

        battery_states = read_csvs_in_folder(folder_name, 'battery', 2)
        return battery_states

    @classmethod
    def extend_battery_values(cls, x_min, x_max, values):
        if values.shape[0] == 0:
            return values
        new_vals = np.ndarray((values.shape[0] + 2, values.shape[1]))
        new_vals[0, :] = [x_min, values[0, 1]]
        new_vals[1:-1, :] = values
        new_vals[-1, :] = [x_max, values[-1, 1]]

        return new_vals



if __name__ == '__main__':
    print("Read:", sys.argv[1])

    decoder = BatteryDecoder()
    data = decoder.read_folder(sys.argv[1])
    print(data)