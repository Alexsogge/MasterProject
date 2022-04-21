import numpy as np
import matplotlib.pyplot as plt
from pathlib import Path
import torch
from torch import nn
import os

from personalization_tools.personalizer import Personalizer
from personalization_tools.sensor_recorder_data_reader import SensorRecorderDataReader
from personalization_tools.dataset import Dataset, RecordedDataset
from personalization_tools.globals import Indicators
from personalization_tools.helpers import generate_predictions
from personalization_tools.models import HandWashingDeepConvLSTMA
from personalization_tools.quality_estimation import calc_best_running_mean_settings, plot_quality_comparison, \
    plot_hw_feedback_areas
from personalization_tools.pseudo_model_settings import pseudo_model_settings

from typing import TYPE_CHECKING, List, Dict

if TYPE_CHECKING:
    from models import RecordingForPersonalization, Recording, Personalization, RecordingTag


def build_datasets_from_recordings(recordings: List['RecordingForPersonalization']) -> Dict['RecordingForPersonalization', Dataset]:
    collection = dict()
    if len(recordings) == 0:
        return collection
    root_path = Path(recordings[0].recording.path).parent.absolute()
    record_reader = SensorRecorderDataReader(root_path)
    recording_names = [recording.recording.base_name for recording in recordings]
    datasets = record_reader.get_collection(recording_names)
    for recording, dataset in zip(recordings, datasets):
        collection[recording] = dataset
    return collection


def clean_collection(collection: Dict['RecordingForPersonalization', Dataset]):
    for recording, dataset in collection.items():
        indicators = dataset.get_indicators()
        num_hw_flags = np.count_nonzero(indicators[1][:, 1] == Indicators.HAND_WASH)
        if num_hw_flags == 0:
            recording.unusable = True


def split_test_from_collection(collection: Dict['RecordingForPersonalization', Dataset], current_iteration: int,
                               existing_test_recordings: Dict['RecordingForPersonalization', Dataset],
                               evaluation_tag: 'RecordingTag') -> Dict['RecordingForPersonalization', Dataset]:
    collection_keys: List['RecordingForPersonalization'] = list(collection.keys())
    test_recordings = existing_test_recordings

    for collection_key in collection_keys[:]:
        if evaluation_tag in collection_key.recording.tags:
            test_recordings[collection_key] = collection.pop(collection_key, None)
            collection_key.used_for_testing = True
            collection_keys.remove(collection_key)


    if (len(collection_keys) + current_iteration) * 0.2 > len(test_recordings):
        target_part = int(len(collection_keys) * 0.2)+1
        for i in range(0, target_part):
            if len(collection[collection_keys[i]].feedback_areas.labeled_regions_hw) > 1 and len(collection[collection_keys[i]].feedback_areas.labeled_regions_noise) > 1:
                collection_keys[i].used_for_testing = True
                test_recordings[collection_keys[i]] = collection.pop(collection_keys[i], None)
            else:
                target_part += 1
                if target_part == len(collection_keys) + 1:
                    break

    return test_recordings

def process_collection(collection: List[Dataset], base_model_path: str):
    predictions = generate_predictions(collection, base_model_path)
    for dataset in collection:
        dataset.generate_feedback_areas(prediction=predictions[dataset.name])

def personalize_model(collection: Dict['RecordingForPersonalization', Dataset], base_model_path: str,
                      target_model_path: str, target_filter: str):
    personalizer = Personalizer()
    personalizer.initialize(base_model_path)

    for recording_entry in collection.keys():
        recording_entry.used_for_training = True

    print('generate pseudo labels with setting:', target_filter)
    for dataset in collection.values():
        dataset.apply_pseudo_label_generators(pseudo_model_settings[target_filter])

    personalizer.incremental_learn_series_pseudo(list(collection.values()), save_model_as=target_model_path, epochs=100)


def convert_pytorch_to_onnx(model_path: str) -> str:
    onnx_model_path = os.path.splitext(model_path)[0] + '.onnx'
    model = HandWashingDeepConvLSTMA(input_shape=6)
    model.load_state_dict(torch.load(model_path))
    model = nn.Sequential(model, nn.Softmax(dim=-1))
    dummy_x = torch.zeros(100, 150, 6)
    dummy_input = dummy_x.to("cpu")
    torch.onnx.export(model, dummy_input, onnx_model_path, input_names=['input'],
                      output_names=['output'], dynamic_axes={'input': {0: 'batch_size'}, 'output': {0: 'batch_size'}})
    return onnx_model_path


def convert_onnx_to_ort(model_path: str) -> str:
    ort_model_path = os.path.splitext(model_path)[0] + '.ort'
    os.system('python -m onnxruntime.tools.convert_onnx_models_to_ort ' + model_path)
    return ort_model_path


def calc_model_settings(test_collection: List[Dataset], base_model_path, new_model_path):
    return calc_best_running_mean_settings(test_collection, base_model_path, new_model_path, 20, 0.59)


def create_personalization_quality_test_plot(dataset, base_model, inc_model, kernel_size, kernel_threshold, fig_name):
    fig = plot_quality_comparison(dataset, base_model, inc_model, kernel_size, kernel_threshold)
    fig.savefig(fig_name, format='svg', dpi=300)

def create_personalization_pseudo_plot(dataset, fig_name):
    fig = plot_hw_feedback_areas(dataset)
    fig.savefig(fig_name, format='svg', dpi=300)

def create_manual_prediction(recording: 'Recording', personalization: 'Personalization', base_model_path: str, fig_name: str):
    root_path = Path(recording.path).parent.absolute()
    record_reader = SensorRecorderDataReader(root_path)
    dataset = record_reader.get_data_set(recording.base_name)
    fig = plot_quality_comparison(dataset, base_model_path, personalization.model_torch_path,
                                  kernel_size=personalization.mean_kernel_width,
                                  kernel_threshold=personalization.mean_threshold,
                                  add_predictions=True)

    fig.savefig(fig_name, format='svg', dpi=300)







