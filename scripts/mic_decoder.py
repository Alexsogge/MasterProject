#!/usr/bin/python3
import sys
from os import listdir
from os.path import join, isfile, splitext
from typing import Dict, List

import numpy as np


from helpers import *


class MicDecoder:

    @classmethod
    def read_folder(cls, folder: str) -> np.ndarray:
        return read_csvs_in_folder(folder, 'mic_time_stamp', 1)


if __name__ == '__main__':
    print("Read:", sys.argv[1])

    decoder = MicDecoder()
    data = decoder.read_folder(sys.argv[1])
    print(data)