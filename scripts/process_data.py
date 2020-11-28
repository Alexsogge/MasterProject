#!/usr/bin/python3
from typing import Dict, List

import numpy as np
import matplotlib.pyplot as plt
import csv
import sys
from os import listdir
from os.path import isfile, join, splitext

nano_sec = 0.000000001
offset = None


class DataProcessor:

    def __init__(self, folder_name):
        self._offset = None
        self.data_csv: Dict[str, List[List]] = self.read_folder(folder_name)
        self.data_dict: Dict[str, np.ndarray] = dict()
        self.data_dict['Acceleration'] = self.sort_data_array(self.data_to_np_array(self.data_csv['Acceleration']))
        self.data_dict['Gyroscope'] = self.sort_data_array(self.data_to_np_array(self.data_csv['Gyroscope']))
        self.data_dict['time_stamps'] = self.sort_data_array(self.time_stamps_to_np_array(self.data_csv['time_stamps']))
        # self.clean_data()

    @property
    def offset(self):
        if self._offset is None:
            self.find_offset_from_datas()
        return self._offset

    @staticmethod
    def read_csv(filename: str) -> List[List]:
        with open(filename, newline='') as csvfile:
            reader = csv.reader(csvfile, delimiter='\t', quotechar='"', quoting=csv.QUOTE_NONNUMERIC)
            data = []
            for row in reader:
                data.append(row)
        return data[1:]

    def read_folder(self, folder_name: str) -> Dict[str, List[List]]:
        data_csvs = {}
        for f in listdir(folder_name):
            print(f)
            path = join(folder_name, f)
            if isfile(path) and splitext(f)[1] == '.csv':
                data_name = splitext(f)[0]
                if 'acc' in data_name:
                    data_name = 'Acceleration'
                if 'gyro' in data_name:
                    data_name = 'Gyroscope'
                if 'time_stamps' in data_name:
                    data_name = 'time_stamps'
                print('read', data_name)
                if data_name not in data_csvs:
                    data_csvs[data_name] = self.read_csv(path)
                else:
                    data_csvs[data_name] += self.read_csv(path)
        return data_csvs

    def clean_data(self):
        def get_initial_values(data):
            first_time_stamp_index = np.argmin(data[:, 0])
            return data[first_time_stamp_index]

        def interpolate_error_vals(initial_values, data):
            for i, vals in enumerate(data[2:-1], start=2):
                split = False
                for j in range(1, len(initial_values)):
                    if vals[j] == initial_values[j]:
                        # print(initial_values[1:], '=>', initial_values[j], '==', vals[j], '->', vals[1:])
                        # print(j)
                        # split = True
                        data[i][j] = (data[i - 1][j] + data[i + 1][j]) / 2
                if split:
                    print('--------')



        np.set_printoptions(precision=9)
        interpolate_error_vals(get_initial_values(self.data_dict['Acceleration']),
                          self.data_dict['Acceleration'])
        interpolate_error_vals(get_initial_values(self.data_dict['Gyroscope']),
                          self.data_dict['Gyroscope'])


    def sort_data_array(self, data_array):
        return data_array[data_array[:, 0].argsort()]



    def find_offset_from_datas(self):
        def find_offset(data):
            for line in data:
                if len(line) > 0:
                    return line[0]

        self._offset = np.inf

        for key, value in self.data_csv.items():
            if 'acc' in key.lower() or 'gyro' in key.lower():
                offset = find_offset(value)
                if offset < self._offset:
                    self._offset = offset


    def data_to_np_array(self, data: List[List]):
        data_len = 0
        for line in data:
            if len(line) > data_len:
                data_len = len(line)
        data_arr = np.ndarray([len(data), data_len])
        for i, line in enumerate(data):
            if len(line) == data_len:
                data_arr[i] = line
                data_arr[i][0] -= self.offset
        return data_arr


    def time_stamps_to_np_array(self, data: List[List]):
        data_arr = np.ndarray([len(data), 2])
        for i, line in enumerate(data):
            if len(line) == 2:
                data_arr[i] = line
                data_arr[i] -= self.offset
        return data_arr

    def plot_hand_wash_events(self, dims, ax=None, scaling: float=1.0):
        if ax is None:
            ax = plt.gca()
        for ts in self.data_dict['time_stamps']:
            # rect = plt.Rectangle([ts[0], dims[1]], 20, dims[1], facecolor='blue', alpha=0.5)
            # ax.add_patch(rect)
            # print(ts[0]*nano_sec)
            ax.add_patch(plt.Rectangle((ts[0]*nano_sec*scaling - 50*scaling, dims[0]), 50*scaling, (dims[1] * 1.2) - dims[0], facecolor='blue', alpha=0.3))
        ax.vlines(self.data_dict['time_stamps'][:, 0]*nano_sec*scaling, dims[0], dims[1] * 1.2, color='black')

    def sub_plot_data(self, data, ax):
        x = data[:, 0]*nano_sec
        ax.plot(x, data[:, 1])
        ax.plot(x, data[:, 2])
        ax.plot(x, data[:, 3])
        # ax.add_patch(plt.Rectangle((300, 15), 50, 10))
        ax.set_xlabel('time in sec')
        ax.set_ylabel('value')
        self.plot_hand_wash_events((np.amin(data[:, 1:]), np.amax(data[:, 1:])), ax)


    def plot_data(self):

        fig, axs = plt.subplots(2, 1)

        self.sub_plot_data(self.data_dict['Acceleration'], axs[0])
        self.sub_plot_data(self.data_dict['Gyroscope'], axs[1])

        axs[0].set_title('Acceleration')
        axs[1].set_title('Gyroscope')

        fig.tight_layout()
        plt.show()

    def plot_timings(self):
        data = self.data_dict['Acceleration']
        y = np.zeros(data.shape[0])
        for i, timing in enumerate(data[1:,0]):
            y[i] = data[i, 0] - data[i-1, 0]
        y *= nano_sec
        fig, ax = plt.subplots()
        colors = []
        for val in y[1:]:
            if val > 10:
                colors.append('red')
            else:
                colors.append('green')

        ax.scatter(data[1:,0]*nano_sec/60, y[1:], color=colors)
        ax.set_xlabel('timestamp min')
        ax.set_ylabel('difference sec')
        self.plot_hand_wash_events((np.amin(y[1:]), np.amax(y[1:])), ax, 1/60)
        plt.show()

    def calc_idle_time(self):
        #print(self.data_dict['Acceleration'][-100:, 0]*nano_sec)
        # print(np.diff(self.data_dict['Acceleration'][:, 0])*nano_sec)
        idle_time = np.sum(np.diff(self.data_dict['Acceleration'][:, 0]) * nano_sec)
        # print("sum:", idle_time, '-', '0.02 *', self.data_dict['Acceleration'].shape[0], '=', 0.02 * self.data_dict['Acceleration'].shape[0])
        idle_time -= 0.02 * self.data_dict['Acceleration'].shape[0]
        return idle_time


    def calc_total_time(self):
        return (np.max(self.data_dict['Acceleration'][:, 0]) - np.min(self.data_dict['Acceleration'][:, 0])) * nano_sec



if __name__ == "__main__":
    data_processor = DataProcessor(sys.argv[1])
    data_processor.plot_data()
    data_processor.plot_timings()
    print("Idle time:", data_processor.calc_idle_time()/60, " min\t Total time:",
          data_processor.calc_total_time()/60, " min \t -> ",
          (data_processor.calc_idle_time() / data_processor.calc_total_time())*100, "% lost")


    
    # data_list = read_csv(sys.argv[1])
    # plot_data(data_list)
    # plot_timings(data_list)
