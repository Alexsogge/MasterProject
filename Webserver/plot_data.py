from typing import Dict, List

from preview_builder import generate_plot_data, get_data_array
import numpy as np
import time

class PlotData:

    def __init__(self, recording_id, path):
        self.recording_id = recording_id
        self.path = path
        self.last_access = time.time()

        self.recording_data_array, self.hand_wash_time_stamps = get_data_array(path)
        self.time_range = self.recording_data_array[-1, 0] - self.recording_data_array[0, 0]
        self.annotations, self.time_stamp_series = self.build_annotations()


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
            series.append(series_entry)

        self.last_access = time.time()
        return series

    def build_annotations(self):
        annotations = []
        time_stamp_series = dict()
        time_stamp_series['name'] = 'hand wash'
        time_stamp_series['data'] = []
        for i, time_stamp in enumerate(self.hand_wash_time_stamps[:, 0]):
            time_stamp_series['data'].append(
                {'x': time_stamp, 'y': 13, 'id': f'ts_{i}', 'marker': {'fillColor': '#BF0B23', 'radius': 10}})
            annotation_entry = {'type': 'verticalLine', 'typeOptions': {'point': f'ts_{i}'}}
            annotations.append(annotation_entry)

        return annotations, time_stamp_series







