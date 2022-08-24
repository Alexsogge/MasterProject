import os
import numpy as np
import pandas as pd
from personalization_tools.pseudo_model_settings import pseudo_model_settings
from personalization_tools.dataset import RecordedDataset
from personalization_tools.helpers import generate_predictions
from sensor_processor.process_data import DataProcessor, RecordingEntry
from personalization_tools.sensor_recorder_data_reader import get_indicators

from typing import TYPE_CHECKING
if TYPE_CHECKING:
    from models import Recording

def find_nearest_index(array, value):
    array = np.asarray(array)
    idx = (np.abs(array - value)).argmin()
    return idx

class DataFactory:
    sensor_data_file_name = 'sensor_data.npy'
    sensor_data_flattened_file_name = 'sensor_data_flattened.npy'
    complete_dataset_file_name = 'complete_dataset'

    def __init__(self, recording: 'Recording', init_all=True, newest_torch_file=None):
        self.recording = recording
        self.path = recording.path
        self.init_all = init_all
        self.newest_torch_file = newest_torch_file
        self._data_processor = None

    @property
    def data_processor(self):
        if self._data_processor is None:
            self._data_processor = DataProcessor(self.path, init_all=self.init_all, use_numpy_caching=True)
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

    def generate_complete_dataset_file(self, pseudo_label_filter=None):
        header = ['timestamp', 'acc x', 'acc y', 'acc z', 'gyro x', 'gyro y', 'gyro z', 'battery', 'user yes/no', 'compulsive', 'tense', 'urge']
        data_size = min(self.data_processor.data_dict[RecordingEntry.ACCELERATION].shape[0], self.data_processor.data_dict[RecordingEntry.GYROSCOPE].shape[0])
        data_array = np.empty((data_size, len(header)))

        data_array[:data_size, 0] = self.data_processor.data_dict[RecordingEntry.ACCELERATION][:data_size, 0]
        data_array[:data_size, 1:4] = self.data_processor.data_dict[RecordingEntry.ACCELERATION][:data_size, 1:]
        data_array[:data_size, 4:7] = self.data_processor.data_dict[RecordingEntry.GYROSCOPE][:data_size, 1:]

        data_array[:, 7] = self.data_processor.data_dict[RecordingEntry.BATTERY][0, 1] + 1
        index_start = find_nearest_index(data_array[:, 0], self.data_processor.data_dict[RecordingEntry.BATTERY][0, 0])
        index_end = index_start
        for battery_entry in self.data_processor.data_dict[RecordingEntry.BATTERY][1:]:
            index_end = find_nearest_index(data_array[:, 0], battery_entry[0])
            #print('battery index', index_start, index_end)
            data_array[index_start:index_end, 7] = battery_entry[1]
            index_start = index_end
        data_array[index_end:] = self.data_processor.data_dict[RecordingEntry.BATTERY][-1, 0]

        df = pd.DataFrame(data_array[:, :8], columns=header[:8])
        # df.iloc[:, :7] = data_array[:, :7]
        df_eval = pd.DataFrame(columns=header[8:], index=range(data_array.shape[0]))
        indexes = []
        for evaluation in self.data_processor.data_dict[RecordingEntry.EVALUATIONS]:
            index = find_nearest_index(data_array[:, 0], evaluation[0])
            indexes.append(index)
            #print('evaluation index', index)
            data_array[index, 8:] = evaluation[1:]
            df_eval.iloc[index] = evaluation[1:]
        df = pd.concat((df, df_eval), axis=1)

        if pseudo_label_filter is not None:
            #print('newest torch file:', self.newest_torch_file)
            general_model = self.newest_torch_file
            y_values = np.zeros((data_size,))
            indicators = get_indicators(self.data_processor, 75)
            dataset = RecordedDataset('participant', data_array[:data_size, 1:7], y_values, indicators, self.recording.get_name())
            predictions = generate_predictions([dataset, ], general_model)
            dataset.generate_feedback_areas(prediction=predictions[dataset.name])
            dataset.apply_pseudo_label_generators(pseudo_model_settings[pseudo_label_filter])
            pseudo_labels = np.repeat(dataset.pseudo_labels.y_win, 75, axis=0)
            #print('yvalues:', y_values.shape, 'pseudo:', pseudo_labels.shape, dataset.pseudo_labels.y_win.shape, 'sensor:', data_array[:data_size, 1:7].shape, dataset.y_win.shape, dataset.y_data.shape)
            pseudo_labels = np.concatenate((pseudo_labels, np.ones((int(data_size-pseudo_labels.shape[0]), pseudo_labels.shape[1])) * pseudo_labels[-1]), axis=0)
            #print('yvalues:', y_values.shape, 'pseudo:', pseudo_labels.shape, dataset.pseudo_labels.y_win.shape,
            #      'sensor:', data_array[:data_size, 1:7].shape, dataset.y_win.shape, dataset.y_data.shape)
            df_pseudo = pd.DataFrame(pseudo_labels, columns=['pseudo null', 'pseudo hw'])
            df = pd.concat((df, df_pseudo), axis=1)

        #print('\t'.join(header))
        # np.savetxt(os.path.join(self.path, self.complete_dataset_file_name), data_array, delimiter='\t', header='\t'.join(header))
        compression_opts = dict(method='zip', archive_name=self.complete_dataset_file_name + '.csv')
        df[:-1].to_csv(os.path.join(self.path, self.complete_dataset_file_name + '.zip'), index=False, sep='\t', compression=compression_opts)

    def read_stat_files(self):
        self.data_processor.read_entry(RecordingEntry.EVALUATIONS, use_numpy_caching=True)
        self.data_processor.read_entry(RecordingEntry.MANUALWHTS, use_numpy_caching=True)


    def get_evaluations(self):
        return self.data_processor.data_dict[RecordingEntry.EVALUATIONS]

    def get_manual_hw_ts(self):
        return self.data_processor.data_dict[RecordingEntry.MANUALWHTS]

    def calc_variance(self):
        if RecordingEntry.ACCELERATION not in self.data_processor.data_dict:
            self.data_processor.read_entry(RecordingEntry.ACCELERATION, use_numpy_caching=True)
        acc_data = self.data_processor.data_dict[RecordingEntry.ACCELERATION]
        variance = np.var(acc_data[:, 1:], axis=0)
        return variance





