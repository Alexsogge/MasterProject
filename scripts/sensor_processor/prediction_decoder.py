#!/usr/bin/python3
import sys
from os import listdir
from os.path import join, isfile, splitext
from typing import Dict, List

import numpy as np
from sensor_processor.helpers import *

nano_sec = 0.000000001


class PredictionDecoder:

    @classmethod
    def read_folder(cls, folder_name: str) -> np.ndarray:
        predictions = read_csvs_in_folder(folder_name, 'prediction', 5, limit=4)

        return predictions


if __name__ == '__main__':
    print("Read:", sys.argv[1])

    decoder = PredictionDecoder()
    data = decoder.read_folder(sys.argv[1])
    print(data)
