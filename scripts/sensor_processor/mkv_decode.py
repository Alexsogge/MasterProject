#!/usr/bin/python3
import sys
from os import listdir
from os.path import join, isfile, splitext
from typing import Dict, List

import numpy as np
from av.io import read, annotate
from tests.common import fate_suite


# https://github.com/pscholl/PyAV
# acc, groundtruth = read('a: s:', file='104.mkv')
# read('acce', '104.mkv')

nano_sec = 0.000000001

class MKVDecoder:

    def __init__(self):
        pass

    @classmethod
    def read_folder(cls, folder_name: str) -> Dict[str, List[List]]:
        data_mkvs = {}
        for f in listdir(folder_name):
            print(f)
            path = join(folder_name, f)
            if isfile(path) and splitext(f)[1] == '.mkv':
                data_mkvs['Acceleration'] = cls.read_mkv(path, 'acc')
                data_mkvs['Gyroscope'] = cls.read_mkv(path, 'gyro')
                break
                # data_name = splitext(f)[0]
                # data_filter = ''
                # if 'acc' in data_name:
                #     data_name = 'Acceleration'
                #     data_filter = 'acc'
                # elif 'gyro' in data_name:
                #     data_name = 'Gyroscope'
                #     data_filter = 'gyro'
                # elif 'time_stamps' in data_name:
                #     data_name = 'time_stamps'
                #     data_filter = 'ts'
                # print('read', data_name)
                # if data_name not in data_mkvs:
                #     data_mkvs[data_name] = cls.read_mkv(path, data_filter)
                # else:
                #     data_mkvs[data_name] += cls.read_mkv(path, data_filter)
        return data_mkvs

    @classmethod
    def read_mkv(cls, filename, filter) -> np.ndarray:
        print('Decode:', filename)
        # stream = read(filter, file=filename)[0]
        stream = read(filter, file=filename)[0]
        print(type(stream.info), stream.info.rate, stream.shape, type(stream))
        rate = (1 / stream.info.rate) / nano_sec
        data_array = np.ndarray((stream.shape[0], stream.shape[1]+1))
        data_array[:, 1:] = stream
        data_array[:, 0] = np.arange(0, data_array.shape[0] * rate, rate)

        return data_array

if __name__ == '__main__':
    print("Read:", sys.argv[1])

    decoder = MKVDecoder()
    data = decoder.read_folder(sys.argv[1])
    print(data)