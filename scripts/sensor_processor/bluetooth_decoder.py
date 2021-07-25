#!/usr/bin/python3
import sys
from os import listdir
from os.path import join, isfile, splitext
from typing import Dict, List

import numpy as np
from sensor_processor.helpers import *

nano_sec = 0.000000001


class BluetoothDecoder:

    @classmethod
    def read_folder(cls, folder_name: str) -> np.ndarray:
        values = read_csvs_in_folder(folder_name, 'bluetooth', 4, 3)
        return values


if __name__ == '__main__':
    print("Read:", sys.argv[1])

    decoder = BluetoothDecoder()
    data = decoder.read_folder(sys.argv[1])
    print(data)
