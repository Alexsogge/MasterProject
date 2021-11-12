#!/usr/bin/python3
import sys
from os import listdir
from os.path import join, isfile, splitext
from typing import Dict, List

import numpy as np
from sensor_processor.helpers import *

nano_sec = 0.000000001



class MarkerDecoder:

    @classmethod
    def read_data(cls, folder) -> np.ndarray:

        value_array = read_csvs_in_folder(folder, 'marker_time_stamp', 2)
        return value_array



if __name__ == '__main__':
    print("Read:", sys.argv[1])

    data = MarkerDecoder.read_data(sys.argv[1])
    print(data)