#!/usr/bin/python3
import json
import os
from typing import Dict, List

import numpy as np
import matplotlib.pyplot as plt
import csv
import sys
from os import listdir
from os.path import isfile, join, splitext

from sensor_processor.helpers import *
from sensor_processor.sensor_decoder import SensorDecoder
from sensor_processor.mic_decoder import MicDecoder
from sensor_processor.battery_decoder import BatteryDecoder
from sensor_processor.handwash_decoder import HandwashDecoder

if len(sys.argv) > 2 and sys.argv[2] == 'mkv':
    from mkv_decode import MKVDecoder

nano_sec = 0.000000001


class DataProcessor:

    def __init__(self, folder_name, use_mkv=False):
        self.folder_name = folder_name
        self.sensor_decoder = SensorDecoder(folder_name)
        self.data_dict: Dict[str, np.ndarray] = dict()
        self.data_dict['mic_time_stamps'] = MicDecoder.read_folder(folder_name)
        self.data_dict['battery'] = BatteryDecoder.read_folder(folder_name)
        self.data_dict['time_stamps'] = HandwashDecoder.read_data(folder_name)
        if use_mkv:
            self.data_dict = {**self.data_dict, **MKVDecoder.read_folder(folder_name)}

        else:
            # self.data_csv: Dict[str, List[List]] = self.read_folder(folder_name)
            # self.data_dict['Acceleration'] = self.sort_data_array(self.data_to_np_array(self.data_csv['Acceleration']))
            # self.data_dict['Gyroscope'] = self.sort_data_array(self.data_to_np_array(self.data_csv['Gyroscope']))
            # self.data_dict['time_stamps'] = self.sort_data_array(self.time_stamps_to_np_array(self.data_csv['time_stamps']))

            self.data_dict['Acceleration'] = self.sensor_decoder.read_data('acc')
            self.data_dict['Gyroscope'] = self.sensor_decoder.read_data('gyro')
            # self.data_dict['time_stamps'] = self.sensor_decoder.time_stamps
            self.data_dict['Acceleration'] = align_array(self.data_dict['Acceleration'],
                                                         self.sensor_decoder.min_time_stamp)
            self.data_dict['Gyroscope'] = align_array(self.data_dict['Gyroscope'],
                                                         self.sensor_decoder.min_time_stamp)
            # self.data_dict['time_stamps'] = align_array(self.data_dict['time_stamps'],
            #                                              self.sensor_decoder.min_time_stamp)

            self.data_dict['time_stamps'] = align_array(self.data_dict['time_stamps'],
                                                        self.sensor_decoder.min_time_stamp)

            self.data_dict['mic_time_stamps'] = align_array(self.data_dict['mic_time_stamps'],
                                                            self.sensor_decoder.min_time_stamp)
            self.data_dict['battery'] = BatteryDecoder.extend_battery_values(self.sensor_decoder.min_time_stamp,
                                                                             self.sensor_decoder.max_time_stamp,
                                                                             self.data_dict['battery'])

            self.data_dict['battery'] = align_array(self.data_dict['battery'], self.sensor_decoder.min_time_stamp)

        # self.clean_data()

    def plot_hand_wash_events(self, dims, ax=None, scaling: float=1.0):
        if ax is None:
            ax = plt.gca()
        for ts in self.data_dict['time_stamps']:
            # rect = plt.Rectangle([ts[0], dims[1]], 20, dims[1], facecolor='blue', alpha=0.5)
            # ax.add_patch(rect)
            # print(ts[0]*nano_sec)
            ax.add_patch(plt.Rectangle((ts[0]*nano_sec*scaling - 50*scaling, dims[0]), 50*scaling, (dims[1] * 1.2) - dims[0], facecolor='blue', alpha=0.3))
        ax.vlines(self.data_dict['time_stamps'][:, 0]*nano_sec*scaling, dims[0], dims[1] * 1.2, color='black')

    def plot_mic_events(self, dims, ax=None, scaling: float=1.0):
        if ax is None:
            ax = plt.gca()
        # print(self.data_dict['mic_time_stamps'].shape)
        # print(self.data_dict['mic_time_stamps'][:, 0])
        ax.vlines(self.data_dict['mic_time_stamps'][:, 0]*nano_sec*scaling, dims[0], dims[1] * 1.2, color='pink')



    def sub_plot_data(self, data, ax, y_label='value', add_time_stamps=True):
        x = data[:, 0]*nano_sec
        for i in range(1, data.shape[1]):
            ax.plot(x, data[:, i])

        # ax.add_patch(plt.Rectangle((300, 15), 50, 10))
        ax.set_xlabel('time in sec')
        ax.set_ylabel(y_label)

        if add_time_stamps:
            self.plot_mic_events((np.amin(data[:, 1:]), np.amax(data[:, 1:])), ax)
            self.plot_hand_wash_events((np.amin(data[:, 1:]), np.amax(data[:, 1:])), ax)


    def plot_data(self, generate_image=False):

        fig, axs = plt.subplots(3, 1, sharex=True, figsize=(20, 15))

        self.sub_plot_data(self.data_dict['Acceleration'], axs[0])
        self.sub_plot_data(self.data_dict['Gyroscope'], axs[1])
        self.sub_plot_data(self.data_dict['battery'], axs[2], 'percentage', False)
        axs[2].set_ylim([0, 105])

        axs[0].set_title('Acceleration')
        axs[1].set_title('Gyroscope')
        axs[2].set_title('Battery')


        plt.xlim([0, self.data_dict['Acceleration'][-1, 0]*nano_sec])

        fig.tight_layout()
        if generate_image:
            fig.savefig(os.path.join(self.folder_name, "data_plot.png"), dpi=500)
        plt.show()

    def plot_timings(self, generate_image=False):
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

        self.plot_mic_events((np.amin(y[1:]), np.amax(y[1:])), ax, 1 / 60)
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

    def get_acceleration_data(self, as_json=False):
        if not as_json:
            return self.data_dict['Acceleration']
        else:
            return json.dumps(self.data_dict['Acceleration'])




if __name__ == "__main__":
    use_mkv = False
    if len(sys.argv) > 2:
        if sys.argv[2] == 'mkv':
            use_mkv = True
    data_processor = DataProcessor(sys.argv[1], use_mkv)
    data_processor.plot_data(True)
    # data_processor.plot_timings()
    print("Idle time:", data_processor.calc_idle_time()/60, " min\t Total time:",
          data_processor.calc_total_time()/60, " min \t -> ",
          (data_processor.calc_idle_time() / data_processor.calc_total_time())*100, "% lost")


    
    # data_list = read_csv(sys.argv[1])
    # plot_data(data_list)
    # plot_timings(data_list)
