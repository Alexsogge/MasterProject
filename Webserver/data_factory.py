import os
import numpy as np
from sensor_processor.process_data import DataProcessor


class DataFactory:
    sensor_data_file_name = 'sensor_data.npy'
    sensor_data_flattened_file_name = 'sensor_data_flattened.npy'

    def __init__(self, recording_id, path):
        self.recording_id = recording_id
        self.path = path
        self._data_processor = None

    @property
    def data_processor(self):
        if self._data_processor is None:
            self._data_processor = DataProcessor(self.path)
        return self._data_processor

    def generate_np_sensor_data_file(self):
        data_size = min(self.data_processor.data_dict['Acceleration'].shape[0], self.data_processor.data_dict['Gyroscope'].shape[0])
        data_array = np.ndarray((data_size, 6))

        data_array[:data_size, :3] = self.data_processor.data_dict['Acceleration'][:data_size, 1:]
        data_array[:data_size, 3:] = self.data_processor.data_dict['Gyroscope'][:data_size, 1:]

        with open(os.path.join(self.path, self.sensor_data_file_name), 'wb') as f:
            np.save(f, data_array)

        with open(os.path.join(self.path, self.sensor_data_flattened_file_name), 'wb') as f:
            np.save(f, data_array.flatten())







