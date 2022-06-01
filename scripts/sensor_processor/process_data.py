#!/usr/bin/python3
import json
import os
import time
from typing import Dict, List, Union

import numpy as np
import matplotlib as mpl
import matplotlib.pyplot as plt
import csv
import sys
from os import listdir
from os.path import isfile, join, splitext
from enum import Enum

from sensor_processor.helpers import *
from sensor_processor.sensor_decoder import SensorDecoder
from sensor_processor.mic_decoder import MicDecoder
from sensor_processor.battery_decoder import BatteryDecoder
from sensor_processor.handwash_decoder import HandwashDecoder
from sensor_processor.marker_decoder import MarkerDecoder
from sensor_processor.prediction_decoder import PredictionDecoder
from sensor_processor.evaluation_decoder import EvaluationDecoder
from sensor_processor.bluetooth_decoder import BluetoothDecoder

if len(sys.argv) > 2 and sys.argv[2] == 'mkv':
    from mkv_decode import MKVDecoder

nano_sec = 0.000000001

mpl.rcParams['agg.path.chunksize'] = 20000

class RecordingEntry(Enum):
    ACCELERATION = 1
    GYROSCOPE = 2

    MICTIMESTAMPS = 3
    BATTERY = 4
    MANUALWHTS = 5
    MARKERS = 6
    PREDICTIONS = 7
    EVALUATIONS = 8
    BLUETOOTHBEACONS = 9




class DataProcessor:

    def __init__(self, folder_name, time_offset=0, init_all=True):
        self.folder_name = folder_name
        self.time_offset = time_offset
        self.sensor_decoder = SensorDecoder(folder_name)
        self.data_dict: Dict[RecordingEntry, np.ndarray] = dict()
        self.sensor_decoder.find_min_max_cheap(['acc', 'gyro'])

        if init_all:
            for entry in RecordingEntry:
                self.read_entry(entry)

    def read_entry(self, entry: RecordingEntry, use_numpy_caching=False):
        if entry == RecordingEntry.MICTIMESTAMPS:
            self.read_mic_ts()
        if entry == RecordingEntry.BATTERY:
            self.read_battery()
        if entry == RecordingEntry.MANUALWHTS:
            self.read_manual_hw_ts()
        if entry == RecordingEntry.MARKERS:
            self.read_marker()
        if entry == RecordingEntry.PREDICTIONS:
            self.read_predictions()
        if entry == RecordingEntry.EVALUATIONS:
            self.read_evaluations()
        if entry == RecordingEntry.BLUETOOTHBEACONS:
            self.read_bluetooth_beacons()
        if entry == RecordingEntry.ACCELERATION:
            self.read_acceleration(use_numpy_caching=use_numpy_caching)
        if entry == RecordingEntry.GYROSCOPE:
            self.read_gyroscope(use_numpy_caching=use_numpy_caching)

    def __getitem__(self, key: RecordingEntry):
        if isinstance(key, RecordingEntry):
            return self.data_dict[key]
        return None

    def find_numpy_file_of(self, file_part):
        for f in listdir(self.folder_name):
            if f[0] == '.' and file_part in f and splitext(f)[1] == '.npy':
                return os.path.join(self.folder_name, f)
        return None

    def read_acceleration(self, use_numpy_caching=False):
        cached_numpy_file = None
        if use_numpy_caching:
                cached_numpy_file = self.find_numpy_file_of('acc')
        if cached_numpy_file is not None:
            self.data_dict[RecordingEntry.ACCELERATION] = np.load(cached_numpy_file)
        else:
            self.data_dict[RecordingEntry.ACCELERATION] = self.sensor_decoder.read_data('acc')
            self.data_dict[RecordingEntry.ACCELERATION] = align_array(self.data_dict[RecordingEntry.ACCELERATION],
                                                         self.sensor_decoder.min_time_stamp)
            if use_numpy_caching:
                cached_numpy_file = '.android_sensor_accelerometer_cache.npy'
                cached_numpy_file = os.path.join(self.folder_name, cached_numpy_file)
                np.save(cached_numpy_file, self.data_dict[RecordingEntry.ACCELERATION])

    def read_gyroscope(self, use_numpy_caching=False):
        cached_numpy_file = None
        if use_numpy_caching:
            cached_numpy_file = self.find_numpy_file_of('gyro')
        if cached_numpy_file is not None:
            self.data_dict[RecordingEntry.GYROSCOPE] = np.load(cached_numpy_file)
        else:
            self.data_dict[RecordingEntry.GYROSCOPE] = self.sensor_decoder.read_data('gyro')
            self.data_dict[RecordingEntry.GYROSCOPE] = align_array(self.data_dict[RecordingEntry.GYROSCOPE],
                                                                      self.sensor_decoder.min_time_stamp)
            if use_numpy_caching:
                cached_numpy_file = '.android_sensor_gyroscope_cache.npy'
                cached_numpy_file = os.path.join(self.folder_name, cached_numpy_file)
                np.save(cached_numpy_file, self.data_dict[RecordingEntry.GYROSCOPE])


    def read_mic_ts(self):
        self.data_dict[RecordingEntry.MICTIMESTAMPS] = MicDecoder.read_folder(self.folder_name)
        self.data_dict[RecordingEntry.MICTIMESTAMPS] = align_array(self.data_dict[RecordingEntry.MICTIMESTAMPS],
                                                        self.sensor_decoder.min_time_stamp)

    def read_battery(self):
        self.data_dict[RecordingEntry.BATTERY] = BatteryDecoder.read_folder(self.folder_name)
        self.data_dict[RecordingEntry.BATTERY] = BatteryDecoder.extend_battery_values(self.sensor_decoder.min_time_stamp,
                                                                         self.sensor_decoder.max_time_stamp,
                                                                         self.data_dict[RecordingEntry.BATTERY])
        self.data_dict[RecordingEntry.BATTERY] = align_array(self.data_dict[RecordingEntry.BATTERY], self.sensor_decoder.min_time_stamp)

    def read_manual_hw_ts(self):
        self.data_dict[RecordingEntry.MANUALWHTS] = HandwashDecoder.read_data(self.folder_name)
        self.data_dict[RecordingEntry.MANUALWHTS] = align_array(self.data_dict[RecordingEntry.MANUALWHTS],
                                                    self.sensor_decoder.min_time_stamp)

    def read_marker(self):
        self.data_dict[RecordingEntry.MARKERS] = MarkerDecoder.read_data(self.folder_name)
        self.data_dict[RecordingEntry.MARKERS] = align_array(self.data_dict[RecordingEntry.MARKERS],
                                                self.sensor_decoder.min_time_stamp)

    def read_predictions(self):
        self.data_dict[RecordingEntry.PREDICTIONS] = PredictionDecoder.read_folder(self.folder_name)
        self.data_dict[RecordingEntry.PREDICTIONS] = align_array(self.data_dict[RecordingEntry.PREDICTIONS],
                                                                 self.sensor_decoder.min_time_stamp)

    def read_evaluations(self):
        self.data_dict[RecordingEntry.EVALUATIONS] = EvaluationDecoder.read_folder(self.folder_name)
        self.data_dict[RecordingEntry.EVALUATIONS] = align_array(self.data_dict[RecordingEntry.EVALUATIONS],
                                                    self.sensor_decoder.min_time_stamp)

    def read_bluetooth_beacons(self):
        self.data_dict[RecordingEntry.BLUETOOTHBEACONS] = BluetoothDecoder.read_folder(self.folder_name)
        self.data_dict[RecordingEntry.BLUETOOTHBEACONS] = align_array(self.data_dict[RecordingEntry.BLUETOOTHBEACONS],
                                                          self.sensor_decoder.min_time_stamp)



    def map_right_hand_to_left(self):
        #tmp_x = self.data_dict[RecordingEntry.ACCELERATION][:, 1].copy()
        #tmp_z = self.data_dict[RecordingEntry.ACCELERATION][:, 3].copy()

        # self.data_dict[RecordingEntry.ACCELERATION][:, 1] = self.data_dict[RecordingEntry.ACCELERATION][:, 2]
        # self.data_dict[RecordingEntry.ACCELERATION][:, 3] = tmp_x
        # self.data_dict[RecordingEntry.ACCELERATION][:, 2] = tmp_z

        self.data_dict[RecordingEntry.ACCELERATION][:, 1] *= -1
        #self.data_dict[RecordingEntry.ACCELERATION][:, 3] *= -1
        # self.data_dict[RecordingEntry.ACCELERATION][:, 2] *= -1
        #
        self.data_dict[RecordingEntry.GYROSCOPE][:, 1] *= -1
        # self.data_dict[RecordingEntry.GYROSCOPE][:, 2] *= -1

    def plot_hand_wash_events(self, dims, ax=None, scaling: float=1.0):
        if ax is None:
            ax = plt.gca()
        for ts in self.data_dict[RecordingEntry.MANUALWHTS]:
            # rect = plt.Rectangle([ts[0], dims[1]], 20, dims[1], facecolor='blue', alpha=0.5)
            # ax.add_patch(rect)
            # print(ts[0]*nano_sec)
            ax.add_patch(plt.Rectangle((ts[0]*nano_sec*scaling - 50*scaling, dims[0]), 50*scaling, (dims[1] * 1.2) - dims[0], facecolor='blue', alpha=0.3))
        ax.vlines(self.data_dict[RecordingEntry.MANUALWHTS][:, 0]*nano_sec*scaling, dims[0], dims[1] * 1.2, color='black')

    def plot_markers(self, dims, ax, scaling: float=1.0):
        ax.vlines(self.data_dict[RecordingEntry.MARKERS][:, 0]*nano_sec*scaling, dims[0], dims[1] * 1.2, color='indigo')

    def plot_mic_events(self, dims, ax=None, scaling: float=1.0):
        if ax is None:
            ax = plt.gca()
        # print(self.data_dict['mic_time_stamps'].shape)
        # print(self.data_dict['mic_time_stamps'][:, 0])
        ax.vlines(self.data_dict[RecordingEntry.MICTIMESTAMPS][:, 0]*nano_sec*scaling, dims[0], dims[1] * 1.2, color='pink')

    def sub_predictions(self, data, ax, add_time_stamps=True):
        x = data[:, 0]*nano_sec

        if add_time_stamps:
            self.plot_hand_wash_events((-5, 110), ax)

        ax.scatter(x, data[:, 1] * 100, c='blue', alpha=0.3, label='noise')
        ax.scatter(x, data[:, 2] * 100, c='red', alpha=0.8, label='handwash')
        ax.plot(x, data[:, 3] * 100, c='orange', label='mean')

        data = self.data_dict[RecordingEntry.EVALUATIONS]
        data[:, 0] *= nano_sec
        pos_data = data[data[:, 1] == 1]
        neg_data = data[data[:, 1] == 0]
        neutral_data = data[data[:, 1] == -1]
        ax.scatter(neutral_data[:, 0], np.full(neutral_data.shape[0], 50), c='grey', s=100, marker='>', alpha=0.8, label='neutral')
        ax.scatter(neg_data[:, 0], np.full(neg_data.shape[0], -5), c='purple', s=200, marker='^', alpha=1, label='no')
        ax.scatter(pos_data[:, 0], np.full(pos_data.shape[0], 105), c='green', s=200, marker='v', alpha=1, label='yes')

        data = self.data_dict[RecordingEntry.BLUETOOTHBEACONS]
        data[:, 0] *= nano_sec
        ax.scatter(data[:, 0], np.full(data.shape[0], 50), c='royalblue', s=100, marker='d', alpha=0.8,
                   label='bluetooth')


        # ax.add_patch(plt.Rectangle((300, 15), 50, 10))
        ax.set_xlabel('time')
        ax.set_ylabel('percentage')
        ax.legend()

    def sub_plot_data(self, data, ax, y_label='value', add_time_stamps=True):
        x = data[:, 0]*nano_sec
        for i in range(1, data.shape[1]):
            ax.plot(x, data[:, i], label=f'axes {i-1}')

        # ax.add_patch(plt.Rectangle((300, 15), 50, 10))
        ax.set_xlabel('time')
        ax.set_ylabel(y_label)

        if add_time_stamps:
            self.plot_mic_events((np.amin(data[:, 1:]), np.amax(data[:, 1:])), ax)
            self.plot_hand_wash_events((np.amin(data[:, 1:]), np.amax(data[:, 1:])), ax)



    def plot_data(self, generate_image=False):

        fig, axs = plt.subplots(4, 1, sharex=True, figsize=(20, 15))

        self.sub_plot_data(self.data_dict[RecordingEntry.ACCELERATION], axs[0])
        self.sub_plot_data(self.data_dict[RecordingEntry.GYROSCOPE], axs[1])
        self.sub_plot_data(self.data_dict[RecordingEntry.BATTERY], axs[2], 'percentage', False)
        self.sub_predictions(self.data_dict[RecordingEntry.PREDICTIONS], axs[3])
        axs[2].set_ylim([0, 105])

        axs[0].set_title('Acceleration')
        axs[1].set_title('Gyroscope')
        axs[2].set_title('Battery')
        axs[3].set_title('Predictions')

        self.plot_markers((np.amin(self.data_dict[RecordingEntry.ACCELERATION][:, 1:]), np.amax(self.data_dict[RecordingEntry.ACCELERATION][:, 1:])), axs[0])
        self.plot_markers((-1, 110), axs[3])


        plt.xlim([0, self.data_dict[RecordingEntry.ACCELERATION][-1, 0]*nano_sec])

        formatter = mpl.ticker.FuncFormatter(lambda s, x: time.strftime('%H:%M:%S', time.gmtime(s + self.time_offset)))
        axs[0].xaxis.set_major_formatter(formatter)
        # plt.gcf().autofmt_xdate()

        fig.legend()
        fig.tight_layout()
        if generate_image:
            fig.savefig(os.path.join(self.folder_name, "data_plot.png"), dpi=500)
            # fig.savefig(os.path.join(self.folder_name, "data_plot.svg"))
        plt.show()

    def plot_timings(self, generate_image=False):
        data = self.data_dict[RecordingEntry.ACCELERATION]
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
        idle_time = np.sum(np.diff(self.data_dict[RecordingEntry.ACCELERATION][:, 0]) * nano_sec)
        # print("sum:", idle_time, '-', '0.02 *', self.data_dict['Acceleration'].shape[0], '=', 0.02 * self.data_dict['Acceleration'].shape[0])
        idle_time -= 0.02 * self.data_dict[RecordingEntry.ACCELERATION].shape[0]
        return idle_time


    def calc_total_time(self):
        return (np.max(self.data_dict[RecordingEntry.ACCELERATION][:, 0]) - np.min(self.data_dict[RecordingEntry.ACCELERATION][:, 0])) * nano_sec


    def calc_prediction_ratio(self):
        pos_pred = self.data_dict[RecordingEntry.PREDICTIONS][:, 2] >= 0.5
        neg_pred = self.data_dict[RecordingEntry.PREDICTIONS][:, 2] < 0.5
        print(pos_pred, neg_pred, np.sum(pos_pred), np.sum(neg_pred), np.sum(pos_pred)/len(pos_pred))

    def get_acceleration_data(self, as_json=False):
        if not as_json:
            return self.data_dict[RecordingEntry.ACCELERATION]
        else:
            return json.dumps(self.data_dict[RecordingEntry.ACCELERATION])


    def export_numpy_array(self):
        print("Acc shape:", self.data_dict[RecordingEntry.ACCELERATION].shape[0])
        print("Gyroscope shape:", self.data_dict[RecordingEntry.GYROSCOPE].shape[0])

        data_size = min(self.data_dict[RecordingEntry.ACCELERATION].shape[0], self.data_dict[RecordingEntry.GYROSCOPE].shape[0])
        export_data = np.ndarray((data_size, 6))

        print(export_data.shape)
        export_data[:data_size, :3] = self.data_dict[RecordingEntry.ACCELERATION][:data_size, 1:]
        export_data[:data_size, 3:] = self.data_dict[RecordingEntry.GYROSCOPE][:data_size, 1:]

        plt.plot(np.arange(data_size), export_data[:, 5])
        plt.show()
        with open('test_data.npy', 'wb') as f:
            np.save(f, export_data)



if __name__ == "__main__":
    use_mkv = False
    # if len(sys.argv) > 2:
    #     if sys.argv[2] == 'mkv':
    #         use_mkv = True
    data_processor = DataProcessor(sys.argv[1], init_all=True)
    print(data_processor.sensor_decoder.min_time_stamp)
    data_processor.plot_data()

    data_processor = DataProcessor(sys.argv[2], init_all=True)
    print(data_processor.sensor_decoder.min_time_stamp)
    data_processor.map_right_hand_to_left()
    data_processor.plot_data()
    # data_processor.plot_timings()
    # data_processor.export_numpy_array()
    #data_processor.calc_prediction_ratio()
    # print("Idle time:", data_processor.calc_idle_time()/60, " min\t Total time:",
    #       data_processor.calc_total_time()/60, " min \t -> ",
    #       (data_processor.calc_idle_time() / data_processor.calc_total_time())*100, "% lost")


    
    # data_list = read_csv(sys.argv[1])
    # plot_data(data_list)
    # plot_timings(data_list)
