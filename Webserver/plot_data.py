import os
from typing import Dict, List

from preview_builder import generate_plot_data, get_data_array
import numpy as np
import time

class PlotData:

    def __init__(self, recording_id, path):
        self.recording_id = recording_id
        self.path = path
        self.last_access = time.time()

        if (os.path.exists(os.path.join(path, 'data_array_acc.npy'))):
            self.recording_data_array, self.hand_wash_time_stamps = self.load_data_array()
        else:
            self.recording_data_array, self.hand_wash_time_stamps = self.create_new_data_array()
        self.time_range = self.recording_data_array[-1, 0] - self.recording_data_array[0, 0]
        self.annotations, self.time_stamp_series = self.build_annotations()


    def load_data_array(self):
        recording_array = np.load(os.path.join(self.path, 'data_array_acc.npy'))
        hand_wash_array = np.load(os.path.join(self.path, 'data_array_hand_wash.npy'))
        return recording_array, hand_wash_array

    def create_new_data_array(self):
        recording_array, hand_wash_array = get_data_array(self.path)
        np.save(os.path.join(self.path, 'data_array_acc.npy'), recording_array)
        np.save(os.path.join(self.path, 'data_array_hand_wash.npy'), hand_wash_array)
        return recording_array, hand_wash_array

    def get_index_of_ts(self, time_stamp):
        return np.searchsorted(self.recording_data_array[:, 0], time_stamp)

    def get_sliced_data(self, start_ts, end_ts):
        start_i = self.get_index_of_ts(start_ts)
        end_i = self.get_index_of_ts(end_ts)

        return self.recording_data_array[start_i:end_i]

    def get_series(self, start, end):
        start_time_stamp = self.time_range * start
        end_time_stamp = self.time_range * end
        data_array = self.get_sliced_data(start_time_stamp, end_time_stamp)

        series: List[Dict] = list()

        for i in range(3):
            series_entry = dict()
            series_entry['name'] = 'axis ' + str(i)


            series_entry['data'] = data_array[:, [0, i + 1]].tolist()
            series_entry['id'] = str(i)
            series.append(series_entry)

        self.last_access = time.time()
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
            #annotation_entry = {'type': 'verticalLine', 'typeOptions': {'point': f'ts_{i}'}}
            #annotations.append(annotation_entry)
            labels.append({'point': {'xAxis': 0, 'yAxis': 0, 'x': time_stamp, 'y': 15}, 'text': f'hand wash {i}'})

        annotations.append({'draggable': '', 'labelOptions': {'backgroundColor': 'rgba(255,255,255,0.5)',
                                                              'verticalAlign': 'top', 'y': 15},
                           'labels': labels})


        return annotations, time_stamp_series
        # return {'type': 'flags', 'data': annotations, 'onSeries': '0', 'shape': 'circlepin', 'width': 16,}, time_stamp_series






