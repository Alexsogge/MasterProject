import os

from sensor_processor.process_data import DataProcessor, RecordingEntry
import numpy as np
import json
import dateutil.parser as dp

nano_sec = 0.000000001


def generate_plot_data(folder_name):
    time_offset = 0
    meta_info_file = None
    for file in os.listdir(folder_name):
        if os.path.splitext(file)[1] == '.json' and 'metaInfo' in file:
            meta_info_file = os.path.join(folder_name, file)
    if meta_info_file is not None:
        with open(meta_info_file) as json_file:
            try:
                meta_info = json.load(json_file)
                if 'date' in meta_info:
                    parsed_t = dp.parse(meta_info.get('date'))
                    time_offset = parsed_t.timestamp()
            except json.JSONDecodeError as e:
                print(e.__traceback__)

    processor = DataProcessor(folder_name, time_offset=time_offset)
    processor.plot_data(True)


def get_data_array(folder_name):
    processor = DataProcessor(folder_name)
    data_array: np.ndarray = processor.get_acceleration_data()
    data_array[:, 0] /= 1000000

    hand_wash_time_stamps = processor.data_dict[RecordingEntry.MANUALWHTS]
    hand_wash_time_stamps /= 1000000

    marker_time_stamps = processor.data_dict[RecordingEntry.MARKERS]
    marker_time_stamps /= 1000000

    predictions = processor.data_dict[RecordingEntry.PREDICTIONS]
    predictions[:, 0] /= 1000000

    evaluations = processor.data_dict[RecordingEntry.EVALUATIONS]
    evaluations[:, 0] /= 1000000

    bluetooth = processor.data_dict[RecordingEntry.BLUETOOTHBEACONS]
    bluetooth[:, 0] /= 1000000

    return data_array, hand_wash_time_stamps, marker_time_stamps, predictions, evaluations, bluetooth
