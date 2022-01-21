#!/usr/bin/python3
import sys
from os import listdir
from os.path import join, isfile, splitext
from typing import Dict, List

import numpy as np
from sensor_processor.helpers import *

nano_sec = 0.000000001


class EvaluationDecoder:

    @classmethod
    def read_folder(cls, folder_name: str) -> np.ndarray:
        evaluations = read_csvs_in_folder(folder_name, 'evaluation', 5)
        evaluations = evaluations.astype(int)
        clean_evaluations = dict()
        for evaluation in evaluations:
            if evaluation[1] == -1:
                if evaluation[0] not in clean_evaluations:
                    clean_evaluations[evaluation[0]] = evaluation
            else:
                clean_evaluations[evaluation[0]] = evaluation

        return np.asarray(list(clean_evaluations.values()), dtype=np.float)


if __name__ == '__main__':
    print("Read:", sys.argv[1])

    decoder = EvaluationDecoder()
    data = decoder.read_folder(sys.argv[1])
    print(data)
