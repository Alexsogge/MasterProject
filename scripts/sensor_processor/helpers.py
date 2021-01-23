import csv
from typing import Dict, List
from os import listdir
from os.path import join, isfile, splitext
import numpy as np


def read_csv(filename: str) -> List[List]:
    with open(filename, newline='') as csvfile:
        reader = csv.reader(csvfile, delimiter='\t', quotechar='"', quoting=csv.QUOTE_NONNUMERIC)
        data = []
        for row in reader:
            if len(row) > 0:
                data.append(row)
    return data


def data_list_to_2d_array(data: List[List]) -> np.ndarray:
    if len(data) == 0:
        return np.ndarray([0, 0])
    data_arr = np.ndarray([len(data), len(data[0])])
    for i, line in enumerate(data):
        if len(line) == data_arr.shape[1]:
            data_arr[i] = line
    return data_arr


def align_array(data: np.ndarray, offset: float) -> np.ndarray:
    if len(data.shape) == 1:
        data -= offset
    else:
        data[:, 0] -= offset
    return data

def read_csvs_in_folder(folder_name, data_name, entries_per_line):
    overall_entries = []
    overall_entries_length = 0

    for f in listdir(folder_name):
        path = join(folder_name, f)
        if isfile(path) and splitext(f)[1] == '.csv':
            if data_name in f:
                data = read_csv(path)
                data_array = data_list_to_2d_array(data)
                overall_entries_length += data_array.shape[0]
                overall_entries.append(data_array)
    if overall_entries_length == 0:
        return np.ndarray([0, entries_per_line])

    value_array = np.ndarray([overall_entries_length, overall_entries[0].shape[1]])
    offset = 0
    for entry in sorted(overall_entries, key=lambda x: x[0, 0]):
        value_array[offset: offset+entry.shape[0], :] = entry
        offset += entry.shape[0]

    return value_array