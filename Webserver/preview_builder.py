from sensor_processor.process_data import DataProcessor
import numpy as np

nano_sec = 0.000000001

def generate_plot_data(folder_name):
    processor = DataProcessor(folder_name)
    processor.plot_data(True)


def get_data_array(folder_name):
    processor = DataProcessor(folder_name)
    data_array: np.ndarray = processor.get_acceleration_data()
    data_array[:, 0] /= 1000000

    hand_wash_time_stamps = processor.data_dict['time_stamps']
    hand_wash_time_stamps /= 1000000

    predictions = processor.data_dict['predictions']
    predictions[:, 0] /= 1000000

    evaluations = processor.data_dict['evaluations']
    evaluations[:, 0] /= 1000000

    return data_array, hand_wash_time_stamps, predictions, evaluations
