import os
import numpy as np
from sensor_processor.process_data import DataProcessor, RecordingEntry

from typing import TYPE_CHECKING
if TYPE_CHECKING:
    from models import Recording

class DataFactory:
    sensor_data_file_name = 'sensor_data.npy'
    sensor_data_flattened_file_name = 'sensor_data_flattened.npy'

    def __init__(self, recording: 'Recording', init_all=True):
        self.recording = recording
        self.path = recording.path
        self.init_all = init_all
        self._data_processor = None

    @property
    def data_processor(self):
        if self._data_processor is None:
            self._data_processor = DataProcessor(self.path, init_all=self.init_all)
        return self._data_processor

    def generate_np_sensor_data_file(self):
        data_size = min(self.data_processor.data_dict[RecordingEntry.ACCELERATION].shape[0], self.data_processor.data_dict[RecordingEntry.GYROSCOPE].shape[0])
        data_array = np.ndarray((data_size, 6))

        data_array[:data_size, :3] = self.data_processor.data_dict[RecordingEntry.ACCELERATION][:data_size, 1:]
        data_array[:data_size, 3:] = self.data_processor.data_dict[RecordingEntry.GYROSCOPE][:data_size, 1:]

        with open(os.path.join(self.path, self.sensor_data_file_name), 'wb') as f:
            np.save(f, data_array)

        with open(os.path.join(self.path, self.sensor_data_flattened_file_name), 'wb') as f:
            np.save(f, data_array.flatten())

    def read_stat_files(self):
        self.data_processor.read_entry(RecordingEntry.EVALUATIONS)
        self.data_processor.read_entry(RecordingEntry.MANUALWHTS)


    def get_evaluations(self):
        return self.data_processor.data_dict[RecordingEntry.EVALUATIONS]

    def get_manual_hw_ts(self):
        return self.data_processor.data_dict[RecordingEntry.MANUALWHTS]

    def calc_variance(self):
        if RecordingEntry.ACCELERATION not in self.data_processor.data_dict:
            self.data_processor.read_entry(RecordingEntry.ACCELERATION)
        acc_data = self.data_processor.data_dict[RecordingEntry.ACCELERATION]
        variance = np.var(acc_data[:, 1:], axis=0)
        return variance





