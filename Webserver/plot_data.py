import os
from typing import Dict, List

from preview_builder import generate_plot_data, get_data_array
import numpy as np
import time

from typing import TYPE_CHECKING
if TYPE_CHECKING:
    from models import Recording

def get_index_of_ts(time_stamp, data):
    return np.searchsorted(data, time_stamp)


class PlotData:

    def __init__(self, recording: 'Recording'):
        self.recording = recording
        self.path = recording.path
        self.last_access = time.time()

        if os.path.exists(os.path.join(self.path, 'data_array_acc.npy')):
            self.recording_data_array, self.hand_wash_time_stamps, self.marker_time_stamps, self.predictions, self.evaluations, self.bluetooth = self.load_data_array()
        else:
            self.recording_data_array, self.hand_wash_time_stamps, self.marker_time_stamps, self.predictions, self.evaluations, self.bluetooth = self.create_new_data_array()
        self.time_range = self.recording_data_array[-1, 0] - self.recording_data_array[0, 0]
        self.annotations, self.time_stamp_series = self.build_annotations()

    def load_data_array(self):
        recording_array = np.load(os.path.join(self.path, 'data_array_acc.npy'))
        hand_wash_array = np.load(os.path.join(self.path, 'data_array_hand_wash.npy'))
        marker_array = np.load(os.path.join(self.path, 'data_array_marker.npy'))
        prediction_array = np.load(os.path.join(self.path, 'data_array_prediction.npy'))
        evaluation_array = np.load(os.path.join(self.path, 'data_array_evaluation.npy'))
        bluetooth_array = np.load(os.path.join(self.path, 'data_array_bluetooth.npy'))
        return recording_array, hand_wash_array, marker_array, prediction_array, evaluation_array, bluetooth_array

    def create_new_data_array(self):
        recording_array, hand_wash_array, marker_array, prediction_array, evaluation_array, bluetooth_array = get_data_array(self.path)
        np.save(os.path.join(self.path, 'data_array_acc.npy'), recording_array)
        np.save(os.path.join(self.path, 'data_array_hand_wash.npy'), hand_wash_array)
        np.save(os.path.join(self.path, 'data_array_marker.npy'), marker_array)
        np.save(os.path.join(self.path, 'data_array_prediction.npy'), prediction_array)
        np.save(os.path.join(self.path, 'data_array_evaluation.npy'), evaluation_array)
        np.save(os.path.join(self.path, 'data_array_bluetooth.npy'), bluetooth_array)
        return recording_array, hand_wash_array, marker_array, prediction_array, evaluation_array, bluetooth_array

    @staticmethod
    def slice_data(start_ts, end_ts, data):
        start_i = get_index_of_ts(start_ts, data[:, 0])
        end_i = get_index_of_ts(end_ts, data[:, 0])
        return data[start_i:end_i]

    def get_sliced_data(self, start_ts, end_ts):
        return (self.slice_data(start_ts, end_ts, self.recording_data_array),
                self.slice_data(start_ts, end_ts, self.hand_wash_time_stamps),
                self.slice_data(start_ts, end_ts, self.predictions),
                self.slice_data(start_ts, end_ts, self.evaluations),
                self.slice_data(start_ts, end_ts, self.bluetooth),
                self.slice_data(start_ts, end_ts, self.marker_time_stamps),
                )

    def get_series(self, start, end):
        start_time_stamp = self.time_range * start
        end_time_stamp = self.time_range * end
        # data_array = self.get_sliced_data(start_time_stamp, end_time_stamp)
        data = self.get_sliced_data(start_time_stamp, end_time_stamp)
        print("plot daste markers:", self.marker_time_stamps)
        series: Dict[str, Dict] = dict()

        acc_entry = dict()
        acc_entry['ts'] = data[0][:, 0].tolist()
        acc_entry['vals'] = dict()
        for i, label in enumerate(('x', 'y', 'z')):
            acc_entry['vals'][label] = data[0][:, i+1].tolist()
        self.last_access = time.time()

        series['acc'] = acc_entry
        series['hw'] = {'ts': data[1][:,0][data[1][:, 0] != 0].tolist()}
        series['pred'] = {'ts': data[2][:, 0].tolist(), 'noise': data[2][:, 1].tolist(), 'hw': data[2][:, 2].tolist(),
                          'mean': data[2][:, 3].tolist(), 'pred': data[2][:, 4].tolist()}
        series['eval'] = {'ts': data[3][:, 0].tolist(), 'answer': data[3][:, 1].tolist(), 'compulsive': data[3][:, 2].tolist(), 'tense': data[3][:, 3].tolist(), 'urge': data[3][:, 4].tolist()}
        series['bluetooth'] = {'ts': data[4][:, 0].tolist(), 'rssi': data[4][:, 1].tolist(), 'dist': data[4][:, 2].tolist()}
        series['marker'] = {'ts': data[5][:, 0].tolist(), 'val': data[5][:, 1].tolist()}

        return series

    def build_annotations(self):
        annotations = []
        labels = []
        time_stamp_series = dict()
        time_stamp_series['name'] = 'hand wash'
        time_stamp_series['data'] = []
        for i, time_stamp in enumerate(self.hand_wash_time_stamps[:, 0]):
            time_stamp_series['data'].append(
                {'x': time_stamp, 'y': 13, 'id': f'ts_{i}', 'marker': {'fillColor': '#BF0B23', 'radius': 10}})
            # annotation_entry = {'type': 'verticalLine', 'typeOptions': {'point': f'ts_{i}'}}
            # annotations.append(annotation_entry)
            labels.append({'point': {'xAxis': 0, 'yAxis': 0, 'x': time_stamp, 'y': 15}, 'text': f'hand wash {i}'})

        for i, time_stamp in enumerate(self.predictions[:, 0]):
            if self.predictions[i, 1] > self.predictions[i, 2]:
                labels.append({'point': {'xAxis': 0, 'yAxis': 0, 'x': time_stamp, 'y': 15}, 'text': f'n'})
            else:
                labels.append({'point': {'xAxis': 0, 'yAxis': 0, 'x': time_stamp, 'y': 15}, 'text': f'hw'})

        annotations.append({'draggable': '', 'labelOptions': {'backgroundColor': 'rgba(255,255,255,0.5)',
                                                              'verticalAlign': 'top', 'y': 15},
                            'labels': labels})

        return annotations, time_stamp_series
        # return {'type': 'flags', 'data': annotations, 'onSeries': '0', 'shape': 'circlepin', 'width': 16,}, time_stamp_series
