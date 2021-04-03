import csv
from itertools import islice
from typing import Dict, List
from os import listdir
from os.path import join, isfile, splitext
import numpy as np
from zipfile import ZipFile
from io import StringIO


def read_csv(filename: str, limit=None) -> List[List]:      
    with open(filename, newline='') as csvfile:
        # reader = csv.reader(csvfile, delimiter='\t', quotechar='"', quoting=csv.QUOTE_NONNUMERIC)
        data = []
        for row in islice(csvfile, 0, None):
            values = list(row.strip('\n').split('\t'))
            # for i, value in enumerate(values[:limit]):
            #     values[i]
            data.append(values[:limit])
            
        # for row in reader:
        #     if len(row) > 0:
        #         data.append(row)

    return data


def read_zip(filename: str, data_name) -> List[List]:
    with ZipFile(filename) as myzip:
        for f in myzip.namelist():
            if data_name in f and splitext(f)[1] == '.csv':
                with myzip.open(f, mode='r') as csvbytes:
                    string_list = [x.decode('utf-8') for x in csvbytes]
                    csvfile = StringIO('\n'.join(string_list))
                    reader = csv.reader(csvfile, delimiter='\t', quotechar='"', quoting=csv.QUOTE_NONNUMERIC)
                    data = []
                    for row in reader:
                        if len(row) > 0:
                            data.append(row)
                return data
    return []


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

def read_csvs_in_folder(folder_name, data_name, entries_per_line, open_zips=True, limit=None):
    overall_entries = []
    overall_entries_length = 0

    for f in listdir(folder_name):
        path = join(folder_name, f)
        if isfile(path):
            if data_name in f and f[0] != '.':
                data = None
                if splitext(f)[1] == '.csv':
                    data = read_csv(path, limit=limit)
                elif splitext(f)[1] == '.zip' and open_zips:
                    data = read_zip(path, data_name)
                if data is not None:
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